package app.vitune.android.utils

import android.app.Application
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Printer
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import app.vitune.android.BuildConfig
import app.vitune.core.ui.utils.isAtLeastAndroid12
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Diagnostic tool active in debug builds to pinpoint what causes lag/jank.
 *
 * It monitors:
 * 1. Main-thread stalls and stack traces (BlockCanary principle)
 * 2. Hardware FrameMetrics pipeline breakdown (Draw, Layout, Sync, Command Issue, GPU)
 * 3. Per-thread CPU and memory resource consumption via /proc/self/task/
 * 4. Jetpack Compose excessive recompositions
 * 5. Screen & player context (expanded, lyrics, canvas, playing state)
 */
object LagDiagnostics {
    const val TAG = "LagDiagnostics"

    // Configuration thresholds (ms)
    private const val STALL_THRESHOLD_MS = 24L
    private const val SEVERE_STALL_MS = 50L
    private const val JANK_FRAME_THRESHOLD_MS = 18.0f
    private const val SEVERE_FRAME_THRESHOLD_MS = 40.0f
    private const val CPU_SAMPLE_INTERVAL_MS = 3000L

    private val isInitialized = AtomicBoolean(false)
    private val isWindowAttached = AtomicBoolean(false)

    // Current app context
    private val currentScreen = AtomicReference("Unknown")
    private val isCanvasActiveRef = AtomicBoolean(false)
    private val isLyricsVisibleRef = AtomicBoolean(false)
    private val isPlayingRef = AtomicBoolean(false)

    // Dedicated background thread for diagnostics
    private var diagnosticThread: HandlerThread? = null
    private var diagnosticHandler: Handler? = null

    fun updateContext(
        screen: String? = null,
        isCanvasActive: Boolean? = null,
        isLyricsVisible: Boolean? = null,
        isPlaying: Boolean? = null
    ) {
        if (!BuildConfig.DEBUG) return
        if (screen != null) currentScreen.set(screen)
        if (isCanvasActive != null) isCanvasActiveRef.set(isCanvasActive)
        if (isLyricsVisible != null) isLyricsVisibleRef.set(isLyricsVisible)
        if (isPlaying != null) isPlayingRef.set(isPlaying)
    }

    fun getContextSummary(): String {
        return "Screen=${currentScreen.get()} | Canvas=${if (isCanvasActiveRef.get()) "Active" else "Off"} | " +
            "Lyrics=${if (isLyricsVisibleRef.get()) "Visible" else "Hidden"} | " +
            "Playing=${isPlayingRef.get()}"
    }

    fun init(application: Application) {
        if (!BuildConfig.DEBUG) return
        if (!isInitialized.compareAndSet(false, true)) return

        Log.i(TAG, "════════════════════════════════════════════════════════════════")
        Log.i(TAG, "🚀 LagDiagnostics started! Monitoring main thread, frames & CPU")
        Log.i(TAG, "💡 Logcat filter: adb logcat -s $TAG:*")
        Log.i(TAG, "════════════════════════════════════════════════════════════════")

        val ht = HandlerThread("LagDiagnosticsWorker").apply { start() }
        diagnosticThread = ht
        diagnosticHandler = Handler(ht.looper)

        MainThreadWatchdog.start()
        ThreadResourceProfiler.start(diagnosticHandler!!)
    }

    fun attachWindow(window: Window) {
        if (!BuildConfig.DEBUG || !isInitialized.get()) return
        if (!isWindowAttached.compareAndSet(false, true)) return

        val handler = diagnosticHandler ?: return
        FrameMetricsMonitor.attach(window, handler)
    }

    /**
     * Watchdog that detects main thread stalls and captures the exact stack trace
     * of what was running on the main thread during the lag.
     */
    private object MainThreadWatchdog {
        private val isRunningMessage = AtomicBoolean(false)
        private val messageStartTime = AtomicLong(0)
        private val messageHeader = AtomicReference("")
        private val sampledStack = AtomicReference<Array<StackTraceElement>?>(null)

        fun start() {
            // Background sampler thread that periodically inspects the main thread
            val watchdogThread = Thread({
                val mainThread = Looper.getMainLooper().thread
                while (true) {
                    try {
                        if (isRunningMessage.get()) {
                            val elapsed = SystemClock.uptimeMillis() - messageStartTime.get()
                            if (elapsed >= STALL_THRESHOLD_MS && sampledStack.get() == null) {
                                val trace = mainThread.stackTrace
                                if (isRealWork(trace)) {
                                    sampledStack.set(trace)
                                }
                            }
                        }
                        Thread.sleep(10)
                    } catch (_: InterruptedException) {
                        break
                    } catch (_: Throwable) {}
                }
            }, "LagWatchdogThread")
            watchdogThread.isDaemon = true
            watchdogThread.priority = Thread.MAX_PRIORITY
            watchdogThread.start()

            // Main looper message logging
            Looper.getMainLooper().setMessageLogging(object : Printer {
                override fun println(x: String) {
                    if (x.startsWith(">>>>> Dispatching to ")) {
                        messageHeader.set(x.removePrefix(">>>>> Dispatching to "))
                        sampledStack.set(null)
                        messageStartTime.set(SystemClock.uptimeMillis())
                        isRunningMessage.set(true)
                    } else if (x.startsWith("<<<<< Finished to ")) {
                        isRunningMessage.set(false)
                        val start = messageStartTime.get()
                        if (start > 0) {
                            val duration = SystemClock.uptimeMillis() - start
                            if (duration >= STALL_THRESHOLD_MS) {
                                reportStall(duration, messageHeader.get(), sampledStack.get())
                            }
                        }
                    }
                }
            })
        }

        private fun isRealWork(trace: Array<StackTraceElement>): Boolean {
            if (trace.isEmpty()) return false
            val top = trace[0].toString()
            if (top.contains("MessageQueue.nativePollOnce") || top.contains("Object.wait")) {
                return false
            }
            return true
        }

        private fun reportStall(durationMs: Long, header: String, trace: Array<StackTraceElement>?) {
            val droppedFrames = (durationMs / 16.6f).roundToInt()
            val isSevere = durationMs >= SEVERE_STALL_MS
            val level = if (isSevere) Log.ERROR else Log.WARN

            val sb = StringBuilder()
            sb.appendLine("🚨 [MAIN THREAD STALL] ${durationMs}ms (~$droppedFrames frames dropped)")
            sb.appendLine("├─ Target: $header")
            sb.appendLine("├─ Context: ${getContextSummary()}")

            if (trace != null && trace.isNotEmpty()) {
                sb.appendLine("┌── Stack trace during stall (Executing code):")
                var printed = 0
                for (elem in trace) {
                    val str = elem.toString()
                    // Filter out looper internals to highlight app / Compose code
                    if (str.startsWith("android.os.Looper") || str.startsWith("android.app.ActivityThread.main")) {
                        continue
                    }
                    val isAppCode = str.startsWith("app.vitune.")
                    val prefix = if (isAppCode) "│  👉 " else "│     "
                    sb.appendLine("$prefix$str")
                    printed++
                    if (printed >= 12) break
                }
                sb.append("└── End of stack trace")
            } else {
                sb.append("└── (Stack trace could not be sampled)")
            }

            Log.println(level, TAG, sb.toString())
        }
    }

    /**
     * Hardware FrameMetrics listener that breaks down each dropped frame into its pipeline stages:
     * Layout/Measure, Draw, RenderThread Sync, GPU Command Issue, Anim, Input, etc.
     */
    private object FrameMetricsMonitor {
        private var totalFrames = 0L
        private var droppedFrames = 0L
        private var severeFrames = 0L
        private var lastStatsLog = SystemClock.uptimeMillis()

        fun attach(window: Window, handler: Handler) {
            val refreshRate = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.context.display?.refreshRate ?: 60f
                } else {
                    @Suppress("DEPRECATION")
                    window.windowManager.defaultDisplay.refreshRate
                }
            } catch (_: Throwable) {
                60f
            }
            val targetFrameMs = 1000f / refreshRate.coerceIn(30f, 240f)

            window.addOnFrameMetricsAvailableListener(
                Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                    val totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                    val totalMs = totalDurationNs / 1_000_000f

                    totalFrames++

                    if (totalMs > targetFrameMs + 2.0f) {
                        droppedFrames++
                        if (totalMs >= SEVERE_FRAME_THRESHOLD_MS) severeFrames++

                        if (totalMs >= JANK_FRAME_THRESHOLD_MS) {
                            reportFrameJank(frameMetrics, totalMs, targetFrameMs)
                        }
                    }

                    // Periodic stats summary every 15 seconds
                    val now = SystemClock.uptimeMillis()
                    if (now - lastStatsLog >= 15000L && totalFrames > 0) {
                        val dropPercent = (droppedFrames * 100f) / totalFrames
                        Log.i(
                            TAG,
                            String.format(
                                Locale.US,
                                "📈 [Frame Summary] Frames: %d | Dropped: %d (%.1f%%) | Severe (>40ms): %d | %s",
                                totalFrames, droppedFrames, dropPercent, severeFrames, getContextSummary()
                            )
                        )
                        lastStatsLog = now
                    }
                },
                handler
            )
        }

        private fun reportFrameJank(metrics: FrameMetrics, totalMs: Float, targetMs: Float) {
            val layoutMs = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000_000f
            val drawMs = metrics.getMetric(FrameMetrics.DRAW_DURATION) / 1_000_000f
            val syncMs = metrics.getMetric(FrameMetrics.SYNC_DURATION) / 1_000_000f
            val cmdIssueMs = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) / 1_000_000f
            val animMs = metrics.getMetric(FrameMetrics.ANIMATION_DURATION) / 1_000_000f
            val inputMs = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) / 1_000_000f
            val swapMs = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION) / 1_000_000f
            val delayMs = metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION) / 1_000_000f
            val gpuMs = if (isAtLeastAndroid12) metrics.getMetric(FrameMetrics.GPU_DURATION) / 1_000_000f else 0f

            // Identify primary bottleneck
            val stages = listOf(
                "Draw (Canvas/Graphics/Blur)" to drawMs,
                "Layout & Measure" to layoutMs,
                "RenderThread Sync" to syncMs,
                "GPU Command Issue" to cmdIssueMs,
                "Animation Computation" to animMs,
                "Input Dispatch" to inputMs,
                "Swap Buffers" to swapMs,
                "Scheduling Delay" to delayMs
            )
            val bottleneck = stages.maxByOrNull { it.second } ?: ("Unknown" to 0f)

            val lostFrames = (totalMs / targetMs).toInt()
            val level = if (totalMs >= SEVERE_FRAME_THRESHOLD_MS) Log.ERROR else Log.WARN

            val sb = StringBuilder()
            sb.appendLine(
                String.format(
                    Locale.US,
                    "⚠️ [DROPPED FRAME] %.1fms (Target: %.1fms | ~%d frames lost)",
                    totalMs, targetMs, lostFrames
                )
            )
            sb.appendLine(String.format(Locale.US, "├─ Primary Bottleneck: %s (%.1fms)", bottleneck.first, bottleneck.second))
            sb.appendLine("┌── Pipeline Breakdown:")
            sb.appendLine(String.format(Locale.US, "│  • Draw:              %5.1f ms %s", drawMs, if (bottleneck.first.startsWith("Draw")) "<-- HOT" else ""))
            sb.appendLine(String.format(Locale.US, "│  • Layout & Measure:  %5.1f ms %s", layoutMs, if (bottleneck.first.startsWith("Layout")) "<-- HOT" else ""))
            sb.appendLine(String.format(Locale.US, "│  • Sync to Render:    %5.1f ms %s", syncMs, if (bottleneck.first.startsWith("Sync")) "<-- HOT" else ""))
            sb.appendLine(String.format(Locale.US, "│  • Command Issue:     %5.1f ms", cmdIssueMs))
            sb.appendLine(String.format(Locale.US, "│  • Animation:         %5.1f ms", animMs))
            sb.appendLine(String.format(Locale.US, "│  • Input Dispatch:    %5.1f ms", inputMs))
            if (gpuMs > 0f) {
                sb.appendLine(String.format(Locale.US, "│  • GPU Duration:      %5.1f ms", gpuMs))
            }
            sb.appendLine("└── Context: ${getContextSummary()}")

            Log.println(level, TAG, sb.toString())
        }
    }

    /**
     * Reads /proc/self/task/ to monitor per-thread CPU consumption and memory usage.
     * Identifies exactly which threads (main, RenderThread, ExoPlayer, decoders, workers)
     * are consuming resources.
     */
    private object ThreadResourceProfiler {
        private val previousThreadTicks = ConcurrentHashMap<Int, Long>()
        private var previousProcessTicks = 0L
        private var previousSampleTime = 0L

        fun start(handler: Handler) {
            val profilerRunnable = object : Runnable {
                override fun run() {
                    try {
                        sampleAndReport()
                    } catch (_: Throwable) {}
                    handler.postDelayed(this, CPU_SAMPLE_INTERVAL_MS)
                }
            }
            handler.postDelayed(profilerRunnable, CPU_SAMPLE_INTERVAL_MS)
        }

        private fun sampleAndReport() {
            val now = SystemClock.uptimeMillis()
            val deltaMs = if (previousSampleTime > 0) now - previousSampleTime else 0L
            previousSampleTime = now

            if (deltaMs < 1000L) return

            val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val totalAvailableTicks = (deltaMs / 1000.0) * 100.0 * numCores

            // Read process stats
            var currentProcessTicks = 0L
            val procStat = runCatching { File("/proc/self/stat").readText() }.getOrNull()
            if (procStat != null) {
                val closeParen = procStat.lastIndexOf(')')
                if (closeParen != -1 && closeParen + 2 < procStat.length) {
                    val fields = procStat.substring(closeParen + 2).split(' ')
                    if (fields.size >= 13) {
                        val utime = fields[11].toLongOrNull() ?: 0L
                        val stime = fields[12].toLongOrNull() ?: 0L
                        currentProcessTicks = utime + stime
                    }
                }
            }

            val processDeltaTicks = if (previousProcessTicks > 0) currentProcessTicks - previousProcessTicks else 0L
            previousProcessTicks = currentProcessTicks
            val processCpuPercent = ((processDeltaTicks / totalAvailableTicks) * 100.0).coerceIn(0.0, 100.0 * numCores)

            // Read per-thread stats from /proc/self/task/
            val taskDir = File("/proc/self/task")
            val threadDirs = taskDir.listFiles() ?: return

            data class ThreadStat(val tid: Int, val name: String, val cpuPercent: Double)
            val threadStats = mutableListOf<ThreadStat>()

            for (dir in threadDirs) {
                val tid = dir.name.toIntOrNull() ?: continue
                val statFile = File(dir, "stat")
                val statText = runCatching { statFile.readText() }.getOrNull() ?: continue

                val closeParen = statText.lastIndexOf(')')
                if (closeParen == -1 || closeParen + 2 >= statText.length) continue

                val openParen = statText.indexOf('(')
                val threadName = if (openParen != -1 && closeParen > openParen) {
                    statText.substring(openParen + 1, closeParen)
                } else {
                    File(dir, "comm").readText().trim()
                }

                val fields = statText.substring(closeParen + 2).split(' ')
                if (fields.size < 13) continue

                val utime = fields[11].toLongOrNull() ?: 0L
                val stime = fields[12].toLongOrNull() ?: 0L
                val currentTicks = utime + stime

                val prevTicks = previousThreadTicks[tid]
                previousThreadTicks[tid] = currentTicks

                if (prevTicks != null && prevTicks > 0) {
                    val deltaTicks = currentTicks - prevTicks
                    if (deltaTicks > 0) {
                        val cpuPercent = ((deltaTicks / totalAvailableTicks) * 100.0).coerceIn(0.0, 100.0 * numCores)
                        if (cpuPercent >= 0.5) {
                            threadStats.add(ThreadStat(tid, threadName, cpuPercent))
                        }
                    }
                }
            }

            threadStats.sortByDescending { it.cpuPercent }

            // Log if process CPU is notable (> 10%) or top thread > 5%
            val topThreadCpu = threadStats.firstOrNull()?.cpuPercent ?: 0.0
            if (processCpuPercent >= 10.0 || topThreadCpu >= 5.0) {
                val dalvikUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024f * 1024f)
                val dalvikMaxMb = Runtime.getRuntime().maxMemory() / (1024f * 1024f)
                val nativeAllocMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)

                val sb = StringBuilder()
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "📊 [RESOURCE REPORT] Process CPU: %.1f%% | Dalvik Heap: %.1f/%.1f MB | Native: %.1f MB",
                        processCpuPercent, dalvikUsedMb, dalvikMaxMb, nativeAllocMb
                    )
                )
                sb.appendLine("┌── Active Threads by CPU:")
                val displayThreads = threadStats.take(6)
                for (t in displayThreads) {
                    val tag = when {
                        t.name == "main" -> " (UI Thread)"
                        t.name.startsWith("RenderThread") -> " (GPU Renderer)"
                        t.name.contains("ExoPlayer") -> " (Audio Playback)"
                        t.name.contains("Video") || t.name.contains("Codec") -> " (Video Decode)"
                        else -> ""
                    }
                    sb.appendLine(String.format(Locale.US, "│  • %-24s (tid %5d): %5.1f%% CPU%s", t.name, t.tid, t.cpuPercent, tag))
                }
                sb.append("└── Context: ${getContextSummary()}")

                Log.d(TAG, sb.toString())
            }
        }
    }

    /**
     * Diagnostic helper for Compose to detect when a Composable is recomposing excessively.
     */
    class RecompositionCounter(val name: String, val alertThresholdPerSec: Int = 30) {
        private var count = 0
        private var windowStart = SystemClock.uptimeMillis()

        fun onRecompose() {
            val now = SystemClock.uptimeMillis()
            count++
            if (now - windowStart >= 1000L) {
                if (count >= alertThresholdPerSec) {
                    Log.w(
                        TAG,
                        "⚠️ [HIGH RECOMPOSITION] '$name' recomposed $count times in 1000ms! (Context: ${getContextSummary()})"
                    )
                }
                count = 0
                windowStart = now
            }
        }
    }
}

/**
 * Tracks recomposition rate in debug builds. In release builds this compiles to a no-op.
 */
@Composable
fun TrackRecomposition(name: String, alertThresholdPerSec: Int = 30) {
    if (!BuildConfig.DEBUG) return
    val counter = remember(name) { LagDiagnostics.RecompositionCounter(name, alertThresholdPerSec) }
    SideEffect {
        counter.onRecompose()
    }
}
