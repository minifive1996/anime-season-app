package com.minifive.animeseason.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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

private sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val data: DetailUi) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

private data class DetailUi(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val format: String?,
    val status: String?, // 選配：有才顯示
    val startDate: String?,
    val studios: List<String>,
    val genres: List<String>,
    val description: String,
    val siteUrl: String?,
    val links: List<Pair<String, String>>,
)

@Composable
fun DetailScreen(
    animeId: String,
    onBack: () -> Unit
) {
    var uiState: DetailUiState by remember { mutableStateOf(DetailUiState.Loading) }
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val prefs = remember { UserPrefsRepository(context.applicationContext) }
    val favoriteIds by prefs.favoriteIdsFlow.collectAsState(initial = emptySet())
    val isFav = favoriteIds.contains(animeId)

    LaunchedEffect(animeId) {
        uiState = DetailUiState.Loading
        uiState = runCatching { fetchDetailFromSeasonJson(animeId) }
            .getOrElse { e -> DetailUiState.Error(e.message ?: e.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top row：返回 + 收藏（不引入 icon，沿用你目前風格）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← 返回",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onBack() }
            )

            Text(
                text = if (isFav) "♥" else "♡",
                style = MaterialTheme.typography.headlineSmall,
                color = if (isFav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable {
                        scope.launch { prefs.toggleFavorite(animeId) }
                    }
            )
        }

        when (val s = uiState) {
            DetailUiState.Loading -> {
                Text(
                    text = "載入中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is DetailUiState.Error -> {
                Text(
                    text = "載入失敗：${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "改用 AniList 開啟 ↗",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://anilist.co/anime/$animeId")
                            )
                        )
                    }
                )
            }

            is DetailUiState.Success -> {
                val d = s.data

                val headerImage = d.coverUrl ?: d.bannerUrl
                if (!headerImage.isNullOrBlank()) {
                    AsyncImage(
                        model = headerImage,
                        contentDescription = d.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                Text(
                    text = d.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metaLine = buildString {
                    d.format?.let { append("類型：$it") }
                    d.status?.let {
                        if (isNotEmpty()) append("  •  ")
                        append("狀態：$it")
                    }
                }.ifBlank { "類型資訊未提供" }

                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "首播：${d.startDate ?: "未提供"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (d.studios.isNotEmpty()) {
                    Text(
                        text = "製作：${d.studios.joinToString(" / ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (d.genres.isNotEmpty()) {
                    Text(
                        text = "分類：${d.genres.joinToString(" / ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (d.description.isNotBlank()) {
                    Text(
                        text = d.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val anilistUrl = d.siteUrl ?: "https://anilist.co/anime/${d.id}"
                Text(
                    text = "在 AniList 開啟 ↗",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(anilistUrl)))
                    }
                )

                if (d.links.isNotEmpty()) {
                    Text(
                        text = "相關連結",
                        style = MaterialTheme.typography.titleMedium
                    )
                    d.links.take(8).forEach { (site, url) ->
                        Text(
                            text = "$site ↗",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private suspend fun fetchDetailFromSeasonJson(animeId: String): DetailUiState {
    val url = BuildConfig.API_BASE_URL + "season/season.json"
    val body = withContext(Dispatchers.IO) {
        URL(url).openStream().bufferedReader().use { it.readText() }
    }

    val root = JSONObject(body)
    val items = root.optJSONArray("items") ?: JSONArray()

    var target: JSONObject? = null
    for (i in 0 until items.length()) {
        val obj = items.optJSONObject(i) ?: continue
        if (obj.optString("id") == animeId) {
            target = obj
            break
        }
    }

    val t = target ?: return DetailUiState.Error("找不到此作品（id=$animeId）")

    val title = pickTitle(t.optJSONObject("title"))
    val image = t.optJSONObject("image")
    val meta = t.optJSONObject("meta")
    val airing = t.optJSONObject("airing")

    val studios = meta?.optJSONArray("studios")?.toStringList().orEmpty()
    val genres = meta?.optJSONArray("genres")?.toStringList().orEmpty()

    val links = t.optJSONArray("links")?.let { arr ->
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val site = o.optString("site").trim()
                val u = o.optString("url").trim()
                if (site.isNotBlank() && u.isNotBlank()) add(site to u)
            }
        }
    }.orEmpty()

    val d = DetailUi(
        id = animeId,
        title = title,
        coverUrl = image?.optString("coverLarge")?.takeIf { it.isNotBlank() },
        bannerUrl = image?.optString("banner")?.takeIf { it.isNotBlank() },
        format = meta?.optString("format")?.takeIf { it.isNotBlank() },
        status = meta?.optString("status")?.takeIf { it.isNotBlank() },
        startDate = airing?.optString("startDate")?.takeIf { it.isNotBlank() },
        studios = studios,
        genres = genres,
        description = t.optString("description", "").trim(),
        siteUrl = meta?.optString("siteUrl")?.takeIf { it.isNotBlank() },
        links = links
    )

    return DetailUiState.Success(d)
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
