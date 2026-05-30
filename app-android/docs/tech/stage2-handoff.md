# 阶段 2 交接文档（接云：转写 + 每日整理）

> 状态：✅ 代码实现完成（待真机 + 真实 key 验证）
> 最后更新：2026-05-30
> 写给：下一个接手的 AI Agent / 叁拾自己（跨 session）
> 前置阅读：`docs/product/core/requirements.md`、`docs/product/core/ui-design.md`、`docs/tech/design.md`

## 0. 一句话现状

阶段 2 共 5 个子任务（见下方任务清单）。**数据层 v2、设置页导航、DeepSeek 整理层、火山 ASR 上传层、UI 接真实数据均已完成代码实现**。当前已通过 `:app:testDebugUnitTest` 和 `:app:compileDebugKotlin`；云端能力仍需贰玖用真实火山/DeepSeek key 在真机验证。

## 1. 关键决策（已拍板，不要推翻）

| 决策 | 内容 | 出处 |
|------|------|------|
| 模型配置方式 | **App 内全用户配置**，不内置任何 key、不进 APK/git。火山+DeepSeek 都用户自填。 | journal 2026-05-29 |
| 火山实现策略 | 已找到官方“大模型录音文件极速版识别 API”：`POST /api/v3/auc/bigmodel/recognize/flash`，支持本地文件 base64 直传，Resource ID 默认 `volc.bigasr.auc_turbo`。`VolcAsrClient` 已按官方文档实现旧控制台 AppKey+AccessKey；新控制台可只填 API Key。 | volcengine.com/docs/6561/1631584 |
| 豆包 2.0 边界 | 当前 Echo **不是流式 1.0**，而是录音文件极速版。官方流式 2.0 是 WebSocket `sauc` 链路，录音文件 2.0 标准版是 `submit/query` 链路，二者都不能只替换 Resource ID 接入 `recognize/flash`。当前代码会拦截 `volc.seedasr.sauc.*`、`volc.bigasr.sauc.*`、`volc.seedasr.auc`、`volc.bigasr.auc` 这类错配资源，并提示先使用 `volc.bigasr.auc_turbo`。 | volcengine.com/docs/6561/1354868, volcengine.com/docs/6561/1354869 |
| DeepSeek 规格 | 已查准：OpenAI 兼容，`POST {baseUrl}/chat/completions`，`Authorization: Bearer <key>`，支持 `response_format:{type:"json_object"}` 且 prompt 必须包含 json/schema。默认模型更新为 `deepseek-v4-flash`。 | api-docs.deepseek.com |
| 推进方式 | 贰玖要求"一次全做完"，除火山 HTTP 实现外都要落地。 | 本 session |

## 2. 任务清单与状态

- ✅ **#3 数据层 v2**：daily_summary 表 + segment 状态扩展 + Room 1→2 迁移。
- ✅ **#4 设置页 + 加密存储**：设置页导航已接入，今天页右上角设置入口可进入。
- ✅ **#5 DeepSeek 整理层**：LlmClient + DeepSeek 客户端 + SummaryGenerator + SummaryWorker + 每日调度/手动触发。
- ✅ **#6 ASR 上传层**：AsrClient + VolcAsrClient + UploadProcessor + UploadWorker；录音落段后自动 enqueue 上传。
- ✅ **#7 UI 接真实数据**：历史/详情接 Room，今天页使用真实日期并可触发“整理今天”。

## 3. 已完成的代码（#3 数据层 v2）

全部在 `app/src/main/java/tech/echo/app/core/data/`：

- `db/DailySummaryEntity.kt`：每日整理实体（date PK / diary / todos / inspirations / timeline / status / generatedAt）。含 `TimelineEntryData`（@Serializable，time/person/topic）和 `SummaryStatusDb` 枚举（PENDING/GENERATING/DONE/FAILED）。
- `db/Converters.kt`：JSON 列转换器（List<String> 和 List<TimelineEntryData> ⇄ TEXT），用 kotlinx-serialization。
- `db/DailySummaryDao.kt`：upsert / observeByDate(Flow) / getByDate / observeAllStatus(Flow) / updateStatus。含 `SummaryStatusRow(date,status)`。
- `db/SegmentDao.kt`：**已扩展**阶段 2 查询——findPendingUpload(限量取 RECORDED/FAILED) / updateStatus / markTranscribed(写回文本+说话人置 DONE) / getDoneByDate / getByDate。
- `db/EchoDatabase.kt`：version=2，加 DailySummaryEntity + @TypeConverters，含 `MIGRATION_1_2`（只新增 daily_summary 表，segment 表无改动）。
- `repository/DailySummaryRepository.kt`：薄封装 DailySummaryDao。
- `repository/SegmentRepository.kt`：**已扩展** findPendingUpload/updateStatus/markTranscribed/getDoneByDate/getByDate。
- `di/DataModule.kt`：注册 MIGRATION_1_2 + provideDailySummaryDao。

**注意 segment 表无需改结构**——阶段 1 已预留 transcriptText/speakerLabel/status 字段，阶段 2 直接复用。这是迁移只动一张表的原因。

## 4. 设置页（#4 已完成）

已写完（编译通过）：
- `core/settings/AppConfig.kt`：配置模型，含 isAsrConfigured/isLlmConfigured 判断 + 默认值常量。
- `core/settings/SettingsRepository.kt`：EncryptedSharedPreferences（AES256）加密存储，暴露 `config: Flow<AppConfig>`（用 callbackFlow 桥接 prefs 变更）+ `current()` + `save()`。
- `ui/settings/SettingsViewModel.kt`：HiltViewModel，form/saved 两个 StateFlow。
- `ui/settings/SettingsScreen.kt`：M3 表单，火山/DeepSeek 两组，key 字段密码遮罩，含"密钥仅本机加密保存"提示。

已完成接线：
1. `EchoRoutes.SETTINGS = "settings"`。
2. `EchoNavHost` 已注册 `SettingsScreen(onBack = popBackStack)`。
3. 今天页 TopAppBar 右侧增加 Settings 图标入口。

UI 变更已记入 journal：这是阶段 2 云配置的必要入口，属于对原今天页极简设计的受控扩展。

## 5. 已完成工作（#5 #6 #7）

### #5 DeepSeek 整理层
- `core/summary/LlmClient.kt`：接口 `suspend fun summarize(prompt): String`。
- `core/summary/DeepSeekLlmClient.kt`：OkHttp + kotlinx-serialization，OpenAI 兼容 JSON mode。
- `core/summary/SummaryPromptBuilder.kt` / `SummaryJsonParser.kt`：prompt 构造与 JSON 解析。
- `core/summary/SummaryGenerator.kt`：取当天 DONE segment → 调 LLM → 写 daily_summary，状态 GENERATING→DONE/FAILED。
- `core/summary/SummaryWorker.kt` / `SummaryWorkScheduler`：凌晨周期整理昨天 + 今天页手动整理。

### #6 ASR 上传层
- `core/upload/AsrClient.kt`：接口 `suspend fun transcribe(audioFile: File): List<AsrUtterance>`。
- `core/upload/VolcAsrClient.kt`：按官方 flash API 实现本地文件 base64 直传；解析 `utterances`，宽容读取 `speaker` / `speaker_label`。
- `core/upload/UploadProcessor.kt`：findPendingUpload(limit) → UPLOADING → ASR → markTranscribed；失败置 FAILED。
- `core/upload/UploadWorker.kt` / `UploadWorkScheduler`：联网约束 + 指数退避；录音落段后自动 enqueue。

### #7 UI 接真实数据
- `HistoryViewModel`：combine `SegmentRepository.observeDailyCounts()` + `DailySummaryRepository.observeAllStatus()`。
- `DetailViewModel`：combine `daily_summary.observeByDate(date)` + `segment.observeByDate(date)`。
- `TodayViewModel`：真实当天日期、summaryReady 状态、手动整理触发。
- `EchoNavHost`：今天页/详情页日期硬编码已移除；主 UI 不再引用 `FakeData`。

## 6. 依赖与构建

阶段 2 已加依赖（`gradle/libs.versions.toml` + `app/build.gradle.kts`，已验证解析+编译通过）：
- okhttp 4.12.0 + logging-interceptor
- kotlinx-serialization-json 1.7.3（+ serialization 插件）
- datastore-preferences 1.1.1
- security-crypto 1.1.0-alpha06（EncryptedSharedPreferences）
- work-runtime-ktx 2.10.0 + hilt-work 1.2.0 + hilt-compiler(androidx)
- mockwebserver 4.12.0（单测用）

构建命令（注意先 cd 到 app-android）：
```bash
cd /Users/heguangbao/dev/echo/app-android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin --console=plain
```

`EchoApplication` 已实现 `Configuration.Provider` 并注入 `HiltWorkerFactory`，@HiltWorker 可由 WorkManager 正常创建。

## 7. 给贰玖的待办（人工）

1. **真机验证**：阶段 2 云集成全程无法在本地验证（无 key、无真机），需贰玖拿真实火山/DeepSeek key 在 Android Studio 实测 API 是否跑通。
2. **火山账号口径确认**：旧控制台填 App ID + Access Key；新控制台可把 API Key 填在 App ID/API Key 字段并留空 Access Key。Resource ID 默认 `volc.bigasr.auc_turbo`。
   - 不要把豆包流式 1.0/2.0 的 `volc.*.sauc.*` 填进当前 App；那是 WebSocket 接口。
   - 不要把录音文件 2.0 标准版 `volc.seedasr.auc` 直接填进当前 App；它需要 submit/query 和音频 URL 链路。
   - 如果后续要上“最新模型”，优先评估新增录音文件 2.0 标准版客户端；只有做实时字幕/边说边显时再切流式 2.0。
3. 阶段 1 的真机装机验证（8 步清单）也还没做，见 `docs/tech/codex-handoff-core-audio.md`。

## 8. 本轮验证

```bash
cd /Users/heguangbao/dev/echo/app-android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:compileDebugKotlin --console=plain
./gradlew :app:assembleDebug --console=plain
```

截至 2026-05-30 12:16，以上命令均为 `BUILD SUCCESSFUL`。
