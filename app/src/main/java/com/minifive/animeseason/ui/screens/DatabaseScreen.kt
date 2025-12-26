package com.minifive.animeseason.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private sealed interface DbUiState {
    data object Loading : DbUiState
    data class Success(
        val items: List<DbItemUi>,
        val topGenres: List<String>,
        val formats: List<String>
    ) : DbUiState
    data class Error(val message: String) : DbUiState
}

private data class DbItemUi(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val format: String?,
    val status: String?,      // 選配：有才顯示
    val genres: List<String>,
    val siteUrl: String?
)

@Composable
fun DatabaseScreen(
    onAnimeClick: (String) -> Unit = {}
) {
    val context = LocalContext.current

    var uiState: DbUiState by remember { mutableStateOf(DbUiState.Loading) }
    var query by remember { mutableStateOf("") }
    var selectedFormats by remember { mutableStateOf(setOf<String>()) }
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }

    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        uiState = DbUiState.Loading
        uiState = runCatching { fetchDatabaseFromSeasonJson() }
            .getOrElse { e -> DbUiState.Error(e.message ?: e.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "資料庫",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(10.dp))

        when (val s = uiState) {
            DbUiState.Loading -> {
                Text(
                    text = "載入中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is DbUiState.Error -> {
                Text(
                    text = "載入失敗：${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "請確認：\n1) season.json 可開\n2) 手機網路正常\n3) API_BASE_URL 是否正確",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is DbUiState.Success -> {
                // 搜尋
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜尋（標題 / id）") }
                )

                Spacer(Modifier.height(10.dp))

                // Format chips
                if (s.formats.isNotEmpty()) {
                    Text(
                        text = "類型",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipsRow(
                        options = s.formats,
                        selected = selectedFormats,
                        onToggle = { value ->
                            selectedFormats = if (selectedFormats.contains(value)) {
                                selectedFormats - value
                            } else {
                                selectedFormats + value
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Genre chips（取常見前幾個，避免太多）
                if (s.topGenres.isNotEmpty()) {
                    Text(
                        text = "分類",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipsRow(
                        options = s.topGenres,
                        selected = selectedGenres,
                        onToggle = { value ->
                            selectedGenres = if (selectedGenres.contains(value)) {
                                selectedGenres - value
                            } else {
                                selectedGenres + value
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                val display = remember(s.items, query, selectedFormats, selectedGenres) {
                    val q = query.trim().lowercase()

                    s.items
                        .asSequence()
                        .filter { item ->
                            if (q.isBlank()) true
                            else item.id.lowercase().contains(q) || item.title.lowercase().contains(q)
                        }
                        .filter { item ->
                            if (selectedFormats.isEmpty()) true
                            else item.format != null && selectedFormats.contains(item.format)
                        }
                        .filter { item ->
                            if (selectedGenres.isEmpty()) true
                            else item.genres.any { g -> selectedGenres.contains(g) }
                        }
                        .toList()
                }

                Text(
                    text = "共 ${display.size} 筆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                // Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(display, key = { it.id }) { item ->
                        DbGridCard(
                            item = item,
                            onClick = {
                                // App 內 Detail
                                onAnimeClick(item.id)
                            },
                            onFallbackOpen = {
                                val url = item.siteUrl ?: "https://anilist.co/anime/${item.id}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipsRow(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    // 簡單的換行 chips（不做水平捲動，避免互動過複雜）
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var rowWidth = 0
        var row = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()

        // 粗略分行：每行最多 4 個 chip（夠用、穩定）
        options.forEach { opt ->
            row.add(opt)
            if (row.size >= 4) {
                rows.add(row.toList())
                row = mutableListOf()
            }
        }
        if (row.isNotEmpty()) rows.add(row.toList())

        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { opt ->
                    FilterChip(
                        selected = selected.contains(opt),
                        onClick = { onToggle(opt) },
                        label = { Text(opt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DbGridCard(
    item: DbItemUi,
    onClick: () -> Unit,
    onFallbackOpen: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // 圖片區
            val bg = MaterialTheme.colorScheme.surfaceVariant

            if (!item.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                // 無圖 placeholder
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "IMG",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 標題
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            // 小摘要（status 選配：有才顯示）
            val meta = buildList {
                item.format?.let { add(it) }
                item.status?.let { add(it) }
            }.joinToString(" • ")

            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 長按/更多操作未做，先保留 fallback：如果你不想只導 Detail，可改這裡
            // 例如：點圖片導 Detail，點 ↗ 開外部
        }
    }
}

private suspend fun fetchDatabaseFromSeasonJson(): DbUiState {
    val url = BuildConfig.API_BASE_URL + "database/database.json"
    val body = withContext(Dispatchers.IO) {
        URL(url).openStream().bufferedReader().use { it.readText() }
    }

    val root = JSONObject(body)
    val itemsJson = root.optJSONArray("items") ?: JSONArray()

    val items = ArrayList<DbItemUi>(itemsJson.length())
    val genreCount = HashMap<String, Int>()
    val formatSet = LinkedHashSet<String>()

    for (i in 0 until itemsJson.length()) {
        val obj = itemsJson.optJSONObject(i) ?: continue

        val id = obj.optString("id").trim()
        if (id.isBlank()) continue

        val title = pickTitle(obj.optJSONObject("title"))
        val image = obj.optJSONObject("image")
        val meta = obj.optJSONObject("meta")
        val airing = obj.optJSONObject("airing")

        val coverUrl = image?.optString("coverLarge")?.takeIf { it.isNotBlank() }
        val format = meta?.optString("format")?.takeIf { it.isNotBlank() }
        val status = meta?.optString("status")?.takeIf { it.isNotBlank() } // 選配：有才顯示
        val siteUrl = meta?.optString("siteUrl")?.takeIf { it.isNotBlank() }

        val genres = meta?.optJSONArray("genres")?.toStringList().orEmpty()

        // 統計 genres / formats
        genres.forEach { g -> genreCount[g] = (genreCount[g] ?: 0) + 1 }
        if (!format.isNullOrBlank()) formatSet.add(format)

        items.add(
            DbItemUi(
                id = id,
                title = title,
                coverUrl = coverUrl,
                format = format,
                status = status,
                genres = genres,
                siteUrl = siteUrl
            )
        )
    }

    // Genres 取前 12 個常見的（避免 chips 太多）
    val topGenres = genreCount.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(12)

    val formats = formatSet.toList()

    return DbUiState.Success(
        items = items,
        topGenres = topGenres,
        formats = formats
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

private fun JSONArray.toStringList(): List<String> {
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = optString(i).trim()
        if (v.isNotBlank()) out.add(v)
    }
    return out
}
