package com.nonea.ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import com.nonea.ai.ai.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.usageDs by preferencesDataStore("nonea_usage")

/** Counted on this phone only; Google's own quota dashboard is the source of truth for limits. */
data class UsageStats(
    val totalRequests: Long = 0, val totalPromptTokens: Long = 0, val totalOutputTokens: Long = 0,
    val totalErrors: Long = 0, val todayRequests: Long = 0, val todayTokens: Long = 0
)

class UsageStore(private val ctx: Context) {
    private val kReq = longPreferencesKey("req")
    private val kPrompt = longPreferencesKey("prompt")
    private val kOut = longPreferencesKey("out")
    private val kErr = longPreferencesKey("err")
    private val kDay = stringPreferencesKey("day")
    private val kDayReq = longPreferencesKey("day_req")
    private val kDayTok = longPreferencesKey("day_tok")

    private fun today() = LocalDate.now().toString()

    val flow: Flow<UsageStats> = ctx.usageDs.data.map { p ->
        val same = p[kDay] == today()
        UsageStats(
            p[kReq] ?: 0, p[kPrompt] ?: 0, p[kOut] ?: 0, p[kErr] ?: 0,
            if (same) p[kDayReq] ?: 0 else 0, if (same) p[kDayTok] ?: 0 else 0
        )
    }

    private fun rollDay(p: MutablePreferences) {
        if (p[kDay] != today()) { p[kDay] = today(); p[kDayReq] = 0; p[kDayTok] = 0 }
    }

    suspend fun recordSuccess(u: TokenUsage) {
        ctx.usageDs.edit { p ->
            rollDay(p)
            p[kReq] = (p[kReq] ?: 0) + 1
            p[kPrompt] = (p[kPrompt] ?: 0) + u.promptTokens
            p[kOut] = (p[kOut] ?: 0) + u.billedOutput
            p[kDayReq] = (p[kDayReq] ?: 0) + 1
            p[kDayTok] = (p[kDayTok] ?: 0) + u.promptTokens + u.billedOutput
        }
    }

    suspend fun recordError() {
        ctx.usageDs.edit { p -> rollDay(p); p[kErr] = (p[kErr] ?: 0) + 1 }
    }

    suspend fun reset() { ctx.usageDs.edit { it.clear() } }
}
