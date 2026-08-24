package tech.echo.app.core.text

import android.icu.text.Transliterator

/**
 * 將任何簡體中文內容統一轉成繁體中文顯示／儲存。
 * Android API 24+ 已內建 ICU Transliterator；Echo minSdk=26 可直接使用。
 */
object TraditionalChinese {
    private val converter: Transliterator by lazy {
        runCatching { Transliterator.getInstance("Simplified-Traditional") }
            .getOrElse { Transliterator.getInstance("Hans-Hant") }
    }

    fun convert(text: String): String {
        if (text.isBlank()) return text
        return synchronized(converter) {
            runCatching { converter.transliterate(text) }.getOrDefault(text)
        }
    }

    fun convert(items: List<String>): List<String> = items.map(::convert)
}
