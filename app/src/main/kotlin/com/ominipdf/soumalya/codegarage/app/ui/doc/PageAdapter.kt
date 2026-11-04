package com.ominipdf.soumalya.codegarage.app.ui.doc

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ominipdf.soumalya.codegarage.app.data.PageItem
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ItemPageBinding
import com.ominipdf.soumalya.codegarage.app.utils.ThumbLoader

class PageAdapter(private val docId: String) : RecyclerView.Adapter<PageAdapter.VH>() {
    private var pages: List<PageItem> = emptyList()

    class VH(val b: ItemPageBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<PageItem>) {
        pages = list
        notifyDataSetChanged()
    }

    fun pageAt(i: Int): PageItem? = pages.getOrNull(i)

    override fun getItemCount() = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        ThumbLoader.load(h.b.pageImage, ScanRepository.pageFile(docId, pages[position].id), 1400, false)
    }
}
