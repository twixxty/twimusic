package com.mocharealm.accompanist.lyrics.ui.internal.text

internal fun graphemeBoundaries(text: String): BooleanArray {
    val result = BooleanArray(text.length + 1)
    if (android.os.Build.VERSION.SDK_INT >= 24) {
        val iterator = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
        iterator.setText(text)
        var boundary = iterator.first()
        while (boundary != android.icu.text.BreakIterator.DONE) {
            result[boundary] = true
            boundary = iterator.next()
        }
    } else {
        result[0] = true
        result[text.length] = true
    }
    return result
}
