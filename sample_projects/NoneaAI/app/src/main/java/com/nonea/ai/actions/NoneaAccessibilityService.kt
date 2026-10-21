package com.nonea.ai.actions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.nonea.ai.R
import com.nonea.ai.automation.AutomationGuard
import com.nonea.ai.automation.AutomationPolicy
import com.nonea.ai.automation.ConfirmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * User-authorised UI automation. It only exists once the user switches it on in Android Settings > Accessibility,
 * and it only acts while Nonea's own master switch is on ([AutomationGuard.armed]).
 *
 * What it uses: global actions (Back/Home/...), gestures (tap/swipe), and the on-screen node tree to find a button by its label.
 * What it never does: send screen content to Gemini or anywhere else, read password fields, or act inside the blocked apps
 * listed in [AutomationPolicy]. Android itself hides content of secure windows (FLAG_SECURE) from this service.
 */
class NoneaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instance: NoneaAccessibilityService? = null
        private val _connected = MutableStateFlow(false)
        /** True while Android has this service bound (i.e. the user has enabled it). */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
        /** Last app (other than Nonea / system UI) the user was in; "scroll down" etc. act on it. */
        @Volatile var lastExternalPackage: String? = null
            private set

        fun get(): NoneaAccessibilityService? = instance
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false

        private const val MAX_NODES = 3000
        private const val MAX_DEPTH = 40
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stopView: View? = null
    private var confirmView: View? = null
    private var hideJob: Job? = null

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
        scope.launch {
            AutomationGuard.busy.collect { n ->
                hideJob?.cancel()
                if (n > 0) showStopButton()
                else hideJob = launch { delay(6000); hideStopButton() }   // stays a few seconds after the last step
            }
        }
        scope.launch { AutomationGuard.stops.collect { hideStopButton(); hideConfirm() } }
        scope.launch {
            AutomationGuard.pending.collect { req ->
                if (req == null) hideConfirm()
                // The in-app dialog covers the case where Nonea is on screen; elsewhere show the panel over the other app.
                else if (activePackage() != packageName) showConfirm(req) else hideConfirm()
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }

    private fun shutdown() {
        hideStopButton(); hideConfirm(); scope.coroutineContext[Job]?.cancel()
        if (instance === this) instance = null
        _connected.value = false
        AutomationGuard.emergencyStop()   // the service going away always ends any run in progress
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName && pkg != "com.android.systemui") lastExternalPackage = pkg
    }

    override fun onInterrupt() { AutomationGuard.emergencyStop() }

    /** Turns Accessibility access off for Nonea (the user can still re-enable it in Android Settings). */
    fun turnOff() { AutomationGuard.emergencyStop(); disableSelf() }

    // ------------------------------------------------------------------ screen info

    fun activePackage(): String? = rootInActiveWindow?.packageName?.toString()

    private fun screenSize(): Pair<Int, Int> = resources.displayMetrics.let { it.widthPixels to it.heightPixels }

    private fun labelOf(n: AccessibilityNodeInfo): String? =
        n.text?.toString()?.takeIf { it.isNotBlank() } ?: n.contentDescription?.toString()?.takeIf { it.isNotBlank() }

    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var seen = 0
        while (stack.isNotEmpty() && seen < MAX_NODES) {
            val (n, d) = stack.removeLast(); seen++
            visit(n)
            if (d >= MAX_DEPTH) continue
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it to d + 1) }
        }
    }

    /** Visible nodes whose text/description equals [label] (preferred) or contains it. */
    fun findByLabel(label: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val q = label.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val exact = ArrayList<AccessibilityNodeInfo>()
        val partial = ArrayList<AccessibilityNodeInfo>()
        walk(root) { n ->
            if (!n.isVisibleToUser) return@walk
            val l = labelOf(n)?.trim()?.lowercase() ?: return@walk
            if (l == q) exact += n else if (l.contains(q)) partial += n
        }
        return exact.ifEmpty { partial }
    }

    fun labelFor(n: AccessibilityNodeInfo): String? = labelOf(n)

    /** The node itself or its nearest enabled, clickable ancestor (up to 6 levels). */
    fun clickableTarget(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var i = 0
        while (cur != null && i < 7) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent; i++
        }
        return null
    }

    /** Does this node, or something up to 8 levels above it, look like an advertisement? */
    fun adSignals(n: AccessibilityNodeInfo): Boolean = adSignals(listOf(n))

    fun adSignals(path: List<AccessibilityNodeInfo>): Boolean {
        val texts = ArrayList<String?>(); val ids = ArrayList<String?>()
        for (start in path.takeLast(1)) {
            var cur: AccessibilityNodeInfo? = start; var i = 0
            while (cur != null && i < 9) {
                texts += cur.text?.toString(); texts += cur.contentDescription?.toString()
                ids += cur.viewIdResourceName
                cur = cur.parent; i++
            }
        }
        return AutomationPolicy.looksLikeAd(texts, ids)
    }

    /** Nodes containing (x, y), outermost first, deepest last. Empty if the tree isn't available (games, secure windows). */
    fun pathAt(x: Int, y: Int): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>()
        val budget = intArrayOf(MAX_NODES)
        fun go(n: AccessibilityNodeInfo, depth: Int): Boolean {
            if (budget[0]-- <= 0 || depth > MAX_DEPTH) return false
            val r = Rect(); n.getBoundsInScreen(r)
            if (!r.contains(x, y)) return false
            out += n
            for (i in n.childCount - 1 downTo 0) {
                val c = n.getChild(i) ?: continue
                if (c.isVisibleToUser && go(c, depth + 1)) return true
            }
            return true
        }
        go(root, 0)
        return out
    }

    fun nodeCenter(n: AccessibilityNodeInfo): Pair<Int, Int>? {
        val r = Rect(); n.getBoundsInScreen(r)
        return if (r.isEmpty) null else r.centerX() to r.centerY()
    }

    fun displaySize() = screenSize()

    // ------------------------------------------------------------------ actions

    fun click(n: AccessibilityNodeInfo): Boolean = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    suspend fun tapAt(x: Int, y: Int): Boolean {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(p, 50)
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        val accepted = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!accepted && cont.isActive) cont.resume(false)
    }

    /** direction = the way the content should move into view: "down" shows what is below. */
    suspend fun scroll(direction: String): Boolean {
        val want = when (direction) {
            "down" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
            "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
            "left" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
            "right" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
            else -> return false
        }
        val root = rootInActiveWindow
        if (root != null) {
            var best: AccessibilityNodeInfo? = null; var bestArea = 0L
            walk(root) { n ->
                if (n.isScrollable && n.isVisibleToUser && n.actionList.any { it.id == want.id }) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    val a = r.width().toLong() * r.height()
                    if (a > bestArea) { best = n; bestArea = a }
                }
            }
            if (best?.performAction(want.id) == true) return true
        }
        // No scrollable node exposed it: swipe, like a finger would.
        val (w, h) = screenSize()
        val p = Path()
        when (direction) {
            "down" -> { p.moveTo(w * 0.5f, h * 0.72f); p.lineTo(w * 0.5f, h * 0.32f) }
            "up" -> { p.moveTo(w * 0.5f, h * 0.32f); p.lineTo(w * 0.5f, h * 0.72f) }
            "left" -> { p.moveTo(w * 0.25f, h * 0.5f); p.lineTo(w * 0.75f, h * 0.5f) }
            else -> { p.moveTo(w * 0.75f, h * 0.5f); p.lineTo(w * 0.25f, h * 0.5f) }
        }
        return dispatch(p, 350)
    }

    /** The focused input field, if any. */
    fun focusedInput(): AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    /** Appends [text] to the focused field. Never called for password fields (the caller checks). */
    fun typeInto(field: AccessibilityNodeInfo, text: String): Boolean {
        val existing = if (field.isShowingHintText) "" else field.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ------------------------------------------------------------------ floating controls (accessibility overlay: no extra permission)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wm() = getSystemService(WINDOW_SERVICE) as WindowManager

    private fun showStopButton() {
        if (stopView != null) return
        val b = Button(this).apply {
            text = getString(R.string.overlay_stop)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xEED32F2F.toInt())
            minimumHeight = dp(48); minimumWidth = dp(48)
            setOnClickListener { AutomationGuard.emergencyStop() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(8); y = dp(72) }
        runCatching { wm().addView(b, lp); stopView = b }
    }

    private fun hideStopButton() { stopView?.let { v -> runCatching { wm().removeView(v) } }; stopView = null }

    private fun showConfirm(req: ConfirmRequest) {
        hideConfirm()
        val pad = dp(16)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF2202124.toInt()); setPadding(pad, pad, pad, pad)
        }
        panel.addView(TextView(this).apply { text = req.title; setTextColor(Color.WHITE); textSize = 17f })
        panel.addView(TextView(this).apply { text = req.detail; setTextColor(0xFFD0D0D0.toInt()); textSize = 14f; setPadding(0, dp(6), 0, dp(10)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = getString(R.string.confirm_cancel); minimumHeight = dp(48)
            setOnClickListener { AutomationGuard.answer(req.id, false) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = getString(R.string.confirm_allow); minimumHeight = dp(48)
            setOnClickListener { AutomationGuard.answer(req.id, true) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(row)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }
        runCatching { wm().addView(panel, lp); confirmView = panel }
    }

    private fun hideConfirm() { confirmView?.let { v -> runCatching { wm().removeView(v) } }; confirmView = null }
}
