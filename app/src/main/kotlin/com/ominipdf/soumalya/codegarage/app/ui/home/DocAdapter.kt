package com.ominipdf.soumalya.codegarage.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.ScanDoc
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ItemDocBinding
import com.ominipdf.soumalya.codegarage.app.utils.ThumbLoader
import com.ominipdf.soumalya.codegarage.app.utils.dp
import com.ominipdf.soumalya.codegarage.app.utils.formatDate

class DocAdapter(
    private val onClick: (ScanDoc) -> Unit,
    private val onLongClick: (ScanDoc) -> Unit,
    private val onShare: (ScanDoc) -> Unit,
    private val onMenu: (ScanDoc, View) -> Unit
) : RecyclerView.Adapter<DocAdapter.VH>() {

    private var items: List<ScanDoc> = emptyList()
    val selected = LinkedHashSet<String>()
    var selectionMode = false

    class VH(val b: ItemDocBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<ScanDoc>) {
        items = list
        selected.retainAll(list.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    fun toggle(doc: ScanDoc) {
        if (!selected.remove(doc.id)) selected.add(doc.id)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        selectionMode = false
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDocBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val doc = items[position]
        val ctx = h.itemView.context
        h.b.docName.text = doc.name
        h.b.docMeta.text = ctx.getString(R.string.pages_count, doc.pages.size) + "\n" + ctx.formatDate(doc.updatedAt)
        val first = doc.pages.firstOrNull()
        if (first != null) ThumbLoader.load(h.b.thumb, ScanRepository.pageFile(doc.id, first.id), 240)
        else h.b.thumb.setImageDrawable(null)
        val sel = selected.contains(doc.id)
        h.b.selCheck.visibility = if (sel) View.VISIBLE else View.GONE
        h.b.card.strokeWidth = if (sel) ctx.dp(2) else 0
        h.b.card.strokeColor = ctx.getColor(R.color.pink_primary)
        h.b.btnShare.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE
        h.b.btnMenu.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE
        h.b.card.setOnClickListener { onClick(doc) }
        h.b.card.setOnLongClickListener { onLongClick(doc); true }
        h.b.btnShare.setOnClickListener { onShare(doc) }
        h.b.btnMenu.setOnClickListener { onMenu(doc, it) }
    }
}
