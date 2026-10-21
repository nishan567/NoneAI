package com.nonea.ai.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import com.nonea.ai.apps.AppAlias
import com.nonea.ai.apps.AppEntry
import com.nonea.ai.apps.AppLauncher
import com.nonea.ai.apps.AppNames
import com.nonea.ai.apps.MediaCommands
import com.nonea.ai.apps.SettingsPages
import com.nonea.ai.apps.Shortcut
import com.nonea.ai.apps.Shortcuts
import com.nonea.ai.apps.YouTubeLinks
import com.nonea.ai.automation.AutomationPolicy
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nonea.ai.work.ReminderWorker
import java.util.concurrent.TimeUnit

data class Action(val type: String, val arg: String = "")

object ActionParser {
    private val tag = Regex("\\[\\[ACTION:([a-z_]+)(?:\\|([^\\]]*))?\\]\\]")
    /** Extracts [[ACTION:...]] tags from model output; returns (cleanText, actions). */
    fun extract(text: String): Pair<String, List<Action>> {
        val actions = tag.findAll(text).map { Action(it.groupValues[1], it.groupValues[2].trim()) }.toList()
        return tag.replace(text, "").trim() to actions
    }
}

/** "stop" style commands that must work even while Nonea is busy. */
object StopCommand {
    private val rx = Regex("^(?:nonea[,\\s]*)?(?:emergency stop|stop automation|stop it|stop now|cancel automation|cancel it|cancel that|stop|cancel|থামো|থাম|বন্ধ করো|থামাও|stop koro|thamo)$")
    fun matches(raw: String) = rx.matches(raw.trim().lowercase().trimEnd('.', '!', '?', '।'))
}

/** Fast offline rules for common Bengali / Banglish / English commands. Everything else goes to Gemini. */
object LocalRouter {
    private fun alias(n: String) = AppNames.canonical(n)

    /** [shortcuts] are the user's own voice commands (Settings > Apps & YouTube commands); they are checked first, but never before "stop". */
    fun parse(raw: String, shortcuts: List<Shortcut> = emptyList()): Action? {
        val t = raw.trim().lowercase().trimEnd('.', '!', '?', '।')
        val time = Regex("(\\d{1,2})[:.](\\d{2})").find(t)
        val mins = Regex("(\\d+)\\s*(minute|min|মিনিট|minit|ghonta|hour|ঘণ্টা|ঘন্টা|sec|second|সেকেন্ড)").find(t)

        // The user's own voice commands. "stop" / "cancel" are never overridable (Shortcuts.validate refuses them and they are skipped here).
        if (!StopCommand.matches(t)) Shortcuts.match(t, shortcuts)?.let { return it }

        // Links first: their text must never be mistaken for a keyword such as "torch" or "volume". The verb has to come first
        // ("open <link>", "play <link>"), so a link pasted into a normal question still goes to Gemini.
        YouTubeLinks.findUrl(raw)?.let { url ->
            if (Regex("^(?:please\\s+)?(?:open|play|watch|launch|start|show)\\b").containsMatchIn(t) && YouTubeLinks.parse(url) != null)
                return Action("youtube_play", url)   // the original case is kept: video ids are case-sensitive
        }
        // A plain https link opens as a link, never as an app name
        Regex("^(?:please\\s+)?(?:open|go to|launch)\\s+(https?://\\S+)$", RegexOption.IGNORE_CASE).find(raw.trim())?.let {
            if (AutomationPolicy.isSafeWebUrl(it.groupValues[1])) return Action("open_url", it.groupValues[1])
        }

        if (Regex("torch|flashlight|টর্চ|ফ্ল্যাশ|flash light").containsMatchIn(t))
            return Action("flashlight", if (Regex("\\boff\\b|নিভা|বন্ধ|nibha|bondho").containsMatchIn(t)) "off" else "on")
        if (Regex("alarm|অ্যালার্ম").containsMatchIn(t) && time != null)
            return Action("alarm", "${time.groupValues[1]}:${time.groupValues[2]}")
        if (Regex("timer|টাইমার").containsMatchIn(t) && mins != null) {
            val n = mins.groupValues[1].toLong(); val u = mins.groupValues[2]
            val s = when {
                u.startsWith("h") || u.startsWith("g") || u.startsWith("ঘ") -> n * 3600
                u.startsWith("s") || u.startsWith("সে") -> n
                else -> n * 60
            }
            return Action("timer", s.toString())
        }
        if (Regex("volume|ভলিউম|awaj|আওয়াজ").containsMatchIn(t))
            return Action("volume", when {
                Regex("\\bup\\b|increase|বাড়া|bara").containsMatchIn(t) -> "up"
                Regex("mute|silent|বন্ধ|bondho").containsMatchIn(t) -> "mute"
                else -> "down"
            })
        // Emergency stop works even while something is running.
        if (StopCommand.matches(t)) return Action("stop_automation")

        if (Regex("screenshot|স্ক্রিনশট").containsMatchIn(t)) return Action("system", "screenshot")
        if (t == "go home" || t == "home" || t == "হোমে যাও") return Action("system", "home")
        if (t == "go back" || t == "back" || t == "পিছনে যাও") return Action("system", "back")
        Regex("^(?:press|tap|click|hit)\\s+(?:the\\s+)?(back|home|recents?|overview)(?:\\s+(?:button|key))?$").find(t)?.let {
            return Action("system", when (val k = it.groupValues[1]) { "overview" -> "recents"; "recent" -> "recents"; else -> k })
        }
        if (Regex("^(?:open|show|pull down)\\s+(?:the\\s+)?notifications?$").matches(t)) return Action("system", "notifications")
        if (Regex("^(?:open|show)\\s+(?:the\\s+)?quick settings$").matches(t)) return Action("system", "quick_settings")

        // Scrolling: anchored so a question like "how do I scroll in Excel?" still goes to Gemini.
        Regex("^(?:please\\s+)?scroll(?:\\s+(down|up|left|right))?(?:\\s+(?:a\\s+)?(?:bit|little|more))?$").find(t)
            ?.let { return Action("scroll", it.groupValues[1].ifEmpty { "down" }) }
        Regex("^(নিচে|উপরে|বামে|ডানে)\\s+স্ক্রল\\s*(?:করো|কর)?$").find(t)?.let {
            return Action("scroll", when (it.groupValues[1]) { "নিচে" -> "down"; "উপরে" -> "up"; "বামে" -> "left"; else -> "right" })
        }
        Regex("^(niche|nicha|upore|opore)\\s+scroll\\s*(?:koro|kor)?$").find(t)?.let {
            return Action("scroll", if (it.groupValues[1].startsWith("n")) "down" else "up")
        }

        // Settings pages and other system screens
        if (Regex("^(?:open|go to|show)\\s+(?:the\\s+)?settings$|^settings (?:kholo|খোলো)$").matches(t)) return Action("settings", "")
        Regex("^(?:open|go to|show)\\s+(?:the\\s+)?(.+?)\\s+settings$").find(t)?.let { m ->
            SettingsPages.normalize(m.groupValues[1])?.takeIf { it.isNotEmpty() }?.let { return Action("settings", it) }
        }
        Regex("^(?:open|show)\\s+(?:the\\s+)?(.+?)\\s+app info$|^app info\\s+(?:for|of)\\s+(.+)$").find(t)?.let { m ->
            return Action("appinfo", m.groupValues[1].ifEmpty { m.groupValues[2] }.trim().let { alias(it) })
        }

        // App list
        if (Regex("^(?:show|open|list|see)\\s+(?:me\\s+)?(?:(?:my|all|the)\\s+)*(?:apps|app list|app launcher|installed apps|applications)$|^app launcher$|^(?:আমার\\s+)?অ্যাপ\\s+(?:লিস্ট|তালিকা)(?:\\s+(?:দেখাও|খোলো))?$").matches(t))
            return Action("show_apps")

        // Media keys for whatever app is playing (a bare "stop" is the emergency stop and was handled above)
        MediaCommands.parse(t)?.let { return Action("media", it) }

        // YouTube: a link opens exactly that video; plain words can only open the results page (see AppLauncher.youtubePlay)
        Regex("^(?:please\\s+)?(?:play|watch)\\s+(.+?)\\s+on\\s+youtube$").find(t)?.let { return Action("youtube_play", it.groupValues[1].trim()) }
        Regex("^youtube\\s+(?:play|watch)\\s+(.+)$").find(t)?.let { return Action("youtube_play", it.groupValues[1].trim()) }
        Regex("^open\\s+(?:a\\s+)?(?:youtube\\s+)?video\\s+(?:of|about|for|on)\\s+(.+)$").find(t)?.let { return Action("youtube_play", it.groupValues[1].trim()) }
        Regex("^open\\s+youtube\\s+(?:and\\s+)?(search|play|watch)(?:\\s+for)?\\s+(.+)$").find(t)?.let {
            return Action(if (it.groupValues[1] == "search") "youtube_search" else "youtube_play", it.groupValues[2].trim())
        }
        Regex("^(?:youtube e|ইউটিউবে)\\s+(.+?)\\s+(?:chalao|cholao|dekhao|dekho|চালাও|দেখাও|দেখো)$").find(t)?.let { return Action("youtube_play", it.groupValues[1].trim()) }

        // YouTube search (results only: never auto-plays, because the first result can be an ad)
        Regex("^search\\s+(?:for\\s+)?(?:a\\s+)?videos?\\b(?:\\s+(?:about|of|on|for))?\\s*(.*)$").find(t)
            ?.let { return Action("youtube_search", it.groupValues[1].trim()) }
        Regex("^(?:search|find)\\s+(?:youtube\\s+for\\s+|for\\s+)?(.+?)\\s+on\\s+youtube$").find(t)
            ?.let { return Action("youtube_search", it.groupValues[1].trim()) }
        Regex("^(?:search\\s+youtube\\s+for|youtube\\s+search)\\s+(.+)$").find(t)
            ?.let { return Action("youtube_search", it.groupValues[1].trim()) }
        Regex("^(?:youtube e|ইউটিউবে)\\s+(.+?)\\s+(?:search koro|search kor|খোঁজো|সার্চ করো)$").find(t)
            ?.let { return Action("youtube_search", it.groupValues[1].trim()) }

        // Store: only opens the search page; the user taps Install themselves
        Regex("^(?:search\\s+(?:the\\s+)?play\\s*store\\s+for|install)\\s+(.+)$").find(t)
            ?.let { return Action("play_store", it.groupValues[1].trim()) }

        // Tapping
        Regex("^(?:tap|click|press|touch)\\s+(?:on\\s+)?(?:the\\s+)?screen$|^(?:tap|click)$|^(?:স্ক্রিনে )?(?:ট্যাপ|ক্লিক|চাপ) (?:করো|কর|দাও)$|^tap koro$")
            .find(t)?.let { return Action("tap", "") }
        Regex("^(?:tap|click|press|touch)\\s+(?:on\\s+)?(?:the\\s+)?(.+?)(?:\\s+button)?$").find(t)
            ?.let { m -> m.groupValues[1].trim().takeIf { it.split(" ").size <= 4 }?.let { return Action("tap_text", it) } }
        Regex("^(.+?)\\s+(?:e\\s+|এ\\s+)?(?:tap|click|ট্যাপ|ক্লিক)\\s*(?:koro|kor|করো|কর|dao|দাও)$").find(t)
            ?.let { return Action("tap_text", it.groupValues[1].trim()) }
        // Typing needs quotes so "type of cars?" is just a question: type "hello world"
        Regex("^(?:please\\s+)?type\\s+(?:in\\s+)?[\"“'](.+?)[\"”']$", RegexOption.IGNORE_CASE).find(raw.trim())
            ?.let { return Action("type", it.groupValues[1]) }

        Regex("^(?:open|launch|start)\\s+(.+)$").find(t)?.let { return Action("open_app", alias(it.groupValues[1])) }
        Regex("^(.+?)\\s+(?:kholo|khulo|khol|open koro|open kor|খোলো|খোল|খুলো|ওপেন করো|ওপেন কর)$").find(t)
            ?.let { return Action("open_app", alias(it.groupValues[1])) }
        Regex("^(?:call|ring|dial)\\s+(.+)$").find(t)?.let { return Action("call", it.groupValues[1]) }
        Regex("^(.+?)\\s+(?:ke\\s+|কে\\s+)?(?:call|phone|কল|ফোন)\\s*(?:koro|kor|dao|করো|কর|দাও)?$").find(t)
            ?.let { return Action("call", it.groupValues[1].trim()) }
        return null
    }
}

class ActionExecutor(private val ctx: Context, aliases: () -> List<AppAlias> = { emptyList() }) {
    private val automation = AutomationExecutor(ctx)
    private val launcher = AppLauncher(ctx, aliases)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    suspend fun execute(a: Action): String = try { run(a) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { "Couldn't do that (${a.type}): ${e.message}" }

    private suspend fun run(a: Action): String = when (a.type) {
        in AutomationExecutor.TYPES -> automation.run(a)
        "open_app" -> launcher.openApp(a.arg)
        "call" -> call(a.arg)
        "sms" -> {
            val parts = a.arg.split(";", limit = 2)
            val to = parts[0].trim(); val body = parts.getOrElse(1) { "" }
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${resolveNumber(to) ?: to}")).putExtra("sms_body", body))
            "Message draft ready for you to review and send."
        }
        "flashlight" -> torch(a.arg != "off")
        "alarm" -> {
            val (h, m) = a.arg.split(":").map { it.trim().toInt() }
            start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Nonea").putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "Alarm set for %02d:%02d.".format(h, m)
        }
        "timer" -> {
            val s = a.arg.trim().toInt()
            start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            "Timer started for ${s / 60} min ${s % 60} sec."
        }
        "remind" -> {
            val parts = a.arg.split(";", limit = 2)
            val m = parts[0].trim().toLong(); val text = parts.getOrElse(1) { "Reminder" }
            val req = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(m, TimeUnit.MINUTES)
                .setInputData(Data.Builder().putString("text", text).build()).build()
            WorkManager.getInstance(ctx).enqueue(req)
            "Okay, I'll remind you in $m minutes."
        }
        "volume" -> {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val d = when (a.arg) { "up" -> AudioManager.ADJUST_RAISE; "mute" -> AudioManager.ADJUST_MUTE; else -> AudioManager.ADJUST_LOWER }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI); "Volume adjusted."
        }
        "web" -> { start(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", a.arg)); "Searching for ${a.arg}." }
        "maps" -> { start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(a.arg)}"))); "Opening maps for ${a.arg}." }
        "wifi" -> { start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); "Opened the Wi-Fi panel (Android doesn't let apps toggle it directly)." }
        "bluetooth" -> { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "Opened Bluetooth settings." }
        "settings" -> launcher.openSettings(a.arg)
        "appinfo" -> launcher.appInfo(a.arg)
        "youtube_search" -> launcher.youtubeSearch(a.arg)
        "youtube_play" -> launcher.youtubePlay(a.arg)
        "media" -> launcher.media(a.arg)
        "show_apps" -> "Here are your apps."   // the chat screen opens the launcher; nothing to do here
        "open_url" -> openUrl(a.arg)
        "play_store" -> playStore(a.arg)
        else -> "I don't know how to do '${a.type}' yet."
    }

    /** Installed launchable apps (label + package) for the app launcher. */
    fun installedApps(): List<AppEntry> = launcher.installedApps()
    fun appIcon(pkg: String) = launcher.appIcon(pkg)

    // ---- intents and deep links (no Accessibility needed). App, Settings, YouTube and media actions live in apps/AppLauncher.kt ----

    private fun openUrl(url: String): String {
        if (!AutomationPolicy.isSafeWebUrl(url)) return "I only open plain http(s) links."
        return try { start(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))); "Opening the link." }
        catch (e: android.content.ActivityNotFoundException) { "No app can open that link." }
    }

    private fun playStore(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "Which app should I look for?"
        return try {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(q)}&c=apps")))
            "Opened the Play Store search for “$q”. Tap Install yourself when you're ready."
        } catch (e: android.content.ActivityNotFoundException) {
            try { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(q)}&c=apps"))); "Opened the Play Store search for “$q” in your browser." }
            catch (e2: android.content.ActivityNotFoundException) { "No app can open the Play Store here." }
        }
    }

    private fun resolveNumber(arg: String): String? {
        val digits = arg.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5) return digits
        if (granted(Manifest.permission.READ_CONTACTS)) {
            val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val num = ContactsContract.CommonDataKinds.Phone.NUMBER
            val nm = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ctx.contentResolver.query(p, arrayOf(num), "$nm LIKE ?", arrayOf("%$arg%"), null)
                ?.use { if (it.moveToFirst()) return it.getString(0) }
        }
        return null
    }

    private fun call(arg: String): String {
        val num = resolveNumber(arg) ?: return "I couldn't find '$arg' in contacts (is Contacts permission granted?)."
        val uri = Uri.parse("tel:${Uri.encode(num)}")
        return if (granted(Manifest.permission.CALL_PHONE)) { start(Intent(Intent.ACTION_CALL, uri)); "Calling $arg." }
        else { start(Intent(Intent.ACTION_DIAL, uri)); "Opened the dialer for $arg (grant Phone permission for direct calls)." }
    }

    private fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "This phone has no flashlight."
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on." else "Flashlight off."
    }
}
