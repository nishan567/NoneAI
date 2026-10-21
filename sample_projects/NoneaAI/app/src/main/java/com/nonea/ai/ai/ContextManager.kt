package com.nonea.ai.ai

import kotlin.math.ceil
import kotlin.math.min

/** Cheap offline token estimate. Bengali script costs far more tokens per character than Latin text. */
object TokenEstimator {
    fun estimate(text: String): Int {
        var w = 0.0
        for (ch in text) {
            w += when {
                ch.code < 0x80 -> 0.28
                ch.code in 0x0980..0x09FF -> 0.55
                else -> 0.4
            }
        }
        return ceil(w).toInt() + 4   // small per-message overhead
    }
}

data class TrimResult(val turns: List<Turn>, val dropped: Int, val estPromptTokens: Int)

/** Keeps the newest part of the conversation that fits the token budget. */
object ContextManager {
    fun trim(
        history: List<Turn>, system: String, user: String,
        budgetTokens: Int, modelInputLimit: Int, reserveOutput: Int, calibration: Double = 1.0
    ): TrimResult {
        val hardCap = (modelInputLimit - reserveOutput - 512).coerceAtLeast(2_000)
        val cap = min(budgetTokens, hardCap)
        fun scaled(raw: Int) = (raw * calibration).toInt() + 1

        val fixedRaw = TokenEstimator.estimate(system) + TokenEstimator.estimate(user)
        var remaining = cap - scaled(fixedRaw)
        var rawTotal = fixedRaw
        val kept = ArrayDeque<Turn>()
        var i = history.size - 1
        while (i >= 0) {
            val raw = TokenEstimator.estimate(history[i].text)
            val cost = scaled(raw)
            if (cost > remaining) break          // stop at the first turn that doesn't fit: keep it contiguous
            remaining -= cost
            rawTotal += raw
            kept.addFirst(history[i])
            i--
        }
        var dropped = history.size - kept.size
        while (kept.isNotEmpty() && kept.first().role != Role.USER) {
            rawTotal -= TokenEstimator.estimate(kept.first().text)
            kept.removeFirst(); dropped++
        }
        return TrimResult(kept.toList(), dropped, rawTotal)
    }
}
