package com.ominipdf.soumalya.codegarage.app.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ominipdf.soumalya.codegarage.app.R

enum class QuickTool(@StringRes val label: Int, @DrawableRes val icon: Int) {
    SMART_SCAN(R.string.tool_smart_scan, R.drawable.ic_scan),
    ID_CARD(R.string.tool_id_card, R.drawable.ic_id_card),
    IMPORT(R.string.tool_import, R.drawable.ic_import),
    EXTRACT_TEXT(R.string.tool_extract_text, R.drawable.ic_text),
    SHARE_PDF(R.string.tool_share_pdf, R.drawable.ic_share),
    SIGNATURE(R.string.tool_signature, R.drawable.ic_signature),
    EDIT_PDF(R.string.tool_edit_pdf, R.drawable.ic_edit),
    MORE(R.string.tool_more, R.drawable.ic_apps),
    WATERMARK(R.string.tool_watermark, R.drawable.ic_watermark),
    COMPRESS(R.string.tool_compress, R.drawable.ic_compress),
    MERGE(R.string.tool_merge, R.drawable.ic_merge),
    CONVERT(R.string.tool_convert, R.drawable.ic_convert);

    companion object {
        val interestOptions: List<QuickTool> get() = entries.filter { it != MORE }

        fun homeList(selected: List<String>): List<QuickTool> {
            val sel = selected.mapNotNull { n -> entries.firstOrNull { it.name == n } }.filter { it != MORE }
            val result = mutableListOf<QuickTool>()
            result.addAll(sel.take(7))
            val defaults = listOf(SMART_SCAN, ID_CARD, IMPORT, EXTRACT_TEXT, SHARE_PDF, SIGNATURE, EDIT_PDF)
            for (d in defaults) {
                if (result.size >= 7) break
                if (d !in result) result.add(d)
            }
            result.add(MORE)
            return result
        }
    }
}
