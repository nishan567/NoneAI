package com.nonea.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nonea.ai.R
import com.nonea.ai.data.Settings
import com.nonea.ai.voice.SpeechManager
import com.nonea.ai.voice.VoiceError
import kotlin.math.roundToInt

/** What the voice controls in the chat screen can do; permission and system-settings plumbing lives in ChatScreen. */
internal class VoiceActions(
    val tap: () -> Unit,              // tap-to-talk toggle (also the TalkBack path in hold-to-talk mode)
    val down: () -> Unit,             // push-to-talk: finger down
    val up: () -> Unit,               // push-to-talk: finger up
    val allow: () -> Unit,            // ask for the microphone permission
    val openAppSettings: () -> Unit,  // this app's page in system Settings
    val openTts: () -> Unit           // system text-to-speech settings
)

@Composable
internal fun VoiceError.text(): String = when (this) {
    VoiceError.MicNeeded -> str(R.string.vz_mic_needed)
    VoiceError.MicBlocked -> str(R.string.vz_mic_blocked)
    VoiceError.RecognizerUnavailable -> str(R.string.vz_unavailable)
    VoiceError.OfflineOnlyUnsupported -> str(R.string.vz_offline_only_unsupported)
    VoiceError.OfflineUnavailable -> str(R.string.vz_offline_unavailable)
    VoiceError.LanguageUnavailable -> str(R.string.vz_language_unavailable)
    VoiceError.Network -> str(R.string.vz_network)
    VoiceError.NoSpeech -> str(R.string.vz_no_speech)
    VoiceError.NoMatch -> str(R.string.vz_no_match)
    VoiceError.Busy -> str(R.string.vz_busy)
    VoiceError.TooManyRequests -> str(R.string.vz_too_many)
    VoiceError.Audio -> str(R.string.vz_audio)
    is VoiceError.Generic -> str(R.string.vz_generic, code)
    VoiceError.TtsUnavailable -> str(R.string.vz_tts_unavailable)
    is VoiceError.TtsLanguageMissing -> str(R.string.vz_tts_lang_missing, if (lang == "bn") "বাংলা" else "English")
    is VoiceError.HandsFreeEnded -> str(R.string.vz_handsfree_ended, quietTurns)
    VoiceError.WakeStopped -> str(R.string.vz_wake_stopped)
    VoiceError.HoldHint -> str(R.string.vz_hold_hint)
}

/** Stop controls: shown only while there is something to stop. */
@Composable
internal fun VoiceBar(ui: UiState, vm: ChatViewModel) {
    if (!ui.listening && !ui.speaking && !ui.handsFree) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.speaking) VoiceChip("■  " + str(R.string.voice_stop_speaking)) { vm.interruptSpeaking() }
        if (ui.listening) VoiceChip("✕  " + str(R.string.voice_stop_listening)) { vm.stopListening() }
        if (ui.handsFree) VoiceChip("⏹  " + str(R.string.voice_end_handsfree)) { vm.cancelVoice() }
    }
}

@Composable
private fun VoiceChip(label: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label, style = MaterialTheme.typography.labelLarge) }

/** Voice problem banner, with the one action that can fix it (allow mic, open Settings, TTS settings). */
@Composable
internal fun VoiceBanner(e: VoiceError, voice: VoiceActions, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(e.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        when (e) {
            VoiceError.MicNeeded -> TextButton(onClick = { onDismiss(); voice.allow() }) { Text(str(R.string.vz_mic_allow)) }
            VoiceError.MicBlocked -> TextButton(onClick = voice.openAppSettings) { Text(str(R.string.vz_open_settings)) }
            VoiceError.TtsUnavailable, is VoiceError.TtsLanguageMissing -> TextButton(onClick = voice.openTts) { Text(str(R.string.open_tts_short)) }
            else -> {}
        }
        TextButton(onClick = onDismiss) { Text("✕", modifier = Modifier.clearAndSetSemantics { contentDescription = str(R.string.cd_dismiss) }) }
    }
}

@Composable
internal fun VoiceInputSettings(d: Settings, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    val caps = remember {
        Triple(SpeechManager.recognitionAvailable(ctx), SpeechManager.onDeviceAvailable(ctx),
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val note = MaterialTheme.typography.bodySmall
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Text(str(R.string.voice_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    Section(str(R.string.voice_input))
    ChipRow {
        Choice(str(R.string.voice_input_tap), d.voiceInput == "tap") { onChange(d.copy(voiceInput = "tap")) }
        Choice(str(R.string.voice_input_push), d.voiceInput == "push") { onChange(d.copy(voiceInput = "push")) }
    }
    if (d.voiceInput == "push") Text(str(R.string.voice_input_note), style = note, color = dim)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(R.string.voice_continuous), Modifier.weight(1f)); Switch(d.continuous, { onChange(d.copy(continuous = it)) })
    }
    Text(str(R.string.voice_continuous_note), style = note, color = dim)
    if (d.continuous) {
        Section(str(R.string.voice_continuous_idle, d.continuousMaxIdle))
        Slider(d.continuousMaxIdle.toFloat(), { onChange(d.copy(continuousMaxIdle = it.roundToInt().coerceIn(1, 5))) }, valueRange = 1f..5f, steps = 3)
    }

    Section(str(R.string.recog_lang))
    ChipRow {
        Choice(str(R.string.recog_auto), d.language == "auto") { onChange(d.copy(language = "auto")) }
        Choice(str(R.string.recog_bn), d.language == "bn-BD") { onChange(d.copy(language = "bn-BD")) }
        Choice("English", d.language == "en-US") { onChange(d.copy(language = "en-US")) }
    }
    if (d.language == "auto") Text(str(R.string.recog_auto_note), style = note, color = dim)

    Section(str(R.string.stt_offline))
    ChipRow {
        Choice(str(R.string.stt_offline_auto), d.sttOffline == "auto") { onChange(d.copy(sttOffline = "auto")) }
        Choice(str(R.string.stt_offline_prefer), d.sttOffline == "prefer") { onChange(d.copy(sttOffline = "prefer")) }
        Choice(str(R.string.stt_offline_only), d.sttOffline == "only") { onChange(d.copy(sttOffline = "only")) }
    }
    Text(str(R.string.stt_offline_note), style = note, color = dim)

    val yes = str(R.string.cap_yes); val no = str(R.string.cap_no)
    Text(str(R.string.cap_recog, if (caps.first) yes else no) + "\n" +
        str(R.string.cap_ondevice, if (caps.second) yes else no) + "\n" +
        str(R.string.cap_mic, if (caps.third) str(R.string.cap_allowed) else str(R.string.cap_denied)), style = note, color = dim)
}

@Composable
internal fun WakeSettings(d: Settings, onChange: (Settings) -> Unit) {
    val note = MaterialTheme.typography.bodySmall
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Text(str(R.string.wake_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(str(R.string.wake_note), style = note, color = dim)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = d.wakeAck, role = Role.Checkbox) { onChange(d.copy(wakeAck = it, wakeEnabled = d.wakeEnabled && it)) },
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(d.wakeAck, null)
        Spacer(Modifier.width(8.dp))
        Text(str(R.string.wake_ack), Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(R.string.wake_enable), Modifier.weight(1f), color = if (d.wakeAck) MaterialTheme.colorScheme.onSurface else dim)
        Switch(d.wakeEnabled, { onChange(d.copy(wakeEnabled = it && d.wakeAck)) }, enabled = d.wakeAck)
    }
    if (d.wakeEnabled) {
        OutlinedTextField(d.wakePhrase, { onChange(d.copy(wakePhrase = it.take(40))) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(str(R.string.wake_phrase)) })
    }
}
