package com.nonea.ai.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nonea.ai.automation.AutomationGuard
import com.nonea.ai.automation.AutomationPolicy
import com.nonea.ai.automation.AutomationPolicy.Verdict
import com.nonea.ai.automation.AutomationStopped
import com.nonea.ai.automation.ConfirmRequest
import kotlinx.coroutines.delay

/**
 * Runs screen automation (tap, scroll, type, Back/Home/...) through [NoneaAccessibilityService].
 * Every step: master switch on -> service connected -> not rate limited -> policy verdict -> (user confirmation) -> act.
 * Results are plain English strings and never contain text read from the screen (they are saved in the chat and sent to Gemini as history).
 */
class AutomationExecutor(private val ctx: Context) {
    private val own = ctx.packageName

    companion object {
        val TYPES = setOf("tap", "tap_text", "scroll", "type", "system", "stop_automation")
    }

    suspend fun run(a: Action): String {
        if (a.type == "stop_automation") {
            AutomationGuard.emergencyStop()
            return "Stopped. Automation is switched off until you turn it on again in Automation status."
        }
        if (!AutomationGuard.armed.value)
            return "Automation is switched off. Turn it on in Settings → Automation & Accessibility (it's off by default)."
        val svc = NoneaAccessibilityService.get()
            ?: return "Accessibility access isn't enabled for Nonea. Open Settings → Automation & Accessibility for the steps. I can't do this without it."
        if (AutomationGuard.pending.value != null) return "Please answer the confirmation question first."
        if (!AutomationGuard.allowStep()) return "I've done a lot of automated steps in the last minute, so I paused to be safe. Try again shortly."

        val token = AutomationGuard.token()
        AutomationGuard.begin()
        return try {
            when (a.type) {
                "tap_text" -> tapText(svc, a.arg.trim(), token)
                "tap" -> tapXy(svc, a.arg.trim(), token)
                "scroll" -> scroll(svc, a.arg.trim().lowercase(), token)
                "type" -> type(svc, a.arg, token)
                "system" -> system(svc, a.arg.trim().lowercase(), token)
                else -> "I can't do '${a.type}' yet."
            }
        } catch (e: AutomationStopped) {
            "Stopped."
        } catch (e: Exception) {
            "Couldn't do that: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            AutomationGuard.end()
        }
    }

    // ------------------------------------------------------------------ target app

    private fun appName(pkg: String?): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg!!, 0)).toString()
    }.getOrDefault(pkg ?: "this app")

    /**
     * Voice and typing happen inside Nonea, so the foreground app is Nonea. Bring the app the user was last in back to the front
     * (allowed because Nonea is visible), then act on it. Returns an error text, or null when the target is ready.
     */
    private suspend fun ensureTarget(svc: NoneaAccessibilityService, token: Int): String? {
        var pkg = svc.activePackage()
        if (pkg != null && pkg != own) return null
        val target = NoneaAccessibilityService.lastExternalPackage
            ?: return "I don't know which app to act on yet. Open it first (for example “Open YouTube”), then ask me again."
        val pm = ctx.packageManager
        val homePkg = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val intent = (if (target == homePkg) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        else pm.getLaunchIntentForPackage(target)) ?: return "I can't switch back to ${appName(target)}."
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(20) {
            AutomationGuard.check(token)
            delay(150)
            pkg = svc.activePackage()
            if (pkg != null && pkg != own) { delay(250); return null }
        }
        return "I couldn't switch to ${appName(target)}. Open it yourself, then ask again."
    }

    private suspend fun askIfNeeded(v: Verdict, title: String, token: Int): String? = when (v) {
        is Verdict.Allow -> null
        is Verdict.Refuse -> v.reason
        is Verdict.Confirm ->
            if (AutomationGuard.confirm(ConfirmRequest(title, v.reason), token)) null else "Okay, I didn't do it."
    }

    // ------------------------------------------------------------------ tap by label

    private suspend fun tapText(svc: NoneaAccessibilityService, label: String, token: Int): String {
        if (label.isBlank()) return tapXy(svc, "", token)
        ensureTarget(svc, token)?.let { return it }
        val pkg = svc.activePackage()
        val nodes = svc.findByLabel(label)
        if (nodes.isEmpty())
            return "I couldn't find “$label” on the screen. It may be named differently, off-screen (try “scroll down”), or in a protected area. Tell me the exact word on the button, or tap it yourself."
        val node = nodes.first()
        val shown = svc.labelFor(node)
        val judged = if (AutomationPolicy.isSensitiveLabel(label) && !AutomationPolicy.isSensitiveLabel(shown)) label else shown
        val verdict = AutomationPolicy.judgeTap(
            pkg, own, judged, node.isPassword, svc.adSignals(node), AutomationGuard.confirmAll.value,
            byCoordinates = false, ambiguousMatches = nodes.size
        )
        askIfNeeded(verdict, "Tap “$label” in ${appName(pkg)}?", token)?.let { return it }

        // The screen may have changed while the user was deciding.
        AutomationGuard.check(token)
        if (svc.activePackage() != pkg || !node.refresh()) return "The screen changed, so I cancelled the tap. Ask again if you still want it."

        val target = svc.clickableTarget(node)
        if (target != null && svc.click(target)) return "Tapped “$label”."
        val c = svc.nodeCenter(node)
        if (c != null && svc.tapAt(c.first, c.second)) return "Tapped “$label”."
        return "I found “$label” but Android or the app wouldn't let me tap it. You can tap it yourself."
    }

    // ------------------------------------------------------------------ tap by position

    private fun parsePoint(arg: String, w: Int, h: Int): Pair<Int, Int>? {
        if (arg.isBlank() || arg.equals("center", true) || arg.equals("centre", true) || arg == "middle") return w / 2 to h / 2
        val parts = arg.split(",", ";", " ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        fun v(s: String, total: Int): Int? =
            if (s.endsWith("%")) s.dropLast(1).toFloatOrNull()?.let { (total * it / 100f).toInt() } else s.toFloatOrNull()?.toInt()
        val x = v(parts[0], w) ?: return null
        val y = v(parts[1], h) ?: return null
        return if (x in 1 until w && y in 1 until h) x to y else null
    }

    private suspend fun tapXy(svc: NoneaAccessibilityService, arg: String, token: Int): String {
        val (w, h) = svc.displaySize()
        val pt = parsePoint(arg, w, h) ?: return "I need a position like “50%,80%”, or tell me which button to tap by its label."
        ensureTarget(svc, token)?.let { return it }
        val pkg = svc.activePackage()
        val path = svc.pathAt(pt.first, pt.second)
        val deepest = path.lastOrNull()
        val verdict = AutomationPolicy.judgeTap(
            pkg, own, deepest?.let { svc.labelFor(it) }, path.any { it.isPassword },
            if (path.isEmpty()) false else svc.adSignals(path), AutomationGuard.confirmAll.value, byCoordinates = true
        )
        val where = if (arg.isBlank()) "the middle of the screen" else "that spot"
        askIfNeeded(verdict, "Tap $where in ${appName(pkg)}?", token)?.let { return it }
        AutomationGuard.check(token)
        if (svc.activePackage() != pkg) return "The screen changed, so I cancelled the tap."
        return if (svc.tapAt(pt.first, pt.second)) "Tapped $where." else "Android didn't accept the tap (this app may block gestures)."
    }

    // ------------------------------------------------------------------ scroll / type

    private suspend fun scroll(svc: NoneaAccessibilityService, dir: String, token: Int): String {
        if (dir !in setOf("down", "up", "left", "right")) return "Tell me which way: up, down, left or right."
        ensureTarget(svc, token)?.let { return it }
        val pkg = svc.activePackage()
        val v = AutomationPolicy.judgeScrollOrType(pkg, own)
        val verdict = if (v is Verdict.Allow && AutomationGuard.confirmAll.value) Verdict.Confirm("You asked me to confirm every action.") else v
        askIfNeeded(verdict, "Scroll $dir in ${appName(pkg)}?", token)?.let { return it }
        AutomationGuard.check(token)
        return if (svc.scroll(dir)) "Scrolled $dir." else "I couldn't scroll here. The screen may not be scrollable."
    }

    private suspend fun type(svc: NoneaAccessibilityService, text: String, token: Int): String {
        if (text.isBlank()) return "What should I type?"
        if (text.length > 500) return "That's too long to type in one go."
        ensureTarget(svc, token)?.let { return it }
        val pkg = svc.activePackage()
        val field = svc.focusedInput() ?: return "No text box is selected. Tap a text field first (say “tap Search”), then ask me to type."
        val v = AutomationPolicy.judgeScrollOrType(pkg, own, isPassword = field.isPassword)
        val verdict = if (v is Verdict.Allow && AutomationGuard.confirmAll.value) Verdict.Confirm("You asked me to confirm every action.") else v
        askIfNeeded(verdict, "Type into the selected box in ${appName(pkg)}?", token)?.let { return it }
        AutomationGuard.check(token)
        return if (svc.typeInto(field, text)) "Typed it. I didn't press Enter; say “tap Search” if you want to submit."
        else "This text box wouldn't accept typing from me."
    }

    // ------------------------------------------------------------------ Back / Home / ...

    private suspend fun system(svc: NoneaAccessibilityService, what: String, token: Int): String {
        val code = when (what) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            else return "Screenshots through Nonea need Android 9 or newer. Use your phone's buttons instead."
            "lock" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            else return "Locking the screen through Nonea needs Android 9 or newer."
            else -> return "I don't know the system action “$what”."
        }
        if (AutomationGuard.confirmAll.value && !AutomationGuard.confirm(ConfirmRequest("Press ${what.replace('_', ' ')}?", "You asked me to confirm every action."), token))
            return "Okay, I didn't do it."
        // "Back" from Nonea's own screen would go back inside Nonea; go to the app the user meant first.
        if (what == "back") ensureTarget(svc, token)
        AutomationGuard.check(token)
        return if (svc.performGlobalAction(code)) "Done." else "Android didn't accept that action."
    }
}
