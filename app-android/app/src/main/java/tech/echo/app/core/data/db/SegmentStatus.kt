package tech.echo.app.core.data.db

/**
 * 片段处理状态（见 design.md §5.2）。
 * 阶段 1 只产生 RECORDED；后续阶段推进到上传/转写/完成。
 */
enum class SegmentStatus {
    RECORDED,      // 已落盘（阶段 1 终态）
    UPLOADING,     // 上传中（阶段 2）
    TRANSCRIBING,  // 转写中（阶段 2）
    DONE,          // 转写完成（阶段 2）
    FAILED,        // 失败（阶段 2，后台静默重试）
}
