package com.ominipdf.soumalya.codegarage.app.ui.crop

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ominipdf.soumalya.codegarage.app.data.FilterType
import com.ominipdf.soumalya.codegarage.app.databinding.ItemFilterBinding
import com.ominipdf.soumalya.codegarage.app.utils.dp

class FilterAdapter(
    private val names: List<String>,
    private val onPick: (FilterType) -> Unit
) : RecyclerView.Adapter<FilterAdapter.VH>() {

    var thumbs: List<Bitmap?> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }
    var selected: FilterType = FilterType.AUTO
        set(v) { field = v; notifyDataSetChanged() }

    class VH(val b: ItemFilterBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = FilterType.entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = FilterType.entries[position]
        h.b.filterName.text = names[position]
        h.b.filterThumb.setImageBitmap(thumbs.getOrNull(position))
        h.b.filterCard.strokeWidth = if (f == selected) h.itemView.context.dp(3) else 0
        h.b.root.setOnClickListener {
            selected = f
            onPick(f)
        }
    }
}
