package com.minifive.animeseason.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.minifive.animeseason.BuildConfig
import com.minifive.animeseason.core.prefs.FavoriteEntry
import com.minifive.animeseason.core.prefs.UserPrefsRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { UserPrefsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val themeColorArgb by prefs.themeColorArgbFlow.collectAsState(initial = null)
    val favorites by prefs.favoriteEntriesFlow.collectAsState(initial = emptyList())

    // 匯入：二次確認狀態
    var importText by remember { mutableStateOf("") }
    var showImportPasteDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<FavoriteEntry>?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showImportOverwriteDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "設定",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(12.dp))
        Divider()

        // ---------- 個人化 ----------
        Spacer(Modifier.height(14.dp))
        SectionTitle("個人化")

        Text(text = "主題顏色", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))

        ThemeColorRow(
            selectedArgb = themeColorArgb,
            onSelect = { argb ->
                scope.launch { prefs.setThemeColorArgb(argb) }
            }
        )

        Spacer(Modifier.height(18.dp))
        Divider()

        // ---------- 收藏備份 ----------
        Spacer(Modifier.height(14.dp))
        SectionTitle("收藏備份")

        Text(
            text = "目前收藏：${favorites.size} 筆",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        // 匯出：複製到剪貼簿
        ActionRow(
            title = "匯出收藏（複製到剪貼簿）",
            subtitle = "產出 JSON，可自行保存"
        ) {
            val json = exportFavoritesJson(favorites)
            clipboard.setText(AnnotatedString(json))
            Toast.makeText(context, "已複製收藏備份到剪貼簿", Toast.LENGTH_SHORT).show()
        }

        // 匯出：分享
        ActionRow(
            title = "匯出收藏（分享）",
            subtitle = "透過其他 App 傳送 JSON"
        ) {
            val json = exportFavoritesJson(favorites)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
            }
            context.startActivity(Intent.createChooser(send, "分享收藏備份"))
        }

        // 匯入：從剪貼簿（先貼上/檢查，再二次確認覆蓋）
        ActionRow(
            title = "匯入收藏（從剪貼簿）",
            subtitle = "將 JSON 貼上後匯入（會覆蓋現有收藏）"
        ) {
            val clip = clipboard.getText()?.text?.toString().orEmpty()
            importText = clip
            showImportPasteDialog = true
        }

        Spacer(Modifier.height(18.dp))
        Divider()

        // ---------- 支援與關於 ----------
        Spacer(Modifier.height(14.dp))
        SectionTitle("支援與關於")

        LinkRow(
            title = "隱私權政策",
            url = BuildConfig.PRIVACY_POLICY_URL
        )

        LinkRow(
            title = "第三方服務與廣告",
            url = BuildConfig.THIRD_PARTY_ADS_URL
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "App Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // -------------------------
    // Dialog 1：貼上 / 檢查 JSON
    // -------------------------
    if (showImportPasteDialog) {
        AlertDialog(
            onDismissRequest = { showImportPasteDialog = false },
            title = { Text("匯入收藏（步驟 1/2）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "請貼上先前匯出的 JSON。按「檢查」後會進入覆蓋確認。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 10,
                        label = { Text("收藏備份 JSON") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = runCatching { parseFavoritesJson(importText) }.getOrNull()
                        if (parsed == null) {
                            Toast.makeText(context, "JSON 格式不正確，請確認內容", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        pendingImport = parsed
                        showImportPasteDialog = false
                        showImportConfirmDialog = true
                    }
                ) { Text("檢查") }
            },
            dismissButton = {
                TextButton(onClick = { showImportPasteDialog = false }) { Text("取消") }
            }
        )
    }

    // -------------------------
    // Dialog 2：顯示匯入筆數（第一層確認）
    // -------------------------
    if (showImportConfirmDialog) {
        val list = pendingImport.orEmpty()
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("匯入收藏（步驟 2/2）") },
            text = {
                Text(
                    text = "已偵測到 ${list.size} 筆收藏。\n下一步會要求你確認是否覆蓋現有 ${favorites.size} 筆收藏。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        showImportOverwriteDialog = true
                    }
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        pendingImport = null
                    }
                ) { Text("取消") }
            }
        )
    }

    // -------------------------
    // Dialog 3：覆蓋確認（第二層確認，符合你硬性規格）
    // -------------------------
    if (showImportOverwriteDialog) {
        val list = pendingImport.orEmpty()
        AlertDialog(
            onDismissRequest = { showImportOverwriteDialog = false },
            title = { Text("確認覆蓋收藏") },
            text = {
                Text(
                    text = "此操作會覆蓋現有 ${favorites.size} 筆收藏，改為匯入的 ${list.size} 筆。\n\n確定要覆蓋嗎？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportOverwriteDialog = false

                        scope.launch {
                            // 1) 清空現有收藏
                            favorites.forEach { prefs.removeFavorite(it.id) }

                            // 2) 匯入新收藏（保留 createdAtMillis）
                            list.distinctBy { it.id }.forEach { prefs.restoreFavorite(it) }

                            pendingImport = null
                            Toast.makeText(context, "已完成匯入收藏（已覆蓋）", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("覆蓋") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportOverwriteDialog = false
                        pendingImport = null
                    }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Divider()
}

@Composable
private fun ThemeColorRow(
    selectedArgb: Int?,
    onSelect: (Int?) -> Unit
) {
    val colors = listOf(
        0xFF5E35B1.toInt(), // Purple
        0xFF1565C0.toInt(), // Blue
        0xFF2E7D32.toInt(), // Green
        0xFF8D6E63.toInt(), // Brown
        0xFFC62828.toInt(), // Red
        0xFFAD1457.toInt()  // Pink
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { argb ->
            val isSelected = selectedArgb == argb
            ColorDot(
                color = Color(argb),
                selected = isSelected,
                onClick = { onSelect(argb) }
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "重置",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onSelect(null) }
        )
    }
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .size(34.dp)
            .border(width = 2.dp, color = ringColor, shape = CircleShape)
            .padding(3.dp)
            .background(color = color, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.bodyMedium,
                color = checkColor
            )
            Spacer(Modifier.height(24.dp))

        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    url: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            text = "↗",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Divider()
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * 匯出格式（JSON）：
 * {
 *   "version": 1,
 *   "favorites": [
 *     {"id":"123","createdAtMillis":1700000000000},
 *     ...
 *   ]
 * }
 */
private fun exportFavoritesJson(entries: List<FavoriteEntry>): String {
    val root = JSONObject()
    root.put("version", 1)

    val arr = JSONArray()
    entries.forEach { e ->
        val o = JSONObject()
        o.put("id", e.id)
        o.put("createdAtMillis", e.createdAtMillis)
        arr.put(o)
    }
    root.put("favorites", arr)
    return root.toString()
}

private fun parseFavoritesJson(text: String): List<FavoriteEntry> {
    val root = JSONObject(text)
    val arr = root.optJSONArray("favorites") ?: return emptyList()

    val list = ArrayList<FavoriteEntry>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optString("id").trim()
        if (id.isBlank()) continue
        val createdAt = o.optLong("createdAtMillis", 0L)
        list.add(FavoriteEntry(id = id, createdAtMillis = createdAt))
    }
    return list
}
