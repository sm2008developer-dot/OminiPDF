package com.ominipdf.soumalya.codegarage.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ominipdf.soumalya.codegarage.app.data.ScanRepository
import com.ominipdf.soumalya.codegarage.app.utils.Exporter
import com.ominipdf.soumalya.codegarage.app.utils.Prefs

class OminiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ScanRepository.init(this)
        Exporter.clearExports(this)
        if (Prefs.language.isEmpty()) {
            Prefs.language = "bn"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("bn"))
        }
    }
}
