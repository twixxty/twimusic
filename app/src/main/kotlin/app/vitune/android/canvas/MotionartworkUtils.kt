package app.vitune.android.canvas

import app.vitune.android.lyrics.LyricsUtils
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
            requestedList.any { req -> resultList.any { res -> res == req || res.contains(req) || req.contains(res) } }
    } else true

    val cleanReqTitle = LyricsUtils.cleanTitleForSearch(requestedTitle).normalizeForComparison()
    val cleanResName = name?.let { LyricsUtils.cleanTitleForSearch(it).normalizeForComparison() }

    val songMatches = if (cleanResName != null && cleanReqTitle.isNotBlank()) {
        cleanResName == cleanReqTitle ||
        cleanResName.contains(cleanReqTitle) ||
        cleanReqTitle.contains(cleanResName)
    } else true

    val albumMatches = if (albumName != null && requestedAlbum.isNotBlank()) {
        val normResAlbum = albumName.normalizeForComparison()
        val normReqAlbum = requestedAlbum.normalizeForComparison()
        normResAlbum == normReqAlbum || normResAlbum.contains(normReqAlbum) || normReqAlbum.contains(normResAlbum)
    } else false

    return (songMatches || albumMatches) && artistMatches
}
