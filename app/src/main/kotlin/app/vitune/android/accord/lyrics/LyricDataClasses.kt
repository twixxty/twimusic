package app.vitune.android.accord.lyrics

open class LyricBase

data class Lyric(
    val content: String,
    val isMagicLyric: Boolean = false
) : LyricBase()

data class SyncedLyric(
    val list: List<Lyric>,
    val relativeTime: LongArray,
) : LyricBase() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SyncedLyric

        if (list != other.list) return false
        if (!relativeTime.contentEquals(other.relativeTime)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = list.hashCode()
        result = 31 * result + relativeTime.contentHashCode()
        return result
    }
}

data class Creator(
    val content: String
) : LyricBase()
