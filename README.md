# 摆渡桌宠（Ferry Pet）Android

一个原生 Kotlin 安卓悬浮桌宠 App：在屏幕上饲养一只可拖动、可抛掷、会游走、会说台词、
可点击互动的小宠物，并内置一个可配置接口的 AI 聊天面板。

- **桌宠能力**借鉴自开源项目 [dsh-pet-indesktop](https://github.com/MerZlin/dsh-pet-indesktop)
  （游走 / 气泡台词 / 点击音效 / 母宠 + 小鲸鱼 / 多宠碰撞 / 长按菜单 / 通知栏控制 / 锁屏暂停）。
- **视觉与聊天体验**借鉴样本 APK「Ferry 摆渡 1.2」：深蓝底 + 暖橙（`#0E2233` / `#E8A87C`）
  的配色与对话气泡风格；聊天改为 OpenAI 兼容接口，地址、Key、模型均可在设置页填写，
  不再绑定某个远程网站。

## 功能清单

| 模块 | 说明 |
|---|---|
| 悬浮桌宠 | 透明无边框置顶悬浮窗；可拖动、快速甩动松手抛掷；点击 Q 弹回弹 |
| 自由游走 | 待机 / 转向 / 移动动画自动切换；可在设置中关闭，关闭后停在待机循环 |
| 台词气泡 | 定时随机台词，间隔与停留时长可调；支持自定义台词池，留空用内置 5 条 |
| 点击音效 | 内置鸭叫音效（MIT），可换本机音频、调音量、试听 |
| 小鲸鱼 | 母宠 + 最多 4 只独立悬浮窗小鲸鱼；长按菜单生成 / 退出；位置持久化 |
| 碰撞 | 多宠互相挤开、撞后弹开；被拖拽的宠物不可推动；弹力可调 |
| AI 聊天 | 独立聊天面板，兼容 OpenAI `/chat/completions`；可接 New API / One API / DeepSeek / 通义 / 自建中转 |
| 系统集成 | 前台服务 + 低优先级通知栏（暂停 / 继续 / 停止）；息屏 / 锁屏自动隐藏并暂停，解锁恢复 |

## 目录结构

```
ferry-pet-android/
├── app/
│   └── src/main/
│       ├── java/com/ferry/pet/     # 桌宠引擎、视图、悬浮服务、模块、聊天面板
│       │   ├── modules/            # 可选能力模块（游走/台词/音效/小鲸鱼/碰撞）
│       │   ├── ui/                 # 设置页 Compose 分区
│       │   └── chat/               # 聊天 HTTP 客户端与消息模型
│       ├── res/                    # 字符串、主题、图标、内置点击音效
│       └── assets/                 # 构建期由导出脚本生成（webp 帧 + manifest）
├── pet-assets/videos/<group>/      # 原始 webm 素材（idle/move/turn/click/drag）
├── scripts/export_android_assets.py# 离线素材导出：webm → 透明 WebP 帧序列 + manifest
├── settings.gradle.kts / build.gradle.kts / gradle/wrapper/
└── README.md
```

## 构建环境要求

- JDK 17
- Android SDK：compileSdk / targetSdk 36，minSdk 26（Android 8.0+）
- Android Gradle Plugin 8.10.1，Kotlin 2.1.21，Compose BOM 2025.05.01
- Gradle 8.11.1（wrapper 已固定）
- 命令行素材导出依赖：Python 3、Pillow、ffmpeg、ffprobe

> 本工程在无 Android SDK 的环境里无法直接出包；请在装有 Android Studio（或配置好
> SDK / cmdline-tools 的命令行）的机器上编译。

## 编译步骤

### 1. 准备素材（离线，先跑一次）

把角色 webm 放到 `pet-assets/videos/<group>/`，五个必需动作组：
`idle`、`move`、`turn`、`click`、`drag`。然后执行：

```bash
python3 scripts/export_android_assets.py \
  --output app/build/generated/petAssets \
  --res-output app/build/generated/petRes
```

脚本会把黑底 webm 抠成透明 WebP 帧序列，生成 `pet/manifest.json` 和启动图标。
`app/build/generated/` 下的产物不入库，构建时 `preBuild` 也会自动触发该脚本。

### 2. 编译 Debug APK

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

Release 包需要自配签名：

```bash
./gradlew assembleRelease
```

## 扩展能力模块（插件位）

可选能力以 `modules/` 下的模块实现，注册表 `ModuleRegistry` 按偏好增删对象：
关闭的模块根本不会被构造，不持有窗口、线程或解码器游标，也不接收逐帧回调。

新增一个能力：

1. 在 `modules/` 实现 `PetModule`（可继承 `HostBoundModule`），声明 `id`、`enable/disable`、
   可选 `onTick/onFrame/onEvent/menuItems/diagnostics`。
2. 在服务装配处把 `ModuleFactory(id) { YourModule() }` 加进 factories 列表。
3. 在偏好里补对应开关键，`ModuleHost.options.enables(id)` 即决定其是否存活。

模块只能经 `ModuleHost` 读取快照、播报台词、触发 Q 弹，不直接持有 Android 窗口状态。

## 构建渠道：测试版 / 正式版

- **Debug（测试版）**：测试网关 Key 从本地 `local.properties` 的 `test.chat.key`
  注入（该文件已被 `.gitignore` 排除，不入库）；缺省留空，运行时填写；`BUILD_FLAVOR=test`。
- **Release（正式版）**：默认渠道 Key 留空，由用户首次运行时在「模型渠道」页填写，
  不携带内置密钥；`BUILD_FLAVOR=formal`。
- 密钥与更新清单地址通过 `buildConfigField` 注入（见 `app/build.gradle.kts`），
  源码与版本库里不出现明文 Key。

## 热更新（对齐上游 dsh-pet 的静态清单 + Releases 模式）

- **清单** `update.json` 存于仓库主干（`main`），App 按 jsDelivr 三个 CDN 线路
  轮询回退：`cdn.jsdelivr.net` / `fastly.jsdelivr.net` / `gcore.jsdelivr.net`
  （`/{owner}/{repo}@main/update.json`），任一线路成功即采用。
- **安装包** 挂在 GitHub Releases 附件，清单里 `apkUrls` 可写多条镜像线路，
  下载前逐条校验为 HTTPS 且主机在可信白名单（GitHub 域名、jsDelivr、gh-proxy 等）。
- 清单可选 `sha256`（小写十六进制）与 `size`，落盘后做摘要/大小校验；未填则跳过。

```json
{
  "versionCode": 7,
  "versionName": "1.6.0",
  "apkUrl": "https://github.com/OWNER/REPO/releases/download/v1.6.0/app-release.apk",
  "apkUrls": ["https://mirror.example/app-release.apk"],
  "sha256": "可选，小写十六进制",
  "size": 25174993,
  "changelog": "更新说明…"
}
```

> 发版流程：出 Release 并上传 APK → 在 `update.json` 填新 versionCode/versionName、
> 下载地址、摘要与大小 → 提交到 `main`；App 下次检查即经 CDN 拉到清单并引导安装。

## 聊天接口配置

1. 安装后打开 App，进入设置 → **AI 聊天**，打开「启用聊天入口」。
2. 填写：
   - **接口地址**：例如 `https://api.openai.com/v1`，或你的网关地址（不带尾部 `/chat/completions`）。
   - **API Key**：仅保存在本机私有偏好里，不上传其他地方。
   - **模型名称**：如 `gpt-4o-mini`、`deepseek-chat` 等。
   - **系统提示词**：可选，定义桌宠助手的语气与身份。
3. 长按桌宠 →「聊天」打开对话面板；未配置时面板会给出引导提示。

接口约定：`POST {baseUrl}/chat/completions`，请求/响应为 OpenAI 消息格式，
`Authorization: Bearer <key>`。

## 权限说明

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 悬浮桌宠显示在其他应用上层 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 桌宠前台服务保活 |
| `POST_NOTIFICATIONS` | 通知栏运行状态与暂停 / 停止控制 |
| `INTERNET` | AI 聊天接口请求；不开启聊天时不发起网络请求 |

## 与上游 dsh-pet 的关系

本工程为独立重构的安卓端：引擎、碰撞、解码缓存、模块门控等核心逻辑移植自上游
Android 0.2.1 测试版，包名、资源名、设置结构与聊天能力为本工程重新组织；
未携带上游的桌面端（Python/PySide6）、AI 识屏、电脑 Agent 联动等桌面特性。
内置点击音为 MIT 素材，版权与声明以上游仓库 `THIRD_PARTY_NOTICES.md` 为准。
