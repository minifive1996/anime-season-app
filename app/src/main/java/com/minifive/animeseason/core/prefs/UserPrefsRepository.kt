package com.minifive.animeseason.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

data class FavoriteEntry(
    val id: String,
    val createdAtMillis: Long
)

class UserPrefsRepository(
    private val appContext: Context
) {
    private object Keys {
        val themeColorArgb = intPreferencesKey("theme_color_argb")

        // 收藏：ids + createdAt（用 JSON 存 map）
        val favoriteIds = stringSetPreferencesKey("favorite_ids")
        val favoriteCreatedAtJson = stringPreferencesKey("favorite_created_at_json")
    }

    /**
     * null 代表未指定（可讓 Theme 走 dynamic 或預設）
     */
    val themeColorArgbFlow: Flow<Int?> =
        appContext.userPrefsDataStore.data.map { prefs ->
            prefs[Keys.themeColorArgb]
        }

    val favoriteIdsFlow: Flow<Set<String>> =
        appContext.userPrefsDataStore.data.map { prefs ->
            prefs[Keys.favoriteIds] ?: emptySet()
        }

    val favoriteCreatedAtMapFlow: Flow<Map<String, Long>> =
        appContext.userPrefsDataStore.data.map { prefs ->
            parseCreatedAtMap(prefs[Keys.favoriteCreatedAtJson])
        }

    /**
     * 已排序（新→舊）的收藏清單（含 createdAt）
     */
    val favoriteEntriesFlow: Flow<List<FavoriteEntry>> =
        combine(favoriteIdsFlow, favoriteCreatedAtMapFlow) { ids, createdMap ->
            ids.map { id ->
                FavoriteEntry(
                    id = id,
                    createdAtMillis = createdMap[id] ?: 0L
                )
            }.sortedByDescending { it.createdAtMillis }
        }

    suspend fun setThemeColorArgb(argb: Int?) {
        appContext.userPrefsDataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(Keys.themeColorArgb)
            } else {
                prefs[Keys.themeColorArgb] = argb
            }
        }
    }

    /**
     * @return true 表示 toggle 後為「已收藏」，false 表示 toggle 後為「未收藏」
     */
    suspend fun toggleFavorite(
        id: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (id.isBlank()) return false

        var isFavAfter = false

        appContext.userPrefsDataStore.edit { prefs ->
            val ids = (prefs[Keys.favoriteIds] ?: emptySet()).toMutableSet()
            val createdMap = parseCreatedAtMap(prefs[Keys.favoriteCreatedAtJson]).toMutableMap()

            if (ids.contains(id)) {
                ids.remove(id)
                createdMap.remove(id)
                isFavAfter = false
            } else {
                ids.add(id)
                createdMap[id] = nowMillis
                isFavAfter = true
            }

            prefs[Keys.favoriteIds] = ids
            prefs[Keys.favoriteCreatedAtJson] = toCreatedAtJson(createdMap)
        }

        return isFavAfter
    }

    /**
     * 立刻移除收藏，回傳被移除的 entry（供 Undo 使用）
     */
    suspend fun removeFavorite(id: String): FavoriteEntry? {
        if (id.isBlank()) return null

        var removed: FavoriteEntry? = null

        appContext.userPrefsDataStore.edit { prefs ->
            val ids = (prefs[Keys.favoriteIds] ?: emptySet()).toMutableSet()
            if (!ids.contains(id)) return@edit

            val createdMap = parseCreatedAtMap(prefs[Keys.favoriteCreatedAtJson]).toMutableMap()
            val createdAt = createdMap[id] ?: 0L

            ids.remove(id)
            createdMap.remove(id)

            prefs[Keys.favoriteIds] = ids
            prefs[Keys.favoriteCreatedAtJson] = toCreatedAtJson(createdMap)

            removed = FavoriteEntry(id = id, createdAtMillis = createdAt)
        }

        return removed
    }

    /**
     * 復原收藏（供 Snackbar Undo 使用）
     */
    suspend fun restoreFavorite(entry: FavoriteEntry) {
        if (entry.id.isBlank()) return

        appContext.userPrefsDataStore.edit { prefs ->
            val ids = (prefs[Keys.favoriteIds] ?: emptySet()).toMutableSet()
            val createdMap = parseCreatedAtMap(prefs[Keys.favoriteCreatedAtJson]).toMutableMap()

            ids.add(entry.id)
            // 若 createdAt 不存在才寫入；避免覆蓋既有排序資訊
            if (!createdMap.containsKey(entry.id)) {
                createdMap[entry.id] = if (entry.createdAtMillis > 0L) entry.createdAtMillis else System.currentTimeMillis()
            }

            prefs[Keys.favoriteIds] = ids
            prefs[Keys.favoriteCreatedAtJson] = toCreatedAtJson(createdMap)
        }
    }

    private fun parseCreatedAtMap(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optLong(k, 0L)
                    if (k.isNotBlank() && v > 0L) put(k, v)
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun toCreatedAtJson(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            if (k.isNotBlank() && v > 0L) obj.put(k, v)
        }
        return obj.toString()
    }
}
