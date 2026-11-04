package com.ominipdf.soumalya.codegarage.app.utils

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ominipdf.soumalya.codegarage.app.R

object LanguageManager {
    class Lang(val tag: String, val nativeName: String)

    val all = listOf(
        Lang("bn", "বাংলা"), Lang("en", "English"), Lang("hi", "हिंदी"), Lang("ar", "العربية"),
        Lang("es", "Español"), Lang("fr", "Français"), Lang("pt", "Português"), Lang("ru", "Русский"),
        Lang("de", "Deutsch"), Lang("it", "Italiano"), Lang("tr", "Türkçe")
    )

    fun current(): String = Prefs.language.ifEmpty { "bn" }

    fun apply(tag: String) {
        Prefs.language = tag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun showPicker(activity: Activity) {
        val idx = all.indexOfFirst { it.tag == current() }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(all.map { it.nativeName }.toTypedArray(), idx) { d, which ->
                d.dismiss()
                if (all[which].tag != current()) apply(all[which].tag)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
