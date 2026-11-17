package app.allfun.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.updateDataStore by preferencesDataStore("updates")

/**
 * Small persistent cache for the updater, separate from user settings so a cached release
 * never leaks into a backup.
 */
class UpdateStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val lastCheckAt = longPreferencesKey("last_check_at")
        val cachedRelease = stringPreferencesKey("cached_release")
        val notifiedTag = stringPreferencesKey("notified_tag")
    }

    suspend fun lastCheckAt(): Long? = context.updateDataStore.data.first()[Keys.lastCheckAt]

    suspend fun cached(): ReleaseInfo? {
        val raw = context.updateDataStore.data.first()[Keys.cachedRelease] ?: return null
        return runCatching { json.decodeFromString<ReleaseInfo>(raw) }.getOrNull()
    }

    suspend fun save(release: ReleaseInfo, checkedAt: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.cachedRelease] = json.encodeToString(release)
            prefs[Keys.lastCheckAt] = checkedAt
        }
    }

    suspend fun markChecked(at: Long) {
        context.updateDataStore.edit { it[Keys.lastCheckAt] = at }
    }

    suspend fun notifiedTag(): String? = context.updateDataStore.data.first()[Keys.notifiedTag]

    suspend fun setNotifiedTag(tag: String) {
        context.updateDataStore.edit { it[Keys.notifiedTag] = tag }
    }
}
