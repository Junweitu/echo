# echo 编码日志

---
date: 2026-05-29
module: core
type: architecture
files: docs/product/core/requirements.md, docs/product/core/ui-design.md
---
### 项目启动：echo 语音生活记录 App 需求与 UI 设计定稿

**背景**：贰玖想要一个后台持续工作、自动录音转文字、每日整理的个人 App。

**关键决策**：
- **只做 Android**：iOS 不允许后台常驻麦克风录音，App Store 也会拒，平台直接砍掉 iOS。
- **连续监听 + VAD 自动激活**：麦克风常驻监听，本地 VAD 判定有人声才落盘，静音丢弃。配**环形预录缓冲**保证不丢句子开头。明确这省的是存储/转写成本，不是省电。
- **说话人能力分两层**：先做成熟的「说话人分离」(标 A/B/C)，再做渐进式「声纹认领」。
- **声纹认领走事后标注**：不预录注册样本，用户在 App 里看到 Speaker N 点一下贴名，系统自动入声纹库，下次自认。否决了「专门录注册样本」和「自动认出陌生人」两个方案。
- **云端批量转写**：不用实时流式，更便宜，每日整理不需要实时。

**UI 核心约束**：极简——全用 Material Design 3 原生组件、单列布局、零自定义绘制。动机是 ① 方便操作 ② coding agent 审美弱，把审美决策在文档里前置定死，让 AI 只做翻译不做创造。共 3 屏(今天/历史/详情) + 通知 + 认领弹窗。

**已确认参数**：每日整理产出日记/待办/灵感/时间线四类；本地音频留 7 天；凌晨自动整理+手动补；先只做 App 内查看。

**UI 已确认**：否决紫色(觉得 low)，改**黑白极简 + 唯一一抹红**(`#E5392F` 仅录音指示)；录音圆**要呼吸动画**(聆听缓慢、检测到人声加快)；3 屏结构通过。

**下一步**：贰玖确认 UI 文档 → 交付 UI 出图 → 写技术 design.md → 交 Codex 实现 MVP 第一版(后台录音+VAD+本地存)。

---
date: 2026-05-29
module: core
type: ui-design
files: docs/product/core/ui-design.md, docs/product/core/ui-mockups/01-today-states.png
---
### 今天页主控收敛：状态圆即开始/暂停按钮

**背景**：第一版今天页出图里，状态圆下方另放了一个横向「暂停/开始」按钮，视觉上显得笨重。

**调整**：
- 录音状态圆升级为圆形主控，直接承载开始/暂停功能。
- 不再单独放横向主按钮，今天页只保留状态圆、状态文案、今日统计和整理入口卡片。
- 聆听/记录中：红色圆形主控 + `pause` 图标 + 呼吸动画；暂停：灰色圆形主控 + `mic` 或 `play_arrow` 图标。
- 已重新生成今天页 3 态 UI 图，覆盖 `docs/product/core/ui-mockups/01-today-states.png`。
- 已把 3 张最终 UI 设计板嵌入 `docs/product/core/ui-design.md`，后续打开文档即可直接查看。

**下一步**：后续实现今天页时以新版「圆形状态主控」为准。

---
date: 2026-05-29
module: core-audio
type: feature
files: app-android/app/src/main/java/tech/echo/app/core/audio/RecordingService.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingEngine.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RealRecordingController.kt
---
### 阶段 1 录音引擎接线完成：占位 Service → 真实前台录音链路

**背景**：DSP 链路（AudioCapture/SileroVad/RingBuffer/VadStateMachine/WavWriter）+ 数据层此前已是真实现且带单测，onnx 模型也在 assets。唯一缺口是 `RecordingService`（空壳 START_STICKY）和 `FakeRecordingController`（假状态），即开发计划后两项。本次由叁拾直接实现（未走 Codex）。

**关键决策**：
- **抽出 `RecordingEngine` 纯逻辑类**：把"帧流→VAD→环形缓冲→落盘→入库"从 Service 里剥离，不依赖 Android Service，可用合成 PCM 帧流端到端单测。动机是验收点 1/2（说话才落盘、防丢开头）必须能脱离真机验证。
- **`RecordingStateHolder` 状态桥**：前台 Service 持麦克风但 UI 拿不到 Service 实例，用 @Singleton 状态桥解耦——Service 写引擎状态，`RealRecordingController` 读出来与当天 Room 统计 combine 成 `TodayState`。
- **环形缓冲 push 时机**：放在 VAD 事件处理之后再 push 当前帧，避免刚补进段开头的帧又被重复入缓冲。
- **音频焦点区分用户暂停 vs 被抢占**：`pausedByUser` 标志决定焦点 GAIN 时要不要自动续录，避免用户主动暂停后被来电结束又自动开。
- **Opus 降级为 WAV**：阶段 1 落 WAV 可直接回听验证，抽象不变，阶段 2 再换。

**验证**：`testDebugUnitTest` 19/19 通过（新增 RecordingEngineTest 3 个端到端用例，断言落盘文件段首确实是触发点之前的预录静音帧）；`assembleDebug` 通过产出 APK。

**遗留**：真机装机验证（跑满一天/各 ROM 杀后台/录音质量/通知交互）叁拾无设备，需贰玖在 Android Studio 实测；跨天计数刷新、7 天清理待阶段 2/3。交付说明见 `docs/tech/codex-handoff-core-audio.md`。

---
date: 2026-05-29
module: core-upload, core-summary
type: architecture
files: docs/tech/design.md
---
### 模型配置定为 App 内全用户配置，不内置任何 key

**背景**：阶段 1 录音引擎完成后，讨论阶段 2 接云时模型配置（baseUrl/模型名/apiKey）走哪条路。

**决策**：**App 内全用户配置**——阶段 2 做设置页，用户自填 DeepSeek（LLM）的 baseUrl/模型名/key 和火山（ASR）的 AppID/AK/SK，存本地加密 DataStore。**火山也做成完全用户配置**，不做开发者编译期注入。

**权衡**：
- 开发者编译期注入（local.properties→BuildConfig）：用户零配置，但 **key 会被打进 APK，反编译可提取**。
- App 内全用户配置：key 不进 APK/git，谁用谁填；代价是多一个设置页 + 用户需自备账号。

**选择理由**：贰玖确认 echo **可能公开分发**，key 焊进包里等于泄露，风险不值得冒。早期自用也走同一条路（自己填自己的），不为省事开后门。

**影响**：
- 推翻 design.md §6.2 原"放 local.properties/BuildConfig"的约定，已改为 App 内设置页 + 加密 DataStore。
- 阶段 2 需新增设置页 UI + 加密配置存储；`AsrClient`/`LlmClient` 运行时读配置构造请求。

**遗留（公开分发待解，功能不阻塞）**：① 火山 ASR 用户配置门槛高（控制台开通+建应用拿三件套，非技术用户劝退）；② 录到第三方语音的隐私合规（需隐私政策/告知/自担条款，"贰玖自担"前提公开后不成立）；③ Google Play 常驻麦克风审核严。上架前需想清，早期自用不影响。

---
date: 2026-05-29
module: core-data, core-settings
type: feature
files: app-android/app/src/main/java/tech/echo/app/core/data/db, app-android/app/src/main/java/tech/echo/app/core/settings, app-android/docs/tech/stage2-handoff.md
---
### 阶段 2 启动：数据层 v2 完成，设置页差导航接线

**进度**：阶段 2（接云）5 子任务，本 session 完成数据层 v2、写完设置页（差导航）。

**已完成**：
- 数据层 v2：daily_summary 表 + DailySummaryDao/Repository + Converters（JSON 列）+ Room 1→2 迁移（只新增表，segment 表复用阶段 1 预留字段）+ SegmentDao 扩展上传转写查询。编译通过。
- 设置页：AppConfig 模型 + SettingsRepository（EncryptedSharedPreferences AES256 加密存储，Flow 暴露）+ SettingsViewModel + SettingsScreen（M3 表单，火山/DeepSeek 两组，key 密码遮罩）。编译通过。

**未完成**：设置页导航接线（EchoRoutes 加 SETTINGS + NavHost 加 composable + 今天页 TopAppBar 加设置入口）；DeepSeek 整理层（#5）；ASR 上传层（#6）；UI 接 Room（#7，当前仍 FakeData）。

**关键约束**：火山真实 API 规格公开文档抓不到，等贰玖发火山控制台文档再实现 VolcAsrClient，在此之前留 TODO 桩不硬写。DeepSeek 规格已查准（OpenAI 兼容）。

**交接文档**：`app-android/docs/tech/stage2-handoff.md`（含 5 子任务状态、每块实现要点、依赖说明、贰玖人工待办）。

**遗留**：@HiltWorker 需在 Application 配 HiltWorkerFactory + Configuration.Provider，#5/#6 写 Worker 时要处理否则注入崩。

---
date: 2026-05-30
module: core-summary, core-upload, ui
type: feature
files: app-android/app/src/main/java/tech/echo/app/core/summary, app-android/app/src/main/java/tech/echo/app/core/upload, app-android/app/src/main/java/tech/echo/app/ui, app-android/docs/tech/stage2-handoff.md
---
### 阶段 2 接云代码闭环：DeepSeek 整理、火山 ASR 上传、UI 接真实数据

**进度**：阶段 2 剩余 #4~#7 已完成代码实现。设置页导航已接入；历史/详情/今天页从 FakeData 切到 Room/Worker；录音落段后会 enqueue 上传；今天页可手动触发整理今天。

**关键实现**：
- DeepSeek：新增 `LlmClient`、`DeepSeekLlmClient`、`SummaryPromptBuilder`、`SummaryJsonParser`、`SummaryGenerator`、`SummaryWorker`，走 OpenAI-compatible `/chat/completions` + `response_format: json_object`。
- 火山 ASR：交接时公开规格缺失，本轮重新查到官方“大模型录音文件极速版识别 API”，已实现 `VolcAsrClient` 的本地文件 base64 直传，Resource ID 默认 `volc.bigasr.auc_turbo`。旧控制台用 App ID + Access Key；新控制台可只填 API Key。
- WorkManager：`EchoApplication` 已接 `HiltWorkerFactory`；`SummaryWorkScheduler` 负责凌晨周期整理/手动整理，`UploadWorkScheduler` 负责落段后上传补传。
- UI：`HistoryViewModel` combine 每日片段数与整理状态；`DetailViewModel` combine daily_summary 与当天 segment；Today 卡片使用真实日期、summaryReady，并新增设置入口。

**验证**：`./gradlew :app:testDebugUnitTest --console=plain` 通过（34 tests）；`./gradlew :app:compileDebugKotlin --console=plain` 通过；`./gradlew :app:assembleDebug --console=plain` 通过。

**遗留**：阶段 2 云端调用尚未用真实 key + 真机验证；阶段 1 跑满一天/通知/ROM 杀后台验证仍需贰玖实测。

---
date: 2026-05-30
module: core-settings
type: feature
files: app-android/app/src/main/java/tech/echo/app/ui/settings, app-android/app/src/test/java/tech/echo/app/ui/settings/SettingsConnectionTesterTest.kt
---
### 设置页新增云服务连通性测试

**背景**：贰玖已在手机设置页填入豆包语音 / DeepSeek 配置，但需要知道配置是否可用，不能等录音和每日整理链路跑完才暴露错误。

**实现**：
- 设置页在火山/豆包语音配置区新增“测试豆包语音连接”按钮，在 DeepSeek 配置区新增“测试 DeepSeek 连接”按钮。
- 点击测试前会先保存当前表单，避免用户改完字段但忘记点保存导致测试旧配置。
- 豆包语音测试会在本机 cache 生成一段短 WAV 测试音频，调用现有 `AsrClient` 上传；只判断接口/鉴权链路能否打通，不要求识别出文字。
- DeepSeek 测试调用现有 `LlmClient`，发送极小 JSON prompt；成功/失败会直接显示在设置页按钮下方。

**验证**：新增 `SettingsConnectionTesterTest` 覆盖 LLM 成功、ASR 生成有效 WAV 并调用客户端、失败可读错误三类；`./gradlew :app:testDebugUnitTest --console=plain` 通过；`./gradlew :app:assembleDebug --console=plain` 通过；已用 `adb install -r` 覆盖安装到 `PJZ110`，保留原 App 数据。

---
date: 2026-05-30
module: core-settings, core-upload
type: fix
files: app-android/app/src/main/java/tech/echo/app/core/upload/AsrClient.kt, app-android/app/src/main/java/tech/echo/app/core/upload/VolcAsrClient.kt, app-android/app/src/main/java/tech/echo/app/ui/settings/SettingsConnectionTester.kt
---
### 设置页豆包语音测试误报失败修正：20000003 是静音音频

**背景**：贰玖在真机设置页点击“测试豆包语音连接”后，页面显示 `HTTP 200, status=20000003`。截图确认 DeepSeek 正常，豆包语音接口返回 body 中 `text` 为空。

**根因**：火山官方文档定义 `20000003 = 静音音频`。当前连接测试生成的是短测试音而非真人语音，服务端已完成鉴权和处理，但把样例判为无语音；这应视为“连接正常，测试音频无语音”，不是配置失败。

**修复**：
- 新增 `AsrStatusException` 保留火山 `X-Api-Status-Code` / message / response body。
- `VolcAsrClient` 对非 `20000000` 状态抛 typed exception。
- `SettingsConnectionTester` 在设置页测试场景中把 `20000003` 判为成功，并显示“豆包语音连接正常（测试音频无语音）”。

**验证**：补充 `silentAudioStatusStillMeansAsrConnectionIsReachable` 单测；`./gradlew :app:testDebugUnitTest --console=plain` 通过；`./gradlew :app:assembleDebug --console=plain` 通过；已 `adb install -r` 覆盖安装到 `PJZ110`。

---
date: 2026-05-30
module: core-settings
type: feature
files: app-android/app/src/main/assets/asr_test_zh.wav, app-android/app/src/main/java/tech/echo/app/ui/settings/AsrTestAudioProvider.kt, app-android/app/src/main/java/tech/echo/app/ui/settings/SettingsConnectionTester.kt
---
### 豆包语音连通性测试改用真实中文语音样例

**背景**：贰玖希望设置页豆包语音测试用一段真实中文声音，而不是短测试音，避免被服务端判为静音。

**实现**：
- 从 Wikimedia Commons 选用 Lingua Libre 普通话“开心”WAV 样例，文件 1.2 秒、约 111KB、CC0 公共领域，打包为 `assets/asr_test_zh.wav`。
- 新增 `AsrTestAudioProvider` / `AssetAsrTestAudioProvider`，设置页测试时从 assets 复制这段真人中文语音到 cache 后上传。
- `SettingsConnectionTesterTest` 增加 fake provider 断言，确保 ASR 测试走样例音频 provider。

**验证**：`file app/src/main/assets/asr_test_zh.wav` 确认为 RIFF/WAVE PCM；`./gradlew :app:testDebugUnitTest --console=plain` 通过；`./gradlew :app:assembleDebug --console=plain` 通过；已 `adb install -r` 覆盖安装到 `PJZ110`。

---
date: 2026-05-30
module: core-audio, core-upload
type: fix
files: app-android/app/src/main/java/tech/echo/app/core/audio, app-android/app/src/main/java/tech/echo/app/core/upload/UploadWorker.kt, app-android/app/src/main/AndroidManifest.xml
---
### 真机录音未转文字修复：VAD 漏判兜底 + WorkManager Hilt 初始化

**背景**：贰玖真机点击录音、说两句话、暂停后没有出现文字。现场排查发现 `files/audio` 为空、Room `segment` 表为 0 条，说明问题不在豆包 ASR，而是录音阶段未生成片段。

**根因**：
- 系统音频侧确认 `AudioRecord` 实际打开内置麦克风，采集过约 5.7s / 10.3s，且声压历史有明显说话峰值。
- App 侧诊断日志显示 Silero ONNX 在明显人声下 `maxProbability` 仍约 `0.00198`，低于状态机阈值 `0.5`，所以永远不会进入 `RECORDING` / 落盘。
- 加能量兜底后片段能落盘，但随即暴露第二个断点：WorkManager 使用默认工厂反射 `UploadWorker`，报 `NoSuchMethodException(Context, WorkerParameters)`，因为 manifest 未移除默认 `WorkManagerInitializer`，`HiltWorkerFactory` 未生效。

**修复**：
- 新增 `EnergyFallbackVadDetector`：Silero 仍优先；仅当模型低分但 32ms 帧 RMS 超过 `ENERGY_VAD_RMS_THRESHOLD=0.008` 时，把该帧兜底视为有声。
- `SileroVadDetector` 的 `sr` 输入形状改为 scalar，匹配 Silero v5 ONNX 模型规格，并增加形状单测。
- `RecordingService` 增加安全诊断日志，并把用户暂停导致的协程取消降为正常日志。
- Manifest 移除 `androidx.work.WorkManagerInitializer`，让 `EchoApplication` 的 `HiltWorkerFactory` 配置生效；`UploadWorker` 增加批次结果日志。

**验证**：
- 真机可控复测：Mac 播放 8 秒中文“开心”样例，手机录音后生成 7.52s / 8.32s 两段 WAV，Room 中两条 segment 均从 `RECORDED` 转为 `DONE`。
- `UploadWorker` 日志显示 `upload batch total=2 completed=2 failed=0`。
- 数据库验证：两条 transcript 均识别为“开心开心开心开心开心开心开心...”。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过；已 `adb install -r` 覆盖安装到 `PJZ110`。

---
date: 2026-05-30
module: core-audio
type: feature
files: app-android/app/src/main/java/tech/echo/app/core/audio/RecordingService.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingNotificationSpec.kt, app-android/app/src/main/AndroidManifest.xml
---
### 后台录音通知升级为 Live Update 兼容状态

**背景**：贰玖的一加手机支持类似“灵动岛”的 Live Alerts，希望 Echo 进入后台后能在系统状态区显示录音状态。

**现状判断**：
- 当前录音主链本来就是 `microphone` Foreground Service：从前台启动后，App 进入后台仍可继续持有麦克风、跑 VAD、落段和上传。
- Android 官方没有面向一加“灵动岛”的通用绘制 API；能做的标准路径是把常驻录音通知做成可被系统提升的 ongoing / Live Update 兼容通知。
- 国产 ROM 仍可能叠加额外规则；最终是否进入一加 Live Alerts 需要真机系统设置和 ROM 策略共同决定。

**实现**：
- 新增 `RecordingNotificationSpec`，把 `PAUSED` / `LISTENING` / `RECORDING` 三态映射为明确的通知标题、正文、短状态芯片文案和操作按钮。
- `RecordingService` 的常驻通知新增 `CATEGORY_STATUS`、`FOREGROUND_SERVICE_IMMEDIATE`、`onlyAlertOnce`，并写入 `android.requestPromotedOngoing=true` 与 `android.shortCriticalText`，让 Android 16+ / OEM Live Update 表面可识别。
- Manifest 新增 `android.permission.POST_PROMOTED_NOTIFICATIONS` 非运行时权限。

**验证**：
- 先补 `RecordingNotificationSpecTest` 并确认 RED 失败于新规格不存在，再实现到 GREEN。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过，汇总 48 tests / 0 failures。
- merged / packaged manifest 均确认包含 `POST_PROMOTED_NOTIFICATIONS` 和 `foregroundServiceType="microphone"`。
- 已 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 覆盖安装到一加 `PJZ110`，设备侧 `dumpsys package` 显示 `POST_PROMOTED_NOTIFICATIONS` / `FOREGROUND_SERVICE_MICROPHONE` granted。

---
date: 2026-05-30
module: core-audio, settings
type: fix
files: app-android/app/src/main/java/tech/echo/app/core/audio/PromotedNotificationPermission.kt, app-android/app/src/main/java/tech/echo/app/ui/settings/SettingsScreen.kt
---
### 一加灵动岛未显示的根因：实时活动权限被 AppOps 拒绝

**背景**：贰玖反馈 Echo 退到后台后，一加灵动岛没有显示录音状态。

**排查结论**：
- 真机系统：一加 `PJZ110`，Android 16，系统版本 `PJZ110_16.0.7.201(CN01)` / `V16.1.0`。
- `RecordingService` 确实在运行，通知也存在，且 extras 中已包含 `android.requestPromotedOngoing=true` 与 `android.shortCriticalText=聆听`。
- 断点在系统权限层：`appops get tech.echo.app` 显示 `POST_PROMOTED_NOTIFICATIONS: ignore`，即普通通知和前台录音允许，但 promoted / Live Update 通知被系统实时活动开关拒绝。
- ADB shell 无权直接改该 AppOps：`appops set tech.echo.app POST_PROMOTED_NOTIFICATIONS allow` 返回 `SecurityException: uid 2000 does not have android.permission.MANAGE_APP_OPS_MODES`，必须走用户可见系统设置。

**修复**：
- 新增 `PromotedNotificationPermission`，在 Android 16+ 调用 `NotificationManager.canPostPromotedNotifications()` 检测实时活动/提升通知是否已允许。
- 设置页顶部新增“后台状态”区块；如果未开启，显示“打开实时活动设置”按钮，跳转 `android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS` 并带上当前包名；若系统不支持该页，则回退到 App 详情设置。
- 从系统设置返回设置页时，会在 `ON_RESUME` 重新检测状态。

**验证**：
- 先补 `PromotedNotificationPermissionTest` 并确认 RED 失败于 helper 不存在，再实现到 GREEN。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过，汇总 54 tests / 0 failures。
- 本轮真机已被拔出，待手机重新连接后统一安装并验证：设置页入口 → 打开实时活动 → Echo 退后台 → 检查 `POST_PROMOTED_NOTIFICATIONS` AppOps 与通知 promoted flags。

---
date: 2026-05-30
module: core-audio
type: polish
files: app-android/app/src/main/java/tech/echo/app/core/audio/RecordingNotificationSpec.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingService.kt, app-android/app/src/main/res/drawable/ic_stat_echo.xml
---
### 灵动岛/Live Alerts 通知呈现优化

**背景**：贰玖希望一加灵动岛里的 Echo 录音状态更好看、更像真正的后台活动，而不是一条普通静态通知。

**实现**：
- 三态短芯片文案收敛为两字状态：`待命` / `录音` / `暂停`，更适合顶部小面积显示。
- 通知标题同步压缩为 `Echo 待命中`、`Echo 录音中`、`Echo 已暂停`；正文保留解释性文案，供通知抽屉显示。
- 三态加入状态色：待命蓝、录音红、暂停灰，并调用 `setColorized(true)`；这是 Android 16 promoted ongoing 非通话类通知进入提升呈现的重要条件之一。
- 待命/录音活跃态加入 `showChronometer` 与 indeterminate progress 元数据，让系统更容易把它识别为进行中的活动；暂停态关闭计时和进度。
- 新增单色麦克风通知小图标 `ic_stat_echo`，替换 launcher 前景图，避免状态区/灵动岛使用不合适的启动图形。

**验证**：
- 先补 `RecordingNotificationSpecTest`，确认 RED 失败于缺少 colorized/color/progress/chronometer 字段，再实现到 GREEN。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过，汇总 54 tests / 0 failures。
- 本轮未连接真机；待重新连接一加后检查通知 dumpsys 是否出现 colorized/color/progress/shortCriticalText，并观察 Live Alerts 实际呈现。

---
date: 2026-05-30
module: ui-detail, core-data
type: fix
files: app-android/app/src/main/java/tech/echo/app/ui/detail, app-android/app/src/main/java/tech/echo/app/core/data
---
### 说话人认领不持久修复：从 UI 内存改为写回 Room

**背景**：贰玖反馈“把一段历史声音标记成我，后面再说话还是没有识别到是我”。排查发现当前版本还没有真正的声纹库，也没有 `person` 表；详情页认领只改 Compose 内存里的显示名。

**根因**：
- `TranscriptTab` 明确写着“本地内存，UI 阶段不落库”，确认按钮只更新 `mutableStateMapOf`。
- `EchoDatabase` 注释仍是 `person 表（声纹）留待阶段 3`，没有 embedding / 比对链路。
- `TranscriptSegment` 之前没有 segment id / raw speakerLabel，UI 即便想写库也没有可靠目标。

**修复**：
- `TranscriptSegment` 增加 `id` 和 `speakerKey`，`DetailMappers` 从 `SegmentEntity` 带出可写回目标。
- 新增 `SegmentRepository.claimSpeaker`：有 ASR speakerLabel 时更新同一天同 label；没有 speakerLabel 时只更新当前 segment。
- `DetailViewModel` 暴露 `claimSpeaker`，`TranscriptTab` 的认领确认改为调用 ViewModel 写回 Room，不再只改本地 UI 状态。
- 已认领显示优先使用 `speakerPersonId` 中暂存的显示名；对已转写但无 speaker label 的片段显示“未识别说话人”，避免误写“未转写”。
- 弹窗提示从“以后会自动识别这个人”改成“保存后会用于这天的原始记录和整理显示”，避免阶段 3 声纹未完成前误导。

**边界**：这次修复的是“认领持久化 / 同日同编号批量更新”，不是完整声纹识别。真正“后面自动识别我”仍需阶段 3 引入本地 voiceprint embedding 模型、person 表和相似度比对。

**验证**：新增 `SegmentRepositoryTest` 覆盖按 label 批量更新、无 label 单段更新、空名字忽略；更新 `DetailMappersTest` 覆盖 segment id / raw speaker key / 已认领显示名优先；`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过；已 `adb install -r` 覆盖安装到 `PJZ110`。

---
date: 2026-05-30
module: core-upload, core-settings
type: fix
files: app-android/app/src/main/java/tech/echo/app/core/settings/VolcAsrResourceIds.kt, app-android/app/src/main/java/tech/echo/app/core/upload/VolcAsrClient.kt, app-android/app/src/main/java/tech/echo/app/ui/settings/SettingsScreen.kt, app-android/docs/tech/stage2-handoff.md
---
### 豆包 ASR 版本口径修正：当前是录音文件极速版，不是流式 1.0

**背景**：贰玖看到豆包后台已有“流式语音识别 2.0”，追问为什么 Echo 还像是在用 1.0。

**核对结论**：
- 当前代码实际调用的是“大模型录音文件极速版” `POST /api/v3/auc/bigmodel/recognize/flash`，Resource ID 固定 `volc.bigasr.auc_turbo`，不是流式语音识别 1.0。
- 豆包流式 2.0 的 Resource ID 是 `volc.seedasr.sauc.duration` / `volc.seedasr.sauc.concurrent`，对应 WebSocket `sauc` 链路。
- 豆包录音文件识别 2.0 的 Resource ID 是 `volc.seedasr.auc`，对应标准版 `submit/query` 链路；也不能直接替换到 `recognize/flash`。

**修复**：
- 新增 `VolcAsrResourceIds`，集中维护当前文件极速接口允许的 Resource ID 和错配提示。
- `VolcAsrClient` 发请求前先做兼容性检查：填了流式 1.0/2.0 或录音文件标准版 1.0/2.0 Resource ID，会直接抛出可读配置错误，不再发错接口。
- 设置页 Resource ID 输入框增加当前链路提示，明确现阶段应填 `volc.bigasr.auc_turbo`。
- `stage2-handoff.md` 增加豆包 2.0 边界，后续升级应优先新增录音文件 2.0 标准版客户端；只有做实时字幕/边说边显才切流式 2.0。

**验证**：先新增 `VolcAsrClientTest` 两个 RED 用例，确认错配 Resource ID 会落到网络超时；实现后 targeted test 通过。`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过；真机安装待手机重新连接后统一执行。

---
date: 2026-05-30
module: release, device
type: verification
files: app-android/app/build/outputs/apk/debug/app-debug.apk
---
### 真机统一安装：打包当前全部更新并覆盖到一加手机

**背景**：贰玖重新连接手机，要求把本轮所有更新统一打包安装到手机上试用。

**包含更新**：
- 豆包 ASR Resource ID 错配保护和设置页提示。
- 一加实时活动 / promoted notification 检测与设置页入口。
- 说话人认领写回 Room。
- VAD 能量兜底、WorkManager Hilt 初始化修复、真实中文 ASR 测试样例等阶段 2 修复。

**验证**：
- `adb devices -l` 识别到 `PJZ110`，状态为 `device`。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` 返回 `Success`，覆盖安装且保留本地数据。
- `adb shell am start -n tech.echo.app/.MainActivity` 已启动 App，`dumpsys activity` / `dumpsys window` 均显示当前焦点为 `tech.echo.app/.MainActivity`。
- `dumpsys package` 显示 `POST_PROMOTED_NOTIFICATIONS` 与 `FOREGROUND_SERVICE_MICROPHONE` 权限 granted；`appops get` 仍可见 `POST_PROMOTED_NOTIFICATIONS` 包级 ignore 记录，若实时活动不显示，需在设置页进入系统实时活动开关确认。

---
date: 2026-05-30
module: ui-detail, core-upload
type: feature
files: app-android/app/src/main/java/tech/echo/app/ui/detail/TranscriptTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/DetailMappers.kt, app-android/app/src/main/java/tech/echo/app/core/data/repository/SegmentRepository.kt, app-android/app/src/main/java/tech/echo/app/core/data/db/SegmentDao.kt, app-android/app/src/main/java/tech/echo/app/core/upload/UploadWorker.kt, app-android/app/src/main/java/tech/echo/app/core/upload/UploadProcessor.kt
---
### 原始记录升级为语音气泡，并修复旧失败段阻塞新转写

**背景**：贰玖反馈原始记录中有录下来的片段显示“尚未转写”，并要求原始记录像 Telegram 语音消息一样显示声音气泡，可点击播放原始声音，播放时有进度条，下方再展示转写文字。

**排查结论**：
- 手机数据库中当天已有音频文件，问题不在录音落盘。
- 当时状态分布为 `DONE=10`、`FAILED=10`、`RECORDED=6`：`尚未转写` 的直接原因是这些段的 `transcriptText` 仍为空。
- 进一步查 WorkManager：上传任务已有多次 attempt，旧的 `FAILED` 段按时间排序排在前面，导致新录的 `RECORDED` 段被限制在 batch 外；另外系统中途停止 Worker 时会留下 `UPLOADING`，旧查询不会再捞它，可能永久卡住。

**修复**：
- 原始记录 UI 新增语音气泡：每段显示播放按钮、进度条、当前/总时长；点击后才懒加载 `MediaPlayer` 播放本地 WAV，避免列表渲染时提前创建音频 track。
- 每段语音气泡下方展示转写文本；无文本时根据状态显示“等待上传转写 / 正在转写 / 转写失败，后台会重试 / 暂无转写文本”。
- `TranscriptSegment` 补充 `audioPath`、`durationMs`、`status`，由 `DetailMappers` 从 Room segment 带出。
- 上传候选从 `RECORDED/FAILED` 扩展为 `RECORDED/UPLOADING/TRANSCRIBING/FAILED`，并在仓库层排序：新录音优先、被系统打断的上传中状态其次、旧失败最后。
- App 启动和新片段落盘都会主动 `enqueueNow()` 踢一次上传，避免旧 backoff 队列让新录音长期等待。
- `UploadProcessor` 增加失败原因日志；`UploadWorker` 不再让失败段触发 WorkManager 指数 backoff，失败段留在 DB，后续 enqueue 再重试。

**验证**：
- 新增/更新 `DetailMappersTest` 覆盖音频路径、时长、状态文案；更新 `SegmentRepositoryTest` 覆盖 `RECORDED` 优先于 `FAILED`，且 `UPLOADING` 可恢复。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 已 `adb install -r` 覆盖安装到 `PJZ110` 并启动 App。
- 安装后数据库状态无 `RECORDED/UPLOADING` 卡住项，剩余为 `DONE=16`、`FAILED=16`；失败项会在 UI 显示“转写失败，后台会重试”，不再混同为“尚未转写”。

---
date: 2026-05-30
module: ui-detail, core-audio
type: feature
files: app-android/app/src/main/java/tech/echo/app/ui/detail/TranscriptTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/VoiceWaveform.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingService.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingAutostartPolicy.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingLiveNotificationPolicy.kt
---
### 原始语音气泡改为声波样式，并修复重启后灵动岛通知断点

**背景**：贰玖希望原始记录中的语音进度条做成 Telegram 语音消息那种声波样式，同时反馈一加灵动岛不显示。

**修复**：
- 原始记录语音气泡从线性进度条改成确定性的声波条：每段按 `audioPath` 生成稳定波形，播放进度用已播放/未播放两段颜色区分。
- 抽出 `VoiceWaveform` 纯逻辑，覆盖波形稳定性和进度换算测试。
- 新增 `RecordingAutostartPolicy`：已完成引导且已有麦克风权限时，MainActivity 启动会自动拉起 `RecordingService`，修复覆盖安装/force-stop 后只进 App、不恢复前台服务的问题。
- 新增 `RecordingLiveNotificationPolicy`：实时活动通知使用新的 `echo_live_recording` channel，重要性升到 default，通知优先级升到 default，同时关闭声音和震动；删除旧低重要性 `echo_recording` channel，避免历史低通道继续压低 Live Alerts 呈现。

**排查结论**：
- 之前灵动岛不显示的第一断点是 `RecordingService` 未运行；修复后 `dumpsys activity services` 显示 `isForeground=true`。
- 第二断点是旧通知通道 `importance=2`、`SILENT`、`mUnimportant=true`，系统不愿意把它提升成 Live Alerts；修复后新通知为 `channel=echo_live_recording`、`importance=3`、`pri=0`、`mUnimportant=false`、`android.requestPromotedOngoing=true`。
- `appops get tech.echo.app POST_PROMOTED_NOTIFICATIONS` 仍可看到历史 `ignore` 记录；若真机 UI 仍不进灵动岛，需要从系统通知/实时活动权限确认 ROM 是否允许该 debug app promoted ongoing。

**验证**：
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- `adb -s 3d57140a install -r app/build/outputs/apk/debug/app-debug.apk` 返回 `Success`，并已启动 `tech.echo.app/.MainActivity`。
- 真机 dump 显示 `RecordingService` 前台运行，通知记录包含 `echo_live_recording`、`importance=3`、`android.requestPromotedOngoing=true`、`android.shortCriticalText=待命`。

---
date: 2026-05-30
module: core-audio
type: fix
files: app-android/app/src/main/java/tech/echo/app/core/audio/RecordingNotificationSpec.kt, app-android/app/src/test/java/tech/echo/app/core/audio/RecordingNotificationSpecTest.kt
---
### 修复灵动岛 UI 回归：Live Update 不能使用 colorized 通知

**背景**：贰玖反馈通知列表能看到 Echo，但一加灵动岛/流体云不显示；之前曾经显示过，是在“优化灵动岛 UI”后又消失。

**根因**：
- 现场系统设置页显示“流体云显示实时活动”已开启，通知权限不是断点。
- Android 官方 Live Update 文档要求候选通知不能 `setColorized(true)`。
- 我们之前为了让灵动岛状态更好看，给 `RecordingNotificationSpec` 开了 `colorized=true`，导致通知虽然在列表显示，但系统没有加 `PROMOTED_ONGOING` flag。

**修复**：
- `RecordingNotificationSpec.colorized` 默认改为 `false`，撤回状态色彩化卡片，只保留标准通知形态、短文案、ongoing、promotion request 和小图标。
- 更新 `RecordingNotificationSpecTest`，明确三态通知都不应 colorized，避免后续 UI 微调再次破坏 Live Update 资格。

**验证**：
- 先把测试改成期望 `colorized=false` 并确认 RED：`RecordingNotificationSpecTest` 失败在原有 `colorized=true`。
- 修复后 targeted test 通过；`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 已 `adb install -r` 覆盖安装到 `PJZ110`，force-stop 后启动 App。
- 真机 `dumpsys activity services` 和 `dumpsys notification --noredact` 显示通知 flags 已包含 `PROMOTED_ONGOING`，extras 中 `android.colorized=false`、`android.requestPromotedOngoing=true`、`android.shortCriticalText=待命`。
- `adb screencap` 确认手机顶部流体云已经显示 Echo 麦克风图标和“待命”。

---
date: 2026-05-30
module: core-audio
type: feature
files: app-android/app/src/main/java/tech/echo/app/core/audio/RecordingNotificationSpec.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingService.kt, app-android/app/src/test/java/tech/echo/app/core/audio/RecordingNotificationSpecTest.kt
---
### 灵动岛状态文案与涟漪呼吸色优化

**背景**：贰玖希望一加灵动岛左侧保留麦克风，右侧状态文案从“待命/录音”改为“聆听/录制”，并让聆听态显示蓝色涟漪呼吸、录制态显示红色涟漪呼吸。

**实现**：
- `RecordingNotificationSpec` 将待命态改名为 `Echo 聆听中` / `聆听`，录音态改名为 `Echo 录制中` / `录制`。
- 聆听态新增蓝色 `livePulseColor`，录制态新增红色 `livePulseColor`；暂停态不设置活动涟漪色。
- `RecordingService` 在 Android 16 / API 36+ 上用标准 `Notification.ProgressStyle` 写入单段彩色 indeterminate progress，让系统 Live Update / 一加流体云渲染活动呼吸效果。
- 保持 `colorized=false`，避免再次破坏 `PROMOTED_ONGOING` 资格；不使用 custom RemoteViews。

**验证**：
- 先补 `RecordingNotificationSpecTest` 期望新文案和 `livePulseColor`，确认 RED 为 `livePulseColor` 未实现。
- 实现后 targeted test 通过；`./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 已 `adb install -r` 覆盖安装到 `PJZ110` 并启动 App。
- 真机录制态 dump 显示 `android.template=android.app.Notification$ProgressStyle`、`android.progressIndeterminate=true`、`android.progressSegments`、`android.shortCriticalText=录制`、`color=0xffe5484d`、flags 包含 `PROMOTED_ONGOING`。
- 真机截图确认顶部流体云显示 Echo 麦克风图标和“聆听”。

---
date: 2026-05-30
module: ui-today, ui-history, ui-detail
type: feature
files: app-android/app/src/main/java/tech/echo/app/ui/today/TodayScreen.kt, app-android/app/src/main/java/tech/echo/app/ui/today/RecordingControl.kt, app-android/app/src/main/java/tech/echo/app/ui/history/HistoryScreen.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/DetailScreen.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/SummaryTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/TranscriptTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/ClaimSpeakerDialog.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/DetailMappers.kt
---
### 参考沉浸式 UI 图重做主流程并收敛字号

**背景**：贰玖给出 `/Users/heguangbao/dev/29space/tmp/echo-ui-redesign/v2-immersive-pages/` 里的 6 张参考图，希望 Echo 当前 UI 按这些图优化；真机预览后进一步反馈整体字号过大，历史/设置入口只保留 icon。

**实现**：
- 今天页改为沉浸入口：移除底部导航，右上仅保留历史/设置 icon，中间圆形录音主控按暂停/聆听/记录三态显示。
- 历史页改为极简日期列表：状态点、日期行和轻分割线。
- 详情页改为大标题 + 轻量双 tab，整理页隐藏空分区，正文和时间线改为阅读流。
- 原始记录改为参考图式行布局：时间、播放按钮、`Speaker A/B/C` chip、声波、时长、转写文本和更多入口；认领说话人改为底部弹层。
- 字号按移动端通用 type scale 收敛：页面标题不再使用展示级大字，状态/列表/正文/辅助文字分别降到更适合 App 的层级；历史/设置入口去掉文字标签。

**验证**：
- 先修改 `DetailMappersTest`，要求未认领说话人显示 `Speaker A`，确认 RED；实现后 targeted test 通过。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 已 `adb install -r` 覆盖安装到 `PJZ110` 并启动 App。
- 真机截图检查今天页、历史页、详情页，确认底部导航已移除、右上入口仅 icon、字号较前一版明显收敛。

---
date: 2026-05-30
module: ui-today, ui-history, ui-detail
type: polish
files: app-android/app/src/main/java/tech/echo/app/ui/today/TodayScreen.kt, app-android/app/src/main/java/tech/echo/app/ui/history/HistoryScreen.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/DetailScreen.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/TranscriptTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/ClaimSpeakerDialog.kt
---
### UI 细节二次收敛：删除无效入口并压缩原始记录

**背景**：贰玖真机预览后指出：头部离顶部过远、原始记录播放和 speaker 控件偏大、转写文本不应缩进、认领弹层标签换行、详情三点/历史搜索筛选无功能、首页统计应放进今日回声卡片、首页标题应改为“回声”、设置 icon 过重。

**实现**：
- 今天页标题从 `今天` 改为 `回声`，头部顶部间距从 58dp 收到 30dp；设置 icon 从齿轮改为更轻的 `Tune`。
- 首页底部 `今日回声` 改为轻卡片：左侧声波图标，中间标题/说明/统计，右侧箭头；`已记录 n 段 · 约 m 分钟` 移入卡片第三行。
- 历史页删除无功能的搜索和筛选 icon，顶部间距和列表行距收紧。
- 详情页删除无功能的右上三点菜单，头部间距收紧。
- 原始记录行压缩：播放按钮和 speaker pill 继续压到 24dp 高，声波高度下降，行距下降；转写文本从左侧内容边界开始，不再为播放按钮留缩进。
- 认领说话人弹层的快捷项改成和原始记录 speaker 一致的轻量胶囊样式，不再用大按钮等分布局。
- 原始记录转写文本保留 3 行摘要，点击文本后从底部弹出“完整转写”阅读层，展示时间、speaker、时长和完整文本，长文本区域可滚动。

**验证**：
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 已 `adb install -r` 覆盖安装到 `PJZ110` 并启动 App。
- 真机截图检查原始记录页，确认播放按钮和 speaker pill 已收小，点击转写文本可打开完整转写底部弹窗。

---
date: 2026-05-30
module: core-audio, ui-detail
type: polish
files: app-android/app/src/main/java/tech/echo/app/core/audio/RecordingNotificationSpec.kt, app-android/app/src/main/java/tech/echo/app/core/audio/RecordingService.kt, app-android/app/src/main/java/tech/echo/app/core/model/Summary.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/DetailViewModel.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/SummaryTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/TranscriptTab.kt, app-android/app/src/main/java/tech/echo/app/ui/detail/TranscriptTimeline.kt
---
### 灵动岛圆点、重新整理反馈与原始记录小时索引

**背景**：贰玖希望一加灵动岛活动态左侧不再显示麦克风，而是圆点呼吸；详情整理页需要重新整理入口；原始记录超过数百段后需要按时间倒序、按小时分组，并像通讯录索引一样在右侧快速切换小时。

**实现**：
- `RecordingNotificationSpec` 增加 `RecordingLiveIndicator`，聆听/录制态使用圆点图标，暂停态保留麦克风；聆听/录制继续走标准 `ProgressStyle` 和活动颜色，避免破坏 `PROMOTED_ONGOING`。
- 整理页在“日记”标题同一行右侧增加轻量 `重新整理` 动作；点击后先把当天 summary 状态写为 `GENERATING`，并用本地 loading 保底显示至少 1.5 秒，按钮显示转圈和“整理中”。
- `DailySummary` 带上 `summaryStatus`，详情页可以实时响应整理状态。
- 原始记录按 `startTime` 倒序展示，并按小时插入 `18:00` 这类分组标题。
- 右侧小时索引改为无外框全高透明触控区，默认浅色；按住/拖动时选中小时放大，并立即滚动到对应小时分组。

**验证**：
- 新增 `TranscriptTimelineTest` 覆盖倒序、小时分组和小时索引 item 位置；更新 `DetailMappersTest` 覆盖 summary 状态。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` 通过。
- 已 `adb install -r` 覆盖安装到 `PJZ110` 并启动 App。
- 真机截图确认整理页按钮已移动到“日记”标题行；原始记录页确认倒序、小时标题和右侧无外框小时索引显示。
- 受一加系统实时活动渲染限制，圆点 small icon 在流体云中仍可能被系统统一转成白色；代码已同时设置小图标和 `ProgressStyle` tracker icon 为状态圆点资源。
