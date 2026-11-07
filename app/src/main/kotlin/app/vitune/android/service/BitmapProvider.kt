package app.vitune.android.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import app.vitune.android.utils.thumbnail
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

class BitmapProvider(
    private val getBitmapSize: () -> Int,
    private val getColor: (isDark: Boolean) -> Int,
    private val context: Context
) {
    @set:Synchronized
    var lastUri: Uri? = null
        private set

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<(Bitmap?) -> Unit>()

    @set:Synchronized
    private var lastBitmap: Bitmap? = null
        get() = field?.takeUnless { it.isRecycled }
        set(value) {
            field = value
            listeners.forEach { it.invoke(value) }
        }
    private var lastIsSystemInDarkMode = false
    private var currentTask: Disposable? = null

    private lateinit var defaultBitmap: Bitmap
    val bitmap get() = lastBitmap ?: defaultBitmap

    init {
        setDefaultBitmap()
    }

    fun setDefaultBitmap(): Boolean {
        val isSystemInDarkMode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        var oldBitmap: Bitmap? = null
        if (::defaultBitmap.isInitialized) {
            if (isSystemInDarkMode == lastIsSystemInDarkMode) return false
            oldBitmap = defaultBitmap
        }

        lastIsSystemInDarkMode = isSystemInDarkMode

        val size = getBitmapSize()
        defaultBitmap = createBitmap(size, size).applyCanvas {
            drawColor(getColor(isSystemInDarkMode))
        }
        oldBitmap?.recycle()

        return lastBitmap == null
    }

    fun load(
        uri: Uri?,
        onDone: (Bitmap) -> Unit = { }
    ) {
        if (lastUri == uri) {
            listeners.forEach { it.invoke(lastBitmap) }
            return
        }

        lastUri = uri

        if (uri == null) {
            lastBitmap = null
            onDone(bitmap)
            return
        }

        val oldTask = currentTask
        currentTask = context.applicationContext.imageLoader.enqueue(
            ImageRequest.Builder(context.applicationContext)
                .data(uri.thumbnail(getBitmapSize()))
                .allowHardware(false)
                .listener(
                    onError = { _, _ ->
                        lastBitmap = null
                        onDone(bitmap)
                    },
                    onSuccess = { _, result ->
                        lastBitmap = result.image.run { toBitmap(width, height) }
                        onDone(bitmap)
                    }
                )
                .build()
        )
        oldTask?.dispose()
    }

    fun addListener(callback: (Bitmap?) -> Unit) {
        listeners.add(callback)
        callback.invoke(lastBitmap)
    }

    fun removeListener(callback: (Bitmap?) -> Unit) {
        listeners.remove(callback)
    }

    fun setListener(callback: ((Bitmap?) -> Unit)?) {
        if (callback != null) {
            addListener(callback)
        }
    }
}
