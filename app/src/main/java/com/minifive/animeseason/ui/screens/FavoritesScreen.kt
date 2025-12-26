package com.minifive.animeseason.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.minifive.animeseason.BuildConfig
import com.minifive.animeseason.core.prefs.FavoriteEntry
import com.minifive.animeseason.core.prefs.UserPrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CardHeight = 92.dp
private val PosterSize = 72.dp
private val timeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private enum class FavSort(val label: String) {
    CreatedAtDesc("加入時間（新→舊）"),
    TitleAsc("標題（A→Z）"),
}

private data class AnimeMeta(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val format: String?,
    val status: String?,   // 選配：有才顯示
    val startDate: String?,
    val siteUrl: String?,
)

@Composable
fun FavoritesScreen(
    onAnimeClick: ((String) -> Unit)? = null,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefsRepository(context.applicationContext) }

    val favorites by prefs.favoriteEntriesFlow.collectAsState(initial = emptyList())

    // 僅最新一次 Undo
    var undoJob: Job? by remember { mutableStateOf(null) }

    // Favorites 顯示需要的資料索引（由 season.json 建立）
    var index by remember { mutableStateOf<Map<String, AnimeMeta>>(emptyMap()) }
    var indexError by remember { mutableStateOf<String?>(null) }

    // 搜尋 / 排序
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(FavSort.CreatedAtDesc) }
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 取一次 season.json 建立 index（不新增端點）
        indexError = null
        index = emptyMap()
        runCatching { fetchSeasonIndex() }
            .onSuccess { index = it }
            .onFailure { indexError = it.message ?: it.toString() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "收藏",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = sort.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { sortMenu = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            DropdownMenu(
                expanded = sortMenu,
                onDismissRequest = { sortMenu = false }
            ) {
                FavSort.entries.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.label) },
                        onClick = {
                            sortMenu = false
                            sort = opt
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜尋（標題 / id）") }
        )

        Spacer(Modifier.height(10.dp))

        if (!indexError.isNullOrBlank()) {
            Text(
                text = "提示：讀取 season.json 失敗（收藏仍可用）：${indexError}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        if (favorites.isEmpty()) {
            Text(
                text = "目前沒有收藏。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        val display = remember(favorites, index, query, sort) {
            val q = query.trim().lowercase()
            val enriched = favorites.map { entry ->
                val meta = index[entry.id]
                entry to meta
            }

            val filtered = if (q.isBlank()) {
                enriched
            } else {
                enriched.filter { (entry, meta) ->
                    val title = meta?.title?.lowercase().orEmpty()
                    entry.id.lowercase().contains(q) || title.contains(q)
                }
            }

            val sorted = when (sort) {
                FavSort.CreatedAtDesc -> filtered.sortedByDescending { it.first.createdAtMillis }
                FavSort.TitleAsc -> filtered.sortedBy { (it.second?.title ?: it.first.id).lowercase() }
            }

            sorted
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(display, key = { it.first.id }) { (entry, meta) ->
                FavoriteAnimeCard(
                    entry = entry,
                    meta = meta,
                    onClick = {
                        if (onAnimeClick != null) {
                            onAnimeClick(entry.id)
                        } else {
                            val url = meta?.siteUrl ?: "https://anilist.co/anime/${entry.id}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    onRemove = {
                        undoJob?.cancel()
                        snackbarHostState.currentSnackbarData?.dismiss()

                        undoJob = scope.launch {
                            val removed = prefs.removeFavorite(entry.id) ?: return@launch

                            val result = snackbarHostState.showSnackbar(
                                message = "已移除收藏",
                                actionLabel = "復原",
                                withDismissAction = true
                            )

                            if (result == SnackbarResult.ActionPerformed) {
                                prefs.restoreFavorite(removed)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoriteAnimeCard(
    entry: FavoriteEntry,
    meta: AnimeMeta?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val title = meta?.title ?: entry.id
    val createdText = if (entry.createdAtMillis > 0L) {
        val local = Instant.ofEpochMilli(entry.createdAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        timeFormatter.format(local)
    } else "—"

    Surface(
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterImage(
                coverUrl = meta?.coverUrl,
                fallbackText = title
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Badge：format + status（status 選配）
                val badges = buildList {
                    meta?.format?.let { add(it) }
                    meta?.status?.let { add(it) }
                }
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badges.forEach { Badge(text = it) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // 第三行：首播日 + 加入時間（資訊集中，不壅擠）
                val start = meta?.startDate ?: "開播日未提供"
                Text(
                    text = "$start  •  加入：$createdText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = "刪除",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PosterImage(
    coverUrl: String?,
    fallbackText: String
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val fg = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(PosterSize),
        tonalElevation = 0.dp
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = fallbackText,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(PosterSize)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Column(
                modifier = Modifier
                    .size(PosterSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = fallbackText.take(10),
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private suspend fun fetchSeasonIndex(): Map<String, AnimeMeta> {
    val url = BuildConfig.API_BASE_URL + "season/season.json"
    val body = withContext(Dispatchers.IO) {
        URL(url).openStream().bufferedReader().use { it.readText() }
    }

    val root = JSONObject(body)
    val items = root.optJSONArray("items") ?: JSONArray()

    val map = HashMap<String, AnimeMeta>(items.length())
    for (i in 0 until items.length()) {
        val obj = items.optJSONObject(i) ?: continue
        val id = obj.optString("id").trim()
        if (id.isBlank()) continue

        val title = pickTitle(obj.optJSONObject("title"))
        val image = obj.optJSONObject("image")
        val meta = obj.optJSONObject("meta")
        val airing = obj.optJSONObject("airing")

        map[id] = AnimeMeta(
            id = id,
            title = title,
            coverUrl = image?.optString("coverLarge")?.takeIf { it.isNotBlank() },
            format = meta?.optString("format")?.takeIf { it.isNotBlank() },
            status = meta?.optString("status")?.takeIf { it.isNotBlank() }, // 選配：有才顯示
            startDate = airing?.optString("startDate")?.takeIf { it.isNotBlank() },
            siteUrl = meta?.optString("siteUrl")?.takeIf { it.isNotBlank() }
        )
    }
    return map
}

private fun pickTitle(titleObj: JSONObject?): String {
    if (titleObj == null) return "(No Title)"
    val native = titleObj.optString("native").trim()
    val romaji = titleObj.optString("romaji").trim()
    val english = titleObj.optString("english").trim()
    return when {
        native.isNotBlank() -> native
        romaji.isNotBlank() -> romaji
        english.isNotBlank() -> english
        else -> "(No Title)"
    }
}
