package com.ominipdf.soumalya.codegarage.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ScanRepository {
    private lateinit var root: File
    private lateinit var indexFile: File
    private val docs = ArrayList<ScanDoc>()
    private val folders = ArrayList<FolderItem>()

    @Synchronized
    fun init(ctx: Context) {
        if (::root.isInitialized) return
        root = File(ctx.filesDir, "docs").apply { mkdirs() }
        indexFile = File(ctx.filesDir, "index.json")
        load()
        root.listFiles()?.forEach { f -> if (docs.none { it.id == f.name }) f.deleteRecursively() }
    }

    private fun load() {
        try {
            if (!indexFile.exists()) return
            val o = JSONObject(indexFile.readText())
            val fa = o.optJSONArray("folders")
            if (fa != null) for (i in 0 until fa.length()) {
                val f = fa.getJSONObject(i)
                folders.add(FolderItem(f.getString("id"), f.getString("name")))
            }
            val da = o.optJSONArray("docs")
            if (da != null) for (i in 0 until da.length()) docs.add(ScanDoc.fromJson(da.getJSONObject(i)))
        } catch (e: Exception) {
            // corrupted index: start clean
        }
    }

    private fun persist() {
        try {
            val o = JSONObject()
            val fa = JSONArray()
            folders.forEach { fa.put(JSONObject().put("id", it.id).put("name", it.name)) }
            o.put("folders", fa)
            val da = JSONArray()
            docs.forEach { da.put(it.toJson()) }
            o.put("docs", da)
            val tmp = File(indexFile.parentFile, "index.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(indexFile)) {
                indexFile.delete()
                tmp.renameTo(indexFile)
            }
        } catch (e: Exception) {
            // ignore write failure
        }
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun docDir(docId: String) = File(root, docId)
    fun origFile(docId: String, pageId: String) = File(docDir(docId), "o_$pageId.jpg")
    fun pageFile(docId: String, pageId: String) = File(docDir(docId), "p_$pageId.jpg")

    @Synchronized fun allDocs(): List<ScanDoc> = docs.toList()
    @Synchronized fun getDoc(id: String?): ScanDoc? = if (id == null) null else docs.firstOrNull { it.id == id }
    @Synchronized fun allFolders(): List<FolderItem> = folders.toList()

    @Synchronized
    fun addFolder(name: String): FolderItem {
        val f = FolderItem(newId(), name)
        folders.add(f)
        persist()
        return f
    }

    @Synchronized
    fun createDoc(name: String): ScanDoc {
        val now = System.currentTimeMillis()
        val d = ScanDoc(newId(), name, now, now)
        docDir(d.id).mkdirs()
        docs.add(d)
        persist()
        return d
    }

    @Synchronized
    fun touch(doc: ScanDoc) {
        doc.updatedAt = System.currentTimeMillis()
        persist()
    }

    @Synchronized
    fun removeRecord(docId: String): ScanDoc? {
        val d = docs.firstOrNull { it.id == docId } ?: return null
        docs.remove(d)
        persist()
        return d
    }

    @Synchronized
    fun restoreRecord(doc: ScanDoc) {
        if (docs.none { it.id == doc.id }) docs.add(doc)
        persist()
    }

    fun purge(doc: ScanDoc) {
        if (getDoc(doc.id) == null) docDir(doc.id).deleteRecursively()
    }

    @Synchronized
    fun deleteDocNow(docId: String) {
        val d = removeRecord(docId) ?: return
        docDir(d.id).deleteRecursively()
    }

    @Synchronized
    fun duplicate(docId: String, suffix: String): ScanDoc? {
        val src = docs.firstOrNull { it.id == docId } ?: return null
        val now = System.currentTimeMillis()
        val copy = ScanDoc(newId(), src.name + " (" + suffix + ")", now, now)
        copy.folderId = src.folderId
        copy.pageSize = src.pageSize
        copy.orientation = src.orientation
        copy.margin = src.margin
        try {
            docDir(src.id).copyRecursively(docDir(copy.id), true)
        } catch (e: Exception) {
            return null
        }
        src.pages.forEach { copy.pages.add(it.copyParams()) }
        docs.add(copy)
        persist()
        return copy
    }

    @Synchronized
    fun addPage(docId: String, page: PageItem) {
        val d = docs.firstOrNull { it.id == docId } ?: return
        d.pages.add(page)
        d.updatedAt = System.currentTimeMillis()
        persist()
    }

    @Synchronized
    fun removePage(docId: String, pageId: String) {
        val d = docs.firstOrNull { it.id == docId } ?: return
        val p = d.pages.firstOrNull { it.id == pageId } ?: return
        d.pages.remove(p)
        origFile(docId, pageId).delete()
        pageFile(docId, pageId).delete()
        if (d.pages.isEmpty()) {
            docs.remove(d)
            docDir(docId).deleteRecursively()
        } else {
            d.updatedAt = System.currentTimeMillis()
        }
        persist()
    }

    @Synchronized
    fun setPageOrder(docId: String, order: List<PageItem>) {
        val d = docs.firstOrNull { it.id == docId } ?: return
        d.pages.clear()
        d.pages.addAll(order)
        d.updatedAt = System.currentTimeMillis()
        persist()
    }
}
