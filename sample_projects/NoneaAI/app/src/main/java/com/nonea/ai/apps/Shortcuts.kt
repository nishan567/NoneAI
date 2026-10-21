package com.nonea.ai.apps

import com.nonea.ai.actions.Action
import com.nonea.ai.actions.StopCommand
import com.nonea.ai.automation.AutomationPolicy

/** A voice / typed command the user defined: saying [phrase] runs the action ([type], [arg]). */
data class Shortcut(val phrase: String, val type: String, val arg: String = "")

data class AppControlConfig(val aliases: List<AppAlias> = emptyList(), val shortcuts: List<Shortcut> = emptyList())

/** Why an alias or shortcut was rejected. The UI turns these into localized messages. */
enum class Problem {
    PHRASE_EMPTY, PHRASE_LONG, PHRASE_RESERVED, TYPE_NOT_ALLOWED, ARG_REQUIRED, ARG_LONG, URL_UNSAFE,
    MEDIA_UNKNOWN, PAGE_UNKNOWN, TOO_MANY, ALIAS_EMPTY, ALIAS_LONG, PACKAGE_BAD
}

/**
 * Custom voice commands. Deliberately limited to harmless "open / show / play-key" actions: a shortcut can never be a call, an SMS,
 * a screen tap, typing or anything else that acts on the phone's contents, so a mistaken or imported shortcut can't do damage.
 * "stop" / "cancel" can never be redefined, and are checked before shortcuts.
 */
object Shortcuts {
    val ALLOWED_TYPES = listOf("open_app", "settings", "youtube_search", "youtube_play", "media", "open_url", "show_apps")
    const val MAX_SHORTCUTS = 50
    const val MAX_ALIASES = 100
    private const val MAX_PHRASE = 60
    private const val MAX_ARG = 300

    fun validate(s: Shortcut): Problem? {
        val phrase = AppNames.normalize(s.phrase)
        val arg = s.arg.trim()
        return when {
            phrase.isEmpty() -> Problem.PHRASE_EMPTY
            phrase.length > MAX_PHRASE -> Problem.PHRASE_LONG
            StopCommand.matches(phrase) -> Problem.PHRASE_RESERVED
            s.type !in ALLOWED_TYPES -> Problem.TYPE_NOT_ALLOWED
            arg.length > MAX_ARG -> Problem.ARG_LONG
            s.type == "show_apps" -> null
            s.type == "settings" -> if (arg.isEmpty() || SettingsPages.normalize(arg) != null) null else Problem.PAGE_UNKNOWN
            arg.isEmpty() -> Problem.ARG_REQUIRED
            s.type == "media" -> if (arg.lowercase() in MediaCommands.ARGS) null else Problem.MEDIA_UNKNOWN
            s.type == "open_url" -> if (AutomationPolicy.isSafeWebUrl(arg)) null else Problem.URL_UNSAFE
            else -> null
        }
    }

    /** Exact (normalised) phrase match, with an optional "nonea," / "hey nonea," / "please" in front. [t] may be any case. */
    fun match(t: String, list: List<Shortcut>): Action? {
        if (list.isEmpty()) return null
        val said = strip(AppNames.normalize(t))
        if (said.isEmpty()) return null
        return list.firstOrNull { strip(AppNames.normalize(it.phrase)) == said && validate(it) == null }
            ?.let { Action(it.type, it.arg.trim()) }
    }

    private fun strip(s: String) = s.replace(Regex("^(?:(?:hey\\s+)?nonea[,\\s]+)?(?:please\\s+)?"), "").trim()

    /** Adds or replaces (same normalised phrase). */
    fun upsert(list: List<Shortcut>, s: Shortcut): List<Shortcut> {
        val clean = s.copy(phrase = s.phrase.trim(), arg = s.arg.trim())
        val key = AppNames.normalize(clean.phrase)
        return list.filterNot { AppNames.normalize(it.phrase) == key } + clean
    }
}

object AppAliases {
    private val pkgRx = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun validate(a: AppAlias): Problem? {
        val alias = AppNames.normalize(a.alias)
        return when {
            alias.isEmpty() -> Problem.ALIAS_EMPTY
            alias.length > 30 -> Problem.ALIAS_LONG
            !pkgRx.matches(a.packageName) -> Problem.PACKAGE_BAD
            else -> null
        }
    }

    fun upsert(list: List<AppAlias>, a: AppAlias): List<AppAlias> {
        val clean = a.copy(alias = a.alias.trim())
        val key = AppNames.normalize(clean.alias)
        return list.filterNot { AppNames.normalize(it.alias) == key } + clean
    }
}

/** Tiny tab / newline separated format with escaping, so aliases and shortcuts can live in one DataStore string each (and are testable on the JVM). */
object ConfigCodec {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun encode(rows: List<List<String>>) = rows.joinToString("\n") { r -> r.joinToString("\t") { esc(it) } }
    private fun decode(s: String): List<List<String>> =
        if (s.isBlank()) emptyList() else s.split("\n").filter { it.isNotBlank() }.map { line -> line.split("\t").map { unesc(it) } }

    fun encodeAliases(l: List<AppAlias>) = encode(l.map { listOf(it.alias, it.packageName) })
    fun decodeAliases(s: String): List<AppAlias> =
        decode(s).filter { it.size == 2 }.map { AppAlias(it[0], it[1]) }.filter { AppAliases.validate(it) == null }.take(Shortcuts.MAX_ALIASES)

    fun encodeShortcuts(l: List<Shortcut>) = encode(l.map { listOf(it.phrase, it.type, it.arg) })
    fun decodeShortcuts(s: String): List<Shortcut> =
        decode(s).filter { it.size == 3 }.map { Shortcut(it[0], it[1], it[2]) }.filter { Shortcuts.validate(it) == null }.take(Shortcuts.MAX_SHORTCUTS)
}
