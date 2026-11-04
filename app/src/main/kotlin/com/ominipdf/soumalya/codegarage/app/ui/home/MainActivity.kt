package com.ominipdf.soumalya.codegarage.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.QuickTool
import com.ominipdf.soumalya.codegarage.app.data.ScanDoc
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.databinding.ActivityMainBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemFolderChipBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemQuickBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.ui.camera.CameraActivity
import com.ominipdf.soumalya.codegarage.app.ui.onboarding.OnboardingActivity
import com.ominipdf.soumalya.codegarage.app.utils.Exporter
import com.ominipdf.soumalya.codegarage.app.utils.Extras
import com.ominipdf.soumalya.codegarage.app.utils.LanguageManager
import com.ominipdf.soumalya.codegarage.app.utils.Prefs
import com.ominipdf.soumalya.codegarage.app.utils.SampleFactory
import com.ominipdf.soumalya.codegarage.app.utils.ScanFlow
import com.ominipdf.soumalya.codegarage.app.utils.ScanMode
import com.ominipdf.soumalya.codegarage.app.utils.haptic
import com.ominipdf.soumalya.codegarage.app.utils.showInputDialog
import com.ominipdf.soumalya.codegarage.app.utils.showProgress
import com.ominipdf.soumalya.codegarage.app.utils.toast

class MainActivity : BaseActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: DocAdapter
    private var folderFilter: String? = null   // null = all
    private var query = ""
    private var ready = false

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ready = true

        adapter = DocAdapter(
            onClick = { doc -> if (adapter.selectionMode) { adapter.toggle(doc); updateSelectionUi() } else ScanFlow.openDoc(this, doc.id) },
            onLongClick = { doc -> it_enterSelection(doc) },
            onShare = { doc -> shareDocs(listOf(doc)) },
            onMenu = { doc, anchor -> showDocMenu(doc, anchor) }
        )
        b.docList.layoutManager = LinearLayoutManager(this)
        b.docList.adapter = adapter

        buildQuickActions()
        b.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                refresh()
            }
        })
        b.btnSettings.setOnClickListener { LanguageManager.showPicker(this) }
        b.btnSort.setOnClickListener { showSortDialog() }
        b.btnNewFolder.setOnClickListener {
            showInputDialog(R.string.new_folder, R.string.folder_name_hint, "") { name ->
                val f = ScanRepository.addFolder(name)
                folderFilter = f.id
                refresh()
            }
        }
        b.fabCamera.setOnClickListener { it.haptic(); openCamera(ScanMode.DOCUMENT) }
        b.fabGallery.setOnClickListener { it.haptic(); pickImages.launch("image/*") }
        b.btnSelClose.setOnClickListener { adapter.clearSelection(); updateSelectionUi() }
        b.btnSelShare.setOnClickListener {
            val docs = adapter.selected.mapNotNull { ScanRepository.getDoc(it) }
            if (docs.isNotEmpty()) shareDocs(docs)
        }
        b.btnSelDelete.setOnClickListener { confirmDelete(adapter.selected.mapNotNull { ScanRepository.getDoc(it) }) }
        b.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_all_files) {
                toast(R.string.coming_soon)
                false
            } else true
        }

        if (!Prefs.sampleCreated) {
            Prefs.sampleCreated = true
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { SampleFactory.create(applicationContext) }
                refresh()
            }
        }
        if (Prefs.startWithCamera && savedInstanceState == null) openCamera(ScanMode.DOCUMENT)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.selectionMode) {
                    adapter.clearSelection()
                    updateSelectionUi()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (ready) refresh()
    }

    private fun it_enterSelection(doc: ScanDoc) {
        adapter.selectionMode = true
        adapter.toggle(doc)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val on = adapter.selectionMode
        if (on && adapter.selected.isEmpty()) {
            adapter.clearSelection()
            return updateSelectionUi()
        }
        b.normalHeader.visibility = if (on) View.GONE else View.VISIBLE
        b.selectionHeader.visibility = if (on) View.VISIBLE else View.GONE
        b.selCount.text = getString(R.string.selected_count, adapter.selected.size)
    }

    private fun openCamera(mode: ScanMode) {
        startActivity(Intent(this, CameraActivity::class.java).putExtra(Extras.MODE, mode.name))
    }

    // ---------- quick actions ----------
    private fun buildQuickActions() {
        val tools = QuickTool.homeList(Prefs.interests)
        b.quickRow1.removeAllViews()
        b.quickRow2.removeAllViews()
        tools.forEachIndexed { i, tool ->
            val qb = ItemQuickBinding.inflate(LayoutInflater.from(this), b.quickRow1, false)
            qb.quickIcon.setImageResource(tool.icon)
            qb.quickLabel.setText(tool.label)
            qb.root.setOnClickListener { it.haptic(); onQuickTool(tool) }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            (if (i < 4) b.quickRow1 else b.quickRow2).addView(qb.root, lp)
        }
    }

    private fun onQuickTool(tool: QuickTool) {
        when (tool) {
            QuickTool.SMART_SCAN -> openCamera(ScanMode.DOCUMENT)
            QuickTool.ID_CARD -> openCamera(ScanMode.ID_CARD)
            QuickTool.SHARE_PDF -> {
                toast(R.string.share_select_hint)
                adapter.selectionMode = true
                adapter.notifyDataSetChanged()
                b.normalHeader.visibility = View.GONE
                b.selectionHeader.visibility = View.VISIBLE
                b.selCount.text = getString(R.string.selected_count, adapter.selected.size)
            }
            else -> toast(R.string.coming_soon)
        }
    }

    // ---------- list ----------
    private fun refresh() {
        if (!ready) return
        val folders = ScanRepository.allFolders()
        if (folderFilter != null && folders.none { it.id == folderFilter }) folderFilter = null
        buildChips()
        var list = ScanRepository.allDocs().filter { folderFilter == null || it.folderId == folderFilter }
        if (query.isNotEmpty()) list = list.filter { it.name.contains(query, ignoreCase = true) }
        list = if (Prefs.sortByName) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.updatedAt }
        adapter.submit(list)
        b.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        b.emptyTitle.setText(if (query.isNotEmpty()) R.string.no_results else R.string.empty_title)
    }

    private fun buildChips() {
        b.folderChips.setOnCheckedStateChangeListener(null)
        b.folderChips.removeAllViews()
        fun addChip(label: String, folderId: String?) {
            val chip = ItemFolderChipBinding.inflate(LayoutInflater.from(this), b.folderChips, false).root as Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.tag = folderId ?: ""
            b.folderChips.addView(chip)
            if (folderId == folderFilter) chip.isChecked = true
        }
        addChip(getString(R.string.all_folders), null)
        ScanRepository.allFolders().forEach { addChip(it.name, it.id) }
        b.folderChips.setOnCheckedStateChangeListener { group, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val tag = group.findViewById<Chip>(id)?.tag as? String ?: ""
            val newFilter = if (tag.isEmpty()) null else tag
            if (newFilter != folderFilter) {
                folderFilter = newFilter
                refresh()
            }
        }
    }

    private fun showSortDialog() {
        val items = arrayOf(getString(R.string.sort_date), getString(R.string.sort_name))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(items, if (Prefs.sortByName) 1 else 0) { d, which ->
                Prefs.sortByName = which == 1
                d.dismiss()
                refresh()
            }
            .show()
    }

    // ---------- doc actions ----------
    private fun showDocMenu(doc: ScanDoc, anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, R.string.rename)
        pm.menu.add(0, 2, 1, R.string.duplicate)
        pm.menu.add(0, 3, 2, R.string.share)
        pm.menu.add(0, 4, 3, R.string.move_to_folder)
        pm.menu.add(0, 5, 4, R.string.delete)
        pm.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> showInputDialog(R.string.rename, R.string.doc_name_hint, doc.name) { n ->
                    doc.name = n
                    ScanRepository.touch(doc)
                    refresh()
                }
                2 -> {
                    ScanRepository.duplicate(doc.id, getString(R.string.copy_suffix))
                    refresh()
                }
                3 -> shareDocs(listOf(doc))
                4 -> showMoveDialog(doc)
                5 -> confirmDelete(listOf(doc))
            }
            true
        }
        pm.show()
    }

    private fun showMoveDialog(doc: ScanDoc) {
        val folders = ScanRepository.allFolders()
        val names = (listOf(getString(R.string.all_folders)) + folders.map { it.name }).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to_folder)
            .setItems(names) { _, which ->
                doc.folderId = if (which == 0) null else folders[which - 1].id
                ScanRepository.touch(doc)
                refresh()
            }
            .show()
    }

    private fun confirmDelete(docs: List<ScanDoc>) {
        if (docs.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                val removed = docs.mapNotNull { ScanRepository.removeRecord(it.id) }
                adapter.clearSelection()
                updateSelectionUi()
                refresh()
                var undone = false
                Snackbar.make(b.root, R.string.deleted_msg, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) {
                        undone = true
                        removed.forEach { ScanRepository.restoreRecord(it) }
                        refresh()
                    }
                    .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (!undone) removed.forEach { ScanRepository.purge(it) }
                        }
                    })
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareDocs(docs: List<ScanDoc>) {
        val dlg = showProgress(R.string.preparing_pdf)
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { docs.mapNotNull { Exporter.buildPdf(applicationContext, it) } }
            dlg.dismiss()
            if (files.isEmpty()) toast(R.string.error_generic)
            else Exporter.share(this@MainActivity, files, "application/pdf", getString(R.string.share_via))
        }
    }

    private fun importUris(uris: List<android.net.Uri>) {
        val dlg = showProgress(R.string.processing)
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { ScanFlow.addImages(applicationContext, null, uris, true) }
            dlg.dismiss()
            if (res == null) {
                toast(R.string.error_generic)
            } else if (res.second.size == 1) {
                ScanFlow.openCrop(this@MainActivity, res.first.id, res.second[0].id, false)
            } else {
                ScanFlow.openDoc(this@MainActivity, res.first.id)
            }
        }
    }
}
