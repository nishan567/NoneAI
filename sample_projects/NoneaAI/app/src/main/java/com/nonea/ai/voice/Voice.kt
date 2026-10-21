package com.nonea.ai.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nonea.ai.data.Settings
import java.util.Locale

/**
 * Wraps Android's SpeechRecognizer. One instance = one recogniser at a time. All calls and callbacks are on the
 * main thread. Every start() gets a session number so late callbacks from a cancelled recogniser are ignored.
 */
class SpeechManager(private val ctx: Context) {
    class Callbacks(
        val onFinal: (text: String, detectedLang: String?) -> Unit,
        val onError: (VoiceError) -> Unit,
        val onPartial: (String) -> Unit = {},
        val onLevel: (Float) -> Unit = {}
    )

    companion object {
        fun recognitionAvailable(ctx: Context): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)
        fun onDeviceAvailable(ctx: Context): Boolean =
            Build.VERSION.SDK_INT >= 31 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var session = 0

    /**
     * [offline]: "auto" (online if reachable), "prefer" (EXTRA_PREFER_OFFLINE) or "only" (on-device recogniser, Android 12+).
     * [detectAmong]: when not null, asks engines that support it (Android 13+, some only) to pick among these languages.
     * [longSilence]: hint for push-to-talk so a pause doesn't end the turn; engines may ignore it.
     */
    fun start(lang: String, offline: String, detectAmong: List<String>?, longSilence: Boolean, cb: Callbacks) {
        cancel()
        val id = session
        val r = create(offline)
        if (r == null) {
            cb.onError(if (offline == "only") VoiceError.OfflineOnlyUnsupported else VoiceError.RecognizerUnavailable)
            return
        }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onRmsChanged(rms: Float) { if (id == session) cb.onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onPartialResults(b: Bundle?) {
                if (id != session) return
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(cb.onPartial)
            }
            override fun onResults(b: Bundle?) {
                if (id != session) return
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                val detected = b?.getString("detected_language")   // SpeechRecognizer.DETECTED_LANGUAGE (API 33), present only on some engines
                release(r)
                if (t.isNullOrBlank()) cb.onError(VoiceError.NoMatch) else cb.onFinal(t, detected)
            }
            override fun onError(error: Int) {
                if (id != session) return
                release(r)
                cb.onError(map(error, offline))
            }
        })
        try {
            r.startListening(intent(lang, offline, detectAmong, longSilence))
        } catch (_: SecurityException) {
            release(r); cb.onError(VoiceError.MicNeeded)
        } catch (_: Exception) {
            release(r); cb.onError(VoiceError.Generic(-1))
        }
    }

    /** Stop capturing and deliver what was heard so far (onFinal, or onError if nothing). */
    fun finish() { rec?.let { runCatching { it.stopListening() } } }

    /** Drop everything; no callback follows. */
    fun cancel() {
        session++
        val r = rec ?: return
        rec = null
        runCatching { r.cancel() }
        main.post { runCatching { r.destroy() } }
    }

    private fun release(r: SpeechRecognizer) {
        if (rec === r) rec = null
        main.post { runCatching { r.destroy() } }
    }

    private fun create(offline: String): SpeechRecognizer? = try {
        if (offline == "only") {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else null
        } else if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx) else null
    } catch (_: Exception) { null }

    private fun intent(lang: String, offline: String, detectAmong: List<String>?, longSilence: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            if (offline != "auto") putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            val silence = if (longSilence) 3500L else 1800L
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            if (detectAmong != null && Build.VERSION.SDK_INT >= 33) {
                // Literal keys = RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION / ..._ALLOWED_LANGUAGES. Engines that don't know them ignore them.
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", ArrayList(detectAmong))
            }
        }

    // SpeechRecognizer.ERROR_* values; raw numbers because several were added after our minSdk (26).
    private fun map(code: Int, offline: String): VoiceError = when (code) {
        1, 2, 4, 11 -> if (offline == "only") VoiceError.OfflineUnavailable else VoiceError.Network  // net timeout / network / server / server disconnected
        3 -> VoiceError.Audio
        6 -> VoiceError.NoSpeech
        7 -> VoiceError.NoMatch
        8 -> VoiceError.Busy
        9 -> VoiceError.MicNeeded
        10 -> VoiceError.TooManyRequests
        12, 13 -> VoiceError.LanguageUnavailable                                                      // language not supported / unavailable
        else -> VoiceError.Generic(code)
    }
}

/**
 * Text-to-speech. Replies are cut into per-language segments that are spoken one after another (the language is
 * only switched between utterances, never while one is playing). All state is touched on the main thread.
 */
class TtsManager(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var initFailed = false
    private var pendingStart: (() -> Unit)? = null
    private var gen = 0
    private var idx = 0
    private val queue = ArrayDeque<Segment>()
    private var cfg = Settings()
    private var speakingNow = false
    private var reportedMissing = false

    /** true when audio starts, false when it ends/stops/fails (also false if nothing could be spoken). Main thread. */
    var onSpeaking: (Boolean) -> Unit = {}
    /** After a speak() finished: true = played to the end, false = interrupted or failed. Main thread. */
    var onFinished: (completed: Boolean) -> Unit = {}
    var onProblem: (VoiceError) -> Unit = {}

    val isReady: Boolean get() = ready

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
    private var hasFocus = false

    private val tts: TextToSpeech = TextToSpeech(ctx) { status ->
        main.post {
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                runCatching { tts.setAudioAttributes(attrs) }
                tts.setOnUtteranceProgressListener(listener)
                pendingStart?.invoke(); pendingStart = null
            } else {
                initFailed = true
                if (pendingStart != null) { pendingStart = null; finish(false); onProblem(VoiceError.TtsUnavailable) }
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        private fun current(id: String?) = id != null && id.startsWith("nonea-$gen-")
        override fun onStart(id: String?) { main.post { if (current(id) && !speakingNow) { speakingNow = true; onSpeaking(true) } } }
        override fun onDone(id: String?) { main.post { if (current(id)) next() } }
        @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { if (current(id)) fail() } }
        override fun onError(id: String?, errorCode: Int) { main.post { if (current(id)) fail() } }
        override fun onStop(id: String?, interrupted: Boolean) {}   // stop() handles its own bookkeeping via gen
    }

    /** Names of installed voices for a language code ("bn" or "en"). Empty until the engine is ready. */
    fun voiceNames(lang: String): List<String> = runCatching {
        tts.voices.orEmpty().filter { it.locale.language == lang }.map { it.name }.sorted()
    }.getOrDefault(emptyList())

    fun speak(raw: String, s: Settings) {
        val segs = Speakable.segments(raw, s.ttsLang)
        if (segs.isEmpty()) { finish(true); return }   // nothing worth reading aloud: counts as "done" so hands-free carries on
        begin(segs, s)
    }

    /** Plays a short sample using the given (possibly unsaved) settings. */
    fun preview(s: Settings, bengali: Boolean) =
        begin(listOf(Segment(if (bengali) "হ্যালো, আমি নোনিয়া। আমার গলাটা কেমন লাগছে?" else "Hi, I'm Nonea. How does this voice sound?",
            if (bengali) "bn" else "en")), s)

    fun stop() {
        gen++; queue.clear(); pendingStart = null
        runCatching { tts.stop() }
        finish(false)
    }

    fun shutdown() { stop(); runCatching { tts.shutdown() } }

    private fun begin(segs: List<Segment>, s: Settings) {
        gen++; idx = 0; reportedMissing = false
        queue.clear(); queue.addAll(segs); cfg = s
        runCatching { tts.stop() }
        if (initFailed) { finish(false); onProblem(VoiceError.TtsUnavailable); return }
        if (!ready) { val g = gen; pendingStart = { if (g == gen) { requestFocus(); next() } }; return }
        requestFocus()
        next()
    }

    private fun next() {
        val seg = queue.removeFirstOrNull()
        if (seg == null) { finish(true); return }
        if (!configure(seg.lang, cfg)) {
            if (!reportedMissing) { reportedMissing = true; onProblem(VoiceError.TtsLanguageMissing(seg.lang)) }
            next(); return    // skip what we can't voice, keep reading the rest
        }
        val r = tts.speak(seg.text, TextToSpeech.QUEUE_FLUSH, null, "nonea-$gen-${idx++}")
        if (r != TextToSpeech.SUCCESS) fail()
    }

    private fun fail() { queue.clear(); finish(false); onProblem(VoiceError.TtsUnavailable) }

    private fun finish(completed: Boolean) {
        abandonFocus()
        speakingNow = false
        onSpeaking(false)
        onFinished(completed)
    }

    private fun configure(lang: String, s: Settings): Boolean {
        val tags = if (lang == "bn") listOf("bn-BD", "bn-IN", "bn") else listOf("en-US", "en-GB", "en")
        if (tags.none { runCatching { tts.setLanguage(Locale.forLanguageTag(it)) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE }) return false
        val wanted = if (lang == "bn") s.ttsVoiceBn else s.ttsVoiceEn
        if (wanted.isNotBlank()) runCatching {
            tts.voices.orEmpty().firstOrNull { it.name == wanted && it.locale.language == lang }?.let { tts.voice = it }
        }
        tts.setPitch(s.ttsPitch)
        tts.setSpeechRate(s.ttsRate)
        return true
    }

    private fun requestFocus() { if (!hasFocus) hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
    private fun abandonFocus() { if (hasFocus) { audio.abandonAudioFocusRequest(focus); hasFocus = false } }
}
