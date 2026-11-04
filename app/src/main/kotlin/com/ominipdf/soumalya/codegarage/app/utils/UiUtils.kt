package com.ominipdf.soumalya.codegarage.app.utils

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.ConfigurationCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ominipdf.soumalya.codegarage.app.R
import com.ominipdf.soumalya.codegarage.app.databinding.DialogInputBinding
import java.text.DateFormat
import java.util.Date

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.toast(@StringRes id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun View.haptic() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

fun Context.formatDate(ms: Long): String {
    val loc = ConfigurationCompat.getLocales(resources.configuration).get(0)
    val df = if (loc != null) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, loc)
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return df.format(Date(ms))
}

fun Context.showProgress(@StringRes msg: Int): AlertDialog {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(24), dp(20), dp(24), dp(20))
    row.addView(ProgressBar(this).apply { isIndeterminate = true })
    val tv = TextView(this)
    tv.text = getString(msg)
    tv.textSize = 16f
    tv.setPadding(dp(20), 0, 0, 0)
    row.addView(tv)
    return MaterialAlertDialogBuilder(this).setView(row).setCancelable(false).show()
}

fun Context.showInputDialog(@StringRes title: Int, @StringRes hint: Int, initial: String, onOk: (String) -> Unit) {
    val b = DialogInputBinding.inflate(LayoutInflater.from(this))
    b.inputLayout.hint = getString(hint)
    b.input.setText(initial)
    b.input.setSelection(initial.length)
    val dlg = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(b.root)
        .setPositiveButton(R.string.ok) { _, _ ->
            val t = b.input.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) onOk(t)
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dlg.show()
    b.input.requestFocus()
}
