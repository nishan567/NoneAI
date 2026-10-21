package com.nonea.ai.ai

import com.nonea.ai.BuildConfig
import com.nonea.ai.data.AiMode
import com.nonea.ai.data.Settings
import com.nonea.ai.data.UsageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

sealed interface ConnState {
    object Unknown : ConnState
    object Testing : ConnState
    data class Connected(val model: String, val latencyMs: Long, val atMs: Long) : ConnState
    data class Failed(val error: AiError, val atMs: Long) : ConnState
}

data class AiReply(val text: String, val usage: TokenUsage?, val droppedTurns: Int, val interrupted: Boolean)

object ProxyConfig {
    /** Returns a clean https base URL, or null if [raw] isn't acceptable (http, credentials in URL, garbage). */
    fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val u = s.toHttpUrlOrNull() ?: return null
        if (u.scheme != "https" || u.host.isBlank()) return null
        if (u.username.isNotEmpty() || u.password.isNotEmpty()) return null
        return u.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
    }
}

object AiConfig {
    fun isConfigured(s: Settings): Boolean =
        if (s.mode == AiMode.PROXY) ProxyConfig.normalize(s.proxyUrl) != null else s.apiKey.isNotBlank()
}

interface AiProviderFactory {
    val id: String
    /** Throws [AiException] (NotConfigured / InvalidConfig) when settings are incomplete. */
    fun create(settings: Settings): AiProvider
}

object GeminiProviderFactory : AiProviderFactory {
    override val id = "gemini"
    override fun create(settings: Settings): AiProvider {
        if (settings.mode == AiMode.PROXY) {
            val url = ProxyConfig.normalize(settings.proxyUrl)
                ?: throw AiException(AiError.InvalidConfig("Proxy address must be a valid https:// URL"))
            return GeminiProvider(GeminiEndpoint.proxy(url, settings.proxyToken))
        }
        if (!BuildConfig.ALLOW_DIRECT_KEY) throw AiException(AiError.InvalidConfig("Direct API keys are disabled in this build"))
        if (settings.apiKey.isBlank()) throw AiException(AiError.NotConfigured)
        return GeminiProvider(GeminiEndpoint.direct(settings.apiKey))
    }
}

/**
 * The single entry point the rest of the app uses for AI: picks the provider from settings, trims
 * context to the token budget, streams, tracks usage and connection status, and never leaks secrets.
 */
class AiService(private val usageStore: UsageStore) {
    private val factories: Map<String, AiProviderFactory> =
        listOf<AiProviderFactory>(GeminiProviderFactory).associateBy { it.id }

    private val _status = MutableStateFlow<ConnState>(ConnState.Unknown)
    val status: StateFlow<ConnState> = _status.asStateFlow()

    @Volatile private var calibration = 1.0                 // learned actual/estimated token ratio
    private val knownModels = ConcurrentHashMap<String, ModelInfo>()

    private fun provider(s: Settings): AiProvider {
        Redact.register(s.apiKey); Redact.register(s.proxyToken)
        val f = factories[s.provider] ?: factories.getValue("gemini")
        return f.create(s)
    }

    private fun now() = System.currentTimeMillis()

    suspend fun chat(
        s: Settings, system: String, history: List<Turn>, user: String, onText: (String) -> Unit = {}
    ): Result<AiReply> {
        val ai = try { provider(s) } catch (e: AiException) { return fail(e) }
        val model = GeminiModels.sanitizeId(s.model)
            ?: return fail(AiException(AiError.InvalidConfig("Invalid model ID")))
        val params = GenerationParams(s.temperature, s.topP, s.topK, s.maxOutputTokens, s.thinking)
        val known = knownModels[model] ?: GeminiModels.curated.firstOrNull { it.id == model }
        var maxOut = params.maxOutputTokens
        if (known != null && known.outputLimit > 0) maxOut = minOf(maxOut, known.outputLimit)
        val trimmed = ContextManager.trim(history, system, user, s.contextBudget,
            GeminiModels.inputLimit(model, known), maxOut, calibration)
        val req = AiRequest(model, system, trimmed.turns, user, params.copy(maxOutputTokens = maxOut))
        SafeLog.d(TAG, "chat model=$model turns=${trimmed.turns.size} dropped=${trimmed.dropped} ~tokens=${trimmed.estPromptTokens} stream=${s.streaming}")

        val t0 = now()
        return try {
            val sb = StringBuilder()
            var usage: TokenUsage? = null
            var finish: String? = null
            var interrupted = false
            var useFallback = !(s.streaming && ai.supportsStreaming)
            if (!useFallback) {
                try {
                    ai.stream(req).collect { ev ->
                        when (ev) {
                            is StreamEvent.Delta -> { sb.append(ev.text); onText(sb.toString()) }
                            is StreamEvent.Done -> { usage = ev.usage; finish = ev.finishReason }
                        }
                    }
                } catch (e: AiException) {
                    when {
                        sb.isNotEmpty() -> interrupted = true                       // keep what arrived
                        e.error is AiError.Network -> useFallback = true            // e.g. proxy that can't stream: retry once without
                        else -> throw e
                    }
                }
            }
            if (useFallback) {
                val r = ai.generate(req)
                sb.append(r.text); usage = r.usage; finish = r.finishReason
                onText(r.text)
            }
            val text = sb.toString().trim()
            if (text.isBlank()) throw AiException(classifyEmpty(finish))

            val u = usage
            usageStore.recordSuccess(u ?: TokenUsage(trimmed.estPromptTokens, TokenEstimator.estimate(text)))
            if (u != null && u.promptTokens > 0 && trimmed.estPromptTokens > 0) {
                val ratio = u.promptTokens.toDouble() / trimmed.estPromptTokens
                calibration = (0.7 * calibration + 0.3 * ratio).coerceIn(0.5, 2.5)
            }
            _status.value = ConnState.Connected(model, now() - t0, now())
            Result.success(AiReply(text, u, trimmed.dropped, interrupted))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            fail(e)
        } catch (e: Throwable) {
            fail(AiException(AiError.Unknown(Redact.text(e.message)), e))
        }
    }

    private suspend fun <T> fail(e: AiException): Result<T> {
        runCatching { usageStore.recordError() }
        _status.value = ConnState.Failed(e.error, now())
        SafeLog.w(TAG, "request failed: ${e.error::class.simpleName}")
        return Result.failure(e)
    }

    /** Tests with the given (possibly unsaved) settings and updates [status]. */
    suspend fun test(s: Settings): ConnectionResult {
        _status.value = ConnState.Testing
        val model = GeminiModels.sanitizeId(s.model) ?: GeminiModels.DEFAULT
        val result = try {
            provider(s).testConnection(model)
        } catch (e: CancellationException) {
            _status.value = ConnState.Unknown
            throw e
        } catch (e: AiException) {
            ConnectionResult(false, model = model, error = e.error)
        } catch (e: Throwable) {
            ConnectionResult(false, model = model, error = AiError.Unknown(Redact.text(e.message)))
        }
        result.modelInfo?.let { knownModels[it.id] = it }
        _status.value = if (result.ok) ConnState.Connected(model, result.latencyMs, now())
        else ConnState.Failed(result.error ?: AiError.Unknown("failed"), now())
        return result
    }

    suspend fun refreshModels(s: Settings): Result<List<ModelInfo>> = try {
        val list = provider(s).listModels()
        list.forEach { knownModels[it.id] = it }
        Result.success(list)
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiException) {
        Result.failure(e)
    } catch (e: Throwable) {
        Result.failure(AiException(AiError.Unknown(Redact.text(e.message)), e))
    }

    private companion object { const val TAG = "AiService" }
}
