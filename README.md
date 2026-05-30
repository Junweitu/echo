# Echo / 回声

Echo is an Android-first personal voice journal. It keeps a foreground microphone service running, uses local VAD to keep only useful speech segments, transcribes those segments through a user-configured ASR service, and turns each day into a structured diary.

Echo（回声）是一个 Android 端个人语音日记 App。它通过前台麦克风服务持续聆听，用本地 VAD 只保留有效人声片段，再通过用户自行配置的语音转写服务生成文字，并把每天的内容整理成结构化日记。

> Current status: early self-use MVP. The app builds and has unit coverage for the local audio, ASR/LLM client, summary, history, detail, settings, and notification logic. Before public distribution, review privacy policy, third-party model licenses, and release signing.
>
> 当前状态：早期自用 MVP。项目已覆盖本地录音、ASR/LLM 客户端、每日整理、历史、详情、设置、通知等核心逻辑的单元测试。公开分发前仍需补齐隐私政策、第三方模型授权核验和正式签名流程。

## Features / 功能

- Foreground recording service with persistent notification and pause/resume controls.
- Local VAD pipeline with pre-roll buffering, energy fallback, WAV segment writing, and Room persistence.
- Volcengine/Doubao file ASR integration through user-filled in-app settings.
- DeepSeek-compatible daily summary pipeline with diary, todos, inspirations, and timeline.
- Today, history, day detail, raw transcript, speaker claim dialog, and Telegram-style voice bubble playback UI.
- Settings connection tests for ASR and DeepSeek.

- 前台录音服务，带常驻通知和暂停/继续入口。
- 本地 VAD 流水线，包含预录缓冲、能量兜底、WAV 片段落盘和 Room 持久化。
- 火山引擎 / 豆包录音文件识别接入，凭证由用户在 App 设置页填写。
- DeepSeek 兼容的每日整理链路，产生日记、待办、灵感和时间线。
- 今天、历史、每日详情、原始记录、说话人认领，以及类似 Telegram 的语音气泡播放 UI。
- 设置页支持 ASR 和 DeepSeek 连通性测试。

## Privacy And Secrets / 隐私与密钥

No cloud credential is hardcoded in the source tree or APK. ASR and LLM credentials are entered in the app settings and stored locally with AndroidX Security `EncryptedSharedPreferences`.

源码和 APK 不内置任何云服务密钥。ASR 和 LLM 凭证由用户在 App 设置页填写，并通过 AndroidX Security `EncryptedSharedPreferences` 加密保存在本机。

Open-source hygiene in this repository:

- `local.properties`, `.env*`, signing keys, build outputs, APK/AAB files, local agent state, logs, and private recordings are ignored.
- Runtime audio should stay in the app-private storage area and must not be committed.
- The bundled `asr_test_zh.wav` file is only for ASR connectivity testing; the project journal records it as a Wikimedia Commons / Lingua Libre Mandarin sample released under CC0.
- The bundled `silero_vad.onnx` model is used for local VAD. Confirm and document the upstream model license before a public release.

本仓库的开源卫生约束：

- `local.properties`、`.env*`、签名密钥、构建产物、APK/AAB、本地 agent 状态、日志和私人录音都不会进入 Git。
- 运行时录音应只保存在 App 私有目录，不能提交到仓库。
- 内置的 `asr_test_zh.wav` 仅用于 ASR 连通性测试；项目日志记录其来源为 Wikimedia Commons / Lingua Libre 普通话样例，授权为 CC0。
- 内置的 `silero_vad.onnx` 用于本地 VAD；公开发布前需要补充并确认上游模型授权说明。

## Project Layout / 项目结构

```text
.
├── app-android/                 # Android Gradle project / Android 工程
│   ├── app/src/main/java/tech/echo/app/
│   │   ├── core/audio/          # Recording, VAD, foreground service / 录音、VAD、前台服务
│   │   ├── core/data/           # Room database and repositories / Room 数据库与仓库
│   │   ├── core/settings/       # Encrypted settings / 加密配置
│   │   ├── core/upload/         # ASR upload pipeline / ASR 上传转写
│   │   ├── core/summary/        # LLM daily summary pipeline / 每日整理
│   │   └── ui/                  # Compose screens / Compose 界面
│   └── app/src/test/            # Unit tests / 单元测试
└── docs/
    ├── product/core/            # Product and UI specs / 产品与 UI 规格
    ├── tech/                    # Technical design and handoff docs / 技术设计与交接
    └── changelog/               # Development journal / 开发日志
```

## Requirements / 环境要求

- Android Studio with JDK 17.
- Android SDK / Build Tools compatible with `compileSdk = 36`.
- Android device or emulator on API 26+.
- For real cloud tests: user-owned Volcengine/Doubao ASR credentials and DeepSeek API key.

- Android Studio，使用 JDK 17。
- Android SDK / Build Tools 需兼容 `compileSdk = 36`。
- Android 8.0（API 26）及以上真机或模拟器。
- 真实云端链路测试需要用户自己的火山引擎 / 豆包 ASR 凭证和 DeepSeek API Key。

## Build And Test / 构建与测试

```bash
cd app-android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Install a debug build to a connected device:

安装 debug 包到已连接设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configuration / 配置

After installing the app, open Settings and fill in:

- Volcengine/Doubao ASR: App ID or API Key, optional Access Key for the old console, and Resource ID.
- DeepSeek: Base URL, API Key, and model.

安装 App 后进入设置页填写：

- 火山引擎 / 豆包 ASR：App ID 或 API Key，旧控制台可额外填写 Access Key，以及 Resource ID。
- DeepSeek：Base URL、API Key 和模型名。

The current ASR implementation uses the file flash recognition API. The default Resource ID is:

当前 ASR 实现使用录音文件极速版接口，默认 Resource ID 为：

```text
volc.bigasr.auc_turbo
```

Streaming ASR 2.0 and standard file ASR 2.0 use different API flows and are intentionally rejected by the current client.

流式语音识别 2.0 和录音文件标准版 2.0 属于不同接口形态，当前客户端会主动拦截这些错配 Resource ID。

## Public Release Checklist / 公开发布前检查

- Add a project `LICENSE` after choosing the license.
- Add third-party notices for model/assets/dependencies where required.
- Write an explicit privacy policy for always-on microphone usage and cloud transcription.
- Verify that no real recordings, user transcripts, screenshots with personal data, API keys, signing keys, or local config files are staged.
- Use release signing outside the repository.

- 选定开源协议后补充项目 `LICENSE`。
- 按需补充模型、素材和依赖的第三方授权说明。
- 为常驻麦克风和云端转写写清楚隐私政策。
- 提交前确认没有真实录音、用户转写文本、含个人数据的截图、API Key、签名密钥或本地配置文件进入暂存区。
- 正式签名文件放在仓库外管理。
