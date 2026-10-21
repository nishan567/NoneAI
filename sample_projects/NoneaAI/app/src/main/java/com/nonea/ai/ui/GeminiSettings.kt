package com.nonea.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nonea.ai.BuildConfig
import com.nonea.ai.R
import com.nonea.ai.ai.*
import com.nonea.ai.data.AiMode
import com.nonea.ai.data.Settings
import kotlin.math.roundToInt

private val Green = Color(0xFF3DDC84)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFFF5252)
private val Grey = Color(0xFF8A8F99)

/** Turns a typed AI error into localized text (uses the in-app language, like every other string). */
@Composable
fun AiError.text(): String = when (this) {
    AiError.NotConfigured -> str(R.string.err_not_configured)
    is AiError.InvalidKey -> str(if (viaProxy) R.string.err_invalid_token else R.string.err_invalid_key)
    is AiError.RateLimited -> when {
        quotaExhausted -> str(R.string.err_quota)
        retryAfterSec != null -> str(R.string.err_rate_limited, retryAfterSec)
        else -> str(R.string.err_rate_limited_generic)
    }
    is AiError.Network -> str(if (timeout) R.string.err_timeout else R.string.err_network)
    is AiError.ModelUnavailable -> str(R.string.err_model_unavailable, model)
    is AiError.Blocked -> str(R.string.err_blocked, reason)
    is AiError.BadRequest -> str(R.string.err_bad_request, detail)
    is AiError.Server -> str(R.string.err_server, code)
    is AiError.EmptyResponse -> str(R.string.err_empty, reason)
    is AiError.InvalidConfig -> str(R.string.err_invalid_config, detail)
    is AiError.Unknown -> str(R.string.err_unknown, detail)
}

@Composable
private fun statusLook(st: ConnState, configured: Boolean): Pair<Color, String> = when {
    st is ConnState.Testing -> Amber to str(R.string.ai_status_testing)
    st is ConnState.Connected -> Green to str(R.string.ai_status_connected, st.model, st.latencyMs.toInt())
    st is ConnState.Failed ->
        (if (st.error is AiError.RateLimited) Amber else Red) to str(R.string.ai_status_failed, st.error.text())
    !configured -> Grey to str(R.string.ai_status_not_configured)
    else -> Grey to str(R.string.ai_status_unknown)
}

/** Small colored dot for the chat top bar; the color alone never carries the meaning (content description too). */
@Composable
fun AiStatusDot(st: ConnState, configured: Boolean, modifier: Modifier = Modifier) {
    val (color, text) = statusLook(st, configured)
    val desc = str(R.string.cd_ai_status, text)
    Box(modifier.size(10.dp).background(color, CircleShape).semantics { contentDescription = desc })
}

internal fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Section(label)
    Slider(value, onChange, valueRange = range, steps = steps)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeminiSettingsSection(vm: ChatViewModel, d: Settings, onChange: (Settings) -> Unit) {
    val p = LocalPalette.current
    val status by vm.aiStatus.collectAsState()
    val modelsUi by vm.models.collectAsState()
    val usage by vm.usage.collectAsState()
    var showKey by remember { mutableStateOf(false) }
    val configured = AiConfig.isConfigured(d)
    val st = status
    val (dot, statusText) = statusLook(st, configured)

    Section(str(R.string.ai_section))

    // ---- status + test ----
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.glass)
        .border(BorderStroke(1.dp, p.glassStroke), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(statusText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { vm.testConnection(d) }, enabled = configured && st !is ConnState.Testing) {
            if (st is ConnState.Testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text(str(R.string.ai_test))
        }
    }

    // ---- connection type ----
    if (BuildConfig.ALLOW_DIRECT_KEY) {
        Section(str(R.string.ai_mode))
        ChipRow {
            Choice(str(R.string.ai_mode_direct), d.mode == AiMode.DIRECT) { onChange(d.copy(aiMode = AiMode.DIRECT)) }
            Choice(str(R.string.ai_mode_proxy), d.mode == AiMode.PROXY) { onChange(d.copy(aiMode = AiMode.PROXY)) }
        }
    }
    if (d.mode == AiMode.DIRECT) {
        Text(str(R.string.ai_direct_warning), style = MaterialTheme.typography.bodySmall, color = Amber)
        Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(d.copy(directKeyAck = !d.directKeyAck)) },
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(d.directKeyAck, { onChange(d.copy(directKeyAck = it)) })
            Text(str(R.string.ai_direct_ack), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(d.apiKey, { onChange(d.copy(apiKey = it)) }, Modifier.fillMaxWidth(),
            label = { Text(str(R.string.api_key)) }, singleLine = true,
            enabled = d.directKeyAck || d.apiKey.isNotBlank(),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(str(if (showKey) R.string.ai_key_hide else R.string.ai_key_show)) } })
    } else {
        val badUrl = d.proxyUrl.isNotBlank() && ProxyConfig.normalize(d.proxyUrl) == null
        OutlinedTextField(d.proxyUrl, { onChange(d.copy(proxyUrl = it)) }, Modifier.fillMaxWidth(),
            label = { Text(str(R.string.ai_proxy_url)) }, singleLine = true, isError = badUrl,
            supportingText = if (badUrl) ({ Text(str(R.string.ai_proxy_bad_url)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrect = false))
        OutlinedTextField(d.proxyToken, { onChange(d.copy(proxyToken = it)) }, Modifier.fillMaxWidth(),
            label = { Text(str(R.string.ai_proxy_token)) }, singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(str(if (showKey) R.string.ai_key_hide else R.string.ai_key_show)) } })
        Text(str(R.string.ai_proxy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // ---- model ----
    Section(str(R.string.model))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modelsUi.models.take(16).forEach { m ->
            val tag = when (m.tier) {
                ModelInfo.Tier.LEGACY -> " · " + str(R.string.ai_tag_legacy)
                ModelInfo.Tier.RETIRED -> " · " + str(R.string.ai_tag_retired)
                else -> ""
            }
            Choice(m.displayName + tag, d.model == m.id) { onChange(d.copy(model = m.id)) }
        }
    }
    OutlinedTextField(d.model, { onChange(d.copy(model = it.trim())) }, Modifier.fillMaxWidth(),
        label = { Text(str(R.string.ai_model_custom)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrect = false))
    when (GeminiModels.tier(d.model)) {
        ModelInfo.Tier.LEGACY -> Text(str(R.string.ai_model_legacy_note), style = MaterialTheme.typography.bodySmall, color = Amber)
        ModelInfo.Tier.RETIRED -> Text(str(R.string.ai_model_retired_note), style = MaterialTheme.typography.bodySmall, color = Red)
        else -> {}
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { vm.refreshModels(d) }, enabled = configured && !modelsUi.loading) {
            Text(str(if (modelsUi.loading) R.string.ai_models_refreshing else R.string.ai_models_refresh))
        }
        val err = modelsUi.error
        if (err != null) Text(err.text(), style = MaterialTheme.typography.bodySmall, color = Red, modifier = Modifier.weight(1f))
        else if (modelsUi.fetched) Text(str(R.string.ai_models_loaded, modelsUi.models.size), style = MaterialTheme.typography.bodySmall)
    }

    // ---- generation ----
    Section(str(R.string.ai_gen_title))
    ParamSlider(str(R.string.ai_temperature, "%.2f".format(d.temperature)), d.temperature, 0f..2f, 39) { onChange(d.copy(temperature = (it * 20).roundToInt() / 20f)) }
    ParamSlider(str(R.string.ai_top_p, "%.2f".format(d.topP)), d.topP, 0f..1f, 19) { onChange(d.copy(topP = (it * 20).roundToInt() / 20f)) }
    ParamSlider(str(R.string.ai_top_k, if (d.topK == 0) str(R.string.ai_auto) else d.topK.toString()), d.topK.toFloat(), 0f..100f, 99) { onChange(d.copy(topK = it.roundToInt())) }
    ParamSlider(str(R.string.ai_max_tokens, d.maxOutputTokens), d.maxOutputTokens.toFloat(), 256f..8192f, 30) { onChange(d.copy(maxOutputTokens = (it / 256f).roundToInt() * 256)) }
    if (GeminiModels.supportsThinkingLevel(d.model)) {
        Section(str(R.string.ai_thinking))
        ChipRow {
            Choice(str(R.string.ai_auto), d.thinking == "auto") { onChange(d.copy(thinking = "auto")) }
            Choice(str(R.string.ai_think_low), d.thinking == "low") { onChange(d.copy(thinking = "low")) }
            Choice(str(R.string.ai_think_high), d.thinking == "high") { onChange(d.copy(thinking = "high")) }
        }
    }
    ParamSlider(str(R.string.ai_context_budget, d.contextBudget), d.contextBudget.toFloat(), 1000f..32000f, 30) { onChange(d.copy(contextBudget = (it / 1000f).roundToInt() * 1000)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(R.string.ai_streaming), Modifier.weight(1f)); Switch(d.streaming, { onChange(d.copy(streaming = it)) })
    }

    // ---- usage ----
    Section(str(R.string.ai_usage_title))
    Text(str(R.string.ai_usage_today, usage.todayRequests, compact(usage.todayTokens)), style = MaterialTheme.typography.bodyMedium)
    Text(str(R.string.ai_usage_total, usage.totalRequests, compact(usage.totalPromptTokens + usage.totalOutputTokens), usage.totalErrors), style = MaterialTheme.typography.bodyMedium)
    Text(str(R.string.ai_usage_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { vm.resetUsage() }) { Text(str(R.string.ai_usage_reset)) }
}
