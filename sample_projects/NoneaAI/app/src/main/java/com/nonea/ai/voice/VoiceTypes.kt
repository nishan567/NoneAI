package com.nonea.ai.voice

/** Everything that can go wrong with voice, as data. The UI turns these into localized text. */
sealed interface VoiceError {
    data object MicNeeded : VoiceError              // permission not granted yet (can still be asked)
    data object MicBlocked : VoiceError             // permission denied for good: only system Settings can fix it
    data object RecognizerUnavailable : VoiceError  // no speech-recognition service installed/enabled
    data object OfflineOnlyUnsupported : VoiceError // "offline only" chosen but no on-device recognizer (needs Android 12+)
    data object OfflineUnavailable : VoiceError     // offline-only recognition failed (language pack missing / engine unreachable)
    data object LanguageUnavailable : VoiceError    // recognizer can't do this language right now
    data object Network : VoiceError
    data object NoSpeech : VoiceError
    data object NoMatch : VoiceError
    data object Busy : VoiceError
    data object TooManyRequests : VoiceError
    data object Audio : VoiceError
    data class Generic(val code: Int) : VoiceError
    data object TtsUnavailable : VoiceError
    data class TtsLanguageMissing(val lang: String) : VoiceError   // "bn" | "en"
    data class HandsFreeEnded(val quietTurns: Int) : VoiceError
    data object WakeStopped : VoiceError
    data object HoldHint : VoiceError
}

/** One stretch of text to be spoken in one language ("bn" or "en"). */
data class Segment(val text: String, val lang: String)

object Lang {
    fun isBengali(c: Char) = c in '\u0980'..'\u09FF'
    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /**
     * "bn", "en", or null when there are no letters to judge by. This is script-based, so it is reliable for
     * Bengali script vs. Latin script but cannot tell Banglish (Bengali written in Latin letters) from English.
     */
    fun of(text: String): String? {
        var bn = 0; var en = 0
        for (c in text) if (isBengali(c)) bn++ else if (isLatin(c)) en++
        return when { bn == 0 && en == 0 -> null; bn >= en -> "bn"; else -> "en" }
    }

    fun tagFor(lang: String) = if (lang == "bn") "bn-BD" else "en-US"

    /** "bn-BD" -> "bn", "en_US" -> "en", anything else (incl. "auto") -> null. */
    fun fromTag(tag: String?): String? {
        val t = tag?.lowercase() ?: return null
        return when { t.startsWith("bn") -> "bn"; t.startsWith("en") -> "en"; else -> null }
    }
}

/** Turns a Gemini reply into something worth reading aloud, split by language. */
object Speakable {
    private const val MAX = 1200   // well under TextToSpeech.getMaxSpeechInputLength() (4000)
    private val SENTENCE = Regex("(?<=[.!?।॥])\\s+|\\n+")
    private val CODE = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val LINK = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
    private val URL = Regex("https?://\\S+")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val LIST_MARK = Regex("(?m)^\\s{0,3}(#{1,6}|[-*+>•]|\\d+[.)])\\s+")
    private val MD_CHARS = Regex("[*_#`>~|]")
    private val EMOJI = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]")

    fun clean(raw: String): String {
        val codeNote = if (Lang.of(raw.replace(CODE, " ")) == "bn") "কোডটি স্ক্রিনে আছে।" else "The code is on your screen."
        return raw.replace(CODE, " $codeNote ")
            .replace(LINK, "$1").replace(URL, " ").replace(INLINE_CODE, "$1")
            .replace(LIST_MARK, "").replace(MD_CHARS, "").replace(EMOJI, "")
            .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{2,}"), "\n").trim()
    }

    /**
     * [forcedTag] is Settings.ttsLang: "bn-BD" / "en-US" force one voice, "auto" picks per sentence by script.
     * Banglish (Latin letters) is read with the English voice; the engine cannot tell it apart from English.
     */
    fun segments(raw: String, forcedTag: String): List<Segment> {
        val text = clean(raw)
        if (text.isBlank()) return emptyList()
        val forced = Lang.fromTag(forcedTag)
        val fallback = forced ?: Lang.of(text) ?: "en"
        val out = ArrayList<Segment>()
        var prev: String? = null
        for (sentence in text.split(SENTENCE).map { it.trim() }) {
            if (sentence.none { it.isLetterOrDigit() }) continue
            val lang = forced ?: Lang.of(sentence) ?: prev ?: fallback
            prev = lang
            for (piece in atWords(sentence, MAX)) {
                val last = out.lastOrNull()
                if (last != null && last.lang == lang && last.text.length + piece.length < MAX)
                    out[out.lastIndex] = last.copy(text = last.text + " " + piece)
                else out.add(Segment(piece, lang))
            }
        }
        return out
    }

    private fun atWords(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val res = ArrayList<String>(); var cur = StringBuilder()
        for (w in s.split(' ')) {
            if (cur.isNotEmpty() && cur.length + w.length + 1 > max) { res.add(cur.toString()); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) res.add(cur.toString())
        return res
    }
}

/**
 * Matches the user's wake phrase in a transcript. This is plain text matching on what the speech recogniser
 * returned (tolerating a one-letter slip in longer words); it is NOT an acoustic wake-word engine.
 */
object WakePhrase {
    private val NOT_WORD = Regex("[^\\p{L}\\p{M}\\p{N}\\s]")   // \p{M}: Bengali vowel signs must survive

    private fun words(s: String) = s.lowercase().replace(NOT_WORD, " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** The text after the phrase ("" if the phrase was the whole utterance), or null if the phrase wasn't heard. */
    fun match(heard: String, phrase: String): String? {
        val p = words(phrase)
        val h = words(heard)
        if (p.isEmpty() || h.size < p.size) return null
        for (i in 0..h.size - p.size) {
            if (p.indices.all { close(h[i + it], p[it]) }) return h.drop(i + p.size).joinToString(" ")
        }
        return null
    }

    private fun close(a: String, b: String) = a == b || (b.length >= 5 && lev(a, b) <= 1)

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}
