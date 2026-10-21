package com.nonea.ai.automation

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/** What the user is asked before a risky step. */
data class ConfirmRequest(val title: String, val detail: String, val id: Long = System.nanoTime())

class AutomationStopped : RuntimeException("Stopped by user")

/**
 * Process-wide switchboard for automation: master switch, emergency stop, confirmation hand-off and rate limit.
 * The master switch is persisted synchronously (commit) so a stop survives the process being killed right after.
 */
object AutomationGuard {
    private const val PREFS = "nonea_automation"
    private var prefs: android.content.SharedPreferences? = null

    private val _armed = MutableStateFlow(false)
    /** Master switch: automation through Accessibility does nothing unless this is on. Off by default. */
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _confirmAll = MutableStateFlow(false)
    val confirmAll: StateFlow<Boolean> = _confirmAll.asStateFlow()

    private val _disclosed = MutableStateFlow(false)
    /** The user has read the Accessibility disclosure. */
    val disclosed: StateFlow<Boolean> = _disclosed.asStateFlow()

    private val _busy = MutableStateFlow(0)
    /** Number of automation steps currently running (drives the floating Stop button). */
    val busy: StateFlow<Int> = _busy.asStateFlow()

    private val _pending = MutableStateFlow<ConfirmRequest?>(null)
    val pending: StateFlow<ConfirmRequest?> = _pending.asStateFlow()

    private val _stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stops: SharedFlow<Unit> = _stops.asSharedFlow()

    private val epoch = AtomicInteger(0)
    @Volatile private var waiting: CompletableDeferred<Boolean>? = null
    private val recent = ArrayDeque<Long>()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _armed.value = p.getBoolean("armed", false)
        _confirmAll.value = p.getBoolean("confirm_all", false)
        _disclosed.value = p.getBoolean("disclosed", false)
    }

    fun setArmed(on: Boolean) { _armed.value = on; prefs?.edit()?.putBoolean("armed", on)?.commit() }
    fun setConfirmAll(on: Boolean) { _confirmAll.value = on; prefs?.edit()?.putBoolean("confirm_all", on)?.apply() }
    fun setDisclosed(on: Boolean) { _disclosed.value = on; prefs?.edit()?.putBoolean("disclosed", on)?.apply() }

    /** Captured at the start of a run; a later emergency stop makes [check] throw. */
    fun token(): Int = epoch.get()
    fun check(token: Int) { if (epoch.get() != token) throw AutomationStopped() }

    /** Cancels everything in flight, denies any open question and switches automation off until the user re-enables it. */
    fun emergencyStop() {
        epoch.incrementAndGet()
        setArmed(false)
        waiting?.complete(false); waiting = null
        _pending.value = null
        _stops.tryEmit(Unit)
    }

    fun begin() { _busy.value = _busy.value + 1 }
    fun end() { _busy.value = (_busy.value - 1).coerceAtLeast(0) }

    /** Asks the user; denied on timeout, on stop, or if another question replaces it. Voice can't approve: it needs a tap. */
    suspend fun confirm(req: ConfirmRequest, token: Int, timeoutMs: Long = 30_000): Boolean {
        check(token)
        waiting?.complete(false)
        val d = CompletableDeferred<Boolean>()
        waiting = d
        _pending.value = req
        val ok = withTimeoutOrNull(timeoutMs) { d.await() } ?: false
        if (waiting === d) { waiting = null; _pending.value = null }
        check(token)
        return ok
    }

    fun answer(id: Long, yes: Boolean) {
        if (_pending.value?.id == id) waiting?.complete(yes)
    }

    /** Simple loop guard: at most [AutomationPolicy.MAX_PER_MINUTE] automated steps per minute. */
    @Synchronized fun allowStep(now: Long = System.currentTimeMillis()): Boolean {
        while (recent.isNotEmpty() && now - recent.first() > 60_000) recent.removeFirst()
        if (recent.size >= AutomationPolicy.MAX_PER_MINUTE) return false
        recent.addLast(now); return true
    }
}
