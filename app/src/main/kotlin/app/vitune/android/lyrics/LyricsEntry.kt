/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package app.vitune.android.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.coroutines.flow.MutableStateFlow

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val hasTrailingSpace: Boolean = true
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val agent: String? = null,
    val isBackground: Boolean = false
) : Comparable<LyricsEntry> {
    val translation: String?
        get() = translatedTextFlow.value

    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}

fun List<LyricsEntry>.toSyncedLyrics(title: String = ""): SyncedLyrics {
    if (isEmpty()) return SyncedLyrics(emptyList(), title = title)

    val lines = mutableListOf<ISyncedLine>()
    val n = size
    for (i in 0 until n) {
        val entry = this[i]
        val startMs = entry.time.toInt()
        val nextStartMs = if (i + 1 < n) this[i + 1].time.toInt() else startMs + 6000
        val endMs = if (entry.words.isNullOrEmpty()) {
            maxOf(startMs + 1000, minOf(nextStartMs, startMs + 8000))
        } else {
            val lastWordEnd = (entry.words.last().endTime * 1000).toInt()
            maxOf(startMs + 500, maxOf(lastWordEnd, minOf(nextStartMs, lastWordEnd + 2000)))
        }

        val trans = entry.translatedTextFlow.value?.takeIf { it.isNotBlank() }
        val phonetic = entry.romanizedTextFlow.value?.takeIf { it.isNotBlank() }

        if (entry.words.isNullOrEmpty()) {
            lines.add(
                SyncedLine(
                    content = entry.text,
                    translation = trans,
                    start = startMs,
                    end = endMs,
                    phonetic = phonetic,
                )
            )
        } else {
            val syllables = entry.words.map { word ->
                KaraokeSyllable(
                    content = word.text + if (word.hasTrailingSpace) " " else "",
                    start = (word.startTime * 1000).toInt(),
                    end = (word.endTime * 1000).toInt(),
                )
            }
            if (entry.isBackground) {
                lines.add(
                    KaraokeLine.AccompanimentKaraokeLine(
                        syllables = syllables,
                        translation = trans,
                        alignment = KaraokeAlignment.End,
                        start = startMs,
                        end = endMs,
                        phonetic = phonetic,
                    )
                )
            } else {
                lines.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables,
                        translation = trans,
                        alignment = KaraokeAlignment.Start,
                        start = startMs,
                        end = endMs,
                        phonetic = phonetic,
                    )
                )
            }
        }
    }
    return SyncedLyrics(lines = lines, title = title)
}
