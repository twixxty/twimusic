package app.vitune.core.data.utils

inline val String.version
    get() = Version(
        trimStart('v', 'V')
            .substringBefore("-")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    )

@JvmInline
value class Version(val parts: List<Int>) : Comparable<Version> {
    val major get() = parts.firstOrNull()
    val minor get() = parts.getOrNull(1)
    val patch get() = parts.getOrNull(2)

    override operator fun compareTo(other: Version): Int {
        val maxLen = maxOf(this.parts.size, other.parts.size)
        for (i in 0 until maxLen) {
            val partThis = this.parts.getOrElse(i) { 0 }
            val partOther = other.parts.getOrElse(i) { 0 }
            if (partThis != partOther) {
                return partThis.compareTo(partOther)
            }
        }
        return 0
    }
}
