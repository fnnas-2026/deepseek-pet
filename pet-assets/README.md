# 素材目录

把角色 webm 按动作组放到对应子目录：

```
pet-assets/videos/
├── idle/    # 待机呼吸
├── move/    # 游走移动
├── turn/    # 转向
├── click/   # 点击回应
└── drag/    # 拖拽反馈
```

要求：
- 640×360、24fps、VP9/webm，黑色背景（脚本用 colorkey 抠成透明）；
- 五个动作组都至少各有一段，否则导出脚本会报错；
- `random/`、`events/` 等冷集不打包，需要时自行扩展 `scripts/export_android_assets.py`。

运行导出（工程根目录）：

```bash
python3 scripts/export_android_assets.py \
  --output app/build/generated/petAssets \
  --res-output app/build/generated/petRes
```

当前入库的 webm 来自上游开源角色素材，仅用于本地测试；正式发布前请确认素材授权。
