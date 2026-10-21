package com.nonea.ai.ai

import kotlinx.coroutines.flow.Flow

/** Provider-neutral types. Nothing in here knows about Gemini's wire format. */

enum class Role { USER, ASSISTANT }

data class Turn(val role: Role, val text: String)

data class GenerationParams(
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 0,               // 0 = let the API decide
    val maxOutputTokens: Int = 2048,
    val thinking: String = "low"     // "auto" | "low" | "high" (only sent to models that support it)
)

data class AiRequest(
    val model: String,
    val system: String,
    val history: List<Turn>,
    val user: String,
    val params: GenerationParams = GenerationParams()
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val thinkingTokens: Int = 0,
    val totalTokens: Int = 0
) {
    /** Thinking tokens are billed like output tokens. */
    val billedOutput: Int get() = outputTokens + thinkingTokens
}

data class AiResponse(val text: String, val usage: TokenUsage?, val finishReason: String?)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Done(val usage: TokenUsage?, val finishReason: String?) : StreamEvent
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val tier: Tier = Tier.CURRENT,
    val inputLimit: Int = 0,   // 0 = unknown
    val outputLimit: Int = 0
) {
    enum class Tier { CURRENT, LEGACY, RETIRED }
}

data class ConnectionResult(
    val ok: Boolean,
    val latencyMs: Long = 0,
    val model: String = "",
    val error: AiError? = null,
    val modelInfo: ModelInfo? = null
)

/**
 * Everything the app needs from an AI backend. Add another provider by implementing this and
 * registering an [AiProviderFactory] in [AiService]. Implementations throw [AiException].
 */
interface AiProvider {
    val id: String
    val displayName: String
    val supportsStreaming: Boolean get() = true
    suspend fun generate(request: AiRequest): AiResponse
    fun stream(request: AiRequest): Flow<StreamEvent>
    suspend fun testConnection(model: String): ConnectionResult
    suspend fun listModels(): List<ModelInfo> = emptyList()
}

/** Every `detail` string below must already be passed through [Redact.text]. */
sealed class AiError {
    open val retryable: Boolean get() = false

    object NotConfigured : AiError()
    data class InvalidConfig(val detail: String) : AiError()
    data class InvalidKey(val viaProxy: Boolean, val detail: String = "") : AiError()
    data class RateLimited(val retryAfterSec: Int?, val quotaExhausted: Boolean) : AiError() {
        override val retryable: Boolean get() = !quotaExhausted
    }
    data class Network(val timeout: Boolean, val detail: String) : AiError() {
        override val retryable: Boolean get() = true
    }
    data class ModelUnavailable(val model: String, val detail: String) : AiError()
    data class Blocked(val reason: String) : AiError()
    data class BadRequest(val detail: String) : AiError()
    data class Server(val code: Int, val detail: String) : AiError() {
        override val retryable: Boolean get() = code == 500 || code == 502 || code == 503 || code == 504
    }
    data class EmptyResponse(val reason: String) : AiError()
    data class Unknown(val detail: String) : AiError()
}

class AiException(val error: AiError, cause: Throwable? = null) : Exception(error.toString(), cause)
