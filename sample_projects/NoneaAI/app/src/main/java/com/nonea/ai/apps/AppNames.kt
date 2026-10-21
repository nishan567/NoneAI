package com.nonea.ai.apps

/** One installed, launchable app. */
data class AppEntry(val label: String, val packageName: String)

/** A name the user chose for an app ("fb" -> Facebook). Stored by package, so it survives label changes and translations. */
data class AppAlias(val alias: String, val packageName: String)

/** What a spoken or typed app name turned out to mean. */
sealed class Resolution {
    data class Found(val app: AppEntry) : Resolution()
    /** Several installed apps fit and none is clearly the one meant: ask instead of guessing. */
    data class Ambiguous(val options: List<AppEntry>) : Resolution()
    data class NotInstalled(val name: String) : Resolution()
}

/** App-name handling: built-in aliases, well-known packages and the matching order. Pure Kotlin, unit-tested. */
object AppNames {
    fun normalize(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', '।')

    /** Lower-case, no spaces or punctuation: "Whats App" == "whatsapp". */
    fun compact(s: String): String = normalize(s).filterNot { it.isWhitespace() || it in "-_.'’" }

    /** Built-in aliases: spoken / typed name -> canonical name (a key of [knownPackages], or just a label to look for). */
    val builtinAliases: Map<String, String> = mapOf(
        "ইউটিউব" to "youtube", "you tube" to "youtube", "yt" to "youtube", "youtube.com" to "youtube",
        "ইউটিউব মিউজিক" to "youtube music", "yt music" to "youtube music",
        "ফেসবুক" to "facebook", "fb" to "facebook",
        "হোয়াটসঅ্যাপ" to "whatsapp", "whats app" to "whatsapp", "ওয়াটসঅ্যাপ" to "whatsapp",
        "ক্যামেরা" to "camera", "গ্যালারি" to "gallery", "সেটিংস" to "settings", "ক্রোম" to "chrome",
        "মেসেঞ্জার" to "messenger", "গুগল ম্যাপ" to "maps", "google maps" to "maps", "ম্যাপ" to "maps",
        "ক্যালকুলেটর" to "calculator", "ঘড়ি" to "clock", "টেলিগ্রাম" to "telegram",
        "insta" to "instagram", "ইনস্টাগ্রাম" to "instagram", "জিমেইল" to "gmail",
        "playstore" to "play store", "google play" to "play store", "প্লে স্টোর" to "play store",
        "স্পটিফাই" to "spotify", "নেটফ্লিক্স" to "netflix"
    )

    /** Well-known apps -> package, so "YouTube" opens the real YouTube even if another app has "youtube" in its label. */
    val knownPackages: Map<String, String> = mapOf(
        "youtube" to YouTubeLinks.PACKAGE, "youtube music" to "com.google.android.apps.youtube.music",
        "chrome" to "com.android.chrome", "whatsapp" to "com.whatsapp",
        "facebook" to "com.facebook.katana", "messenger" to "com.facebook.orca", "telegram" to "org.telegram.messenger",
        "maps" to "com.google.android.apps.maps", "gmail" to "com.google.android.gm", "instagram" to "com.instagram.android",
        "play store" to "com.android.vending", "spotify" to "com.spotify.music", "netflix" to "com.netflix.mediaclient"
    )

    /** "fb" / "ইউটিউব" -> "facebook" / "youtube"; anything else is returned trimmed. */
    fun canonical(name: String): String = builtinAliases[normalize(name)] ?: name.trim()

    /**
     * Order: your own alias -> exact package name -> well-known package -> exact label (spaces ignored) -> label starts with ->
     * label contains. Several equally good matches are reported as [Resolution.Ambiguous] rather than guessed.
     * A well-known app that isn't installed is never replaced by a look-alike ("YouTube" must not open "YouTube Music").
     */
    fun resolve(query: String, installed: List<AppEntry>, userAliases: List<AppAlias>): Resolution {
        val q = normalize(query)
        if (q.isEmpty()) return Resolution.NotInstalled("")
        val byPkg = installed.associateBy { it.packageName }

        userAliases.firstOrNull { normalize(it.alias) == q }?.let { a ->
            return byPkg[a.packageName]?.let { Resolution.Found(it) } ?: Resolution.NotInstalled(query.trim())
        }
        if ('.' in q && ' ' !in q) byPkg.entries.firstOrNull { it.key.equals(q, true) }?.let { return Resolution.Found(it.value) }

        val canon = builtinAliases[q] ?: q
        knownPackages[canon]?.let { pkg -> byPkg[pkg]?.let { return Resolution.Found(it) } }

        val cq = compact(canon)
        if (cq.isEmpty()) return Resolution.NotInstalled(query.trim())
        installed.firstOrNull { it.label.equals(canon, true) }?.let { return Resolution.Found(it) }
        installed.firstOrNull { compact(it.label) == cq }?.let { return Resolution.Found(it) }
        if (canon in knownPackages || cq.length < 2) return Resolution.NotInstalled(query.trim())

        val candidates = installed.filter { compact(it.label).startsWith(cq) }
            .ifEmpty { installed.filter { compact(it.label).contains(cq) } }
        return when (candidates.size) {
            0 -> Resolution.NotInstalled(query.trim())
            1 -> Resolution.Found(candidates[0])
            else -> Resolution.Ambiguous(candidates.sortedBy { it.label.length }.take(5))
        }
    }
}

/** Android Settings screens Nonea can open (only the standard, public Settings actions; the mapping to intents is in AppLauncher). */
object SettingsPages {
    private val synonyms: Map<String, String> = mapOf(
        "" to "", "settings" to "", "main" to "",
        "wifi" to "wifi", "wlan" to "wifi",
        "bluetooth" to "bluetooth", "bt" to "bluetooth",
        "display" to "display", "screen" to "display", "brightness" to "display",
        "sound" to "sound", "sounds" to "sound", "volume" to "volume",
        "battery" to "battery", "batterysaver" to "battery",
        "location" to "location", "gps" to "location",
        "accessibility" to "accessibility",
        "language" to "language", "languages" to "language",
        "storage" to "storage", "privacy" to "privacy", "security" to "security",
        "airplane" to "airplane", "airplanemode" to "airplane", "flightmode" to "airplane",
        "apps" to "apps", "applications" to "apps", "allapps" to "allapps", "defaultapps" to "defaultapps",
        "date" to "date", "time" to "date", "datetime" to "date", "dateandtime" to "date",
        "about" to "about", "aboutphone" to "about",
        "keyboard" to "keyboard", "network" to "network", "networks" to "network",
        "mobiledata" to "mobiledata", "mobilenetwork" to "mobiledata", "cellular" to "mobiledata",
        "cast" to "cast", "dnd" to "dnd", "donotdisturb" to "dnd",
        "developer" to "developer", "developeroptions" to "developer",
        "nonea" to "nonea"
    )

    /** Canonical page key ("" = main Settings), or null when this isn't a page Nonea knows. */
    fun normalize(name: String): String? = synonyms[AppNames.compact(name)]

    /** Keys of every page, for help text. */
    val keys: Set<String> get() = synonyms.values.filter { it.isNotEmpty() }.toSet()
}
