package com.nonea.ai.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.delay

/**
 * App launching, YouTube links, Settings screens and media keys, using only public Android interfaces:
 * launch intents, https links handled by the YouTube app, Settings intents, and media-button events. No special permission.
 *
 * What this does NOT do (Android doesn't offer it to normal apps): pick a video from a search, read what is playing, seek,
 * or control another app's UI. Screen taps are the separate, opt-in Accessibility layer (AutomationExecutor).
 */
class AppLauncher(private val ctx: Context, private val aliases: () -> List<AppAlias> = { emptyList() }) {
    private val pm get() = ctx.packageManager

    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun isInstalled(pkg: String): Boolean = runCatching { pm.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)

    // ------------------------------------------------------------------ app list

    /** Installed apps that have a launcher icon, A-Z, without Nonea itself. Needs the LAUNCHER <queries> entry in the manifest. */
    fun installedApps(): List<AppEntry> {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appIcon(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    // ------------------------------------------------------------------ open an app

    fun openApp(name: String): String {
        val q = name.trim()
        if (q.isEmpty()) return "Which app should I open?"
        if (AppNames.normalize(q) in setOf("nonea", "nonea ai")) return "You're already in Nonea."
        return when (val r = AppNames.resolve(q, installedApps(), aliases())) {
            is Resolution.Found -> launch(r.app)
            is Resolution.Ambiguous ->
                "More than one app fits “$q”: ${r.options.joinToString(", ") { it.label }}. Say the full name, or pick it from the Apps list."
            is Resolution.NotInstalled -> notInstalled(q)
        }
    }

    private fun launch(app: AppEntry): String {
        val i = pm.getLaunchIntentForPackage(app.packageName)
            ?: return "${app.label} is installed but has no screen I can open (it may be disabled)."
        return try { start(i); "Opening ${app.label}." }
        catch (e: ActivityNotFoundException) { "Android couldn't open ${app.label}." }
        catch (e: SecurityException) { "Android wouldn't let me open ${app.label}." }
    }

    private fun notInstalled(q: String): String {
        if (AppNames.canonical(q) == "youtube") {
            return try {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                "The YouTube app isn't installed, so I opened youtube.com in your browser. Say “search Play Store for YouTube” to get the app."
            } catch (e: ActivityNotFoundException) { "YouTube isn't installed and no browser can open youtube.com." }
        }
        return "I couldn't find an app called $q on this phone. Say “search Play Store for $q” and I'll open the store search so you can install it."
    }

    // ------------------------------------------------------------------ YouTube

    private enum class Where { APP, BROWSER, NONE }

    /** Opens a youtube.com link in the YouTube app when it is installed and enabled, otherwise in whatever handles https links. */
    private fun openYouTube(url: String): Where {
        val uri = Uri.parse(url)
        if (isInstalled(YouTubeLinks.PACKAGE)) {
            try { start(Intent(Intent.ACTION_VIEW, uri).setPackage(YouTubeLinks.PACKAGE)); return Where.APP }
            catch (e: ActivityNotFoundException) { /* disabled or can't take this link: fall through to the browser */ }
        }
        return try { start(Intent(Intent.ACTION_VIEW, uri)); Where.BROWSER } catch (e: ActivityNotFoundException) { Where.NONE }
    }

    fun youtubeSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "What should I search for on YouTube? Say “search YouTube for …”."
        return when (openYouTube(YouTubeLinks.searchUrl(q))) {
            Where.APP -> "Showing YouTube results for “$q”. Tap a video to play it. I don't auto-play, because the first result can be an ad."
            Where.BROWSER -> "The YouTube app isn't available, so I opened the results for “$q” in your browser. Tap a video to play it."
            Where.NONE -> "No app on this phone can open YouTube links."
        }
    }

    /**
     * A YouTube link opens that exact video / short / playlist. Plain words can only open the results page: Android and YouTube give
     * apps no way to say "play the top result", and the first result is often an ad, so the choice stays with the user.
     */
    fun youtubePlay(arg: String): String {
        val a = arg.trim()
        if (a.isEmpty()) return "Which video? Give me a YouTube link, or say what to look for."
        YouTubeLinks.parse(a)?.let { t ->
            val what = if (t.kind == YouTubeLinks.Kind.PLAYLIST) "playlist" else "video"
            return when (openYouTube(t.url)) {
                Where.APP -> "Opening that $what in YouTube."
                Where.BROWSER -> "The YouTube app isn't available, so I opened that $what in your browser."
                Where.NONE -> "No app on this phone can open YouTube links."
            }
        }
        if (YouTubeLinks.findUrl(a) != null || a.startsWith("http", ignoreCase = true))
            return "I couldn't read a video from that link. I can open youtube.com/watch, youtu.be and Shorts links, or search YouTube for words."
        return youtubeSearch(a)
    }

    // ------------------------------------------------------------------ Settings and system screens

    private fun settingsIntent(key: String): Intent? = when (key) {
        "" -> Intent(Settings.ACTION_SETTINGS)
        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
        "volume" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_VOLUME) else Intent(Settings.ACTION_SOUND_SETTINGS)
        "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "language" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
        "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        "privacy" -> Intent(Settings.ACTION_PRIVACY_SETTINGS)
        "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
        "allapps" -> Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
        "defaultapps" -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        "date" -> Intent(Settings.ACTION_DATE_SETTINGS)
        "about" -> Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        "keyboard" -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        "network" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
        "mobiledata" -> Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        "cast" -> Intent(Settings.ACTION_CAST_SETTINGS)
        "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        "developer" -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        "nonea" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        else -> null
    }

    fun openSettings(page: String): String {
        val key = SettingsPages.normalize(page)
        val intent = key?.let { settingsIntent(it) }
        if (intent == null) {
            return try { start(Intent(Settings.ACTION_SETTINGS)); "I don't have a shortcut for “${page.trim()}”, so I opened Settings. You can pick it from there." }
            catch (e: ActivityNotFoundException) { "This phone has no Settings screen I can open." }
        }
        val shown = page.trim()
        return try { start(intent); if (key == "") "Opening Settings." else "Opening $shown settings." }
        catch (e: ActivityNotFoundException) {
            // A phone that lacks this specific screen (some skins, or developer options not unlocked): fall back to the main page.
            try { start(Intent(Settings.ACTION_SETTINGS)); "This phone has no separate “$shown” screen, so I opened Settings." }
            catch (e2: ActivityNotFoundException) { "This phone has no Settings screen I can open." }
        }
    }

    /** The system "App info" page for an installed app (where you can force stop, clear data, or uninstall it yourself). */
    fun appInfo(name: String): String {
        val q = name.trim()
        if (q.isEmpty()) return "Which app's info should I open?"
        return when (val r = AppNames.resolve(q, installedApps(), aliases())) {
            is Resolution.Found -> try {
                start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", r.app.packageName, null)))
                "Opening the info page for ${r.app.label}."
            } catch (e: ActivityNotFoundException) { "This phone has no app info screen I can open." }
            is Resolution.Ambiguous -> "More than one app fits “$q”: ${r.options.joinToString(", ") { it.label }}. Say the full name."
            is Resolution.NotInstalled -> "I couldn't find an app called $q on this phone."
        }
    }

    // ------------------------------------------------------------------ media keys

    /**
     * Sends a standard media-button press (the same one headphone buttons send). Android delivers it to whichever app last played
     * media, so it only works if such an app is running. Whether it worked is judged from AudioManager.isMusicActive (any playing
     * media stream), never assumed.
     */
    suspend fun media(arg: String): String {
        val key = arg.trim().lowercase()
        val code = when (key) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "I can play, pause, skip to the next or previous item, or stop media."
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val before = am.isMusicActive
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        delay(600)
        val after = am.isMusicActive
        val noPlayer = "Android sends this to the last app that played media, so open your music or video app and start something first."
        return when (key) {
            "pause", "stop" -> when {
                !before -> "Nothing seemed to be playing, so there was nothing to ${if (key == "stop") "stop" else "pause"}."
                !after -> if (key == "stop") "Stopped." else "Paused."
                else -> "I sent “$key”, but something is still playing. That app may not support it. Use its own pause button."
            }
            "play" -> if (after) "Playing." else "I sent “play”, but nothing started. $noPlayer"
            "toggle" -> when {
                before && !after -> "Paused."
                !before && after -> "Playing."
                else -> "I sent play/pause, but nothing changed. $noPlayer"
            }
            else -> "Sent “$key” to the current media app. I can't confirm it worked."
        }
    }
}
