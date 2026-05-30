# Codex 任务：echo 录音引擎（:core-audio + Room segment 存储）

> 任务类型：Android 端上录音引擎实现（MVP 阶段 1 的后端核心）
> 执行者：Codex
> 审查者：叁拾（Claude）会对本任务做对抗性 code review
> 本文档自包含，不依赖任何对话上下文。

## 0. 背景（你必须先理解）

echo 是一个**个人 Android 语音生活记录 App**：后台持续监听，**听到有人说话就自动录音**，每天把语音整理成日记/待办/灵感/时间线。

你负责的是**最底层、最关键的录音引擎**——它决定整个产品"能不能跑满一天、会不会漏录"。这是阶段 1（纯本地，不接任何云服务）的后端核心。

UI 层由叁拾（Claude）用 Jetpack Compose 并行实现，**你不要碰 UI 代码**。你只做录音采集、VAD、本地存储这条链路，并暴露干净的接口/状态给 UI 层消费。

## 1. 项目环境（已确认）

- 工程根目录：`/Users/heguangbao/dev/echo/app-android/`（叁拾会先初始化 Gradle 工程骨架，见第 6 节协调）
- 语言：Kotlin
- compileSdk / targetSdk：**36**；minSdk：**26**
- build-tools：36.1.0
- JDK：用 Android Studio 自带 JDK 21（`/Applications/Android Studio.app/Contents/jbr/Contents/Home`）
- SDK 路径：`~/Library/Android/sdk`
- 异步：Kotlin Coroutines + Flow
- 本地库：Room、Hilt（DI）
- VAD：Silero VAD（ONNX Runtime Mobile）

## 2. 你要交付的能力（范围严格限定）

实现一条链路：**麦克风常驻采集 → VAD 判定人声 → 环形预录缓冲补开头 → 切出有声片段 → Opus 落盘 → Room 记录元数据**。

### 2.1 前台 Service（RecordingService）
- Foreground Service，类型 `microphone`（manifest 声明 `foregroundServiceType="microphone"`）。
- 常驻通知：标题随状态变（"正在聆听" / "已暂停，未在录音"），一个 Action 按钮（暂停/继续）。
- `START_STICKY`，被杀后尽量重建。
- 暴露录音状态给外部（StateFlow）：`Idle / Listening / Recording(检测到人声) / Paused`。
- 提供 start/pause/resume/stop 控制接口（供 UI 层 ViewModel 调用，用绑定 Service 或共享仓库的方式，见 6.2）。
- 处理音频焦点：被来电/其他录音 App 抢占时优雅暂停，恢复后续录。

### 2.2 音频采集（AudioCapture）
- 用 `AudioRecord`（不是 MediaRecorder，要拿 PCM 帧）。
- 参数：16000Hz / 单声道 / 16-bit PCM。
- 帧大小按 Silero VAD 要求（推荐 512 采样点 / 32ms 一帧）。
- 以 `Flow<ShortArray>` 推送 PCM 帧。

### 2.3 VAD 判定（VadDetector）
- 集成 **Silero VAD** ONNX 模型（用 onnxruntime-android），对每帧输出有声概率。
- 状态机：`静音 → 疑似有声 → 录入中 → 疑似结束 → 落盘`
  - 连续 N 帧 > 阈值 → 进入"录入中"
  - 连续静音 > 约 0.8 秒 → 判定一段结束
- 阈值、N、静音时长做成**可配置常量**（集中放一个 Config 对象），给一组合理默认值。

### 2.4 环形预录缓冲（RingBuffer）—— 防丢句子开头（重点）
- 内存里维护定长环形缓冲，**永远滚动保存最近约 1.5 秒**的 PCM 帧。
- VAD 触发"录入中"瞬间，把缓冲区里的历史帧**接到本段录音开头**，保证不丢"喂、那个…"这种开头。
- 这是产品核心卖点之一，务必正确实现并写单测验证。

### 2.5 片段边界
- 最短段过滤：< 1 秒的孤立声音丢弃（避免咳嗽/关门误触发）。
- 最长段上限：单段 > 60 秒强制切段。

### 2.6 落盘 + 编码
- 落盘前 PCM → **Opus** 编码（省空间）。若 Opus 编码在端上集成成本过高，**可先落 PCM/WAV，但要在代码注释和交付说明里标记 TODO**，不要卡住主链路。
- 路径：`filesDir/audio/{yyyyMMdd}/{segmentId}.{ext}`

### 2.7 Room 存储（:core-data 的 segment 部分）
建 `segment` 表（其他表本任务不做）：
```
segment:
  id (String/UUID, PK)
  date (String, yyyyMMdd)
  startTime (Long, epoch ms)
  durationMs (Long)
  audioPath (String)
  speakerLabel (String?, 本阶段留空)
  speakerPersonId (String?, 本阶段留空)
  transcriptText (String?, 本阶段留空)
  status (String 枚举: recorded/uploading/transcribing/done/failed，本阶段只用 recorded)
```
- 提供 `SegmentDao` + `SegmentRepository`：插入片段、按日期查询、查当天计数和总时长（供 UI "已记录 N 段 · 约 X 分"）。

## 3. 验收标准（必须全部满足）

1. App 启动录音后，**说话才落盘，静音不落盘**（VAD 生效）。
2. 录音片段**开头不丢字**（环形缓冲生效）——用单测喂一段"前置静音+突然说话"的 PCM，验证落盘片段包含触发点之前约 1.5 秒的数据。
3. 前台 Service 带常驻通知，能暂停/继续，暂停后不再落盘。
4. 杀后台对抗：申请电池白名单（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`），START_STICKY。
5. Room 能查到片段，当天计数/时长正确。
6. **代码能通过编译**：用 Android Studio 自带 JDK 21 跑 `./gradlew :core-audio:assembleDebug`（或对应 module）通过。
7. 关键逻辑（VAD 状态机、环形缓冲、片段边界）有**单元测试**且通过。
8. 不硬编码任何密钥（本阶段也没有云服务）。

## 4. 质量要求

- 遵循 Kotlin 官方代码风格，Coroutines/Flow 正确使用，不阻塞主线程。
- VAD 状态机、环形缓冲必须有单测，能脱离真机用合成 PCM 数据验证。
- 错误处理：麦克风权限被拒、AudioRecord 初始化失败、磁盘写满，都要优雅处理不崩溃。
- 内存：环形缓冲固定容量，不无限增长；长时间录音不 OOM。
- 注释密度匹配项目（中文注释 OK），关键决策写清楚为什么。
- **叁拾会做对抗性 review**：重点查 VAD 漏判/误判、环形缓冲边界、Service 被杀恢复、并发安全。请自检这些点。

## 5. 参考文件（必读）

- `/Users/heguangbao/dev/echo/docs/tech/design.md` —— 技术蓝图，§3 录音层、§4 VAD 层、§5 存储层是你的主战场。
- `/Users/heguangbao/dev/echo/docs/product/core/requirements.md` —— 需求，§4.1~4.3 能力链。
- 不要看 UI 文档以外去改 UI；UI 是叁拾的活。

## 6. 与 UI 层（叁拾）的协调约定

### 6.1 模块边界
- 你的代码放在 `:core-audio` 和 `:core-data`（segment 部分）两个 module（或 package，若工程是单 module 多 package 结构，以叁拾初始化的实际结构为准——**先读 settings.gradle 确认**）。
- **绝对不要改 `:app` 下的 UI 代码、Theme、Compose 文件。**

### 6.2 给 UI 层的接口契约（重要，UI 要靠这个接线）
请暴露一个清晰的录音控制 + 状态消费接口，建议：
```kotlin
interface RecordingController {
    val state: StateFlow<RecordingState>   // Idle/Listening/Recording/Paused
    fun start()
    fun pause()
    fun resume()
    fun stop()
}
// RecordingState 含：当前状态枚举 + 当天已记录段数 + 当天总时长ms
```
- UI 层会注入这个接口驱动"今天主页"的圆形主控和"已记录 N 段"。
- 把这个接口和 RecordingState 数据类定义清楚，叁拾据此接线。

### 6.3 交付说明
完成后在 `/Users/heguangbao/dev/echo/docs/tech/codex-handoff-core-audio.md` 写一份交付说明：做了什么、接口怎么用、跑了哪些测试、有哪些 TODO（如 Opus 编码降级）、编译验证命令和结果。

## 7. 明确不做

- ❌ 不做任何 UI / Compose / Theme。
- ❌ 不接云端 ASR、不接 DeepSeek、不做声纹、不做每日整理（那是阶段 2/3）。
- ❌ 不做上传队列（阶段 2）。
- ❌ 不擅自扩大范围；发现需要改 UI 或工程骨架，先在交付说明里记录并停下，等叁拾协调。
