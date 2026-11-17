#!/usr/bin/env python3
"""把角色 webm 素材离线导出为 Android 可用的 WebP 帧序列 + manifest。

输入目录 pet-assets/videos/<group>/<name>.webm（黑底 VP9，与 dsh-pet 同源素材）。
处理流程（每段动画）：
  1. ffmpeg 解码 → colorkey 抠掉黑色背景 → 输出带 alpha 的 PNG 帧；
  2. 用 Pillow 计算该段不透明像素的包围盒，作为 crop_box；
  3. 编码为无损 WebP，落到 app 资产目录 pet/clips/<id>/f_0001.webp …；
  4. 汇总生成 pet/manifest.json（schema_version=1，含 canvas / body_box / clips / phrases）。

只处理运行必需的五个动作组：idle / move / turn / click / drag。
random / events 等冷集不打包，控制 APK 体积。

用法（在仓库根目录）：
  python3 scripts/export_android_assets.py \
      --output app/build/generated/petAssets \
      --res-output app/build/generated/petRes

也可直接指定源目录：
  python3 scripts/export_android_assets.py --videos /path/to/videos --output ...
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    print("需要 Pillow：pip install pillow", file=sys.stderr)
    raise

CORE_GROUPS = ("idle", "move", "turn", "click", "drag")
CANVAS = (640, 360)
# 与桌面端一致的身体定位框（源像素）。
BODY_BOX = [212, 60, 428, 330]
DEFAULT_PHRASES = [
    "好女孩……",
    "好模型……",
    "欧鲸鲸……",
    "今天也要认真工作呀。",
    "再陪你一会儿。",
]
# 每段动画抽帧比例：1 = 全部帧；2 = 隔帧。手机包体与解码预算有限，可按需下调。
FRAME_DROP = 3
CLIP_FPS = 24.0


def clip_id(group: str, source: Path) -> str:
    digest = hashlib.sha256(source.as_posix().encode("utf-8")).hexdigest()[:12]
    return f"{group}_{digest}"


def probe_duration(video: Path) -> float:
    try:
        out = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "csv=p=0", str(video)],
            capture_output=True, text=True, check=True)
        return float(out.stdout.strip())
    except Exception:
        return 4.0


def render_clip(video: Path, out_dir: Path, fps: float) -> tuple[int, list[list[float]]]:
    """解码一段 webm 为透明 WebP 帧，返回帧数与移动曲线（留空）。"""
    out_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        # colorkey 抠黑底：邻近黑色转透明，边缘做少量柔化。
        vf = "colorkey=black:0.12:0.08,format=rgba"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(video), "-vf", vf,
             "-vsync", "0", str(tmp_dir / "f_%04d.png")],
            check=True)
        frames = sorted(tmp_dir.glob("f_*.png"))
        if not frames:
            raise RuntimeError(f"解码失败：{video.name}")
        selected = frames[::FRAME_DROP]
        bounds = None
        for index, frame in enumerate(selected, start=1):
            with Image.open(frame) as im:
                im = im.convert("RGBA")
                bbox = im.getchannel("A").point(lambda a: 255 if a >= 16 else 0).getbbox()
                if bbox:
                    bounds = bbox if bounds is None else (
                        min(bounds[0], bbox[0]), min(bounds[1], bbox[1]),
                        max(bounds[2], bounds[2]), max(bounds[3], bounds[3]))
                im.save(out_dir / f"f_{index:04d}.webp", "WEBP", lossless=False, quality=90)
        if bounds is None:
            raise RuntimeError(f"空动画：{video.name}")
        left, top, right, bottom = bounds
        # 对称裁剪，给镜像留边；与桌面端口径一致。
        left = max(0, min(left, CANVAS[0] - right) - 2)
        crop = [left, max(0, top - 2), CANVAS[0] - left, min(CANVAS[1], bottom + 2)]
        return len(selected), crop


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    here = Path(__file__).resolve().parent
    root = here.parent
    parser.add_argument("--videos", type=Path, default=root / "pet-assets" / "videos")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--res-output", type=Path)
    args = parser.parse_args()

    videos: Path = args.videos
    output: Path = args.output
    pet_dir = output / "pet"
    pet_dir.mkdir(parents=True, exist_ok=True)

    clips = []
    for group in CORE_GROUPS:
        group_dir = videos / group
        if not group_dir.is_dir():
            raise FileNotFoundError(f"缺少动作目录：{group_dir}")
        for video in sorted(group_dir.glob("*.webm")):
            cid = clip_id(group, video)
            clip_dir = pet_dir / "clips" / cid
            frames, crop = render_clip(video, clip_dir, CLIP_FPS)
            clips.append({
                "id": cid,
                "group": group,
                "name": video.stem,
                "fps": CLIP_FPS,
                "frames": frames,
                "stride": 180.0,
                "curve": [],
                "path": f"clips/{cid}",
                "crop_box": crop,
            })

    groups = {c["group"] for c in clips}
    missing = set(CORE_GROUPS) - groups
    if missing:
        raise RuntimeError(f"缺少必需动作组：{sorted(missing)}")

    manifest = {
        "schema_version": 1,
        "character_id": "ferry-whale",
        "canvas": list(CANVAS),
        "body_box": BODY_BOX,
        "clips": clips,
        "phrases": DEFAULT_PHRASES,
    }
    (pet_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    # 启动图标：取 idle 第一段首帧。
    if args.res_output:
        idle = next(c for c in clips if c["group"] == "idle")
        first = pet_dir / idle["path"] / "f_0001.webp"
        res = Path(args.res_output) / "drawable-nodpi"
        res.mkdir(parents=True, exist_ok=True)
        with Image.open(first) as im:
            icon = im.convert("RGBA").resize((192, 192), Image.LANCZOS)
            icon.save(res / "pet_icon.png")

    total_frames = sum(c["frames"] for c in clips)
    print(json.dumps({"clips": len(clips), "frames": total_frames}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
