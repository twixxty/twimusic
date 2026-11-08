package app.vitune.android.canvas

import java.text.Normalizer
import java.util.Locale

fun String.normalizeForComparison(): String {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    val withoutDiacritics = Regex("\\p{InCombiningDiacriticalMarks}+").replace(decomposed, "")
    return withoutDiacritics.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun splitAndNormalizeArtists(raw: String): List<String> {
    return raw.split(
        Regex(
            "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
            RegexOption.IGNORE_CASE,
        )
    ).map { it.normalizeForComparison() }
        .filter { it.isNotBlank() }
}

fun validateMotionartworkMatch(
    artwork: Motionartwork,
    requestedTitle: String,
    requestedArtist: String,
    requestedAlbum: String
): Boolean {
    val artist = artwork.artist
    val name = artwork.name
    val albumName = artwork.albumName

    val artistMatches = if (artist != null && requestedArtist.isNotBlank()) {
        val requestedList = splitAndNormalizeArtists(requestedArtist)
        val resultList = splitAndNormalizeArtists(artist)
        requestedList.isNotEmpty() && resultList.isNotEmpty() &&
            requestedList.all { req -> resultList.any { res -> res == req } }
    } else true

    val songMatches = if (name != null && requestedTitle.isNotBlank()) {
        name.normalizeForComparison() == requestedTitle.normalizeForComparison()
    } else true

    val albumMatches = if (albumName != null && requestedAlbum.isNotBlank()) {
        albumName.normalizeForComparison() == requestedAlbum.normalizeForComparison()
    } else false

    return (songMatches || albumMatches) && artistMatches
}
