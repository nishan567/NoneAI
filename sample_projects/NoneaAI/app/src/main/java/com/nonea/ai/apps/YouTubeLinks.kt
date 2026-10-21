package com.nonea.ai.apps

import java.net.URLEncoder

/**
 * Pure-Kotlin YouTube link handling (no Android classes, so it is unit-tested on the JVM).
 *
 * Nonea never passes a link from the user or from Gemini straight to Android. A link is parsed, the video / playlist id is
 * validated, and a canonical https://www.youtube.com/... link is rebuilt from that id. Anything that doesn't parse is refused.
 * (music.youtube.com is deliberately not handled: it belongs to a different app.)
 */
object YouTubeLinks {
    const val PACKAGE = "com.google.android.youtube"

    enum class Kind { VIDEO, SHORT, PLAYLIST }

    data class Target(val kind: Kind, val id: String, val startSeconds: Int? = null) {
        /** Canonical link rebuilt from the validated id. */
        val url: String
            get() = when (kind) {
                Kind.VIDEO -> "https://www.youtube.com/watch?v=$id" + (startSeconds?.let { "&t=${it}s" } ?: "")
                Kind.SHORT -> "https://www.youtube.com/shorts/$id"
                Kind.PLAYLIST -> "https://www.youtube.com/playlist?list=$id"
            }
    }

    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val listId = Regex("^[A-Za-z0-9_-]{10,64}$")
    private val link = Regex("(?i)(?<![A-Za-z0-9.@/=_-])(?:https?://)?((?:www\\.|m\\.)?youtube\\.com|youtu\\.be)(/[^\\s]*)")
    private val trailing = charArrayOf('.', ',', ';', ':', '!', ')', '"', '\'', '”', '’')

    /** The first youtube.com / youtu.be link with a path inside [text], or null. Case is preserved (ids are case-sensitive). */
    fun findUrl(text: String): String? = link.find(text)?.value?.trimEnd(*trailing)

    fun parse(text: String): Target? {
        val m = link.find(text) ?: return null
        val host = m.groupValues[1].lowercase()
        val rest = m.groupValues[2].trimEnd(*trailing).substringBefore('#')
        val path = rest.substringBefore('?')
        val query = parseQuery(rest.substringAfter('?', ""))
        val seg = path.split('/').filter { it.isNotEmpty() }
        val start = (query["t"] ?: query["start"])?.let { parseStart(it) }
        fun vid(s: String?) = s?.takeIf { videoId.matches(it) }
        return when {
            host == "youtu.be" -> vid(seg.firstOrNull())?.let { Target(Kind.VIDEO, it, start) }
            seg.isEmpty() -> null
            seg[0] == "watch" -> vid(query["v"])?.let { Target(Kind.VIDEO, it, start) }
            seg[0] == "shorts" -> vid(seg.getOrNull(1))?.let { Target(Kind.SHORT, it) }
            seg[0] == "live" || seg[0] == "embed" || seg[0] == "v" -> vid(seg.getOrNull(1))?.let { Target(Kind.VIDEO, it, start) }
            seg[0] == "playlist" -> query["list"]?.takeIf { listId.matches(it) }?.let { Target(Kind.PLAYLIST, it) }
            else -> null
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotEmpty() }?.let { it[0] to it[1] } }.toMap()

    /** "90", "90s", "1m30s", "1h2m3s" -> seconds (1..24h), otherwise null. */
    fun parseStart(s: String): Int? {
        s.toIntOrNull()?.let { return it.takeIf { n -> n in 1..86400 } }
        val m = Regex("^(?:(\\d{1,2})h)?(?:(\\d{1,2})m)?(?:(\\d{1,3})s)?$", RegexOption.IGNORE_CASE).find(s) ?: return null
        val (h, mi, se) = m.destructured
        val total = (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
        return total.takeIf { it in 1..86400 }
    }

    /** The YouTube results page for [query] (never auto-plays: the first result can be an ad). */
    fun searchUrl(query: String): String =
        "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query.trim().take(200), "UTF-8").replace("+", "%20")
}
