package com.nonea.ai.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nonea.ai.R

@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit) = FilterChip(selected, onClick, { Text(label) })

@Composable
internal fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun ChipRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun VoicePicker(title: String, names: List<String>, current: String, onPick: (String) -> Unit) {
    Section(title)
    Choice(str(R.string.voice_default), current.isBlank()) { onPick("") }
    names.take(8).forEach { n -> Choice(n, current == n) { onPick(n) } }
}

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit, onAutomation: () -> Unit = {}) {
    val p = LocalPalette.current
    val s by vm.settings.collectAsState()
    val mems by vm.memories.collectAsState()
    var d by remember(s) { mutableStateOf(s) }
    val ctx = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val bnVoices = remember { vm.voiceNames("bn") }
    val enVoices = remember { vm.voiceNames("en") }

    Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            Section(str(R.string.appearance))
            ChipRow {
                Choice(str(R.string.theme_system), d.theme == "system") { d = d.copy(theme = "system") }
                Choice(str(R.string.theme_dark), d.theme == "dark") { d = d.copy(theme = "dark") }
                Choice(str(R.string.theme_light), d.theme == "light") { d = d.copy(theme = "light") }
            }
            Section(str(R.string.ui_language))
            ChipRow {
                Choice(str(R.string.lang_auto), d.uiLang == "auto") { d = d.copy(uiLang = "auto") }
                Choice(str(R.string.lang_en), d.uiLang == "en") { d = d.copy(uiLang = "en") }
                Choice(str(R.string.lang_bn), d.uiLang == "bn") { d = d.copy(uiLang = "bn") }
            }

            HorizontalDivider()
            GeminiSettingsSection(vm, d) { d = it }
            HorizontalDivider()

            Section(str(R.string.reply_lang))
            ChipRow {
                Choice(str(R.string.reply_match), d.replyLang == "auto") { d = d.copy(replyLang = "auto") }
                Choice("বাংলা", d.replyLang == "bn") { d = d.copy(replyLang = "bn") }
                Choice(str(R.string.reply_banglish), d.replyLang == "banglish") { d = d.copy(replyLang = "banglish") }
                Choice("English", d.replyLang == "en") { d = d.copy(replyLang = "en") }
            }
            HorizontalDivider()
            VoiceInputSettings(d) { d = it }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.speak_aloud), Modifier.weight(1f)); Switch(d.speak, { d = d.copy(speak = it) })
            }
            Section(str(R.string.speak_lang))
            ChipRow {
                Choice(str(R.string.lang_auto), d.ttsLang == "auto") { d = d.copy(ttsLang = "auto") }
                Choice("বাংলা", d.ttsLang == "bn-BD") { d = d.copy(ttsLang = "bn-BD") }
                Choice("English", d.ttsLang == "en-US") { d = d.copy(ttsLang = "en-US") }
            }
            Section(str(R.string.speech_rate, "%.2f".format(d.ttsRate)))
            Slider(d.ttsRate, { d = d.copy(ttsRate = it) }, valueRange = 0.5f..1.5f)
            Section(str(R.string.speech_pitch, "%.2f".format(d.ttsPitch)))
            Slider(d.ttsPitch, { d = d.copy(ttsPitch = it) }, valueRange = 0.8f..1.5f)
            if (bnVoices.isNotEmpty()) VoicePicker(str(R.string.voice_bn), bnVoices, d.ttsVoiceBn) { d = d.copy(ttsVoiceBn = it) }
            if (enVoices.isNotEmpty()) VoicePicker(str(R.string.voice_en), enVoices, d.ttsVoiceEn) { d = d.copy(ttsVoiceEn = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.previewVoice(d, true) }) { Text("▶ বাংলা") }
                OutlinedButton(onClick = { vm.previewVoice(d, false) }) { Text("▶ English") }
            }
            Text(str(R.string.voice_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }, Modifier.fillMaxWidth()) {
                Text(str(R.string.open_tts))
            }
            HorizontalDivider()
            WakeSettings(d) { d = it }
            Button(onClick = { vm.saveSettings(d); onBack() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(str(R.string.save)) }

            HorizontalDivider()
            AppControlSettings(vm)

            HorizontalDivider()
            Section(str(R.string.memory_title))
            if (mems.isEmpty()) Text(str(R.string.memory_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mems.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.deleteMemory(m.id) }) { Text("✕") }
                }
            }
            if (mems.isNotEmpty()) OutlinedButton(onClick = { vm.clearMemories() }, Modifier.fillMaxWidth()) { Text(str(R.string.forget_all)) }

            HorizontalDivider()
            Section(str(R.string.perms_note))
            OutlinedButton(onClick = {
                perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS))
            }, Modifier.fillMaxWidth()) { Text(str(R.string.grant_perms)) }
            OutlinedButton(onClick = onAutomation, Modifier.fillMaxWidth()) { Text(str(R.string.enable_a11y)) }
            OutlinedButton(onClick = { vm.clearHistory() }, Modifier.fillMaxWidth()) { Text(str(R.string.clear_history)) }
            TextButton(onClick = onBack) { Text(str(R.string.back)) }
        }
    }
}
