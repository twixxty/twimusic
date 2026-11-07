package app.vitune.android.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Motionartwork(
    val name: String? = null,
    val artist: String? = null,
    @SerialName("albumId")
    val albumId: String? = null,
    val albumName: String? = null,
    val static: String? = null,
    val animated: String? = null,
    val videoUrl: String? = null,
    val animatedTall: String? = null,
) {
    val preferredAnimationUrl: String?
        get() = animatedTall ?: animated ?: videoUrl
}
