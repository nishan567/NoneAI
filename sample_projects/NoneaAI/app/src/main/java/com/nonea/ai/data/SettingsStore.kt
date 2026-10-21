package com.nonea.ai.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.nonea.ai.BuildConfig
import com.nonea.ai.ai.GeminiModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart

private val Context.ds by preferencesDataStore("nonea_settings")

object AiMode {
    const val DIRECT = "direct"   // user's own Gemini key, stored encrypted on this phone (personal use)
    const val PROXY = "proxy"     // your backend holds the Gemini key; the app only holds an optional access token
}

data class Settings(
    val apiKey: String = "",             // lives in SecureStore, never in DataStore
    val model: String = GeminiModels.DEFAULT,
    val language: String = "bn-BD",      // speech-recognition locale: bn-BD | en-US | auto (best effort)
    val speak: Boolean = true,
    val replyLang: String = "auto",      // auto | bn | banglish | en
    val ttsLang: String = "auto",        // auto | bn-BD | en-US
    val ttsRate: Float = 1.0f,           // 0.5 .. 1.5
    val ttsPitch: Float = 1.12f,         // 0.8 .. 1.5
    val ttsVoiceBn: String = "",         // TextToSpeech voice name, "" = engine default
    val ttsVoiceEn: String = "",
    val theme: String = "system",        // system | dark | light
    val uiLang: String = "auto",         // auto | en | bn  (interface language)
    // ---- AI brain ----
    val provider: String = "gemini",
    val aiMode: String = BuildConfig.DEFAULT_AI_MODE,
    val proxyUrl: String = BuildConfig.PROXY_URL,
    val proxyToken: String = "",         // lives in SecureStore
    val directKeyAck: Boolean = false,   // user ticked "I understand the risks"
    val temperature: Float = 1.0f,       // 0 .. 2
    val topP: Float = 0.95f,             // 0 .. 1
    val topK: Int = 0,                   // 0 = API default
    val maxOutputTokens: Int = 2048,     // 256 .. 8192
    val thinking: String = "low",        // auto | low | high (Gemini 3+)
    val contextBudget: Int = 8000,       // max tokens of chat history sent per message
    val streaming: Boolean = true,
    // ---- voice assistant ----
    val voiceInput: String = "tap",      // tap | push (hold to talk)
    val continuous: Boolean = false,     // hands-free: listen again after each reply (app open only)
    val continuousMaxIdle: Int = 2,      // end hands-free after this many silent turns (1..5)
    val sttOffline: String = "auto",     // auto | prefer | only
    val wakeEnabled: Boolean = false,    // experimental in-app wake phrase (NOT a true wake-word engine)
    val wakeAck: Boolean = false,        // user accepted the wake-phrase caveats
    val wakePhrase: String = "hey nonea"
) {
    /** Release builds can switch direct-key entry off entirely (-Pnonea.allowDirectKey=false). */
    val mode: String get() = if (!BuildConfig.ALLOW_DIRECT_KEY) AiMode.PROXY else aiMode
}

class SettingsStore(private val ctx: Context) {
    private val secure = SecureStore.get(ctx)

    private val kLegacyKey = stringPreferencesKey("api_key")      // pre-v2.1 plain-text key, migrated then deleted
    private val kModel = stringPreferencesKey("model")
    private val kModelMigrated = booleanPreferencesKey("model_default_migrated")
    private val kLang = stringPreferencesKey("lang")
    private val kSpeak = booleanPreferencesKey("speak")
    private val kReply = stringPreferencesKey("reply_lang")
    private val kTtsLang = stringPreferencesKey("tts_lang")
    private val kRate = floatPreferencesKey("tts_rate")
    private val kPitch = floatPreferencesKey("tts_pitch")
    private val kVoiceBn = stringPreferencesKey("tts_voice_bn")
    private val kVoiceEn = stringPreferencesKey("tts_voice_en")
    private val kTheme = stringPreferencesKey("theme")
    private val kUiLang = stringPreferencesKey("ui_lang")
    private val kProvider = stringPreferencesKey("ai_provider")
    private val kAiMode = stringPreferencesKey("ai_mode")
    private val kProxyUrl = stringPreferencesKey("proxy_url")
    private val kAck = booleanPreferencesKey("direct_key_ack")
    private val kTemp = floatPreferencesKey("gen_temperature")
    private val kTopP = floatPreferencesKey("gen_top_p")
    private val kTopK = intPreferencesKey("gen_top_k")
    private val kMaxTok = intPreferencesKey("gen_max_tokens")
    private val kThinking = stringPreferencesKey("gen_thinking")
    private val kBudget = intPreferencesKey("ctx_budget")
    private val kStream = booleanPreferencesKey("streaming")
    private val kVoiceInput = stringPreferencesKey("voice_input")
    private val kContinuous = booleanPreferencesKey("voice_continuous")
    private val kContIdle = intPreferencesKey("voice_continuous_idle")
    private val kSttOffline = stringPreferencesKey("stt_offline")
    private val kWake = booleanPreferencesKey("wake_enabled")
    private val kWakeAck = booleanPreferencesKey("wake_ack")
    private val kWakePhrase = stringPreferencesKey("wake_phrase")

    val flow: Flow<Settings> = combine(ctx.ds.data, secure.geminiKey, secure.proxyToken) { p, key, token ->
        val d = Settings()
        Settings(
            apiKey = key, model = p[kModel] ?: d.model, language = p[kLang] ?: d.language, speak = p[kSpeak] ?: d.speak,
            replyLang = p[kReply] ?: d.replyLang, ttsLang = p[kTtsLang] ?: d.ttsLang,
            ttsRate = p[kRate] ?: d.ttsRate, ttsPitch = p[kPitch] ?: d.ttsPitch,
            ttsVoiceBn = p[kVoiceBn] ?: d.ttsVoiceBn, ttsVoiceEn = p[kVoiceEn] ?: d.ttsVoiceEn,
            theme = p[kTheme] ?: d.theme, uiLang = p[kUiLang] ?: d.uiLang,
            provider = p[kProvider] ?: d.provider, aiMode = p[kAiMode] ?: d.aiMode,
            proxyUrl = p[kProxyUrl] ?: d.proxyUrl, proxyToken = token, directKeyAck = p[kAck] ?: d.directKeyAck,
            temperature = p[kTemp] ?: d.temperature, topP = p[kTopP] ?: d.topP, topK = p[kTopK] ?: d.topK,
            maxOutputTokens = p[kMaxTok] ?: d.maxOutputTokens, thinking = p[kThinking] ?: d.thinking,
            contextBudget = p[kBudget] ?: d.contextBudget, streaming = p[kStream] ?: d.streaming,
            voiceInput = p[kVoiceInput] ?: d.voiceInput, continuous = p[kContinuous] ?: d.continuous,
            continuousMaxIdle = p[kContIdle] ?: d.continuousMaxIdle, sttOffline = p[kSttOffline] ?: d.sttOffline,
            wakeEnabled = (p[kWake] ?: d.wakeEnabled) && (p[kWakeAck] ?: d.wakeAck), wakeAck = p[kWakeAck] ?: d.wakeAck,
            wakePhrase = p[kWakePhrase] ?: d.wakePhrase
        )
    }.onStart { migrateLegacy() }

    /**
     * 1) Moves a plain-text key from older versions into the encrypted store and deletes the old copy.
     * 2) The old default model (gemini-2.5-flash) is restricted for newer API accounts: switch to the
     *    current default once, so people who never picked a model aren't stuck on a failing one.
     */
    private suspend fun migrateLegacy() {
        val p = ctx.ds.data.first()
        val legacy = p[kLegacyKey]
        if (!legacy.isNullOrBlank() && secure.geminiKey.value.isBlank()) secure.setGeminiKey(legacy.filterNot { it.isWhitespace() })
        val needModel = p[kModelMigrated] != true
        if (legacy != null || needModel) {
            ctx.ds.edit {
                it.remove(kLegacyKey)
                if (needModel) {
                    if ((it[kModel] ?: "") in GeminiModels.oldDefaults) it[kModel] = GeminiModels.DEFAULT
                    it[kModelMigrated] = true
                }
            }
        }
    }

    suspend fun save(s: Settings) {
        // Secrets go to the encrypted store; whitespace/newlines from pasting are stripped (keys contain none).
        secure.setGeminiKey(s.apiKey.filterNot { it.isWhitespace() })
        secure.setProxyToken(s.proxyToken.filterNot { it.isWhitespace() })
        ctx.ds.edit {
            it[kModel] = GeminiModels.sanitizeId(s.model) ?: GeminiModels.DEFAULT
            it[kLang] = if (s.language in setOf("bn-BD", "en-US", "auto")) s.language else "bn-BD"; it[kSpeak] = s.speak
            it[kReply] = s.replyLang; it[kTtsLang] = s.ttsLang
            it[kRate] = s.ttsRate.coerceIn(0.5f, 1.5f); it[kPitch] = s.ttsPitch.coerceIn(0.8f, 1.5f)
            it[kVoiceBn] = s.ttsVoiceBn; it[kVoiceEn] = s.ttsVoiceEn
            it[kTheme] = s.theme; it[kUiLang] = s.uiLang
            it[kProvider] = s.provider
            it[kAiMode] = if (s.aiMode == AiMode.PROXY) AiMode.PROXY else AiMode.DIRECT
            it[kProxyUrl] = s.proxyUrl.trim(); it[kAck] = s.directKeyAck
            it[kTemp] = s.temperature.coerceIn(0f, 2f); it[kTopP] = s.topP.coerceIn(0f, 1f)
            it[kTopK] = s.topK.coerceIn(0, 100); it[kMaxTok] = s.maxOutputTokens.coerceIn(256, 8192)
            it[kThinking] = if (s.thinking in setOf("auto", "low", "high")) s.thinking else "low"
            it[kBudget] = s.contextBudget.coerceIn(1000, 32000); it[kStream] = s.streaming
            it[kVoiceInput] = if (s.voiceInput == "push") "push" else "tap"
            it[kContinuous] = s.continuous; it[kContIdle] = s.continuousMaxIdle.coerceIn(1, 5)
            it[kSttOffline] = if (s.sttOffline in setOf("auto", "prefer", "only")) s.sttOffline else "auto"
            it[kWakeAck] = s.wakeAck; it[kWake] = s.wakeEnabled && s.wakeAck
            it[kWakePhrase] = s.wakePhrase.trim().take(40).ifBlank { "hey nonea" }
        }
    }
}
