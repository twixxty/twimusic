package app.vitune.android.accord.lyrics

import androidx.annotation.ColorInt

@ColorInt
fun Int.applyAlpha(alpha: Int): Int {
    val a = alpha.coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (a shl 24)
}

fun triangle(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return 1f - 2f * kotlin.math.abs(t - 0.5f)
}
