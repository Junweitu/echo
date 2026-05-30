# 交付说明 · echo 录音引擎（:core-audio 真实实现）

> 对应任务：`docs/tech/tasks/codex-core-audio.md`（阶段 1 后端核心）
> 实现者：叁拾（Claude）。注：原计划交 Codex，本次由叁拾直接实现。
> 状态：✅ 编译通过 + 单测通过；⏳ 真机装机验证待贰玖在设备上跑。

## 1. 做了什么

打通阶段 1 的完整端上录音链路并接通 UI：

```
AudioRecord 采集(16k/mono/PCM)
   → RingBuffer 环形预录缓冲(滚动保存最近 ~1.5s)
   → SileroVadDetector(ONNX) 逐帧有声概率
   → VadStateMachine 切段(起始去抖/静音判尾/最短过滤/最长切段)
   → WavWriter 落盘 filesDir/audio/{yyyyMMdd}/{uuid}.wav
   → SegmentRepository 写 Room(segment 表)
   → RecordingStateHolder 状态桥 → RealRecordingController → 今天主页 UI
```

DSP 链路各组件（AudioCapture / SileroVadDetector / RingBuffer / VadStateMachine /
WavWriter / AudioConfig / 数据层）此前已就位，本次新增的是**把它们串起来的引擎、
前台服务、控制器与 UI 接线**。

## 2. 新增 / 改动文件

新增：
- `core/audio/RecordingEngine.kt` —— 纯逻辑引擎：消费帧流，按 VAD 事件补开头/写入/落盘/入库。无 Service 依赖，可单测。
- `core/audio/RecordingStateHolder.kt` —— @Singleton 状态桥，Service 写、Controller 读。
- `core/audio/RealRecordingController.kt` —— 合并引擎状态 + 当天 Room 统计，暴露 `StateFlow<TodayState>`；控制转发给 Service。
- `core/audio/AudioModule.kt` —— Hilt 把 `RecordingController` 绑定到真实实现。
- `test/.../RecordingEngineTest.kt` —— 端到端单测（见 §4）。

重写：
- `core/audio/RecordingService.kt` —— 占位 → 真实前台服务（前台通知 + 暂停/继续 Action + 音频焦点 + START_STICKY）。
- `ui/onboarding/OnboardingScreen.kt` —— 接真实权限申请（RECORD_AUDIO / POST_NOTIFICATIONS / 忽略电池优化）。
- `MainActivity.kt` —— onboarding 完成标记持久化(SharedPreferences) + 完成后启动服务。
- `EchoApplication.kt` —— 去掉手持 Fake，改纯 Hilt。
- `TodayViewModel.kt` —— 改 @HiltViewModel 注入真实控制器。
- `ui/nav/EchoNavHost.kt` —— TodayViewModel 改 hiltViewModel()。
- `WavWriter.kt` —— `file` 字段由 private 改为可读 val（引擎落库需路径）。

保留未删：`FakeRecordingController.kt`（已不被注入，留作调试，换实现时改 AudioModule 一行即可）。

## 3. 接口契约（UI 已据此接线）

```kotlin
interface RecordingController {
    val state: StateFlow<TodayState>   // status + 当天 segmentCount + totalMinutes
    fun start(); fun pause(); fun toggle()
}
```
- `state` = 引擎实时状态（RecordingStateHolder）⊕ 当天 Room 统计（段数/时长）合并。
- 控制方法转发到 `RecordingService` 的 start/pause（前台服务 action）。

服务控制入口（也供通知 Action 用）：
`RecordingService.start/pause/resume/stop(context)`。

## 4. 测试

`./gradlew testDebugUnitTest` 全过，共 19 个用例：
- `VadStateMachineTest`(7)：起始去抖、孤立噪声丢弃、正常落盘、短停顿不断段、最长切段、forceEnd、连续段。
- `RingBufferTest`(7)：顺序/写满/覆盖最旧/拷贝隔离/clear/防丢开头补 1.5s。
- `WavWriterTest`(2)：WAV 头与长度、批量写。
- `RecordingEngineTest`(3，本次新增，端到端验收点 1/2）：
  - 全程静音 → 不落盘不入库。
  - 前置静音+突然说话 → 落盘一段，**段首确实是触发点之前的预录静音帧**（防丢开头核心卖点），时长/总帧数精确匹配。
  - 两段说话中间静音 → 分别落盘两段。

编译验证：
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest   # BUILD SUCCESSFUL，19/19 通过
./gradlew assembleDebug       # BUILD SUCCESSFUL，产出 app/build/outputs/apk/debug/app-debug.apk
```

## 5. 验收点对照（codex-core-audio.md §3）

| # | 验收点 | 状态 | 说明 |
|---|--------|------|------|
| 1 | 说话才落盘、静音不落盘 | ✅ 单测验证 | RecordingEngineTest |
| 2 | 开头不丢字（环形缓冲） | ✅ 单测验证 | 落盘文件段首为预录历史帧 |
| 3 | 前台 Service 常驻通知 + 暂停/继续 | ✅ 已实现 | ⏳ 真机交互待验 |
| 4 | 杀后台对抗（电池白名单 + START_STICKY） | ✅ 已实现 | START_STICKY + onboarding 引导电池白名单 |
| 5 | Room 查到片段、当天计数/时长正确 | ✅ 已实现 | RealRecordingController combine Room Flow |
| 6 | 通过编译 assembleDebug | ✅ 已验证 | JDK 21 |
| 7 | 关键逻辑单测 | ✅ 19/19 | VAD/环形/引擎 |
| 8 | 不硬编码密钥 | ✅ | 阶段 1 无云服务 |

## 6. TODO / 已知降级

- **Opus 编码降级为 WAV**：阶段 1 落 WAV（可直接回听验证），抽象 open/writeFrame/close 不变，阶段 2 换 Opus 仅改 WavWriter 内部。
- **跨天刷新**：RealRecordingController 的 `today` 取一次，跨天后计数需重启应用刷新。阶段 2 接每日整理时改为按当前日期动态查询。
- **7 天清理 / 总量上限**：WorkManager 清理调度阶段 1 未做（design §5.1），待阶段 3。
- **真机装机验证（命门）**：编译与逻辑已验证，但"能否跑满一天、各厂商 ROM 杀后台、实际录音质量、通知交互"必须在真机上跑。叁拾无连接设备，这一步需贰玖在 Android Studio 装机实测。

## 7. 真机自测清单（建议贰玖按此跑）

1. 装机首启 → 走完引导，授予麦克风 + 通知权限 + 电池不优化。
2. 今天页点圆形主控 → 状态转「正在聆听」，下拉看到常驻通知「正在聆听」。
3. 对手机说几句话 → 圆变红涟漪加快（RECORDING），停下约 1 秒 → 回聆听；今天页「已记录 N 段」+1。
4. 静默放置 → 不应新增段。
5. 通知点「暂停」→ 状态转「已暂停」，说话不再落盘；点「继续」恢复。
6. 来电/开另一个录音 App → echo 自动暂停，结束后自动续。
7. 回听落盘文件（adb pull filesDir/audio/）→ 句子开头完整不丢字。
8. 锁屏放置一段时间 → 录音不中断（验证 START_STICKY + 电池白名单）。
