package com.nonea.ai.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nonea.ai.R
import com.nonea.ai.actions.Action
import com.nonea.ai.data.ConversationEntity
import com.nonea.ai.data.MessageEntity
import kotlinx.coroutines.launch

private enum class Sheet { Apps, Phone }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onSettings: () -> Unit, onAutomation: () -> Unit = {}) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val convs by vm.conversations.collectAsState()
    val cur by vm.currentConv.collectAsState()
    var input by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showApps by vm.showApps.collectAsState()
    LaunchedEffect(showApps) { if (showApps) { sheet = Sheet.Apps; vm.consumeShowApps() } }
    val focus = remember { FocusRequester() }
    val activity = remember(ctx) { ctx.findActivity() }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setMicGranted(granted)
        if (granted) { if (settings.voiceInput == "tap") vm.tapMic() }   // hold-to-talk: the finger is long gone, wait for the next press
        else vm.onMicDenied(permanent = activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO))
    }
    val hasMic = { ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val voice = VoiceActions(
        tap = { if (hasMic()) { vm.setMicGranted(true); vm.tapMic() } else micPerm.launch(Manifest.permission.RECORD_AUDIO) },
        down = { if (hasMic()) { vm.setMicGranted(true); vm.pttPress() } else micPerm.launch(Manifest.permission.RECORD_AUDIO) },
        up = { vm.pttRelease() },
        allow = { micPerm.launch(Manifest.permission.RECORD_AUDIO) },
        openAppSettings = { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) } },
        openTts = { runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }
    )
    val isDark = p.dark
    val toggleTheme = { vm.saveSettings(settings.copy(theme = if (isDark) "light" else "dark")); Unit }
    val codePrefill = str(R.string.code_prefill)
    val quick: (String) -> Unit = { id ->
        when (id) {
            "youtube" -> vm.runAction(Action("open_app", "youtube"))
            "apps" -> sheet = Sheet.Apps
            "code" -> { input = codePrefill; runCatching { focus.requestFocus() } }
            "settings" -> onSettings()
            "phone" -> sheet = Sheet.Phone
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().imePadding()) {
        val wide = maxWidth >= 840.dp                       // tablets / unfolded: permanent history sidebar
        val split = maxWidth > maxHeight && maxWidth >= 560.dp // landscape: orb left, chat right
        val short = !split && maxHeight < 520.dp            // keyboard open / tiny window: mini orb in top bar

        val history: @Composable (onDone: () -> Unit) -> Unit = { done ->
            HistoryPanel(convs, cur,
                onNew = { vm.newChat(); done() }, onOpen = { vm.openChat(it); done() }, onDelete = { vm.deleteChat(it) })
        }
        val main: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                TopBar(vm, ui, short, showMenu = !wide, isDark = isDark,
                    onMenu = { scope.launch { drawer.open() } }, onNew = { vm.newChat() }, onTheme = toggleTheme)
                if (split) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            OrbBlock(vm, ui, orbSize = minOf(maxWidth * 0.3f, maxHeight * 0.5f))
                            Spacer(Modifier.height(8.dp))
                            QuickActions(quick)
                        }
                        Column(Modifier.weight(0.58f).fillMaxHeight()) {
                            ChatBody(vm, Modifier.weight(1f), input, { input = it }, focus, voice)
                        }
                    }
                } else {
                    if (!short) {
                        OrbBlock(vm, ui, orbSize = (maxHeight * 0.2f).coerceIn(104.dp, 200.dp).coerceAtMost(maxWidth * 0.6f))
                    }
                    QuickActions(quick)
                    ChatBody(vm, Modifier.weight(1f), input, { input = it }, focus, voice)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(300.dp).fillMaxHeight(), color = p.glass) { history {} }
                main(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    history { scope.launch { drawer.close() } }
                }
            }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { main(Modifier.widthIn(max = 920.dp).fillMaxSize()) }
            }
        }

        if (sheet != null) {
            ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = MaterialTheme.colorScheme.surface) {
                if (sheet == Sheet.Apps) AppLauncherSheet(vm) { sheet = null } else PhoneSheet(vm, onAutomation = { sheet = null; onAutomation() }) { sheet = null }
            }
        }
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(vm: ChatViewModel, ui: UiState, short: Boolean, showMenu: Boolean, isDark: Boolean,
                   onMenu: () -> Unit, onNew: () -> Unit, onTheme: () -> Unit) {
    val level by vm.level.collectAsState()
    val aiStatus by vm.aiStatus.collectAsState()
    val cfg by vm.settings.collectAsState()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showMenu) BarButton("☰", str(R.string.cd_menu), onMenu)
        if (short) {
            AiOrb(ui.orbState(), level, Modifier.size(44.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (showMenu && !short) 4.dp else 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.brand), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                AiStatusDot(aiStatus, com.nonea.ai.ai.AiConfig.isConfigured(cfg))
            }
            Text(if (short) ui.statusText() else str(R.string.tagline), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BarButton("＋", str(R.string.cd_new_chat), onNew)
        BarButton(if (isDark) "☀" else "☾", str(if (isDark) R.string.cd_to_light else R.string.cd_to_dark), onTheme)
    }
}

/** 48dp touch target; TalkBack reads [desc] instead of the glyph. */
@Composable
private fun BarButton(glyph: String, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Text(glyph, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.clearAndSetSemantics { contentDescription = desc })
    }
}

@Composable
private fun UiState.statusText(): String = when {
    listening -> str(R.string.status_listening)
    thinking -> str(R.string.status_thinking)
    speaking -> str(R.string.status_speaking)
    else -> str(R.string.status_idle)
}

private fun UiState.orbState() = when {
    listening -> OrbState.Listening
    thinking -> OrbState.Thinking
    speaking -> OrbState.Speaking
    else -> OrbState.Idle
}

// ---------------------------------------------------------------- orb + status

@Composable
private fun OrbBlock(vm: ChatViewModel, ui: UiState, orbSize: Dp) {
    val level by vm.level.collectAsState()
    val task by vm.task.collectAsState()
    val status = ui.statusText()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AiOrb(ui.orbState(), level, Modifier.size(orbSize).semantics { contentDescription = str(R.string.cd_orb, status) })
        Text(status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite })
        if (ui.partial.isNotBlank()) {
            Text("“${ui.partial}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
        }
        StatusPills(ui, task, vm::emergencyStop)
    }
}

@Composable
private fun StatusPills(ui: UiState, task: TaskState?, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Pill(str(R.string.pill_listening), ui.listening, LocalPalette.current.cyan)
        Pill(str(R.string.pill_thinking), ui.thinking, LocalPalette.current.violet)
        Pill(str(R.string.pill_speaking), ui.speaking, LocalPalette.current.pink)
        if (ui.handsFree) Pill(str(R.string.pill_handsfree), true, LocalPalette.current.cyan)
        if (ui.wake) Pill(str(R.string.pill_wake), true, LocalPalette.current.violet)
        if (task != null) TaskPill(task, onStop)
    }
}

@Composable
private fun Pill(label: String, active: Boolean, accent: Color) {
    val p = LocalPalette.current
    val dot by animateColorAsState(if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), tween(300), label = "dot")
    val pulse = if (active && !LocalReduceMotion.current) {
        val t = rememberInfiniteTransition(label = "pill")
        t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value
    } else 1f
    Row(Modifier.clip(RoundedCornerShape(50)).background(p.glass).border(BorderStroke(1.dp, if (active) accent else p.glassStroke), RoundedCornerShape(50))
        .padding(horizontal = 12.dp, vertical = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).alpha(pulse).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun taskLabel(a: Action): String {
    val base = when (a.type) {
        "open_app" -> R.string.task_open_app; "call" -> R.string.task_call
        "sms" -> R.string.task_sms; "flashlight" -> R.string.task_flashlight
        "alarm" -> R.string.task_alarm; "timer" -> R.string.task_timer
        "remind" -> R.string.task_remind; "volume" -> R.string.task_volume
        "web" -> R.string.task_web; "maps" -> R.string.task_maps
        "wifi" -> R.string.task_wifi; "bluetooth" -> R.string.task_bluetooth
        "system" -> R.string.task_system; "tap", "tap_text" -> R.string.task_tap
        "scroll" -> R.string.task_scroll; "type" -> R.string.task_type; "settings" -> R.string.task_settings
        "youtube_search", "youtube_play" -> R.string.task_youtube; "media" -> R.string.task_media; "appinfo" -> R.string.task_appinfo; "open_url" -> R.string.task_url; "play_store" -> R.string.task_store
        "stop_automation" -> R.string.task_stop; else -> R.string.task_generic
    }
    val label = str(base)
    return if (a.arg.isNotBlank() && a.type in setOf("open_app", "call", "web", "maps", "tap_text", "scroll", "youtube_search")) "$label: ${a.arg}" else label
}

@Composable
private fun TaskPill(t: TaskState, onStop: () -> Unit) {
    val p = LocalPalette.current
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.clip(RoundedCornerShape(50)).background(p.glass).border(BorderStroke(1.dp, accent), RoundedCornerShape(50))
        .padding(horizontal = 12.dp, vertical = 6.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically) {
        if (t.running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accent) else Text("✓", color = accent, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(str(R.string.pill_task) + " · " + (if (t.running) taskLabel(t.action) else t.result.take(60)),
            style = MaterialTheme.typography.labelMedium, maxLines = 1)
        if (t.running && t.action.type in com.nonea.ai.actions.AutomationExecutor.TYPES) {
            TextButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(str(R.string.stop_short)) }
        }
    }
}

// ---------------------------------------------------------------- quick actions

@Composable
private fun QuickActions(quick: (String) -> Unit) {
    val items = listOf(
        Triple("youtube", "▶", R.string.qa_youtube), Triple("apps", "⊞", R.string.qa_apps),
        Triple("code", "</>", R.string.qa_code), Triple("settings", "⚙", R.string.qa_settings),
        Triple("phone", "📱", R.string.qa_phone)
    )
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, glyph, label) ->
            val text = str(label)
            Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(p.glass)
                .border(BorderStroke(1.dp, p.glassStroke), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { quick(id) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(glyph, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------- chat list + composer

@Composable
private fun ChatBody(vm: ChatViewModel, modifier: Modifier, input: String, onInput: (String) -> Unit, focus: FocusRequester, voice: VoiceActions) {
    val msgs by vm.messages.collectAsState()
    val ui by vm.ui.collectAsState()
    val pending by vm.pending.collectAsState()
    val level by vm.level.collectAsState()
    val stream by vm.streamText.collectAsState()
    val cfg by vm.settings.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, ui.thinking, stream.length / 40) {
        val n = msgs.size + (if (ui.thinking) 1 else 0)
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    Column(modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msgs.isEmpty()) item {
                Text(str(R.string.welcome), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
            items(msgs, key = { it.id }) { Bubble(it, onReplay = { t -> vm.replay(t) }) }
            if (ui.thinking) item {
                if (stream.isBlank()) TypingBubble()
                else Bubble(MessageEntity(id = -1, role = "assistant", text = stream, convId = 0))
            }
        }
        pending.firstOrNull()?.let { m -> MemoryCard(m, { vm.approveMemory(m) }, { vm.dismissMemory(m) }) }
        VoiceBar(ui, vm)
        ui.voiceError?.let { e -> VoiceBanner(e, voice) { vm.dismissError() } }
        val aiMsg = ui.aiError?.text() ?: if (ui.interrupted) str(R.string.err_interrupted) else null
        (ui.error ?: aiMsg)?.let { e ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.dismissError() }) { Text("✕", modifier = Modifier.clearAndSetSemantics { contentDescription = str(R.string.cd_dismiss) }) }
            }
        }
        Composer(ui, level, input, onInput, focus, voice, cfg.voiceInput == "push") { vm.send(input); onInput("") }
    }
}

@Composable
private fun MemoryCard(text: String, onSave: () -> Unit, onNo: () -> Unit) {
    val p = LocalPalette.current
    Surface(color = p.glass, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, p.glassStroke),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(str(R.string.remember_q), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(str(R.string.save)) }
                OutlinedButton(onClick = onNo) { Text(str(R.string.no_thanks)) }
            }
        }
    }
}

@Composable
private fun Composer(ui: UiState, level: Float, input: String, onInput: (String) -> Unit, focus: FocusRequester,
                     voice: VoiceActions, push: Boolean, onSend: () -> Unit) {
    val p = LocalPalette.current
    val canSend = input.isNotBlank() && !ui.thinking
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ui.listening) {
            Row(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(p.glass)
                .border(BorderStroke(1.dp, p.cyan), RoundedCornerShape(28.dp)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MiniWave(level, p.cyan, Modifier.width(72.dp).height(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(ui.partial.ifBlank { str(R.string.status_listening) }, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            OutlinedTextField(input, onInput, Modifier.weight(1f).focusRequester(focus), maxLines = 4, shape = RoundedCornerShape(28.dp),
                placeholder = { Text(str(if (push) R.string.hint_message_push else R.string.hint_message)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = p.glass, unfocusedContainerColor = p.glass,
                    focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = p.glassStroke))
        }
        Spacer(Modifier.width(6.dp))
        // send
        IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(48.dp)) {
            Text("➤", fontSize = 22.sp, color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.clearAndSetSemantics { contentDescription = str(R.string.cd_send) })
        }
        // mic
        MicButton(ui.listening, enabled = !ui.thinking, push = push, voice = voice)
    }
}

@Composable
private fun MicButton(listening: Boolean, enabled: Boolean, push: Boolean, voice: VoiceActions) {
    val p = LocalPalette.current
    val pulse = if (listening && !LocalReduceMotion.current) {
        rememberInfiniteTransition(label = "mic").animateFloat(1f, 1.14f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "s").value
    } else 1f
    val desc = str(when { listening -> R.string.cd_mic_stop; push -> R.string.cd_mic_hold; else -> R.string.cd_mic_start })
    // Tap mode: a normal button. Hold mode: press = start, release = send. TalkBack users can't hold, so in hold mode
    // the accessibility "click" action toggles listening instead.
    val gesture = if (push) {
        Modifier.pointerInput(enabled) { detectTapGestures(onPress = { if (enabled) { voice.down(); tryAwaitRelease(); voice.up() } }) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = desc) { if (enabled) voice.tap(); true }
            }
    } else Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = desc, onClick = voice.tap)
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((56 * pulse).dp).alpha(if (enabled) 1f else 0.5f)
            .shadow(if (listening) 12.dp else 4.dp, CircleShape, ambientColor = p.cyan, spotColor = p.cyan)
            .clip(CircleShape)
            .background(Brush.linearGradient(if (listening) listOf(p.pink, p.violet) else listOf(p.cyan, p.violet)))
            .then(gesture)
            .semantics { if (!enabled) disabled() },
            contentAlignment = Alignment.Center) {
            Text(if (listening) "■" else "🎤", fontSize = 22.sp, color = Color.White, modifier = Modifier.clearAndSetSemantics { contentDescription = desc })
        }
    }
}

// ---------------------------------------------------------------- bubbles

@Composable
private fun Bubble(m: MessageEntity, onReplay: ((String) -> Unit)? = null) {
    val p = LocalPalette.current
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth().padding(start = if (mine) 40.dp else 0.dp, end = if (mine) 0.dp else 40.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val shape = RoundedCornerShape(20.dp, 20.dp, if (mine) 4.dp else 20.dp, if (mine) 20.dp else 4.dp)
        Box(Modifier.widthIn(max = 560.dp).clip(shape)
            .then(if (mine) Modifier.background(p.userBubble) else Modifier.background(p.glass).border(BorderStroke(1.dp, p.glassStroke), shape))) {
            SelectionContainer {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val fg = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                    if (mine && m.voice) Text("🎤", fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics { contentDescription = str(R.string.cd_voice_message) })
                    // Split on ``` fences: odd segments are code blocks.
                    m.text.split("```").forEachIndexed { i, seg ->
                        if (i % 2 == 1) {
                            val code = seg.substringAfter('\n', seg).trimEnd()
                            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFB9F6CA),
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF05070F), RoundedCornerShape(10.dp)).padding(10.dp)
                                    .horizontalScroll(rememberScrollState()))
                        } else if (seg.isNotBlank()) Text(seg.trim(), style = MaterialTheme.typography.bodyLarge, color = fg)
                    }
                    if (!mine && onReplay != null && m.id > 0) {
                        IconButton(onClick = { onReplay(m.text) }, modifier = Modifier.size(48.dp)) {
                            Text("🔊", fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { contentDescription = str(R.string.cd_replay) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(p.glass).border(BorderStroke(1.dp, p.glassStroke), RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 3) {
            val a = if (reduce) 0.8f else t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i").value
            Box(Modifier.size(8.dp).alpha(a).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

// ---------------------------------------------------------------- history

@Composable
private fun HistoryPanel(convs: List<ConversationEntity>, current: Long, onNew: () -> Unit, onOpen: (Long) -> Unit, onDelete: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str(R.string.history), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Button(onClick = onNew, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("＋  " + str(R.string.new_chat)) }
        if (convs.isEmpty()) Text(str(R.string.no_history), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(convs, key = { it.id }) { c ->
                val sel = c.id == current
                val del = str(R.string.delete_chat)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(role = Role.Button) { onOpen(c.id) }.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(c.title.ifBlank { str(R.string.new_chat) }, Modifier.weight(1f), maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { onDelete(c.id) }, Modifier.size(48.dp)) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { contentDescription = del })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sheets

@Composable
private fun PhoneSheet(vm: ChatViewModel, onAutomation: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    val S = R.string
    val tools = listOf(
        S.tool_torch_on to Action("flashlight", "on"), S.tool_torch_off to Action("flashlight", "off"),
        S.tool_vol_up to Action("volume", "up"), S.tool_vol_down to Action("volume", "down"),
        S.tool_mute to Action("volume", "mute"), S.tool_wifi to Action("wifi"),
        S.tool_bluetooth to Action("bluetooth"), S.tool_screenshot to Action("system", "screenshot"),
        S.tool_back to Action("system", "back"), S.tool_home to Action("system", "home"),
        S.tool_recents to Action("system", "recents"), S.tool_notifications to Action("system", "notifications"),
        S.tool_scroll_down to Action("scroll", "down"), S.tool_scroll_up to Action("scroll", "up"),
        S.tool_settings to Action("settings", ""),
        S.tool_media_prev to Action("media", "previous"), S.tool_media_toggle to Action("media", "toggle"),
        S.tool_media_next to Action("media", "next")
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str(S.phone_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, action) ->
                    Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(p.glass)
                        .border(BorderStroke(1.dp, p.glassStroke), RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { vm.runAction(action); onDone() }.padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(str(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        OutlinedButton(onClick = onAutomation, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(str(S.auto_title)) }
        Button(onClick = { vm.emergencyStop(); onDone() }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(str(S.auto_stop_now)) }
    }
}

/** Compose gives us a ContextWrapper chain; the permission rationale check needs the real Activity. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}
