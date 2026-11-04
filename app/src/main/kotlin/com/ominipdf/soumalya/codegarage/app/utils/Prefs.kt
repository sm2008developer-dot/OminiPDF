package com.ominipdf.soumalya.codegarage.app.utils

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("omini_prefs", Context.MODE_PRIVATE)
    }

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).apply()

    /** Ordered list of QuickTool names chosen during onboarding. */
    var interests: List<String>
        get() = (sp.getString("interests", "") ?: "").split(",").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("interests", v.joinToString(",")).apply()

    var sortByName: Boolean
        get() = sp.getBoolean("sort_by_name", false)
        set(v) = sp.edit().putBoolean("sort_by_name", v).apply()

    var sampleCreated: Boolean
        get() = sp.getBoolean("sample_created", false)
        set(v) = sp.edit().putBoolean("sample_created", v).apply()

    var ratingShown: Boolean
        get() = sp.getBoolean("rating_shown", false)
        set(v) = sp.edit().putBoolean("rating_shown", v).apply()

    var autoCapture: Boolean
        get() = sp.getBoolean("auto_capture", false)
        set(v) = sp.edit().putBoolean("auto_capture", v).apply()

    var startWithCamera: Boolean
        get() = sp.getBoolean("start_with_camera", false)
        set(v) = sp.edit().putBoolean("start_with_camera", v).apply()

    var defaultFilter: Int
        get() = sp.getInt("default_filter", 1)
        set(v) = sp.edit().putInt("default_filter", v).apply()

    var jpegQuality: Int
        get() = sp.getInt("jpeg_quality", 90)
        set(v) = sp.edit().putInt("jpeg_quality", v).apply()
}
