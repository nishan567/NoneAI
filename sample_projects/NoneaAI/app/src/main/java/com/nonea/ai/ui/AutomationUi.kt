package com.nonea.ai.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nonea.ai.R
import com.nonea.ai.actions.NoneaAccessibilityService
import com.nonea.ai.automation.AutomationGuard

/** Is Nonea's accessibility service switched on in Android Settings? (Official AccessibilityManager query.) */
fun isAccessibilityServiceEnabled(ctx: Context): Boolean {
    val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        it.resolveInfo.serviceInfo.packageName == ctx.packageName &&
            it.resolveInfo.serviceInfo.name == NoneaAccessibilityService::class.java.name
    }
}

/** Shown over any screen of the app while a risky step waits for a yes. Only a real tap here can approve. */
@Composable
fun AutomationConfirmDialog(vm: ChatViewModel) {
    val req by AutomationGuard.pending.collectAsState()
    val r = req ?: return
    AlertDialog(
        onDismissRequest = { vm.answerConfirm(r.id, false) },
        title = { Text(r.title) },
        text = { Text(r.detail + "\n\n" + str(R.string.confirm_note)) },
        confirmButton = { Button(onClick = { vm.answerConfirm(r.id, true) }) { Text(str(R.string.confirm_allow)) } },
        dismissButton = { OutlinedButton(onClick = { vm.answerConfirm(r.id, false) }) { Text(str(R.string.confirm_cancel)) } }
    )
}

@Composable
private fun StatusRow(label: String, ok: Boolean?, detail: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Text, not just colour, so the state is clear to screen readers and colour-blind users.
        Text(
            when (ok) { true -> "✓ " + str(R.string.auto_on); false -> "✗ " + str(R.string.auto_off); null -> "–" },
            color = when (ok) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.onSurfaceVariant },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun AutomationScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val armed by AutomationGuard.armed.collectAsState()
    val confirmAll by AutomationGuard.confirmAll.collectAsState()
    val disclosed by AutomationGuard.disclosed.collectAsState()
    val connected by NoneaAccessibilityService.connected.collectAsState()
    var enabled by remember { mutableStateOf(isAccessibilityServiceEnabled(ctx)) }

    // The user comes back from Android Settings: re-read the status.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) enabled = isAccessibilityServiceEnabled(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val caps = NoneaAccessibilityService.get()?.serviceInfo?.capabilities ?: 0
    val gestures = if (connected) (caps and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0 else null
    val readNodes = if (connected) (caps and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0 else null
    val target = NoneaAccessibilityService.lastExternalPackage

    Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.auto_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(str(R.string.auto_intro), style = MaterialTheme.typography.bodyMedium)

            // Always-reachable emergency controls first.
            Button(onClick = { vm.emergencyStop() }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(str(R.string.auto_stop_now)) }

            HorizontalDivider()
            Section(str(R.string.auto_disclosure_title))
            Text(str(R.string.auto_disclosure_body), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(disclosed, { AutomationGuard.setDisclosed(it) })
                Text(str(R.string.auto_disclosure_ack), Modifier.weight(1f))
            }

            HorizontalDivider()
            Section(str(R.string.auto_status_title))
            StatusRow(str(R.string.auto_row_a11y), enabled, str(R.string.auto_row_a11y_note))
            StatusRow(str(R.string.auto_row_running), connected, "")
            StatusRow(str(R.string.auto_row_master), armed, str(R.string.auto_row_master_note))
            StatusRow(str(R.string.auto_row_gestures), gestures, "")
            StatusRow(str(R.string.auto_row_read), readNodes, str(R.string.auto_row_read_note))
            StatusRow(str(R.string.auto_row_shot), Build.VERSION.SDK_INT >= 28, str(R.string.auto_row_shot_note))
            StatusRow(str(R.string.auto_row_target), target != null, target ?: str(R.string.auto_row_target_none))

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.auto_master), style = MaterialTheme.typography.bodyLarge)
                    Text(str(R.string.auto_master_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(armed, { AutomationGuard.setArmed(it) }, enabled = enabled && connected && disclosed)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.auto_confirm_all), Modifier.weight(1f))
                Switch(confirmAll, { AutomationGuard.setConfirmAll(it) })
            }

            HorizontalDivider()
            Section(str(R.string.auto_steps_title))
            Text(str(R.string.auto_steps_body), style = MaterialTheme.typography.bodySmall)
            Text(str(R.string.auto_restricted_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = disclosed) { Text(str(R.string.auto_open_a11y)) }
            OutlinedButton(onClick = { NoneaAccessibilityService.get()?.turnOff() }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = connected) {
                Text(str(R.string.auto_turn_off))
            }

            HorizontalDivider()
            Section(str(R.string.auto_limits_title))
            Text(str(R.string.auto_limits_body), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onBack) { Text(str(R.string.back)) }
        }
    }
}
