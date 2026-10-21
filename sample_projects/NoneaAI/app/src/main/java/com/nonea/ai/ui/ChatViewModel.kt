package com.nonea.ai.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nonea.ai.actions.*
import com.nonea.ai.apps.AppAlias
import com.nonea.ai.apps.AppControlConfig
import com.nonea.ai.apps.AppControlStore
import com.nonea.ai.apps.AppEntry
import com.nonea.ai.apps.Problem
import com.nonea.ai.apps.Shortcut
import com.nonea.ai.automation.AutomationGuard
import com.nonea.ai.automation.AutomationPolicy
import com.nonea.ai.ai.AiConfig
import com.nonea.ai.ai.AiError
import com.nonea.ai.ai.AiException
import com.nonea.ai.ai.AiService
import com.nonea.ai.ai.ConnState
import com.nonea.ai.ai.GeminiModels
import com.nonea.ai.ai.MemoryParser
import com.nonea.ai.ai.ModelInfo
import com.nonea.ai.ai.Persona
import com.nonea.ai.ai.Redact
import com.nonea.ai.ai.RememberCommand
import com.nonea.ai.ai.Role
import com.nonea.ai.ai.StreamSanitizer
import com.nonea.ai.ai.Turn
import com.nonea.ai.data.*
import com.nonea.ai.voice.Lang
import com.nonea.ai.voice.SpeechManager
import com.nonea.ai.voice.TtsManager
import com.nonea.ai.voice.VoiceError
import com.nonea.ai.voice.WakePhrase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val listening: Boolean = false, val thinking: Boolean = false, val speaking: Boolean = false,
    val partial: String = "", val error: String? = null,
    /** Typed Gemini failure; localized at display time so it follows the in-app language. */
    val aiError: AiError? = null,
    val interrupted: Boolean = false,
    /** Typed voice problem (mic permission, recogniser, TTS ...); localized at display time. */
    val voiceError: VoiceError? = null,
    /** Hands-free conversation loop is active. */
    val handsFree: Boolean = false,
    /** The experimental in-app wake-phrase listener is running. */
    val wake: Boolean = false
)

/** Where a listening turn came from. Push-to-talk keeps the mic open while the button is held. */
private enum class Src { Tap, Push, Loop, Wake }

/** Model list shown in Settings: curated entries, plus whatever the live "refresh" returned. */
data class ModelsUi(
    val models: List<ModelInfo> = GeminiModels.curated,
    val loading: Boolean = false, val fetched: Boolean = false, val error: AiError? = null
)

/** A phone action in flight (running = true) or just finished (result = what the executor reported). */
data class TaskState(val action: Action, val running: Boolean, val result: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()
    private val convDao = db.conversations()
    private val memDao = db.memories()
    private val store = SettingsStore(app)
    private val usageStore = UsageStore(app)
    private val ai = AiService(usageStore)
    private val appStore = AppControlStore(app)
    /** The user's own app names and voice commands (section 7). */
    val appConfig = appStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppControlConfig())
    private val exec = ActionExecutor(app) { appConfig.value.aliases }
    private val tts = TtsManager(app)
    private val stt = SpeechManager(app)
    private val wakeStt = SpeechManager(app)

    /** 0 = a new chat that has no row yet (created on the first message). */
    val currentConv = MutableStateFlow(0L)
    val conversations = convDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val messages = currentConv.flatMapLatest { dao.forConv(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val memories = memDao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    // ---- Gemini status / usage / models ----
    val aiStatus: StateFlow<ConnState> = ai.status
    val usage = usageStore.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsageStats())
    private val _models = MutableStateFlow(ModelsUi())
    val models = _models.asStateFlow()
    /** Text of the reply being streamed (control tags hidden); empty when nothing is streaming. */
    private val _stream = MutableStateFlow("")
    val streamText = _stream.asStateFlow()

    /** Tests with the settings currently on screen (even if unsaved). */
    fun testConnection(draft: Settings) {
        if (ai.status.value is ConnState.Testing) return
        viewModelScope.launch { ai.test(draft) }
    }

    fun refreshModels(draft: Settings) {
        if (_models.value.loading) return
        _models.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            ai.refreshModels(draft).fold(
                onSuccess = { live ->
                    val ids = live.map { it.id }.toSet()
                    val extra = GeminiModels.curated.filter { it.id !in ids }.filter { it.tier != ModelInfo.Tier.CURRENT }
                    _models.value = ModelsUi(models = if (live.isEmpty()) GeminiModels.curated else live + extra, fetched = true)
                },
                onFailure = { e ->
                    val err = (e as? AiException)?.error ?: AiError.Unknown(Redact.text(e.message))
                    _models.update { it.copy(loading = false, error = err) }
                }
            )
        }
    }

    fun resetUsage() { viewModelScope.launch { usageStore.reset() } }

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()
    /** Microphone loudness 0..1 while listening; kept separate so only the orb recomposes on it. */
    private val _level = MutableStateFlow(0f)
    val level = _level.asStateFlow()
    private val _task = MutableStateFlow<TaskState?>(null)
    val task = _task.asStateFlow()
    private var taskJob: Job? = null

    /** Memory proposals from Gemini waiting for the user's approval. */
    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending = _pending.asStateFlow()

    // ---- voice state (all touched on the main thread) ----
    private val _foreground = MutableStateFlow(true)
    private val _micGranted = MutableStateFlow(false)
    private val _wakeBlocked = MutableStateFlow(false)
    private var loopActive = false          // hands-free loop
    private var quietTurns = 0
    private var pttHeld = false
    private var pttSince = 0L
    private val pttBuffer = StringBuilder() // text of earlier recogniser runs while the push-to-talk button is still held
    private var retriedOtherLang = false
    private var lastLang: String? = null    // recognition language used by "auto"; adapts to what the user speaks
    private var resumeAfterSpeech = false
    private var resumeJob: Job? = null
    private var watchdog: Job? = null
    private var wakeActive = false
    private var wakeFails = 0
    private var wakeJob: Job? = null

    init {
        tts.onSpeaking = { sp -> _ui.update { it.copy(speaking = sp) } }
        tts.onFinished = { completed -> if (completed && resumeAfterSpeech && loopActive) { resumeAfterSpeech = false; scheduleResume() } }
        tts.onProblem = { e -> showVoiceError(e) }
        viewModelScope.launch { convDao.latest()?.let { currentConv.value = it.id } }
        // The wake phrase listener may only run while the app is on screen, the mic is allowed and nothing else is going on.
        viewModelScope.launch {
            combine(_ui, settings, _foreground, _micGranted, _wakeBlocked) { u, s, fg, mic, blocked ->
                s.wakeEnabled && s.wakeAck && fg && mic && !blocked && !u.listening && !u.thinking && !u.speaking && !u.handsFree
            }.distinctUntilChanged().collectLatest { want ->
                if (want) { delay(700); startWake() } else stopWake()
            }
        }
    }

    fun saveSettings(s: Settings) = viewModelScope.launch { store.save(s); _wakeBlocked.value = false }

    // ---- conversations ----
    fun newChat() { cancelVoice(); _pending.value = emptyList(); currentConv.value = 0L }
    fun openChat(id: Long) { cancelVoice(); _pending.value = emptyList(); currentConv.value = id }
    fun deleteChat(id: Long) = viewModelScope.launch {
        dao.deleteConv(id); convDao.delete(id)
        if (currentConv.value == id) currentConv.value = 0L
    }
    fun clearHistory() = viewModelScope.launch { dao.clear(); convDao.clear(); currentConv.value = 0L }

    // ---- memory (only user-approved items are ever stored) ----
    fun approveMemory(text: String) { _pending.update { it - text }; viewModelScope.launch { addMemory(text) } }
    fun dismissMemory(text: String) { _pending.update { it - text } }
    fun deleteMemory(id: Long) = viewModelScope.launch { memDao.delete(id) }
    fun clearMemories() = viewModelScope.launch { memDao.clear() }

    private suspend fun addMemory(text: String): Boolean {
        val t = text.trim().take(200)
        if (t.isBlank()) return false
        val all = memDao.list()
        if (all.any { it.text.equals(t, ignoreCase = true) }) return false
        if (all.size >= 50) memDao.delete(all.last().id) // keep the list (and prompt) small
        memDao.insert(MemoryEntity(text = t))
        return true
    }

    // ================================================================ voice assistant
    fun voiceNames(lang: String) = tts.voiceNames(lang)
    fun previewVoice(s: Settings, bengali: Boolean) { cancelVoice(); _ui.update { it.copy(speaking = true) }; tts.preview(s, bengali) }

    /** Reads an earlier message aloud again (history replay). Works even when "speak replies" is off. */
    fun replay(text: String) {
        if (_ui.value.listening || _ui.value.thinking) return
        resumeAfterSpeech = false; resumeJob?.cancel()
        _ui.update { it.copy(speaking = true, voiceError = null) }
        tts.speak(text.replace("⚙", ""), settings.value)
    }

    // ---- permissions & lifecycle (the UI reports them; the VM never asks the OS itself) ----
    fun setMicGranted(granted: Boolean) {
        _micGranted.value = granted
        if (granted) _ui.update { if (it.voiceError == VoiceError.MicNeeded || it.voiceError == VoiceError.MicBlocked) it.copy(voiceError = null) else it }
    }

    /** [permanent] = Android will no longer show the permission dialog; only system Settings can fix it. */
    fun onMicDenied(permanent: Boolean) {
        _micGranted.value = false
        _ui.update { it.copy(voiceError = if (permanent) VoiceError.MicBlocked else VoiceError.MicNeeded) }
    }

    fun onForeground() { _foreground.value = true; _wakeBlocked.value = false }

    /** Nonea never listens or talks from the background: leaving the screen ends everything. */
    fun onBackground() { _foreground.value = false; cancelVoice() }

    // ---- tap-to-talk / push-to-talk ----
    /** Tap: start listening; tap again while listening: stop and send what was heard; tap while speaking: interrupt and listen. */
    fun tapMic() {
        val u = _ui.value
        when {
            u.thinking -> Unit
            u.listening -> requestFinish()
            else -> {
                silenceTts(); pttHeld = false; pttBuffer.setLength(0)
                setLoop(settings.value.continuous); quietTurns = 0
                listen(Src.Tap)
            }
        }
    }

    fun pttPress() {
        val u = _ui.value
        if (u.thinking || pttHeld) return
        silenceTts()
        if (u.listening) stt.cancel()
        setLoop(false)
        pttHeld = true; pttSince = SystemClock.elapsedRealtime(); pttBuffer.setLength(0)
        listen(Src.Push)
    }

    fun pttRelease() {
        if (!pttHeld) return
        pttHeld = false
        if (SystemClock.elapsedRealtime() - pttSince < 300 && pttBuffer.isEmpty()) { // an accidental tap, not a hold
            cancelListening(); showVoiceError(VoiceError.HoldHint); return
        }
        if (_ui.value.listening) requestFinish()
    }

    /** Ask the recogniser to wrap up; if it never answers, send what we have after 4 s. */
    private fun requestFinish() {
        stt.finish()
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            delay(4000)
            if (!_ui.value.listening) return@launch
            val heard = _ui.value.partial.trim()
            cancelListening()
            if (heard.isNotBlank()) send(heard, fromVoice = true) else showVoiceError(VoiceError.NoSpeech)
        }
    }

    // ---- stop controls ----
    /** "Stop speaking". In hands-free mode Nonea then listens again, like talking over someone. */
    fun interruptSpeaking() {
        silenceTts()
        if (loopActive && !_ui.value.listening && !_ui.value.thinking) scheduleResume(200)
    }

    /** "Stop listening": discards the current turn and ends hands-free. */
    fun stopListening() { cancelListening(); setLoop(false); resumeJob?.cancel() }

    /** Stops everything voice: listening, speaking, hands-free. */
    fun cancelVoice() {
        setLoop(false); resumeAfterSpeech = false; resumeJob?.cancel()
        cancelListening(); tts.stop(); _ui.update { it.copy(speaking = false) }
    }

    private fun silenceTts() { resumeAfterSpeech = false; resumeJob?.cancel(); tts.stop(); _ui.update { it.copy(speaking = false) } }

    private fun cancelListening() {
        watchdog?.cancel(); stt.cancel(); pttHeld = false; pttBuffer.setLength(0); endListening()
    }

    private fun endListening() { _level.value = 0f; _ui.update { it.copy(listening = false, partial = "") } }
    private fun setLoop(on: Boolean) { loopActive = on; _ui.update { it.copy(handsFree = on) } }
    private fun showVoiceError(e: VoiceError) { _ui.update { it.copy(voiceError = e) } }

    // ---- one listening turn ----
    private fun listen(src: Src) {
        if (!_micGranted.value) { setLoop(false); onMicDenied(false); return }
        resumeJob?.cancel(); resumeAfterSpeech = false
        tts.stop(); stopWake()
        retriedOtherLang = false
        _ui.update { it.copy(listening = true, speaking = false, partial = pttBuffer.toString().trim(), error = null, voiceError = null) }
        recognize(src)
    }

    private fun defaultLang(s: Settings) = if (s.uiLang == "en") "en-US" else "bn-BD"
    private fun otherLang(l: String) = if (l.startsWith("bn")) "en-US" else "bn-BD"

    private fun recognize(src: Src) {
        val s = settings.value
        val auto = s.language == "auto"
        val lang = if (auto) (lastLang ?: defaultLang(s)) else s.language
        stt.start(lang, s.sttOffline, if (auto) listOf("bn-BD", "en-US") else null, longSilence = src == Src.Push,
            SpeechManager.Callbacks(
                onPartial = { p -> _ui.update { it.copy(partial = (pttBuffer.toString() + p).trim()) } },
                onLevel = { _level.value = it },
                onFinal = { t, det -> onHeard(t, det, src) },
                onError = { e -> onVoiceError(e, src) }))
    }

    private fun onHeard(text: String, detected: String?, src: Src) {
        watchdog?.cancel()
        if (settings.value.language == "auto") (Lang.fromTag(detected) ?: Lang.of(text))?.let { lastLang = Lang.tagFor(it) }
        if (src == Src.Push && pttHeld) {            // still holding: keep the mic open, join the pieces
            pttBuffer.append(text).append(' ')
            _ui.update { it.copy(partial = pttBuffer.toString().trim()) }
            recognize(Src.Push)
            return
        }
        val full = (pttBuffer.toString() + text).trim()
        pttBuffer.setLength(0)
        endListening(); quietTurns = 0
        send(full, fromVoice = true)
    }

    private fun onVoiceError(e: VoiceError, src: Src) {
        watchdog?.cancel()
        val silence = e == VoiceError.NoSpeech || e == VoiceError.NoMatch
        if (src == Src.Push && silence) {
            if (pttHeld) {                           // finger still down, nothing said yet: keep the mic open
                viewModelScope.launch { delay(150); if (pttHeld && _ui.value.listening) recognize(Src.Push) }
                return
            }
            val buffered = pttBuffer.toString().trim()
            if (buffered.isNotEmpty()) { pttBuffer.setLength(0); endListening(); send(buffered, fromVoice = true); return }
        }
        val s = settings.value
        if (e == VoiceError.NoMatch && s.language == "auto" && !retriedOtherLang) {   // wrong language? try the other once
            retriedOtherLang = true
            lastLang = otherLang(lastLang ?: defaultLang(s))
            recognize(src); return
        }
        if (loopActive && silence) {                 // hands-free: stay patient for a few quiet turns
            quietTurns++
            if (quietTurns < s.continuousMaxIdle) {
                viewModelScope.launch { delay(300); if (loopActive && _ui.value.listening) recognize(src) }
                return
            }
            val n = quietTurns
            cancelListening(); setLoop(false); quietTurns = 0
            showVoiceError(VoiceError.HandsFreeEnded(n)); return
        }
        cancelListening(); setLoop(false); quietTurns = 0
        showVoiceError(e)
    }

    /** After a reply (or an interruption) in hands-free mode: listen again once the room has gone quiet. */
    private fun scheduleResume(delayMs: Long = 450) {
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch {
            delay(delayMs)
            val u = _ui.value
            if (loopActive && _foreground.value && !u.thinking && !u.speaking && !u.listening) listen(Src.Loop)
        }
    }

    // ---- experimental wake phrase (in-app only; see README: this is NOT a real wake-word engine) ----
    private fun startWake() {
        if (wakeActive) return
        wakeActive = true; wakeFails = 0
        _ui.update { it.copy(wake = true) }
        wakeListen()
    }

    private fun wakeListen() {
        val s = settings.value
        val phrase = s.wakePhrase
        val lang = if (Lang.of(phrase) == "bn") "bn-BD" else "en-US"
        wakeStt.start(lang, s.sttOffline, null, false, SpeechManager.Callbacks(
            onFinal = { t, _ -> wakeFails = 0; onWakeHeard(t, phrase) },
            onError = { e -> onWakeError(e) }))
    }

    private fun onWakeHeard(heard: String, phrase: String) {
        val rest = WakePhrase.match(heard, phrase)
        if (rest == null) { wakeRestart(250); return }
        stopWake()                                   // hand the microphone over
        if (rest.isNotBlank()) send(rest, fromVoice = true)
        else { quietTurns = 0; setLoop(settings.value.continuous); listen(Src.Wake) }
    }

    private fun onWakeError(e: VoiceError) {
        when (e) {
            VoiceError.NoSpeech, VoiceError.NoMatch -> wakeRestart(300)
            VoiceError.MicNeeded -> { stopWake(); _micGranted.value = false; showVoiceError(VoiceError.MicNeeded) }
            else -> {
                wakeFails++
                if (wakeFails >= 4) { stopWake(); _wakeBlocked.value = true; showVoiceError(VoiceError.WakeStopped) }
                else wakeRestart(1000L * wakeFails)
            }
        }
    }

    private fun wakeRestart(ms: Long) {
        wakeJob?.cancel()
        wakeJob = viewModelScope.launch { delay(ms); if (wakeActive) wakeListen() }
    }

    private fun stopWake() {
        wakeJob?.cancel(); wakeStt.cancel()
        if (wakeActive) { wakeActive = false; _ui.update { it.copy(wake = false) } }
    }

    fun dismissError() { _ui.update { it.copy(error = null, aiError = null, interrupted = false, voiceError = null) } }

    // ---- phone tasks with live status ----
    private suspend fun runWithStatus(a: Action): String {
        if (a.type == "show_apps") { _showApps.value = true; return "Here are your apps." }
        taskJob?.cancel()
        _task.value = TaskState(a, running = true)
        delay(350) // let the status pill show before the system UI takes over
        val r = exec.execute(a)
        _task.value = TaskState(a, running = false, result = r)
        taskJob = viewModelScope.launch { delay(4500); _task.value = null }
        return r
    }

    /** Runs a quick action (no chat message); progress and result show in the task indicator. */
    fun runAction(a: Action) { viewModelScope.launch { runWithStatus(a) } }

    suspend fun apps(): List<AppEntry> = withContext(Dispatchers.IO) { exec.installedApps() }
    fun appIcon(pkg: String) = exec.appIcon(pkg)

    // ---- app launcher, aliases and custom voice commands (section 7) ----
    private val _showApps = MutableStateFlow(false)
    /** Set by the "show my apps" command; the chat screen opens the launcher and calls [consumeShowApps]. */
    val showApps = _showApps.asStateFlow()
    fun consumeShowApps() { _showApps.value = false }

    suspend fun saveAlias(alias: String, pkg: String): Problem? = appStore.upsertAlias(AppAlias(alias.trim(), pkg))
    fun removeAlias(alias: String) { viewModelScope.launch { appStore.removeAlias(alias) } }
    suspend fun saveShortcut(s: Shortcut): Problem? = appStore.upsertShortcut(s)
    fun removeShortcut(phrase: String) { viewModelScope.launch { appStore.removeShortcut(phrase) } }

    /** Asks for suggestions grounded in earlier conversations and approved memories. */
    fun suggest() = send("💡 Looking at our earlier chats and what I've asked you to remember, what could I do or work on right now? Up to 3 short suggestions.")

    /** Emergency stop: cancels the running step, denies any open question and switches automation off. */
    fun emergencyStop() {
        AutomationGuard.emergencyStop()
        taskJob?.cancel()
        _task.value = TaskState(Action("stop_automation"), running = false, result = "Stopped. Automation is off.")
        taskJob = viewModelScope.launch { delay(4500); _task.value = null }
    }

    fun answerConfirm(id: Long, yes: Boolean) = AutomationGuard.answer(id, yes)

    fun send(text: String, fromVoice: Boolean = false) {
        val msg = text.trim()
        // "stop" must work even while a step is running or waiting for a yes/no.
        if (StopCommand.matches(msg) && (AutomationGuard.busy.value > 0 || AutomationGuard.pending.value != null)) { emergencyStop(); return }
        if (msg.isEmpty() || _ui.value.thinking) return
        viewModelScope.launch {
            val s = settings.value
            var conv = currentConv.value
            if (conv == 0L) {
                conv = convDao.insert(ConversationEntity(title = msg.lineSequence().first().take(40)))
                currentConv.value = conv
            }
            // Up to 60 recent messages; AiService trims to the token budget from Settings.
            val history = dao.recentInConv(conv, 60).reversed()
                .map { Turn(if (it.role == "user") Role.USER else Role.ASSISTANT, it.text) }
            dao.insert(MessageEntity(role = "user", text = msg, convId = conv, voice = fromVoice))
            convDao.touch(conv, System.currentTimeMillis())
            _ui.update { it.copy(thinking = true, error = null, aiError = null, interrupted = false) }

            val remember = RememberCommand.find(msg)
            val local = if (remember == null) LocalRouter.parse(msg, appConfig.value.shortcuts) else null
            var aiError: AiError? = null
            var interrupted = false
            val reply: String? = when {
                remember != null ->
                    if (addMemory(remember)) "Okay, saved: “$remember”. You can delete it anytime in Settings."
                    else "That's already saved, or I couldn't save it."
                local != null -> runWithStatus(local)
                !AiConfig.isConfigured(s) -> { aiError = AiError.NotConfigured; null }
                else -> {
                    val mems = memDao.list().take(30).map { it.text }
                    val topics = dao.recentUserElsewhere(conv, 15).map { it.text.take(120) }
                    _stream.value = ""
                    val res = ai.chat(s, Persona.build(s.replyLang, mems, topics), history, msg) { full ->
                        _stream.value = StreamSanitizer.visible(full)
                    }
                    _stream.value = ""
                    res.fold(
                        onSuccess = { r ->
                            interrupted = r.interrupted
                            val (noMem, proposals) = MemoryParser.extract(r.text)
                            val (clean, actions) = ActionParser.extract(noMem)
                            val known = memories.value.map { it.text.lowercase() }
                            val fresh = proposals.filter { it.lowercase() !in known }
                            if (fresh.isNotEmpty()) _pending.update { (it + fresh).distinct().take(3) }
                            // At most a few screen-control steps per reply, so a runaway reply can't chain dozens of taps.
                            var autoSteps = 0
                            val results = actions.map {
                                if (it.type in AutomationExecutor.TYPES && it.type != "stop_automation" && ++autoSteps > AutomationPolicy.MAX_AUTOMATION_PER_REPLY)
                                    "Skipped extra screen steps (limit ${AutomationPolicy.MAX_AUTOMATION_PER_REPLY} per request)."
                                else runWithStatus(it)
                            }
                            listOf(clean).plus(results.map { "⚙ $it" }).filter { it.isNotBlank() }.joinToString("\n\n")
                        },
                        onFailure = { e ->
                            aiError = (e as? AiException)?.error ?: AiError.Unknown(Redact.text(e.message))
                            null
                        }
                    )
                }
            }
            // Failures are shown in the error banner, not stored as chat messages, so they never pollute the context.
            if (!reply.isNullOrBlank()) {
                dao.insert(MessageEntity(role = "assistant", text = reply, convId = conv))
                convDao.touch(conv, System.currentTimeMillis())
            }
            // Spoken replies only while Nonea is on screen. "speaking" is switched on right away (TTS may take a moment
            // to start) so the wake listener and hands-free loop never see a gap; TtsManager clears it again.
            val spoke = s.speak && !reply.isNullOrBlank() && _foreground.value
            if (aiError != null) setLoop(false)
            _ui.update { it.copy(thinking = false, aiError = aiError, interrupted = interrupted, speaking = if (spoke) true else it.speaking) }
            if (spoke) { resumeAfterSpeech = loopActive; tts.speak(reply.orEmpty().replace("⚙", ""), s) }
            else if (loopActive) scheduleResume()
        }
    }

    override fun onCleared() { tts.shutdown(); stt.cancel(); wakeStt.cancel() }
}
