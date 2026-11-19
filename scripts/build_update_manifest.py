#!/usr/bin/env python3
"""发版辅助：读取 release APK，计算 sha256 / size，生成 update.json 热更新清单。

用法（在工程根目录）：
    python3 scripts/build_update_manifest.py --version-code 7 --version-name 1.6.0 \
        --changelog "更新说明…"

产物：update.json（覆盖主干清单）。APK 需先在 GitHub Release 上传，
apkUrls 指向该 Release 附件；脚本只填版本、摘要、大小与说明，不写死下载主机。
"""
import argparse
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
MANIFEST = os.path.join(ROOT, "update.json")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version-code", type=int, required=True)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--changelog", default="")
    ap.add_argument("--apk-name", default="deepseek-pet-v{name}-release.apk")
    ap.add_argument("--repo", default="fnnas-2026/deepseek-pet")
    ap.add_argument("--tag", default=None)
    args = ap.parse_args()

    if not os.path.exists(APK):
        print(f"未找到 release 包：{APK}，请先执行 assembleRelease", file=sys.stderr)
        return 1

    data = open(APK, "rb").read()
    digest = hashlib.sha256(data).hexdigest()
    size = len(data)
    tag = args.tag or f"v{args.version_name}"
    apk_name = args.apk_name.format(name=args.version_name)
    base = f"https://github.com/{args.repo}/releases/download/{tag}/{apk_name}"
    mirrors = [
        f"https://ghfast.top/{base}",
        f"https://ghproxy.net/{base}",
        f"https://gh-proxy.com/{base}",
    ]

    manifest = {
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "apkUrl": base,
        "apkUrls": [base] + mirrors,
        "sha256": digest,
        "size": size,
        "changelog": args.changelog,
    }
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"已生成 {MANIFEST}")
    print(f"  versionCode={args.version_code} versionName={args.version_name}")
    print(f"  size={size} sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
