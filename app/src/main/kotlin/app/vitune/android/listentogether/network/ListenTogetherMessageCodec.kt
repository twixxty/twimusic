/**
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist), GPL-3.0.
 * Wire format must stay identical to upstream for cross-client compatibility.
 */

package app.vitune.android.listentogether.network

import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

class ListenTogetherEnvelope(
    val type: String,
    val payload: ByteArray,
    val requestId: Long = 0L,
)

interface ListenTogetherMessageCodec {
    var compressionEnabled: Boolean

    fun encode(
        msgType: String,
        payload: Any?,
        requestId: Long = 0L,
    ): ByteArray

    fun decodeEnvelope(data: ByteArray): ListenTogetherEnvelope

    fun decode(data: ByteArray): Pair<String, ByteArray> = decodeEnvelope(data).let { it.type to it.payload }

    fun decodePayload(
        msgType: String,
        payloadBytes: ByteArray,
    ): Any?
}

internal fun createListenTogetherMessageCodec(): ListenTogetherMessageCodec = PortableMessageCodec()

internal fun compressListenTogetherPayload(data: ByteArray): ByteArray {
    val output = Buffer()
    GzipSink(output).buffer().use { it.write(data) }
    return output.readByteArray()
}

internal fun decompressListenTogetherPayload(
    data: ByteArray,
    limit: Int
): ByteArray = try {
    GzipSource(Buffer().write(data)).use { gzip ->
        val output = Buffer()
        while (true) {
            val count = gzip.read(output, minOf(8192L, limit - output.size + 1L))
            if (count == -1L) break
            require(output.size <= limit) { "Compressed Listen Together payload exceeds the size limit" }
        }
        output.readByteArray()
    }
} catch (error: Exception) {
    throw IllegalArgumentException("Invalid compressed Listen Together payload", error)
}

internal fun ListenTogetherMessageCodec.decodeServerCapabilities(data: ByteArray): ServerCapabilities? {
    val (type, payloadBytes) = decode(data)
    if (type != MessageTypes.SERVER_CAPABILITIES) return null
    return decodePayload(type, payloadBytes) as? ServerCapabilities
}
