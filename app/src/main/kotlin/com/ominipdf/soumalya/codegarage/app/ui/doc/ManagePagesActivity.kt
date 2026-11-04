package com.ominipdf.soumalya.codegarage.app.ui.doc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.PageItem
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ActivityManagePagesBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemManageBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.utils.Extras
import com.ominipdf.soumalya.codegarage.app.utils.PageRenderer
import com.ominipdf.soumalya.codegarage.app.utils.QuadUtils
import com.ominipdf.soumalya.codegarage.app.utils.ScanFlow
import com.ominipdf.soumalya.codegarage.app.utils.ThumbLoader
import com.ominipdf.soumalya.codegarage.app.utils.haptic
import java.util.Collections

class ManagePagesActivity : BaseActivity() {
    private lateinit var b: ActivityManagePagesBinding
    private var docId = ""
    private val items = ArrayList<PageItem>()
    private lateinit var adapter: RecyclerView.Adapter<ManageVH>

    class ManageVH(val b: ItemManageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManagePagesBinding.inflate(layoutInflater)
        setContentView(b.root)
        docId = intent.getStringExtra(Extras.DOC_ID) ?: return finish()
        b.btnBack.setOnClickListener { finish() }

        adapter = object : RecyclerView.Adapter<ManageVH>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                ManageVH(ItemManageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            override fun onBindViewHolder(h: ManageVH, position: Int) {
                val page = items[position]
                h.b.pageNumber.text = (position + 1).toString()
                ThumbLoader.load(h.b.pageThumb, ScanRepository.pageFile(docId, page.id), 360)
                h.b.btnRotate.setOnClickListener { it.haptic(); rotatePage(page) }
                h.b.btnRecrop.setOnClickListener { ScanFlow.openCrop(this@ManagePagesActivity, docId, page.id, true) }
                h.b.btnDelete.setOnClickListener { confirmDelete(page) }
            }
        }
        b.grid.layoutManager = GridLayoutManager(this, 2)
        b.grid.adapter = adapter

        val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                ScanRepository.setPageOrder(docId, items.toList())
                adapter.notifyDataSetChanged()
            }
        })
        touch.attachToRecyclerView(b.grid)
    }

    override fun onResume() {
        super.onResume()
        val doc = ScanRepository.getDoc(docId)
        if (doc == null || doc.pages.isEmpty()) {
            finish()
            return
        }
        items.clear()
        items.addAll(doc.pages)
        adapter.notifyDataSetChanged()
    }

    private fun rotatePage(page: PageItem) {
        page.rotation = (page.rotation + 90) % 360
        page.quad = QuadUtils.rotate(page.quad, true)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { PageRenderer.render(docId, page) }
            ScanRepository.getDoc(docId)?.let { ScanRepository.touch(it) }
            adapter.notifyDataSetChanged()
        }
    }

    private fun confirmDelete(page: PageItem) {
        val last = items.size == 1
        val builder = MaterialAlertDialogBuilder(this).setTitle(R.string.delete_page_title)
        if (last) builder.setMessage(R.string.delete_last_page_msg)
        builder.setPositiveButton(R.string.delete) { _, _ ->
            ScanRepository.removePage(docId, page.id)
            if (last) {
                finish()
            } else {
                items.remove(page)
                adapter.notifyDataSetChanged()
            }
        }.setNegativeButton(R.string.cancel, null).show()
    }
}
