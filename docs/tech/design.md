# echo · 技术设计文档

> 状态：🚧 待贰玖审阅 → 交付 Codex 实现
> 最后更新：2026-05-29
> 配套阅读：`docs/product/core/requirements.md`、`docs/product/core/ui-design.md`

## 0. 这份文档的定位

这是 echo 的**技术实现蓝图**，交给 Codex 落地代码用。它要回答的不是"做什么"（需求文档已答），而是"**怎么实现、用什么、数据怎么流、模块怎么拆**"。

阅读对象：Codex（实现）+ 贰玖/叁拾（技术评审）。要求自包含——不假设读者有对话上下文。

---

## 1. 技术栈（定死）

| 层 | 选型 | 理由 |
|----|------|------|
| 客户端 | **Android 原生 Kotlin** | 后台常驻录音深度依赖系统能力，Flutter/RN 在前台 Service + 音频底层坑多 |
| UI | **Jetpack Compose + Material 3** (`androidx.compose.material3`) | 声明式、与 UI 文档的 M3 约束一致 |
| 最低版本 | **minSdk 26 (Android 8.0)**，targetSdk 跟最新稳定版 | 前台 Service、Foreground Service Type 等 API 要求 |
| 本地数据库 | **Room** (SQLite) | 存片段元数据、转写、说话人、每日整理结果 |
| 音频文件 | 应用私有目录 `filesDir/audio/` | 不进公共媒体库，隐私 |
| 后台任务 | **WorkManager** | 上传补传、每日整理定时，系统友好、可约束网络/充电 |
| 依赖注入 | **Hilt** | 标准做法，模块解耦 |
| 异步 | **Kotlin Coroutines + Flow** | 录音流、状态流 |
| VAD | **Silero VAD**（ONNX，端上） | 轻量、准、跨设备稳定；备选 WebRTC VAD |
| ASR + 说话人分离 | **火山引擎录音文件识别**（中文强、自带说话人分离） | 中文 + diarization 成熟，连续/远场/多人场景调得最多；数据不出境更合规 |
| 声纹特征 | 端上声纹 embedding 模型（如 3D-Speaker/CAM++ ONNX）+ 本地比对 | 隐私，离线可算 |
| 每日整理 | **DeepSeek API**（可配置，便于切换） | 中文好、便宜、贰玖在 upcv 已有评估经验；整理属结构化抽取，国内模型绰绰有余 |

> Codex 注意：ASR 和 LLM 的具体 SDK 在第二阶段才接，第一阶段（MVP1）不依赖任何云服务。选型已定**火山 ASR + DeepSeek 整理**，但 `AsrClient` 和 `LlmClient` 仍按接口抽象，便于未来切换。

---

## 2. 总体架构

三层 + 一条单向数据流水线：

```
┌──────────────────────── Android 客户端 ────────────────────────┐
│                                                                │
│  [录音层]  AudioRecord 常驻采集 (16kHz/mono/PCM)                 │
│      │  音频帧流 (Flow)                                         │
│      ▼                                                         │
│  [VAD 层]  Silero VAD 判定有声/无声 + 环形预录缓冲               │
│      │  切出"有人说话"的片段                                    │
│      ▼                                                         │
│  [存储层]  音频文件落盘 + Room 记录片段元数据 + 入上传队列        │
│      │                                                         │
│      ▼  (WorkManager, 联网时)                                  │
│  [上传层]  上传片段到云端 ASR                                   │
│      │                                                         │
│      ◄── 转写结果 + 说话人分离 (Speaker A/B/C) 写回 Room        │
│      │                                                         │
│  [声纹层]  对片段算 embedding，比对本地声纹库 → 命中贴名          │
│      │                                                         │
│  [整理层]  每日 WorkManager 触发，聚合当天转写 → 大模型 → 整理结果│
│      │                                                         │
│  [UI 层]   Compose 读 Room 渲染：今天 / 历史 / 详情             │
│                                                                │
└────────────────────────────────────────────────────────────────┘
        │ 仅上传有效语音片段                  │ 仅上传当天转写文本
        ▼                                    ▼
   [云端 ASR + 说话人分离]            [大模型整理 API]
```

核心原则：**端上做"听/判/存/算声纹"，云端只做"转写"和"整理"**。音频不长期留云端，声纹库永远只在本地。

---

## 3. 录音层（前台 Service）

### 3.1 为什么必须是前台 Service
Android 8.0+ 后台无法长时间持有麦克风。唯一可行解：**Foreground Service**，类型声明为 `microphone`（`android:foregroundServiceType="microphone"`，Android 14+ 强制），带一条常驻通知。这是"能跑满一天"的技术地基。

### 3.2 采集参数
- API：`AudioRecord`（比 MediaRecorder 更底层、能拿到 PCM 帧做 VAD）。
- 格式：`16000 Hz / 单声道 / 16-bit PCM`。够 ASR 用，又省空间和带宽。
- 帧长：按 VAD 模型要求切（Silero 推荐 32ms/512 采样点一帧）。
- 采集到的 PCM 帧以 `Flow<ShortArray>` 形式往下游推。

### 3.3 生命周期与韧性
- Service 设 `START_STICKY`，被杀后系统尽量重建。
- 提供前台通知的「暂停/继续」Action（隐私开关，对应 UI 文档 §4.5）。
- 监听音频焦点冲突（来电、其他录音 App）：被抢时优雅暂停并在通知提示，恢复后自动续。
- **杀后台对抗**（命门）：首启引导用户加电池白名单（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）；文档化各厂商「锁后台」路径，但不在代码里 hack。

---

## 4. VAD 层（人声自动激活 + 不丢开头）

这是 echo "零摩擦不遗漏"的技术核心，单独拎出来重点设计。

### 4.1 判定
- 用 **Silero VAD**（ONNX Runtime Mobile 跑）对每帧输出"有声概率"。
- 状态机：`静音 → 疑似有声 → 确认录入 → 疑似结束 → 落盘`
  - 连续 N 帧高于阈值 → 进入"录入中"。
  - 连续 M 帧低于阈值（如静音 > 0.8 秒）→ 判定一段结束。
- 阈值、N、M 做成可调常量，第一阶段给一组经验默认值，跑两天后调。

### 4.2 环形预录缓冲（防丢句子开头）
**问题**：等 VAD 反应过来，"喂那个"已经说完。
**解法**：内存里维护一个固定容量的**环形缓冲**，永远滚动保存最近约 **1.5 秒**的 PCM 帧。一旦 VAD 触发"录入中"，把缓冲区里的历史帧**一并接到这段录音的开头**。
- 缓冲大小 = 1.5s × 16000 × 2 byte ≈ 48KB，内存无压力。
- 实现：定长队列（`ArrayDeque` 或 ring buffer），满了丢最旧帧。

### 4.3 一段的边界
- 一段最短时长门槛（如 < 1 秒的孤立声音）可丢弃，避免咳嗽/关门误触发。
- 一段最长时长上限（如 60 秒）强制切段，避免一段录音过大、也便于转写并发。
- 长时间连续说话超上限 → 自动切成多段，相邻段标记可拼接。

---

## 5. 存储层（本地真相源）

### 5.1 音频文件
- 路径：`filesDir/audio/{yyyyMMdd}/{segmentId}.opus`（落盘时 PCM → Opus 编码省空间）。
- **保留策略：7 天**（需求已定）。WorkManager 每日清理超期音频；转写成功的可提前清，但 7 天内可回听优先保留。
- 加一个总容量上限兜底（如 2GB），超了从最旧删。

### 5.2 Room 数据模型
四张核心表：

```
segment（语音片段）
  id, date(yyyyMMdd), startTime(epoch ms), durationMs,
  audioPath, speakerLabel(A/B/C，转写后填),
  speakerPersonId(声纹命中后填, FK→person),
  transcriptText(转写后填),
  status(枚举: recorded/uploading/transcribing/done/failed)

person（已认领的说话人 / 声纹库条目）
  id, name(我/老婆/张三), voiceprintVector(BLOB, embedding),
  sampleCount(累积样本数), createdAt

daily_summary（每日整理结果）
  date(yyyyMMdd, PK),
  diary(日记文本), todos(JSON 列表), inspirations(JSON 列表),
  timeline(JSON: [{time, person, topic}]),
  status(枚举: pending/generating/done/failed), generatedAt

upload_queue（上传任务，也可直接用 WorkManager 管理）
  segmentId, retryCount, lastError
```

- `segment` 是中心表，状态字段驱动整条流水线。
- `speakerLabel`（A/B/C 临时编号）和 `speakerPersonId`（声纹命中的真名）分开存：先有编号，认领后回填 person。

---

## 6. 上传 + ASR 层（云端转写 + 说话人分离）

### 6.1 上传策略
- 用 **WorkManager** 管理上传，约束 `NetworkType.CONNECTED`（可选仅 WiFi，做成开关）。
- 断网/失败：指数退避重试，`retryCount` 累计；多次失败标 `failed`，UI 不强提示（避免打扰），后台继续重试。
- 一段一个上传任务，互不阻塞；并发上限设 2~3，避免占满带宽。

### 6.2 ASR 选型与对接
- **批量录音文件识别**，不用实时流式（便宜、每日整理不需实时）。
- **已定：火山引擎录音文件识别**（中文 + 说话人分离成熟，数据不出境）。
- **关键要求：开启「说话人分离 / 角色分离」**，转写结果直接带 Speaker A/B/C 标签和每段时间戳。这样 diarization 免费搭车，不用自己做。
- 仍按 `AsrClient` 接口抽象，火山是第一个实现，便于未来切阿里/腾讯。
- 返回结果结构（统一抽象，屏蔽厂商差异）：
  ```
  [{ speakerLabel, text, startMs, endMs }, ...]
  ```
- **密钥管理**：火山 AppID/AK/SK **不硬编码、不进 git、不进 APK**。由用户在 **App 内设置页**填写，存本地加密 DataStore，运行时读取构造签名。原因：echo 可能公开分发，key 焊进包里反编译可提取，等于泄露。早期贰玖自用阶段也走同一条路（自己填自己的）。Codex/实现者不得把任何密钥写进源码或 BuildConfig。

### 6.3 写回
转写成功 → 更新 `segment.transcriptText` + `speakerLabel`，状态置 `done`，触发声纹层。

---

## 7. 声纹层（说话人分离 → 渐进式认领）

分两层，对应需求文档 §4.6。

### 7.1 第一层：说话人分离（已由 ASR 提供）
直接用 ASR 返回的 Speaker A/B/C。**注意**：这个编号只在「单次识别请求/单段音频」内一致，跨段不保证 A 还是同一个人。所以真正"跨段认人"靠下面的声纹层。

### 7.2 第二层：声纹认领（echo 特色）
**注册（事后标注，不预录）**：
- 用户在 UI 详情页点未认领的 Speaker Chip → 输入名字 → 该片段音频送声纹模型算 **embedding 向量** → 存进 `person.voiceprintVector`。
- 当天所有同一 `speakerLabel` 的片段一起回填该 `personId`。

**自动识别**：
- 每个转写完成的片段算 embedding，与 `person` 表所有向量做**余弦相似度**比对。
- 相似度 > 阈值（如 0.75，可调）→ 命中，回填 `speakerPersonId`，UI 显示真名。
- 不过阈值 → 保留 Speaker N，等用户认领。**宁可留编号不贴错名。**

**声纹库自我增强**：
- 每次用户确认认领，把新片段 embedding 并入该 person（增量平均 / 多向量存储），`sampleCount++`，越用越准。

**声纹模型**：
- 端上 ONNX 声纹 embedding 模型（如 CAM++/3D-Speaker，输出 192/256 维向量）。
- 全程离线，向量只存本地，不上云。

### 7.3 现实预期（写进文档管理期望）
远场、嘈杂、多人抢话时会认错或认不出。这是"锦上添花"能力，不追求 100% 准，靠用户事后手改兜底。

---

## 8. 整理层（每日大模型汇总）

### 8.1 触发
- **凌晨自动**：WorkManager 周期任务，约凌晨 3~4 点跑前一天的整理（设备充电+联网时优先）。
- **手动补**：UI "整理今天"按钮立即触发同一逻辑（对应 UI 文档 §4.1）。

### 8.2 逻辑
1. 取当天所有 `status=done` 的 segment，按时间排序。
2. 拼成带「时间 + 说话人（真名或编号）+ 文本」的对话流。
3. 调大模型，要求按固定 schema 输出四块：日记、待办、灵感、时间线。
4. 写入 `daily_summary`，状态 `done`。

### 8.3 Prompt 要点（给 Codex 的约束）
- 系统 prompt 固定四段产出结构，要求**只基于转写内容**，不编造。
- 待办：提取明确的行动项/承诺，给简短动词开头的条目。
- 时间线：`[{time, person, topic}]`，topic 一句话。
- 输出强制 JSON，便于解析入库。
- **大模型已定 DeepSeek**（走 `LlmClient` 接口，endpoint 和 key 配置化，不硬编码，便于切换）。

### 8.4 成本控制
- 只喂文本不喂音频。
- 一天一次批量，不逐段调用。
- 转写文本量大时分段摘要再汇总。

---

## 9. 模块划分（Codex 实现的代码结构建议）

按职责分层，便于分阶段交付和测试：

```
:app                     # UI + 入口
  ui/today               # 今天主页
  ui/history             # 历史列表
  ui/detail              # 当天详情（整理页 + 原始记录页 + 认领弹窗）
  ui/onboarding          # 首启引导
  ui/theme               # M3 黑白主题（color/type/shape）

:core-audio              # 录音 + VAD（可独立测试）
  RecordingService       # 前台 Service
  AudioCapture           # AudioRecord 封装
  VadDetector            # Silero VAD + 状态机
  RingBuffer             # 环形预录缓冲

:core-data               # Room + 仓库
  db (entities/dao)
  repository             # SegmentRepo / PersonRepo / SummaryRepo

:core-upload             # 上传 + ASR 抽象
  AsrClient(interface)   # 屏蔽厂商；火山/阿里/腾讯各一实现
  UploadWorker

:core-voiceprint         # 声纹 embedding + 比对
  VoiceprintExtractor
  VoiceprintMatcher

:core-summary            # 每日整理
  SummaryWorker
  LlmClient(interface)
```

> 分模块是为了「录音层能脱离云服务单独跑通」（MVP1），以及对抗性 review 时职责清晰。Codex 若觉得 module 拆太细可降级为 package，但分层边界要保留。

---

## 10. 权限清单

| 权限 | 用途 | 申请时机 |
|------|------|---------|
| `RECORD_AUDIO` | 录音 | 首启引导 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | 前台录音服务 | 声明即可 |
| `POST_NOTIFICATIONS` | 常驻通知（Android 13+） | 首启引导 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 电池白名单对抗杀后台 | 首启引导 |
| `INTERNET` | 上传转写 + 整理 | 声明即可 |
| `RECEIVE_BOOT_COMPLETED`（可选） | 开机自启录音 | 第三阶段，需用户开关 |

---

## 11. MVP 三阶段技术拆解（与需求文档对齐）

### 阶段 1 · 验习惯（不依赖任何云服务）
**目标**：能后台连续录音 + VAD 自动落盘 + 本地存音频，跑两天看电量/存储/习惯。
- `:core-audio` 全部：前台 Service、AudioRecord、Silero VAD、环形缓冲、状态机。
- `:core-data` 的 segment 表 + 落盘。
- `:app` 的「今天」主页（录音开关 + 状态圆 + 呼吸动画）+ 常驻通知 + 首启引导（麦克风权限 + 电池白名单）。
- 验收：开一天，能稳定录到有声片段、不丢句子开头、静音不落盘、App 不被杀。

### 阶段 2 · 验价值（接云端）
**目标**：转写 + 每日整理跑通，确认 AI 整理有用。
- `:core-upload`：选一家 ASR 接入，上传 + 批量转写 + 写回。
- `:core-summary`：每日整理 Worker + 大模型 + daily_summary。
- `:app` 的「历史」列表 + 「当天详情-整理页」。
- 验收：第二天能看到前一天的日记/待办/灵感/时间线，内容可用。

### 阶段 3 · 顺手（说话人 + 体验）
**目标**：说话人分离 + 声纹认领 + 完整体验。
- 启用 ASR 的说话人分离，详情页「原始记录」展示 Speaker chip。
- `:core-voiceprint`：embedding + 比对 + 认领弹窗 + 自动识别。
- 断网补传、音频清理、暂停/继续完善、深色模式。
- 验收：能认领说话人、再次出现自动贴名、断网不丢数据。

---

## 12. 技术风险登记

| 风险 | 影响 | 缓解 |
|------|------|------|
| 国产 ROM 杀后台 | 录音中断、漏录 | 电池白名单引导 + START_STICKY + 文档化各厂商设置；实测各机型 |
| VAD 误触发/漏触发 | 录噪音 or 丢人声 | 阈值可调 + 最短段过滤 + 跑两天调参 |
| ASR 中文 + 说话人分离质量 | 转写脏、分离乱 | 先小样本对比火山/阿里/腾讯再定 |
| 声纹准确率（远场/嘈杂） | 认错人 | 高阈值 + 留编号不乱贴 + 事后手改 |
| 连续录音耗电 | 用户嫌费电 | 实测功耗，必要时降采样/优化 VAD 频率 |
| 隐私（录到他人） | 伦理/法律 | 贰玖自担 + 随手暂停；不做识别陌生人 |
| 存储膨胀 | 占满空间 | 7 天清理 + 总量上限 + Opus 压缩 |

---

## 13. 选型已定 / 待评审确认

**已定（2026-05-29 贰玖拍板）**：
- **ASR：火山引擎**录音文件识别（中文 + 说话人分离）。
- **整理 LLM：DeepSeek**（可配置切换）。
- **全用国内模型**，连续录音数据不出境，合规更干净。
- **模型配置走 App 内全用户配置**（2026-05-29 追加拍板）：阶段 2 做设置页，
  让用户自填 LLM 的 baseUrl/模型名/apiKey 和火山 ASR 的 AppID/AK/SK，
  存本地加密 DataStore。**不内置任何 key、不进 APK**。LLM 的 baseUrl/模型名
  给 DeepSeek 默认值可改；火山完全用户配置。早期贰玖自用也走同一条路。
  - ⚠️ 公开分发遗留待解（功能不阻塞，上架前需想清）：① 火山 ASR 用户配置门槛高
    （要自己去火山控制台开通+建应用拿 AppID/AK/SK，非技术用户劝退）；
    ② 录到第三方语音的隐私合规（需隐私政策/录音告知/用户自担条款，
    "贰玖自担"前提在公开分发后不成立）；③ Google Play 对常驻麦克风审核严。

**待确认**：
1. ~~MVP1 是否就按"纯本地录音验习惯"~~ → 已定，阶段 1 录音引擎已实现（见 journal 2026-05-29）。
2. 模块拆分粒度是否接受（多 module vs 单 module 多 package）→ 阶段 1 已按**单 module 多 package** 落地（`core.audio`/`core.data`/`ui` 分包）。

---

> 下一步：贰玖确认本文档 → 我把**阶段 1** 拆成 Codex 可执行的自包含任务文档（含验收标准、参考文件、质量要求）→ Bash 调 codex-companion 执行 → 叁拾对抗性 review。
