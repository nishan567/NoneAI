package com.nonea.ai.ai

/**
 * Curated Gemini model list. Google retires models regularly, so this is only a starting point:
 * Settings has a "refresh" button that pulls the live list from the API (models.list).
 * Checked against Google's deprecation page on 2026-10-21.
 */
object GeminiModels {
    const val DEFAULT = "gemini-3.8-flash"

    val curated = listOf(
        ModelInfo("gemini-3.8-flash", "Gemini 3.8 Flash"),
        ModelInfo("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
        ModelInfo("gemini-3.1-pro-preview", "Gemini 3.1 Pro (preview)"),
        ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", ModelInfo.Tier.LEGACY),
        ModelInfo("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", ModelInfo.Tier.LEGACY),
        ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", ModelInfo.Tier.LEGACY)
    )

    /** Old app defaults that new API accounts may no longer be able to use. */
    val oldDefaults = setOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")

    private val retiredPrefixes = listOf("gemini-1.", "gemini-2.0", "gemini-3-pro-preview", "gemini-3.1-flash-lite-preview")
    private val notForChat = listOf(
        "tts", "image", "live", "embedding", "audio", "transcribe", "robotics",
        "computer-use", "omni", "imagen", "veo", "lyria", "aqa", "learnlm", "gemma", "translate"
    )
    private val idPattern = Regex("[A-Za-z0-9._-]{1,80}")
    private const val DEFAULT_INPUT_LIMIT = 1_000_000

    fun tier(id: String): ModelInfo.Tier = when {
        retiredPrefixes.any { id.startsWith(it) } -> ModelInfo.Tier.RETIRED
        id.startsWith("gemini-2.5") -> ModelInfo.Tier.LEGACY
        else -> ModelInfo.Tier.CURRENT
    }

    /** `thinkingLevel` is a Gemini 3+ parameter; older models use a different knob we don't send. */
    fun supportsThinkingLevel(id: String) = id.startsWith("gemini-3")

    /** Accepts "models/x" or "x"; returns null if it isn't a plain model ID (keeps URLs safe). */
    fun sanitizeId(raw: String): String? {
        val id = raw.trim().removePrefix("models/")
        return id.takeIf { idPattern.matches(it) }
    }

    fun isChatModel(id: String): Boolean = id.startsWith("gemini-") && notForChat.none { id.contains(it) }

    fun inputLimit(id: String, known: ModelInfo?): Int = known?.inputLimit?.takeIf { it > 0 } ?: DEFAULT_INPUT_LIMIT
}
