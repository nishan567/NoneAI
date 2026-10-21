package com.nonea.ai.ai

import android.util.Log
import com.nonea.ai.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Removes API keys / tokens from any text that might reach logs, error banners or crash reports.
 * Two layers: exact secrets the app currently knows (any format), plus well-known patterns.
 */
object Redact {
    private const val MASK = "[REDACTED]"
    private val known = CopyOnWriteArraySet<String>()
    private val googleKey = Regex("AIza[0-9A-Za-z_\\-]{20,}")
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=\\-]{8,}")
    private val kv = Regex("(?i)(x-goog-api-key|api[_-]?key|access[_-]?token|authorization)(\\s*[:=]\\s*)\"?[^\\s\",&]+")

    fun register(secret: String) {
        val s = secret.trim()
        if (s.length >= 8) known.add(s)
    }

    fun text(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var out: String = input
        for (s in known) out = out.replace(s, MASK)
        out = googleKey.replace(out, MASK)
        out = bearer.replace(out, "Bearer $MASK")
        out = kv.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + MASK }
        return out
    }

    /** Copy of [e] (and its causes) with redacted messages and the original stack trace. */
    fun throwable(e: Throwable, depth: Int = 0): Throwable {
        val cause = e.cause?.takeIf { depth < 5 && it !== e }?.let { throwable(it, depth + 1) }
        val copy = Throwable(text("${e.javaClass.name}: ${e.message.orEmpty()}"), cause)
        copy.stackTrace = e.stackTrace
        return copy
    }
}

/** Logging wrapper: always redacts, and debug-level output only exists in debug builds. */
object SafeLog {
    fun d(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.d(tag, Redact.text(msg)) }
    fun w(tag: String, msg: String, t: Throwable? = null) {
        val extra = t?.let { " (${it.javaClass.simpleName}: ${it.message})" }.orEmpty()
        Log.w(tag, Redact.text(msg + extra))
    }
}

/** Makes uncaught-exception reports (logcat, system crash dialogs, OEM reporters) carry redacted text. */
object CrashRedactor {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            val safe = runCatching { Redact.throwable(e) }.getOrDefault(e)
            if (previous != null) previous.uncaughtException(thread, safe)
        }
    }
}
