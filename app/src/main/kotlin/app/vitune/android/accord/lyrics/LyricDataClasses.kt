package app.vitune.android.accord.lyrics

open class LyricBase

data class Lyric(
    val content: String,
    val isMagicLyric: Boolean = false,
    val isBackground: Boolean = false,
    val agent: String? = null
) : LyricBase()

data class SyncedLyric(
    val list: List<Lyric>,
    val relativeTime: LongArray,
    val isBackground: Boolean = false,
    val agent: String? = null
) : LyricBase() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SyncedLyric

        if (list != other.list) return false
        if (!relativeTime.contentEquals(other.relativeTime)) return false
        if (isBackground != other.isBackground) return false
        if (agent != other.agent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = list.hashCode()
        result = 31 * result + relativeTime.contentHashCode()
        result = 31 * result + isBackground.hashCode()
        result = 31 * result + (agent?.hashCode() ?: 0)
        return result
    }
}

data class Creator(
    val content: String
) : LyricBase()
