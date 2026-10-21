package com.nonea.ai.ai

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

object SharedHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)   // also the max silence between streamed chunks (thinking models can pause)
            .build()                              // deliberately no logging interceptor: bodies/headers hold secrets
    }
}

/**
 * Where requests go and how they authenticate. The same Gemini wire format is used either way:
 *  - direct: Google's API with the user's own key in the x-goog-api-key header (never in the URL)
 *  - proxy:  your server, authenticated with a bearer token; the server holds the real Gemini key
 */
class GeminiEndpoint private constructor(
    val baseUrl: String,
    private val headers: Map<String, String>,
    val viaProxy: Boolean
) {
    fun authenticate(b: Request.Builder): Request.Builder { headers.forEach { (k, v) -> b.header(k, v) }; return b }
    override fun toString() = "GeminiEndpoint(baseUrl=$baseUrl, viaProxy=$viaProxy)"   // never prints headers

    companion object {
        const val DIRECT_BASE = "https://generativelanguage.googleapis.com/v1beta"
        fun direct(apiKey: String) = GeminiEndpoint(DIRECT_BASE, mapOf("x-goog-api-key" to apiKey.trim()), false)
        fun proxy(baseUrl: String, token: String): GeminiEndpoint {
            val h = if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${token.trim()}")
            return GeminiEndpoint(baseUrl.trimEnd('/') + "/v1beta", h, true)
        }
    }
}

private val BLOCK_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY", "OTHER")

/** An empty answer is either a safety block or a truncation (often thinking ate the output budget). */
internal fun classifyEmpty(finish: String?): AiError =
    if (finish != null && finish in BLOCK_REASONS) AiError.Blocked(finish) else AiError.EmptyResponse(finish ?: "empty")

class GeminiProvider(
    private val endpoint: GeminiEndpoint,
    private val http: OkHttpClient = SharedHttp.client
) : AiProvider {
    override val id = "gemini"
    override val displayName = "Google Gemini"

    private val jsonType = "application/json".toMediaType()

    // ------------------------------------------------------------------ public API

    override suspend fun generate(request: AiRequest): AiResponse {
        val opened = open(buildPost(request, stream = false), request.model)
        val body = opened.response.use { r -> withContext(Dispatchers.IO) { r.body?.string().orEmpty() } }
        val root = parseJson(body)
        root.optJSONObject("error")?.let { throw AiException(mapError(it.optInt("code"), body, request.model)) }
        val usage = parseUsage(root.optJSONObject("usageMetadata"))
        val cands = root.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw AiException(if (reason.isNotBlank()) AiError.Blocked(reason) else AiError.EmptyResponse("no candidates"))
        }
        val cand = cands.getJSONObject(0)
        return AiResponse(extractText(cand), usage, cand.optString("finishReason").ifBlank { null })
    }

    override fun stream(request: AiRequest): Flow<StreamEvent> = callbackFlow {
        val current = AtomicReference<Call?>(null)
        val reader = launch(Dispatchers.IO) {
            var opened: Opened? = null
            try {
                opened = open(buildPost(request, stream = true), request.model)
                current.set(opened.call)
                val src = opened.response.body?.source() ?: throw AiException(AiError.EmptyResponse("no body"))
                var usage: TokenUsage? = null
                var finish: String? = null
                while (isActive) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val obj = try { JSONObject(payload) } catch (_: JSONException) { continue }
                    obj.optJSONObject("error")?.let { throw AiException(mapError(it.optInt("code"), payload, request.model)) }
                    parseUsage(obj.optJSONObject("usageMetadata"))?.let { usage = it }
                    val cands = obj.optJSONArray("candidates")
                    if (cands == null || cands.length() == 0) {
                        val reason = obj.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                        if (reason.isNotBlank()) throw AiException(AiError.Blocked(reason))
                        continue
                    }
                    val cand = cands.getJSONObject(0)
                    cand.optString("finishReason").takeIf { it.isNotBlank() }?.let { finish = it }
                    val t = extractText(cand)
                    if (t.isNotEmpty()) trySend(StreamEvent.Delta(t))
                }
                trySend(StreamEvent.Done(usage, finish))
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                close(e)
            } catch (e: IOException) {
                close(AiException(AiError.Network(e is InterruptedIOException, Redact.text(e.message)), e))
            } catch (e: Throwable) {
                close(AiException(AiError.Unknown(Redact.text(e.message)), e))
            } finally {
                opened?.response?.close()
            }
        }
        // Cancelling the call is what unblocks a read that is waiting on the socket.
        awaitClose { current.get()?.cancel(); reader.cancel() }
    }.buffer(Channel.UNLIMITED)   // never drop chunks if the UI is slow

    override suspend fun testConnection(model: String): ConnectionResult {
        val id = GeminiModels.sanitizeId(model)
            ?: return ConnectionResult(false, model = model, error = AiError.InvalidConfig("Invalid model ID"))
        return try {
            // Step 1 - metadata: verifies key + model access, costs no quota, returns the token limits.
            val info = fetchModel(id)
            // Step 2 - one tiny generation: catches quota / permission problems metadata can't.
            val t0 = SystemClock.elapsedRealtime()
            generate(AiRequest(id, "Reply with one word.", emptyList(), "ping",
                GenerationParams(temperature = 0f, maxOutputTokens = 64, thinking = "low")))
            ConnectionResult(true, SystemClock.elapsedRealtime() - t0, id, modelInfo = info)
        } catch (e: AiException) {
            ConnectionResult(false, model = id, error = e.error)
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        val out = ArrayList<ModelInfo>()
        var token: String? = null
        repeat(3) {
            val url = "${endpoint.baseUrl}/models?pageSize=100" + (token?.let { "&pageToken=$it" } ?: "")
            val root = getJson(url, "")
            val arr = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val id = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                val canGenerate = (0 until (methods?.length() ?: 0)).any { methods!!.optString(it) == "generateContent" }
                if (!canGenerate || !GeminiModels.isChatModel(id)) continue
                out += modelInfoFrom(m, id)
            }
            token = root.optString("nextPageToken").ifBlank { null }
            if (token == null) return sortModels(out)
        }
        return sortModels(out)
    }

    // ------------------------------------------------------------------ requests

    private class Opened(val response: Response, val call: Call)

    private fun sortModels(l: List<ModelInfo>) =
        l.distinctBy { it.id }.sortedWith(compareBy<ModelInfo> { it.tier.ordinal }.thenByDescending { it.id })

    private fun modelInfoFrom(m: JSONObject, id: String) = ModelInfo(
        id = id,
        displayName = m.optString("displayName").ifBlank { id },
        tier = GeminiModels.tier(id),
        inputLimit = m.optInt("inputTokenLimit"),
        outputLimit = m.optInt("outputTokenLimit")
    )

    private suspend fun fetchModel(id: String): ModelInfo {
        val m = getJson("${endpoint.baseUrl}/models/$id", id)
        return modelInfoFrom(m, id)
    }

    private suspend fun getJson(url: String, model: String): JSONObject {
        val req = try {
            endpoint.authenticate(Request.Builder().url(url).get()).build()
        } catch (e: IllegalArgumentException) {
            throw AiException(AiError.InvalidConfig("Invalid address or credentials format"), e)
        }
        val opened = open(req, model)
        val body = opened.response.use { r -> withContext(Dispatchers.IO) { r.body?.string().orEmpty() } }
        return parseJson(body)
    }

    private fun buildPost(r: AiRequest, stream: Boolean): Request {
        val model = GeminiModels.sanitizeId(r.model) ?: throw AiException(AiError.InvalidConfig("Invalid model ID"))
        val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        return try {
            val b = Request.Builder()
                .url("${endpoint.baseUrl}/models/$model:$action")
                .post(buildBody(r, model).toString().toRequestBody(jsonType))
            endpoint.authenticate(b).build()
        } catch (e: IllegalArgumentException) {
            // OkHttp rejects malformed URLs and header values (e.g. a pasted key with a newline or odd character).
            throw AiException(AiError.InvalidConfig("Invalid address or credentials format"), e)
        }
    }

    private fun buildBody(r: AiRequest, model: String): JSONObject {
        // Gemini wants non-empty parts; consecutive same-role turns (e.g. after a failed send) are merged.
        val turns = ArrayList<Turn>()
        for (t in r.history + Turn(Role.USER, r.user)) {
            if (t.text.isBlank()) continue
            val last = turns.lastOrNull()
            if (last != null && last.role == t.role) turns[turns.size - 1] = Turn(t.role, last.text + "\n" + t.text) else turns.add(t)
        }
        val contents = JSONArray()
        for (t in turns) {
            contents.put(JSONObject().put("role", if (t.role == Role.USER) "user" else "model").put("parts", parts(t.text)))
        }
        val p = r.params
        val gen = JSONObject()
            .put("temperature", r2(p.temperature)).put("topP", r2(p.topP)).put("maxOutputTokens", p.maxOutputTokens)
        if (p.topK > 0) gen.put("topK", p.topK)
        if (GeminiModels.supportsThinkingLevel(model) && (p.thinking == "low" || p.thinking == "high")) {
            gen.put("thinkingConfig", JSONObject().put("thinkingLevel", p.thinking))
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", parts(r.system)))
            .put("contents", contents)
            .put("generationConfig", gen)
    }

    private fun parts(text: String) = JSONArray().put(JSONObject().put("text", text))
    private fun r2(v: Float) = round(v * 100.0) / 100.0

    /**
     * Opens the connection and returns once headers arrive. Transient failures (network blips, 429 with a
     * short retry delay, 5xx) are retried a couple of times before the error reaches the caller.
     */
    private suspend fun open(req: Request, model: String): Opened {
        var attempt = 0
        while (true) {
            val call = http.newCall(req)
            val resp = try {
                call.await()
            } catch (e: IOException) {
                if (attempt < MAX_RETRIES) { attempt++; delay(backoffMs(attempt)); continue }
                throw AiException(AiError.Network(e is InterruptedIOException, Redact.text(e.message)), e)
            }
            if (resp.isSuccessful) return Opened(resp, call)

            val retryAfterHeader = resp.header("Retry-After")?.trim()?.toIntOrNull()
            val bodyText = resp.use { r -> withContext(Dispatchers.IO) { r.body?.string().orEmpty() } }
            var err = mapError(resp.code, bodyText, model)
            if (err is AiError.RateLimited && err.retryAfterSec == null && retryAfterHeader != null) {
                err = err.copy(retryAfterSec = retryAfterHeader)
            }
            val waitSec = (err as? AiError.RateLimited)?.retryAfterSec
            val worthWaiting = err !is AiError.RateLimited || (waitSec ?: 2) <= MAX_AUTO_WAIT_SEC
            if (attempt >= MAX_RETRIES || !err.retryable || !worthWaiting) throw AiException(err)
            attempt++
            delay(waitSec?.let { it * 1000L } ?: backoffMs(attempt))
        }
    }

    private fun backoffMs(attempt: Int) = min(8_000L, 800L * (1L shl attempt))

    // ------------------------------------------------------------------ parsing

    private fun parseJson(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw AiException(AiError.Unknown("Unreadable response from server"), e)
    }

    private fun parseUsage(u: JSONObject?): TokenUsage? = u?.let {
        TokenUsage(it.optInt("promptTokenCount"), it.optInt("candidatesTokenCount"),
            it.optInt("thoughtsTokenCount"), it.optInt("totalTokenCount"))
    }

    private fun extractText(cand: JSONObject): String {
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue   // never show thought summaries
            sb.append(p.optString("text"))
        }
        return sb.toString()
    }

    private fun parseDelaySec(s: String): Int? = s.trim().removeSuffix("s").toDoubleOrNull()?.let { ceil(it).toInt() }

    /** Maps an HTTP status + Google error body to a typed error. All text is redacted. */
    private fun mapError(code: Int, body: String, model: String): AiError {
        val err = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val message = Redact.text(err?.optString("message")?.takeIf { it.isNotBlank() } ?: body.take(200).ifBlank { "HTTP $code" })
        val status = err?.optString("status").orEmpty()
        val realCode = if (code > 0) code else err?.optInt("code") ?: 0
        var reason = ""
        var retryDelay: Int? = null
        err?.optJSONArray("details")?.let { d ->
            for (i in 0 until d.length()) {
                val o = d.optJSONObject(i) ?: continue
                o.optString("reason").takeIf { it.isNotBlank() }?.let { reason = it }
                o.optString("retryDelay").takeIf { it.isNotBlank() }?.let { retryDelay = parseDelaySec(it) }
            }
        }
        val rd = retryDelay
        val lower = message.lowercase()
        val vp = endpoint.viaProxy
        return when {
            reason == "API_KEY_INVALID" || lower.contains("api key not valid") || lower.contains("api key expired") ->
                AiError.InvalidKey(vp, message.take(120))
            realCode == 401 -> AiError.InvalidKey(vp, message.take(120))
            realCode == 403 ->
                if (lower.contains("model") && !lower.contains("api key")) AiError.ModelUnavailable(model, message.take(160))
                else AiError.InvalidKey(vp, message.take(120))
            realCode == 404 || status == "NOT_FOUND" -> AiError.ModelUnavailable(model, message.take(160))
            realCode == 429 || status == "RESOURCE_EXHAUSTED" ->
                AiError.RateLimited(rd, quotaExhausted = lower.contains("quota") && (rd == null || rd > 90))
            realCode == 400 -> when {
                lower.contains("api key") -> AiError.InvalidKey(vp, message.take(120))
                lower.contains("is not supported for generatecontent") || lower.contains("models/") && lower.contains("not found") ->
                    AiError.ModelUnavailable(model, message.take(160))
                else -> AiError.BadRequest(message.take(200))
            }
            realCode in 500..599 -> AiError.Server(realCode, message.take(160))
            else -> AiError.Unknown("HTTP $realCode: ${message.take(160)}")
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val MAX_AUTO_WAIT_SEC = 10
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { this@await.cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
