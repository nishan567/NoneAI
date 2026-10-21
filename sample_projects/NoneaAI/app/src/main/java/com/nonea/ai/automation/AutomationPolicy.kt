package com.nonea.ai.automation

/**
 * Pure-Kotlin safety rules for screen automation (no Android classes, so they are unit-tested on the JVM).
 * The rules are deliberately conservative: when unsure, ask the user or refuse.
 */
object AutomationPolicy {

    /** Apps Nonea will never tap, type or scroll in. Opening them is still fine. */
    private val blockedPackages = setOf(
        // Android security / permission / install surfaces
        "com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.systemui",
        "com.android.vpndialogs", "com.android.certinstaller",
        // Password managers and authenticators
        "com.google.android.apps.authenticator2", "com.bitwarden.mobile", "com.x8bit.bitwarden", "com.lastpass.lpandroid",
        "com.onepassword.android", "com.agilebits.onepassword", "org.keepassdroid", "com.authy.authy", "com.azure.authenticator",
        // Wallets, payments and mobile banking
        "com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user", "com.paypal.android.p2pmobile",
        "com.phonepe.app", "net.one97.paytm", "com.bkash.customerapp", "com.konasl.nagad", "com.rocket.android",
        "com.samsung.android.spay", "com.venmo", "com.squareup.cash", "com.coinbase.android"
    )

    /** Apps where every tap needs an explicit yes (stores, where one tap can spend money or install software). */
    private val confirmEveryTap = setOf("com.android.vending")

    fun isBlockedPackage(pkg: String?, ownPackage: String): Boolean =
        pkg != null && (pkg == ownPackage || pkg in blockedPackages)

    fun needsConfirmForEveryTap(pkg: String?) = pkg != null && pkg in confirmEveryTap

    // ---- sensitive labels ----
    private val sensitive = Regex(
        "\\b(pay|payment|purchase|buy|order|checkout|check out|confirm|send|transfer|withdraw|cash ?out|donate|subscribe|" +
            "delete|remove|erase|uninstall|install|update all|allow|grant|accept|agree|authorize|authorise|approve|" +
            "sign in|log in|login|sign up|reset|factory|format|unlock|turn off|disable|enable)\\b" +
            "|পেমেন্ট|কিনুন|কিনো|ক্রয়|অর্ডার|পাঠান|পাঠাও|ট্রান্সফার|টাকা|ক্যাশ ?আউট|মুছুন|মুছে|ডিলিট|ইনস্টল|অনুমতি|সম্মত|সাবস্ক্রাইব|লগ ?ইন|সাইন ইন",
        RegexOption.IGNORE_CASE
    )

    fun isSensitiveLabel(label: String?): Boolean = !label.isNullOrBlank() && sensitive.containsMatchIn(label)

    // ---- advertisements ----
    private val adText = Regex(
        "\\b(ad|ads|advert|advertisement|advertisements|sponsored|promoted|sponsor)\\b|বিজ্ঞাপন",
        RegexOption.IGNORE_CASE
    )
    private val adId = Regex(
        "(^|[_:/.])(ad|ads|adview|admob|adchoices|banner_?ad|native_?ad|interstitial|sponsored|promoted)([_:/.]|\\b|$)",
        RegexOption.IGNORE_CASE
    )
    /** "Skip ad" / "Close ad" dismiss an ad rather than click it, but a fake one is a known trick, so these still ask. */
    private val dismissLabel = Regex("^\\s*(skip|close|dismiss|বাদ|বন্ধ)\\b", RegexOption.IGNORE_CASE)

    fun looksLikeAd(texts: List<String?>, viewIds: List<String?>): Boolean =
        texts.any { !it.isNullOrBlank() && adText.containsMatchIn(it) } ||
            viewIds.any { !it.isNullOrBlank() && adId.containsMatchIn(it) }

    fun isAdDismissLabel(label: String?) = !label.isNullOrBlank() && dismissLabel.containsMatchIn(label)

    // ---- the verdict ----
    sealed class Verdict {
        object Allow : Verdict()
        data class Confirm(val reason: String) : Verdict()
        data class Refuse(val reason: String) : Verdict()
    }

    /**
     * @param label       text or content description of the target (null for a coordinate tap with no node found)
     * @param isPassword  target is a password field
     * @param adSignals   the target or one of its ancestors looks like an advertisement
     * @param confirmAll  the user's "ask before every action" setting
     * @param byCoordinates the tap was given as raw coordinates rather than a label
     */
    fun judgeTap(
        pkg: String?, ownPackage: String, label: String?, isPassword: Boolean, adSignals: Boolean,
        confirmAll: Boolean, byCoordinates: Boolean, ambiguousMatches: Int = 1
    ): Verdict {
        if (pkg == null) return Verdict.Refuse("I can't tell which app is on screen.")
        if (isBlockedPackage(pkg, ownPackage)) return Verdict.Refuse("Nonea doesn't tap inside this app (security, payment or system screen). You can do it yourself.")
        if (isPassword) return Verdict.Refuse("That's a password field. I never fill in or tap passwords.")
        if (adSignals) {
            return if (isAdDismissLabel(label)) Verdict.Confirm("This looks like part of an ad. Tap “${label?.trim()}”?")
            else Verdict.Refuse("That looks like an advertisement, so I won't tap it. Tap it yourself if you really want it.")
        }
        if (isSensitiveLabel(label)) return Verdict.Confirm("“${label?.trim()}” may change something or spend money.")
        if (needsConfirmForEveryTap(pkg)) return Verdict.Confirm("Store apps can spend money or install software.")
        if (byCoordinates) return Verdict.Confirm("Tap at a screen position I can't identify.")
        if (ambiguousMatches > 1) return Verdict.Confirm("$ambiguousMatches things match “${label?.trim()}”. I'll tap the first one.")
        if (confirmAll) return Verdict.Confirm("You asked me to confirm every action.")
        return Verdict.Allow
    }

    fun judgeScrollOrType(pkg: String?, ownPackage: String, isPassword: Boolean = false): Verdict = when {
        pkg == null -> Verdict.Refuse("I can't tell which app is on screen.")
        isBlockedPackage(pkg, ownPackage) -> Verdict.Refuse("Nonea doesn't control this app (security, payment or system screen).")
        isPassword -> Verdict.Refuse("That's a password field. I never fill in passwords.")
        else -> Verdict.Allow
    }

    /** Only plain web links; no intent:, file:, javascript: or custom schemes from model output. */
    fun isSafeWebUrl(url: String): Boolean {
        val u = url.trim()
        return u.length <= 2000 && Regex("^https?://[^\\s]+$", RegexOption.IGNORE_CASE).matches(u)
    }

    const val MAX_AUTOMATION_PER_REPLY = 5
    const val MAX_PER_MINUTE = 20
}
