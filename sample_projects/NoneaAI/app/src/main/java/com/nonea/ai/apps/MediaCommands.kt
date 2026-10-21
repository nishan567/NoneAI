package com.nonea.ai.apps

/**
 * Spoken / typed media commands, as the argument of the `media|<arg>` action.
 * They are sent as standard Android media-button events to whichever app currently owns media playback; Nonea cannot pick a
 * player, read what is playing or seek. Anchored so ordinary sentences ("pause the meeting notes") still go to Gemini.
 */
object MediaCommands {
    val ARGS = setOf("play", "pause", "toggle", "next", "previous", "stop")

    private const val NOUN = "(?:the\\s+|this\\s+|current\\s+)?(?:music|song|songs|track|video|playback|media|audio|podcast)"
    private val pause = Regex("^(?:please\\s+)?pause(?:\\s+$NOUN)?$")
    private val resume = Regex("^(?:please\\s+)?(?:resume|continue)(?:\\s+$NOUN)?$|^(?:please\\s+)?play(?:\\s+$NOUN)?$")
    private val next = Regex("^(?:please\\s+)?(?:play\\s+)?(?:next|skip)\\s+$NOUN$")
    private val prev = Regex("^(?:please\\s+)?(?:play\\s+)?(?:previous|prev)\\s+$NOUN$")
    private val stop = Regex("^(?:please\\s+)?stop\\s+$NOUN$")
    private val toggle = Regex("^play\\s*/\\s*pause$|^toggle\\s+(?:play|playback|music|video)$")

    private val bnPause = Regex("^(?:গান|গানটা|মিউজিক|ভিডিও)\\s+(?:থামাও|থামান|বন্ধ করো|বন্ধ কর)$|^(?:gan|gaan|music|video)\\s+(?:thamao|thamo|bondho koro|bondho kor)$")
    private val bnPlay = Regex("^(?:গান|গানটা|মিউজিক|ভিডিও)\\s+(?:চালাও|বাজাও|চালু করো)$|^(?:gan|gaan|music|video)\\s+(?:chalao|cholao|bajao|chalu koro)$")
    private val bnNext = Regex("^পরের\\s+(?:গান|ভিডিও)$|^porer\\s+(?:gan|gaan)$")
    private val bnPrev = Regex("^আগের\\s+(?:গান|ভিডিও)$|^ager\\s+(?:gan|gaan)$")

    /** [t] is already lower-cased and trimmed. A bare "stop" is NOT here: that is the emergency stop. */
    fun parse(t: String): String? = when {
        toggle.matches(t) -> "toggle"
        pause.matches(t) || bnPause.matches(t) -> "pause"
        resume.matches(t) || bnPlay.matches(t) -> "play"
        next.matches(t) || bnNext.matches(t) -> "next"
        prev.matches(t) || bnPrev.matches(t) -> "previous"
        stop.matches(t) -> "stop"
        else -> null
    }
}
