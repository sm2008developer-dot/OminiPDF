package com.ominipdf.soumalya.codegarage.app.utils

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ThumbLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    /** Loads [file] into [view] asynchronously. The view keeps its skeleton background until the image is ready. */
    fun load(view: ImageView, file: File, maxSide: Int, fade: Boolean = true) {
        val key = file.path + ":" + file.lastModified() + ":" + maxSide
        view.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        scope.launch {
            val decoded: Bitmap? = try {
                ImageUtils.decodeScaled(file, maxSide)
            } catch (e: OutOfMemoryError) {
                null
            }
            val bmp = decoded ?: return@launch
            cache.put(key, bmp)
            withContext(Dispatchers.Main) {
                if (view.tag == key) {
                    view.setImageBitmap(bmp)
                    if (fade) {
                        view.alpha = 0f
                        view.animate().alpha(1f).setDuration(180).start()
                    }
                }
            }
        }
    }
}
