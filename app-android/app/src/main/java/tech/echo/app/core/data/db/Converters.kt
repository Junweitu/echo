package tech.echo.app.core.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room 类型转换器：把结构化列表 ⇄ JSON 字符串存入单列。
 *
 * daily_summary 的 todos/inspirations 是字符串列表，timeline 是结构化条目列表，
 * 都序列化成 JSON 文本存。阶段 2 数据量小，JSON 列足够；未来要按条目查询再拆表。
 */
class Converters {

    @TypeConverter
    fun stringListToJson(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }
            .getOrDefault(emptyList())

    @TypeConverter
    fun timelineToJson(list: List<TimelineEntryData>): String =
        json.encodeToString(ListSerializer(TimelineEntryData.serializer()), list)

    @TypeConverter
    fun jsonToTimeline(value: String): List<TimelineEntryData> =
        runCatching { json.decodeFromString(ListSerializer(TimelineEntryData.serializer()), value) }
            .getOrDefault(emptyList())

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
