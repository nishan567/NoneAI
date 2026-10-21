package com.nonea.ai.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nonea.ai.R
import com.nonea.ai.actions.Action
import com.nonea.ai.apps.AppEntry
import com.nonea.ai.apps.Problem
import com.nonea.ai.apps.Shortcut
import com.nonea.ai.apps.Shortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================ app launcher (section 7)

@Composable
private fun problemText(p: Problem): String = str(when (p) {
    Problem.PHRASE_EMPTY -> R.string.prob_phrase_empty
    Problem.PHRASE_LONG -> R.string.prob_phrase_long
    Problem.PHRASE_RESERVED -> R.string.prob_phrase_reserved
    Problem.TYPE_NOT_ALLOWED -> R.string.prob_type
    Problem.ARG_REQUIRED -> R.string.prob_arg_required
    Problem.ARG_LONG -> R.string.prob_arg_long
    Problem.URL_UNSAFE -> R.string.prob_url_unsafe
    Problem.MEDIA_UNKNOWN -> R.string.prob_media_unknown
    Problem.PAGE_UNKNOWN -> R.string.prob_page_unknown
    Problem.TOO_MANY -> R.string.prob_too_many
    Problem.ALIAS_EMPTY -> R.string.prob_alias_empty
    Problem.ALIAS_LONG -> R.string.prob_alias_long
    Problem.PACKAGE_BAD -> R.string.prob_package_bad
})

/** Grid of installed apps with a search box. Tap = open, press and hold = give the app a short name you can say. */
@Composable
fun AppLauncherSheet(vm: ChatViewModel, onDone: () -> Unit) {
    val cfg by vm.appConfig.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    val apps by produceState<List<AppEntry>?>(null) { value = vm.apps() }
    val q = query.trim()
    val namesByPkg = remember(cfg.aliases) { cfg.aliases.groupBy({ it.packageName }, { it.alias }) }
    val shown = remember(apps, q, namesByPkg) {
        apps.orEmpty().filter { a ->
            q.isEmpty() || a.label.contains(q, true) || a.packageName.contains(q, true) ||
                namesByPkg[a.packageName].orEmpty().any { it.contains(q, true) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str(R.string.apps_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp),
            placeholder = { Text(str(R.string.search_apps)) })
        Text(str(R.string.apps_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            apps == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            shown.isEmpty() -> {
                Text(str(R.string.no_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (q.isNotEmpty()) {
                    // Not installed: the only thing Nonea does is open the store's search page; installing stays your tap.
                    OutlinedButton(onClick = { vm.runAction(Action("play_store", q)); onDone() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(str(R.string.apps_play_store, q))
                    }
                }
            }
        }
        LazyVerticalGrid(GridCells.Adaptive(84.dp), Modifier.heightIn(max = 420.dp)) {
            items(shown, key = { it.packageName }) { app ->
                AppCell(vm, app, onOpen = { vm.runAction(Action("open_app", app.packageName)); onDone() }, onLong = { editing = app })
            }
        }
    }
    editing?.let { app -> AliasDialog(vm, app, namesByPkg[app.packageName].orEmpty()) { editing = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(vm: ChatViewModel, app: AppEntry, onOpen: () -> Unit, onLong: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            vm.appIcon(app.packageName)?.let { d -> runCatching { d.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
        }
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .combinedClickable(role = Role.Button, onLongClickLabel = str(R.string.app_set_alias), onLongClick = onLong, onClick = onOpen)
            .heightIn(min = 48.dp).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(48.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
        Spacer(Modifier.height(4.dp))
        Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AliasDialog(vm: ChatViewModel, app: AppEntry, existing: List<String>, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<Problem?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(str(R.string.alias_title, app.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(str(R.string.alias_help), style = MaterialTheme.typography.bodySmall)
                existing.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("“$a”", Modifier.weight(1f))
                        TextButton(onClick = { vm.removeAlias(a) }) { Text("✕") }
                    }
                }
                OutlinedTextField(text, { text = it; problem = null }, singleLine = true, label = { Text(str(R.string.alias_hint)) },
                    isError = problem != null, supportingText = { problem?.let { Text(problemText(it)) } })
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { val p = vm.saveAlias(text, app.packageName); if (p == null) text = "" else problem = p } }) { Text(str(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(str(R.string.done)) } }
    )
}

// ============================================================ Settings: aliases and custom voice commands

private fun typeLabel(type: String) = when (type) {
    "open_app" -> R.string.sc_type_open_app
    "settings" -> R.string.sc_type_settings
    "youtube_search" -> R.string.sc_type_youtube_search
    "youtube_play" -> R.string.sc_type_youtube_play
    "media" -> R.string.sc_type_media
    "open_url" -> R.string.sc_type_open_url
    else -> R.string.sc_type_show_apps
}

@Composable
fun AppControlSettings(vm: ChatViewModel) {
    val cfg by vm.appConfig.collectAsState()
    val apps by produceState<List<AppEntry>>(emptyList()) { value = vm.apps() }
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }
    var adding by remember { mutableStateOf(false) }

    Section(str(R.string.appctl_title))
    Text(str(R.string.appctl_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Text(str(R.string.alias_list_title), style = MaterialTheme.typography.labelLarge)
    if (cfg.aliases.isEmpty()) Text(str(R.string.alias_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    cfg.aliases.forEach { a ->
        val label = labels[a.packageName]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("“${a.alias}” → ${label ?: (a.packageName + " " + str(R.string.alias_missing))}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.removeAlias(a.alias) }) { Text("✕") }
        }
    }

    Text(str(R.string.shortcut_list_title), style = MaterialTheme.typography.labelLarge)
    if (cfg.shortcuts.isEmpty()) Text(str(R.string.shortcut_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    cfg.shortcuts.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("“${s.phrase}” → ${str(typeLabel(s.type))}" + if (s.arg.isNotBlank()) ": ${s.arg}" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.removeShortcut(s.phrase) }) { Text("✕") }
        }
    }
    OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(str(R.string.shortcut_add)) }
    if (adding) ShortcutDialog(vm) { adding = false }
}

@Composable
private fun ShortcutDialog(vm: ChatViewModel, onClose: () -> Unit) {
    var phrase by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("open_app") }
    var arg by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<Problem?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(str(R.string.shortcut_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(phrase, { phrase = it; problem = null }, singleLine = true, label = { Text(str(R.string.shortcut_phrase)) })
                ChipRow { Shortcuts.ALLOWED_TYPES.forEach { t -> Choice(str(typeLabel(t)), type == t) { type = t; problem = null } } }
                if (type != "show_apps") {
                    OutlinedTextField(arg, { arg = it; problem = null }, singleLine = true, label = { Text(str(R.string.shortcut_arg)) })
                }
                problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(str(R.string.shortcut_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val p = vm.saveShortcut(Shortcut(phrase.trim(), type, if (type == "show_apps") "" else arg.trim()))
                    if (p == null) onClose() else problem = p
                }
            }) { Text(str(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(str(R.string.confirm_cancel)) } }
    )
}
