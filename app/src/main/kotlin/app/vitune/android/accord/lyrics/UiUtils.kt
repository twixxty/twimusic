package app.vitune.android.accord.lyrics

import android.content.Context
import android.util.TypedValue

object UiUtils {
    var density: Float = 3f
        private set
    var scaledDensity: Float = 3f
        private set

    data class ScreenCorners(
        val topLeft: Float,
        val topRight: Float,
        val bottomLeft: Float,
        val bottomRight: Float
    ) {
        fun getAvgRadius() =
            (topLeft + topRight + bottomLeft + bottomRight) / 4f
    }

    fun init(context: Context) {
        val resources = context.resources
        density = resources.displayMetrics.density
        scaledDensity = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            1f,
            resources.displayMetrics
        )
    }
}

@JvmInline
value class Dp(val value: Float) {
    inline val px: Float
        get() = value * UiUtils.density

    companion object {
        val Zero = Dp(0f)
    }
}

inline val Int.dp: Dp
    get() = Dp(this.toFloat())

inline val Float.dp: Dp
    get() = Dp(this)

inline val Double.dp: Dp
    get() = Dp(this.toFloat())

@JvmInline
value class Sp(val value: Float) {
    inline val px: Float
        get() = value * UiUtils.scaledDensity

    companion object {
        val Zero = Sp(0f)
    }
}

inline val Int.sp: Sp
    get() = Sp(this.toFloat())

inline val Float.sp: Sp
    get() = Sp(this)

inline val Double.sp: Sp
    get() = Sp(this.toFloat())
