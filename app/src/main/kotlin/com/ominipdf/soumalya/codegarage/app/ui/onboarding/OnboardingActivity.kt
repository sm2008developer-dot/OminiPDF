package com.ominipdf.soumalya.codegarage.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.data.QuickTool
import com.ominipdf.soumalya.codegarage.app.databinding.ActivityOnboardingBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemInterestBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemLanguageBinding
import com.ominipdf.soumalya.codegarage.app.databinding.ItemSlideBinding
import com.ominipdf.soumalya.codegarage.app.ui.BaseActivity
import com.ominipdf.soumalya.codegarage.app.ui.home.MainActivity
import com.ominipdf.soumalya.codegarage.app.utils.LanguageManager
import com.ominipdf.soumalya.codegarage.app.utils.Prefs
import com.ominipdf.soumalya.codegarage.app.utils.dp
import com.ominipdf.soumalya.codegarage.app.utils.haptic

class OnboardingActivity : BaseActivity() {
    private lateinit var b: ActivityOnboardingBinding
    private var stage = 0
    private val selected = LinkedHashSet<String>()

    private class Slide(val icon: Int, val title: Int, val desc: Int)

    private val slides = listOf(
        Slide(R.drawable.ic_scan, R.string.slide1_title, R.string.slide1_desc),
        Slide(R.drawable.ic_folder, R.string.slide2_title, R.string.slide2_desc),
        Slide(R.drawable.ic_pdf, R.string.slide3_title, R.string.slide3_desc)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)
        stage = savedInstanceState?.getInt("stage") ?: 0
        savedInstanceState?.getStringArrayList("selected")?.let { selected.addAll(it) }

        setupLanguages()
        setupSlides()
        setupInterests()
        showStage(stage)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("stage", stage)
        outState.putStringArrayList("selected", ArrayList(selected))
    }

    private fun showStage(s: Int) {
        stage = s
        b.stageLanguage.visibility = if (s == 0) View.VISIBLE else View.GONE
        b.stageSlides.visibility = if (s == 1) View.VISIBLE else View.GONE
        b.stageInterests.visibility = if (s == 2) View.VISIBLE else View.GONE
    }

    // ---- language ----
    private fun setupLanguages() {
        b.languageList.layoutManager = LinearLayoutManager(this)
        b.languageList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = LanguageManager.all.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val ib = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return object : RecyclerView.ViewHolder(ib.root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ib = ItemLanguageBinding.bind(holder.itemView)
                val lang = LanguageManager.all[position]
                val isSel = lang.tag == LanguageManager.current()
                ib.langName.text = lang.nativeName
                ib.langCheck.visibility = if (isSel) View.VISIBLE else View.INVISIBLE
                ib.card.strokeWidth = if (isSel) dp(2) else 0
                ib.card.strokeColor = getColor(R.color.pink_primary)
                ib.card.setOnClickListener {
                    it.haptic()
                    if (!isSel) LanguageManager.apply(lang.tag) // activity is recreated with the new locale
                }
            }
        }
        b.btnLanguageContinue.setOnClickListener { showStage(1) }
    }

    // ---- slides ----
    private fun setupSlides() {
        b.slidesPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = slides.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val sb = ItemSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return object : RecyclerView.ViewHolder(sb.root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val sb = ItemSlideBinding.bind(holder.itemView)
                val s = slides[position]
                sb.slideIcon.setImageResource(s.icon)
                sb.slideTitle.setText(s.title)
                sb.slideDesc.setText(s.desc)
            }
        }
        buildDots(0)
        b.slidesPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                buildDots(position)
                b.btnNext.setText(if (position == slides.size - 1) R.string.get_started else R.string.next_label)
            }
        })
        b.btnNext.setOnClickListener {
            val p = b.slidesPager.currentItem
            if (p < slides.size - 1) b.slidesPager.currentItem = p + 1 else showStage(2)
        }
        b.btnSkip.setOnClickListener { showStage(2) }
    }

    private fun buildDots(active: Int) {
        b.dots.removeAllViews()
        for (i in slides.indices) {
            val v = View(this)
            v.setBackgroundResource(if (i == active) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
            val lp = ViewGroup.MarginLayoutParams(if (i == active) dp(22) else dp(8), dp(8))
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            b.dots.addView(v, lp)
        }
    }

    // ---- interests ----
    private fun setupInterests() {
        val options = QuickTool.interestOptions
        b.interestList.layoutManager = GridLayoutManager(this, 3)
        b.interestList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = options.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val ib = ItemInterestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return object : RecyclerView.ViewHolder(ib.root) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ib = ItemInterestBinding.bind(holder.itemView)
                val tool = options[position]
                ib.interestIcon.setImageResource(tool.icon)
                ib.interestLabel.setText(tool.label)
                fun paint() {
                    val on = selected.contains(tool.name)
                    ib.card.strokeWidth = if (on) dp(2) else 0
                    ib.card.strokeColor = getColor(R.color.pink_primary)
                    ib.card.setCardBackgroundColor(getColor(if (on) R.color.chip_bg else R.color.card_bg))
                }
                paint()
                ib.card.setOnClickListener {
                    it.haptic()
                    if (!selected.remove(tool.name)) selected.add(tool.name)
                    paint()
                }
            }
        }
        b.btnStart.setOnClickListener {
            Prefs.interests = options.map { it.name }.filter { selected.contains(it) }
            Prefs.onboardingDone = true
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }
}
