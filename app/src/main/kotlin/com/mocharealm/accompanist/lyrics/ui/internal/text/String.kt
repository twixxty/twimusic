package com.mocharealm.accompanist.lyrics.ui.internal.text

import android.os.Build

internal fun platformCodePointDirectionality(codePoint: Int): Int =
    when (Character.getDirectionality(codePoint)) {
        Character.DIRECTIONALITY_LEFT_TO_RIGHT -> 0
        Character.DIRECTIONALITY_RIGHT_TO_LEFT -> 1
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> 13
        else -> -1
    }

private val cjkBlocks: Set<Character.UnicodeBlock> by lazy {
    mutableSetOf(
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                add(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E)
                add(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F)
                add(Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G)
            } catch (_: Throwable) {}
        }
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val field = Character.UnicodeBlock::class.java.getField("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_H")
                add(field.get(null) as Character.UnicodeBlock)
            } catch (_: Throwable) {}
        }
    }
}

private val arabicBlocks: Set<Character.UnicodeBlock> by lazy {
    mutableSetOf(
        Character.UnicodeBlock.ARABIC,
        Character.UnicodeBlock.ARABIC_SUPPLEMENT,
        Character.UnicodeBlock.ARABIC_EXTENDED_A,
        Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
        Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
    ).apply {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val field = Character.UnicodeBlock::class.java.getField("ARABIC_EXTENDED_B")
                add(field.get(null) as Character.UnicodeBlock)
            } catch (_: Throwable) {}
        }
    }
}

private val devanagariBlocks: Set<Character.UnicodeBlock> by lazy {
    setOf(Character.UnicodeBlock.DEVANAGARI, Character.UnicodeBlock.DEVANAGARI_EXTENDED)
}

internal fun Char.isCjk(): Boolean {
    return try {
        Character.UnicodeBlock.of(this) in cjkBlocks
    } catch (_: Exception) {
        false
    }
}

internal fun Char.isJapanese(): Boolean {
    return this.code in 0x3040..0x309F || this.code in 0x30A0..0x30FF || this.code in 0xFF66..0xFF9F
}

internal fun Char.isKorean(): Boolean {
    return this.code in 0xAC00..0xD7AF || this.code in 0x1100..0x11FF
}

internal fun Char.isArabic(): Boolean {
    return try {
        Character.UnicodeBlock.of(this) in arabicBlocks
    } catch (_: Exception) {
        false
    }
}

internal fun Char.isDevanagari(): Boolean {
    return try {
        Character.UnicodeBlock.of(this) in devanagariBlocks
    } catch (_: Exception) {
        false
    }
}

internal fun String.isPureCjk(): Boolean {
    val cleanedStr = this.filter { it != ' ' && it != ',' && it != '\n' && it != '\r' }
    if (cleanedStr.isEmpty()) {
        return false
    }
    return cleanedStr.all { it.isCjk() }
}

internal fun String.containsJapanese(): Boolean = any { it.isJapanese() }

internal fun String.containsKorean(): Boolean = any { it.isKorean() }

internal fun String.isRtl(fallback: Boolean = false): Boolean {
    var index = 0
    while (index < length) {
        val value = this[index++]
        val codePoint =
            if (value.isHighSurrogate() && index < length && this[index].isLowSurrogate())
                0x10000 + ((value.code - 0xD800) shl 10) + this[index++].code - 0xDC00
            else value.code
        when (platformCodePointDirectionality(codePoint)) {
            0 -> return false
            1, 13 -> return true
        }
    }
    return fallback
}

internal fun Char.isProfilePunctuation(): Boolean =
    when (category) {
        CharCategory.CONNECTOR_PUNCTUATION,
        CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION,
        CharCategory.END_PUNCTUATION,
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION -> true
        else -> this == '～'
    }

internal fun String.isPunctuation(): Boolean =
    isNotEmpty() && all { it.isWhitespace() || it.isProfilePunctuation() }
