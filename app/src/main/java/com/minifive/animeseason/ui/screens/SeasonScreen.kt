package com.minifive.animeseason.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.minifive.animeseason.core.prefs.UserPrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private val CardHeight = 92.dp
private val PosterSize = 72.dp

private sealed interface SeasonUiState {
    data object Loading : SeasonUiState
    data class Success(val seasonTitle: String, val items: List<SeasonItemUi>) : SeasonUiState
    data class Error(val message: String) : SeasonUiState
}

private data class SeasonItemUi(
    val id: String,
    val title: String,
    val format: String?,
    val status: String?,       // 選配：有就顯示，沒有就不顯示
    val startDate: String?,
    val nextAiringText: String?,
    val nextAiringSec: Int?,
    val coverUrl: String?,
    val siteUrl: String?,
)

private enum class SortOption(val label: String) {
    PopularityDefault("熱門度（預設）"),
    StartDateDesc("首播日（新→舊）"),
    StartDateAsc("首播日（舊→新）"),
    NextAiringSoon("更新時間（近→遠）"),
    TitleAsc("標題（A→Z）"),
}

@Composable
fun SeasonScreen(
    onAnimeClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 收藏：正式改為 DataStore
    val prefs = remember { UserPrefsRepository(context.applicationContext) }
    val favoriteIds by prefs.favoriteIdsFlow.collectAsState(initial = emptySet())

    var uiState: SeasonUiState by remember { mutableStateOf(SeasonUiState.Loading) }
    var sort by remember { mutableStateOf(SortOption.PopularityDefault) }

    LaunchedEffect(Unit) {
        uiState = SeasonUiState.Loading
        uiState = runCatching { fetchSeasonJson() }
            .getOrElse { e -> SeasonUiState.Error(e.message ?: e.toString()) }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "本季新番",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(10.dp))

        when (val s = uiState) {
            SeasonUiState.Loading -> {
                Text(
                    text = "載入中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is SeasonUiState.Error -> {
                Text(
                    text = "載入失敗：${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "請確認：\n1) GitHub Pages season.json 可開\n2) 手機網路正常\n3) API_BASE_URL 是否正確",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is SeasonUiState.Success -> {
                Text(
                    text = s.seasonTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                SortRow(current = sort, onChange = { sort = it })

                Spacer(Modifier.height(12.dp))

                val displayItems = remember(s.items, sort) { applySort(s.items, sort) }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayItems) { item ->
                        val isFav = favoriteIds.contains(item.id)

                        SeasonAnimeCard(
                            item = item,
                            isFavorite = isFav,
                            onToggleFavorite = {
                                scope.launch {
                                    prefs.toggleFavorite(item.id)
                                }
                            },
                            onClick = {
                                if (onAnimeClick != null) {
                                    onAnimeClick(item.id)
                                } else {
                                    val url = item.siteUrl
                                    if (!url.isNullOrBlank()) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortRow(
    current: SortOption,
    onChange: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "排序：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.size(8.dp))

        Text(
            text = current.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortOption.entries.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        expanded = false
                        onChange(opt)
                    }
                )
            }
        }
    }
}

private fun applySort(items: List<SeasonItemUi>, option: SortOption): List<SeasonItemUi> {
    return when (option) {
        SortOption.PopularityDefault -> items

        SortOption.StartDateDesc -> items.sortedWith(
            compareByDescending<SeasonItemUi> { it.startDate != null }
                .thenByDescending { it.startDate ?: "" }
        )

        SortOption.StartDateAsc -> items.sortedWith(
            compareByDescending<SeasonItemUi> { it.startDate != null }
                .thenBy { it.startDate ?: "9999-99-99" }
        )

        SortOption.NextAiringSoon -> items.sortedWith(
            compareByDescending<SeasonItemUi> { it.nextAiringSec != null }
                .thenBy { it.nextAiringSec ?: Int.MAX_VALUE }
        )

        SortOption.TitleAsc -> items.sortedBy { it.title.lowercase() }
    }
}

private suspend fun fetchSeasonJson(): SeasonUiState {
    val url = BuildConfig.API_BASE_URL + "season/season.json"
    val body = withContext(Dispatchers.IO) {
        URL(url).openStream().bufferedReader().use { it.readText() }
    }

    val root = JSONObject(body)
    val src = root.optJSONObject("source")
    val season = src?.optString("season")?.uppercase() ?: ""
    val year = src?.optInt("year", 0) ?: 0
    val seasonTitle = listOf(
        year.takeIf { it > 0 }?.toString(),
        season.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(" ")

    val itemsJson = root.optJSONArray("items") ?: JSONArray()
    val items = buildList {
        for (i in 0 until itemsJson.length()) {
            val obj = itemsJson.optJSONObject(i) ?: continue
            add(mapSeasonItem(obj))
        }
    }

    return SeasonUiState.Success(
        seasonTitle = if (seasonTitle.isBlank()) "Season" else seasonTitle,
        items = items
    )
}

private fun mapSeasonItem(obj: JSONObject): SeasonItemUi {
    val id = obj.optString("id", "")
    val titleObj = obj.optJSONObject("title")
    val title = pickTitle(titleObj)

    val meta = obj.optJSONObject("meta")
    val format = meta?.optString("format")?.takeIf { it.isNotBlank() }
    val status = meta?.optString("status")?.takeIf { it.isNotBlank() }

    val airing = obj.optJSONObject("airing")
    val startDate = airing?.optString("startDate")?.takeIf { it.isNotBlank() }

    val next = airing?.optJSONObject("nextAiringEpisode")
    val nextSec = next?.optInt("timeUntilAiringSec", -1)?.takeIf { it > 0 }
    val nextText = nextSec?.let { formatTimeUntil(it) }

    val image = obj.optJSONObject("image")
    val coverUrl = image?.optString("coverLarge")?.takeIf { it.isNotBlank() }

    val siteUrl = meta?.optString("siteUrl")?.takeIf { it.isNotBlank() }

    return SeasonItemUi(
        id = id,
        title = title,
        format = format,
        status = status,
        startDate = startDate,
        nextAiringText = nextText,
        nextAiringSec = nextSec,
        coverUrl = coverUrl,
        siteUrl = siteUrl
    )
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

private fun formatTimeUntil(seconds: Int): String? {
    if (seconds <= 0) return null
    var s = seconds
    val days = s / 86400
    s %= 86400
    val hours = s / 3600
    s %= 3600
    val mins = s / 60

    return when {
        days > 0 -> "距離更新 ${days}天 ${hours}小時"
        hours > 0 -> "距離更新 ${hours}小時 ${mins}分"
        mins > 0 -> "距離更新 ${mins}分"
        else -> null
    }
}

@Composable
private fun SeasonAnimeCard(
    item: SeasonItemUi,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
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
                coverUrl = item.coverUrl,
                fallbackText = item.title
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                val badges = buildList {
                    item.format?.let { add(it) }
                    item.status?.let { add(it) } // status 選配
                }
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badges.forEach { Badge(text = it) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                val start = item.startDate ?: "開播日未提供"
                val next = item.nextAiringText

                Text(
                    text = if (next.isNullOrBlank()) start else "$start  •  $next",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (next.isNullOrBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            FavoriteIcon(
                checked = isFavorite,
                onClick = onToggleFavorite
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

@Composable
private fun FavoriteIcon(
    checked: Boolean,
    onClick: () -> Unit
) {
    val text = if (checked) "♥" else "♡"
    val color = if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = color,
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
            .padding(top = 2.dp)
    )
}
