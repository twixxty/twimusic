package com.metrolist.innertube

@Suppress("UnusedParameter", "unused")
object Timber {
    @JvmStatic fun d(message: String?, vararg args: Any?) {}
    @JvmStatic fun d(t: Throwable?, message: String?, vararg args: Any?) {}
    @JvmStatic fun d(t: Throwable?) {}
    @JvmStatic fun i(message: String?, vararg args: Any?) {}
    @JvmStatic fun i(t: Throwable?, message: String?, vararg args: Any?) {}
    @JvmStatic fun w(message: String?, vararg args: Any?) {}
    @JvmStatic fun w(t: Throwable?, message: String?, vararg args: Any?) {}
    @JvmStatic fun w(t: Throwable?) {}
    @JvmStatic fun e(message: String?, vararg args: Any?) {}
    @JvmStatic fun e(t: Throwable?, message: String?, vararg args: Any?) {}
    @JvmStatic fun e(t: Throwable?) {}
    @JvmStatic fun tag(tag: String): Timber = this
}
