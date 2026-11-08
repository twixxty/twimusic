package app.vitune.android.canvas

object MotionartworkPlaybackCache {
    private const val defaultMaxSize = 128
    private val map = LinkedHashMap<String, Motionartwork>(defaultMaxSize, 0.75f, true)

    @Synchronized
    fun get(key: String): Motionartwork? {
        if (key.isBlank()) return null
        return map[key]
    }

    @Synchronized
    fun put(key: String, artwork: Motionartwork) {
        if (key.isBlank()) return
        if (map.size >= defaultMaxSize) {
            val oldest = map.keys.firstOrNull()
            if (oldest != null) map.remove(oldest)
        }
        map[key] = artwork
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
