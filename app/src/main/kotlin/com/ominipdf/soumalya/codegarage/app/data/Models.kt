package com.ominipdf.soumalya.codegarage.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

enum class FilterType(val id: Int) {
    ORIGINAL(0), AUTO(1), DOCUMENTS(2), BRIGHT(3), NO_SHADOW(4), COLOR(5), BW(6), SUPER(7), ENHANCE(8);

    companion object {
        fun fromId(id: Int): FilterType = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

class PageItem(val id: String) {
    var rotation: Int = 0
    var quad: FloatArray = fullQuad()
    var filter: FilterType = FilterType.AUTO
    var brightness: Int = 0
    var contrast: Int = 0
    var sharpness: Int = 0

    fun copyParams(): PageItem {
        val p = PageItem(id)
        p.applyFrom(this)
        return p
    }

    fun applyFrom(o: PageItem) {
        rotation = o.rotation
        quad = o.quad.copyOf()
        filter = o.filter
        brightness = o.brightness
        contrast = o.contrast
        sharpness = o.sharpness
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("rot", rotation)
        val a = JSONArray()
        quad.forEach { a.put(it.toDouble()) }
        o.put("quad", a)
        o.put("filter", filter.id)
        o.put("b", brightness)
        o.put("c", contrast)
        o.put("s", sharpness)
        return o
    }

    companion object {
        fun fullQuad() = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

        fun fromJson(o: JSONObject): PageItem {
            val p = PageItem(o.getString("id"))
            p.rotation = o.optInt("rot", 0)
            val a = o.optJSONArray("quad")
            if (a != null && a.length() == 8) p.quad = FloatArray(8) { a.getDouble(it).toFloat() }
            p.filter = FilterType.fromId(o.optInt("filter", 1))
            p.brightness = o.optInt("b", 0)
            p.contrast = o.optInt("c", 0)
            p.sharpness = o.optInt("s", 0)
            return p
        }
    }
}

class ScanDoc(val id: String, var name: String, var createdAt: Long, var updatedAt: Long) {
    var folderId: String? = null
    val pages: MutableList<PageItem> = CopyOnWriteArrayList()
    var pageSize: String = "A4"
    var orientation: Int = 0   // 0 auto, 1 portrait, 2 landscape
    var margin: Int = 1        // 0 none, 1 small, 2 normal

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("created", createdAt)
        o.put("updated", updatedAt)
        o.put("folder", folderId ?: "")
        o.put("size", pageSize)
        o.put("orient", orientation)
        o.put("margin", margin)
        val a = JSONArray()
        pages.forEach { a.put(it.toJson()) }
        o.put("pages", a)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): ScanDoc {
            val d = ScanDoc(o.getString("id"), o.optString("name", "Scan"), o.optLong("created"), o.optLong("updated"))
            val f = o.optString("folder", "")
            d.folderId = if (f.isEmpty()) null else f
            d.pageSize = o.optString("size", "A4")
            d.orientation = o.optInt("orient", 0)
            d.margin = o.optInt("margin", 1)
            val a = o.optJSONArray("pages")
            if (a != null) for (i in 0 until a.length()) d.pages.add(PageItem.fromJson(a.getJSONObject(i)))
            return d
        }
    }
}

class FolderItem(val id: String, var name: String)
