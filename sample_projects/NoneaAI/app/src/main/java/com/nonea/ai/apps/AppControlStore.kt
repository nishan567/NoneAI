package com.nonea.ai.apps

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDs by preferencesDataStore("nonea_apps")

/** The user's own app names and voice commands (section 7). Not secret; kept in a separate DataStore file from Settings. */
class AppControlStore(private val ctx: Context) {
    private val kAliases = stringPreferencesKey("aliases")
    private val kShortcuts = stringPreferencesKey("shortcuts")

    val flow: Flow<AppControlConfig> = ctx.appDs.data.map { p ->
        AppControlConfig(ConfigCodec.decodeAliases(p[kAliases].orEmpty()), ConfigCodec.decodeShortcuts(p[kShortcuts].orEmpty()))
    }

    /** Returns null on success, or why it was rejected. */
    suspend fun upsertAlias(a: AppAlias): Problem? {
        AppAliases.validate(a)?.let { return it }
        var result: Problem? = null
        ctx.appDs.edit { p ->
            val cur = ConfigCodec.decodeAliases(p[kAliases].orEmpty())
            val next = AppAliases.upsert(cur, a)
            if (next.size > Shortcuts.MAX_ALIASES) result = Problem.TOO_MANY else p[kAliases] = ConfigCodec.encodeAliases(next)
        }
        return result
    }

    suspend fun removeAlias(alias: String) {
        val key = AppNames.normalize(alias)
        ctx.appDs.edit { p ->
            val cur = ConfigCodec.decodeAliases(p[kAliases].orEmpty())
            p[kAliases] = ConfigCodec.encodeAliases(cur.filterNot { AppNames.normalize(it.alias) == key })
        }
    }

    suspend fun upsertShortcut(s: Shortcut): Problem? {
        Shortcuts.validate(s)?.let { return it }
        var result: Problem? = null
        ctx.appDs.edit { p ->
            val cur = ConfigCodec.decodeShortcuts(p[kShortcuts].orEmpty())
            val next = Shortcuts.upsert(cur, s)
            if (next.size > Shortcuts.MAX_SHORTCUTS) result = Problem.TOO_MANY else p[kShortcuts] = ConfigCodec.encodeShortcuts(next)
        }
        return result
    }

    suspend fun removeShortcut(phrase: String) {
        val key = AppNames.normalize(phrase)
        ctx.appDs.edit { p ->
            val cur = ConfigCodec.decodeShortcuts(p[kShortcuts].orEmpty())
            p[kShortcuts] = ConfigCodec.encodeShortcuts(cur.filterNot { AppNames.normalize(it.phrase) == key })
        }
    }
}
