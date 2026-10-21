package com.nonea.ai.ai

/** Hides [[ACTION:..]] / [[MEMORY:..]] control tags (even half-streamed ones) from the live reply. */
object StreamSanitizer {
    private val complete = Regex("\\[\\[(?:ACTION|MEMORY):[^\\]]*\\]\\]")

    fun visible(raw: String): String {
        var t = complete.replace(raw, "")
        val open = t.lastIndexOf("[[")
        if (open >= 0 && !t.substring(open).contains("]]")) t = t.substring(0, open)
        else if (t.endsWith("[")) t = t.dropLast(1)
        return t.trimEnd()
    }
}
