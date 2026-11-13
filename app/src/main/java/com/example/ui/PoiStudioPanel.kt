package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PoiGallery
import com.example.data.PoiGalleryItem
import com.example.ui.i18n.PoiUiStrings
import com.example.ui.i18n.poiStrings
import com.example.ui.theme.LocalAppDimens
import com.example.util.PoiSpinRenderer
import com.example.util.PoiTools
import com.example.util.SpinParams
import com.example.viewmodel.PoiDeviceRow
import com.example.viewmodel.PoiLevel
import com.example.viewmodel.PoiPreview
import com.example.viewmodel.PoiStudio
import com.example.viewmodel.PoiUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min

/** Tốc độ quay của hình xem trước: 1 vòng / 3 giây (như desktop). */
private const val PREVIEW_ROT_SPEED = 2 * PI / 3.0
private const val PREVIEW_MAX_RENDER = 520

/**
 * Công cụ POI (chế độ POI của tab "Upload POI & Cờ LED"). Màn rộng (tablet): thẻ mạch ở trên, 2 cột
 * [Xem trước + Gửi] | [Ảnh nguồn + Thông số], lưới preset ở dưới. Màn hẹp: xếp 1 cột.
 */
@Composable
internal fun PoiStudioPanel(
    poi: PoiStudio,
    onOpenPurge: (deviceId: Int) -> Unit
) {
    val s = poiStrings()
    val state by poi.state.collectAsStateWithLifecycle()
    val preview by poi.preview.collectAsStateWithLifecycle()
    val dimens = LocalAppDimens.current
    var showGallery by remember { mutableStateOf(false) }
    var pendingMany by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var spinning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { poi.onShown() }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) poi.loadImage(uri)
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) pendingMany = uris
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 760.dp
        val gap = dimens.itemSpacing + 4.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            PoiDevicesCard(state, s, wide, poi)

            val previewCard: @Composable ColumnScope.() -> Unit = {
                PoiPreviewCard(state, preview, spinning, onSpinChange = { spinning = it }, s = s)
                PoiSendCard(state, s, onSend = { poi.sendCurrent() }, onSendMany = { pickMany.launch("image/*") })
            }
            val controls: @Composable ColumnScope.() -> Unit = {
                PoiImageCard(state, s, poi, onOpen = { pickOne.launch("image/*") }, onGallery = { showGallery = true })
                PoiParamsCard(state, s, poi)
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), content = previewCard)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap), content = controls)
                }
            } else {
                previewCard()
                controls()
            }
            PoiPresetsCard(state, s, wide, poi, onOpenPurge)
        }
    }

    if (showGallery) {
        PoiGalleryDialog(
            poi = poi,
            favorites = state.favorites,
            onPick = { item -> poi.useGalleryItem(item); showGallery = false },
            onToggleFavorite = { poi.toggleFavorite(it) },
            onDismiss = { showGallery = false },
            s = s
        )
    }
    if (pendingMany.isNotEmpty()) {
        PoiSendManyDialog(
            imageCount = pendingMany.size,
            deviceCount = state.usableTargets.size,
            s = s,
            onConfirm = { overwrite -> poi.sendMany(pendingMany, overwrite); pendingMany = emptyList() },
            onDismiss = { pendingMany = emptyList() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Khung chung
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoiCard(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val dimens = LocalAppDimens.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing + 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun levelColor(level: PoiLevel): Color = when (level) {
    PoiLevel.OK -> Color(0xFF2EBD6B)
    PoiLevel.WARN -> Color(0xFFE3A44F)
    PoiLevel.ERR -> MaterialTheme.colorScheme.error
    PoiLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
}

@Composable
private fun Hint(text: String, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

/** 2 nút chọn 1 (segmented đơn giản, không dùng API thử nghiệm). */
@Composable
private fun TwoChoice(left: String, right: String, rightSelected: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val sel = isRight == rightSelected
            if (sel) {
                Button(onClick = { onSelect(isRight) }, enabled = enabled, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { onSelect(isRight) }, enabled = enabled, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Ô số có nút −/+ (đặt chính xác: lệch vài RPM là hình đã trôi). [autoLabel]: hiện khi giá trị = 0. */
@Composable
private fun NumberStepper(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    suffix: String = "",
    autoLabel: String? = null,
    onValue: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(if (autoLabel != null && value == 0) "" else value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValue((value - 1).coerceIn(range)) }, enabled = enabled && value > range.first) {
            Text("−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() }.take(3)
                text = digits
                digits.toIntOrNull()?.let { if (it in range) onValue(it) }
            },
            enabled = enabled,
            singleLine = true,
            placeholder = autoLabel?.let { { Text(it) } },
            suffix = if (suffix.isNotEmpty()) ({ Text(suffix, style = MaterialTheme.typography.labelSmall) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            modifier = Modifier.widthIn(min = 76.dp, max = 112.dp)
        )
        IconButton(onClick = { onValue((value + 1).coerceIn(range)) }, enabled = enabled && value < range.last) {
            Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mạch POI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoiDevicesCard(state: PoiUiState, s: PoiUiStrings, wide: Boolean, poi: PoiStudio) {
    PoiCard(
        title = s.devicesTitle + " · ${state.usableTargets.size}/${state.rows.count { it.usable }}",
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val allOn = state.rows.filter { it.usable }.all { it.id in state.targetIds }
                TextButton(onClick = { poi.setAllTargets(!allOn) }, enabled = !state.busy && state.rows.any { it.usable }) {
                    Text(if (allOn) s.selectNone else s.selectAll)
                }
                OutlinedButton(onClick = { poi.allOff() }, enabled = !state.busy && state.targetIds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(s.allOff)
                }
                IconButton(onClick = { poi.refreshAll() }, enabled = !state.busy) {
                    Icon(Icons.Default.Refresh, contentDescription = s.refresh)
                }
            }
        }
    ) {
        Hint(s.devicesSub)
        if (state.rows.isEmpty()) {
            Text(s.noDevices, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        val cols = if (wide) 2 else 1
        state.rows.chunked(cols).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { row ->
                    Box(Modifier.weight(1f)) {
                        PoiDeviceItem(
                            row = row,
                            selected = row.id == state.currentId,
                            checked = row.id in state.targetIds,
                            enabled = !state.busy,
                            s = s,
                            onSelect = { poi.selectDevice(row.id) },
                            onToggle = { poi.toggleTarget(row.id) },
                            onIdentify = { poi.identify(row.id) }
                        )
                    }
                }
                repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (state.hiddenNames.isNotEmpty()) {
            Hint("${s.hiddenPrefix} ${state.hiddenNames.size} ${s.hiddenSuffix}: ${state.hiddenNames.joinToString()}")
        }
    }
}

@Composable
private fun PoiDeviceItem(
    row: PoiDeviceRow,
    selected: Boolean,
    checked: Boolean,
    enabled: Boolean,
    s: PoiUiStrings,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onIdentify: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = if (row.usable) 0.62f else 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked && row.usable, onCheckedChange = { onToggle() }, enabled = enabled && row.usable)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    row.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (row.usable) MaterialTheme.colorScheme.onSurface else dim
                )
                if (selected) {
                    Text(
                        s.currentBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                if (row.loading) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
            val parts = buildList {
                if (!row.online) add(s.offline)
                row.fw?.let { add("FW $it") }
                row.wifi?.let { add("WiFi $it%") }
                row.fsPct?.let { add("💾 $it%") }
                row.tempC?.let { add(String.format(java.util.Locale.US, "🌡 %.1f°C", it)) }
                row.logoCount?.let { add("$it ${s.imagesUnit}") }
                if (row.block == null) add("BMP ≤ ${PoiTools.limitLabel(row.bmpLimit)}")
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = dim)
            when (row.block) {
                PoiTools.Block.FIRMWARE -> Hint("⚠ ${s.needFirmware}", MaterialTheme.colorScheme.error)
                PoiTools.Block.PRODUCT -> Hint("⚠ ${s.notSupported}", MaterialTheme.colorScheme.error)
                null -> {}
            }
        }
        OutlinedButton(
            onClick = onIdentify,
            enabled = enabled && row.online && !row.identifying,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (row.identifying) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Text("💡 ${s.identify}")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Xem trước
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoiPreviewCard(state: PoiUiState, preview: PoiPreview?, spinning: Boolean, onSpinChange: (Boolean) -> Unit, s: PoiUiStrings) {
    PoiCard(title = s.previewTitle) {
        TwoChoice(s.previewSpin, s.previewBmp, rightSelected = !spinning, enabled = true) { onSpinChange(!it) }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (preview == null) {
                Text(
                    s.previewEmpty,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                PoiSpinView(preview, spinning, Modifier.fillMaxSize())
            }
        }
        state.image?.let { img ->
            Text(
                buildString {
                    append(img.label)
                    append(" · ${img.width}×${img.height}")
                    if (img.fromDevice) append(" · ${s.fromDeviceNote}")
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Vòng quay POI (hoặc ảnh BMP khi [spinning] = false). Vẽ ở luồng nền, đổi 2 bitmap luân phiên. */
@Composable
private fun PoiSpinView(preview: PoiPreview, spinning: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val side = with(density) { min(maxWidth.roundToPx(), maxHeight.roundToPx()) }.coerceAtLeast(64)
        val renderSize = min(side, PREVIEW_MAX_RENDER)
        var frame by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(preview.version, renderSize, spinning) {
            if (!spinning) {
                frame = preview.bitmap.asImageBitmap()
                return@LaunchedEffect
            }
            val params = if (preview.disc) SpinParams.disc(preview.hole) else SpinParams.flat(preview.sectors)
            val renderer = withContext(Dispatchers.Default) { PoiSpinRenderer(preview.bitmap, renderSize, params) }
            val buffers = Array(2) { Bitmap.createBitmap(renderSize, renderSize, Bitmap.Config.ARGB_8888) }
            val pixels = IntArray(renderSize * renderSize)
            var index = 0
            var angle = 0.0
            var last = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.0 else ((now - last) / 1e9).coerceIn(0.0, 0.08)
                last = now
                angle = (angle + PREVIEW_ROT_SPEED * dt) % (2 * PI)
                val target = buffers[index]
                index = 1 - index
                val t0 = System.nanoTime()
                withContext(Dispatchers.Default) {
                    renderer.render(angle, pixels)
                    target.setPixels(pixels, 0, renderSize, 0, 0, renderSize, renderSize)
                }
                frame = target.asImageBitmap()
                val spentMs = (System.nanoTime() - t0) / 1_000_000
                delay((33 - spentMs).coerceAtLeast(0)) // ~30 FPS
            }
        }
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = if (spinning) FilterQuality.Low else FilterQuality.None
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ① Ảnh nguồn
// ─────────────────────────────────────────────────────────────────────────────

private object PoiThumbCache {
    val cache = LruCache<String, ImageBitmap>(160)
}

@Composable
private fun rememberAssetThumb(poi: PoiStudio, path: String): ImageBitmap? {
    // Luôn nạp theo [path] mới: danh sách đổi thứ tự thì ô cũ không được giữ ảnh của mẫu trước.
    val loaded by produceState(initialValue = PoiThumbCache.cache.get(path), path) {
        value = PoiThumbCache.cache.get(path)
            ?: withContext(Dispatchers.IO) { poi.loadAsset(path)?.asImageBitmap() }?.also { PoiThumbCache.cache.put(path, it) }
    }
    return loaded
}

@Composable
private fun PoiImageCard(state: PoiUiState, s: PoiUiStrings, poi: PoiStudio, onOpen: () -> Unit, onGallery: () -> Unit) {
    PoiCard(title = s.imageTitle) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onOpen, enabled = !state.busy, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.openImage, maxLines = 2, textAlign = TextAlign.Center)
            }
            if (!poi.gallery.isEmpty) {
                Button(
                    onClick = onGallery,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎨 ${s.gallery} (${poi.gallery.items.size})", maxLines = 2, textAlign = TextAlign.Center)
                }
            }
            if (state.image != null) {
                IconButton(onClick = { poi.clearImage() }, enabled = !state.busy) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        }
        if (state.image == null) Hint(s.noImage)
        val picks = remember(state.favorites, state.recents) { poi.gallery.quickPicks(state.favorites, state.recents) }
        if (picks.isNotEmpty()) {
            Hint(s.quickPicks)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                picks.forEach { item -> key(item.key) {
                    val selected = state.image?.galleryKey == item.key
                    val thumb = rememberAssetThumb(poi, item.thumb)
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.busy) { poi.useGalleryItem(item) }
                    ) {
                        thumb?.let { Image(it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                } }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ② Thông số
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParamLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun PoiParamsCard(state: PoiUiState, s: PoiUiStrings, poi: PoiStudio) {
    val p = state.params
    val busy = state.busy
    val fromDevice = state.image?.fromDevice == true
    val disc = state.sendShape == PoiTools.Shape.DISC
    PoiCard(title = s.paramsTitle) {
        // Pixel LED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ParamLabel(s.pixels)
                Hint(s.pixelsHint)
            }
            NumberStepper(p.pixels, PoiTools.PIXEL_MIN..PoiTools.PIXEL_MAX, !busy, suffix = "px") { poi.setPixels(it) }
        }

        // Độ sáng
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParamLabel(s.brightness)
            Spacer(Modifier.weight(1f))
            Text("${PoiTools.briToPercent(p.bri)}%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = p.bri.toFloat(),
            onValueChange = { poi.setBri(it.toInt()) },
            onValueChangeFinished = { poi.applyBrightness() },
            valueRange = PoiTools.BRI_MIN.toFloat()..PoiTools.POI_BRI_MAX.toFloat(),
            enabled = !busy
        )
        Hint(s.brightnessHint)

        // Dung lượng
        val size = state.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParamLabel(s.size)
            Spacer(Modifier.weight(1f))
            Text(
                if (state.image == null) "— / ${PoiTools.limitLabel(size.limit)}" else "${PoiTools.humanSize(size.bytes)} / ${PoiTools.limitLabel(size.limit)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { if (size.limit > 0) (size.bytes.toFloat() / size.limit).coerceIn(0f, 1f) else 0f },
            color = levelColor(size.level),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Hint(size.hint, levelColor(size.level))

        HorizontalDivider()

        // Kiểu hình
        ParamLabel(s.shape)
        TwoChoice(s.shapeFlat, s.shapeDisc, rightSelected = p.shape == PoiTools.Shape.DISC, enabled = !busy && !fromDevice) { right ->
            poi.setShape(if (right) PoiTools.Shape.DISC else PoiTools.Shape.FLAT)
        }
        Hint(if (fromDevice) s.shapeLocked else if (p.shape == PoiTools.Shape.DISC) s.shapeDiscHint else s.shapeFlatHint)

        if (disc) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ParamLabel(s.hole)
                Spacer(Modifier.weight(1f))
                Text("${p.hole}%", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = p.hole.toFloat(),
                onValueChange = { poi.setHole(it.toInt()) },
                valueRange = 0f..PoiTools.DISC_HOLE_MAX.toFloat(),
                steps = PoiTools.DISC_HOLE_MAX - 1,
                enabled = !busy
            )
            Hint(s.holeHint)
        }

        HorizontalDivider()

        // Tốc độ quay
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ParamLabel(s.speed)
                Text(
                    if (p.rpm <= 0) s.auto else "${p.rpm} RPM",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            NumberStepper(p.rpm, PoiTools.RPM_AUTO..PoiTools.RPM_MAX, !busy, suffix = "RPM", autoLabel = s.auto) { poi.setRpm(it) }
        }
        Slider(
            value = p.rpm.toFloat(),
            onValueChange = { poi.setRpm(it.toInt()) },
            valueRange = PoiTools.RPM_AUTO.toFloat()..PoiTools.RPM_MAX.toFloat(),
            enabled = !busy
        )
        if (!disc) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ParamLabel(s.imagesPerTurn)
                    Hint(s.imagesPerTurnHint)
                }
                NumberStepper(p.imagesPerTurn, 1..PoiTools.IMAGES_PER_TURN_MAX, !busy && p.rpm > 0) { poi.setImagesPerTurn(it) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ParamLabel(s.reverse)
                Hint(s.reverseHint)
            }
            Switch(checked = p.reverse, onCheckedChange = { poi.setReverse(it) }, enabled = !busy && p.rpm > 0)
        }
        Hint(state.speedHint, levelColor(state.speedHintLevel))
        if (state.runningPoi) {
            Hint(s.liveSpeedNote, MaterialTheme.colorScheme.primary)
            Button(
                onClick = { poi.saveSpeedToPreset() },
                enabled = !busy && state.speedUnsaved,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 ${s.saveSpeed}" + (state.runningPresetId?.let { " · ID $it" } ?: ""), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ③ Gửi
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoiSendCard(state: PoiUiState, s: PoiUiStrings, onSend: () -> Unit, onSendMany: () -> Unit) {
    val targets = state.usableTargets
    PoiCard(title = s.sendTitle) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSend,
                enabled = !state.busy && state.image != null && state.size.ok && targets.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)
            ) {
                Text("📤 ${s.sendThis(targets.size)}", fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }
            OutlinedButton(
                onClick = onSendMany,
                enabled = !state.busy && targets.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) {
                Text("📚 ${s.sendMany}", maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        when {
            targets.isEmpty() -> Hint(s.sendNoTargets, MaterialTheme.colorScheme.error)
            state.image == null -> Hint(s.sendNeedImage)
            else -> Hint("→ " + targets.joinToString { it.name })
        }
        state.progress?.let { pr ->
            LinearProgressIndicator(
                progress = { if (pr.total > 0) pr.done.toFloat() / pr.total else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Hint("${pr.done}/${pr.total} · ${pr.note}")
        }
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = levelColor(state.statusLevel),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(levelColor(state.statusLevel).copy(alpha = 0.08f))
                    .padding(10.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preset trên mạch
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoiPresetsCard(state: PoiUiState, s: PoiUiStrings, wide: Boolean, poi: PoiStudio, onOpenPurge: (Int) -> Unit) {
    val current = state.current
    PoiCard(
        title = s.presetsTitle + (current?.let { " · ${it.name}" } ?: ""),
        trailing = {
            if (state.presetsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TwoChoice(s.filterLogo, s.filterAll, rightSelected = state.showAllPresets, enabled = true) { poi.setShowAllPresets(it) }
            }
            OutlinedButton(
                onClick = { current?.let { onOpenPurge(it.id) } },
                enabled = !state.busy && current?.usable == true,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.deletePresets, color = MaterialTheme.colorScheme.error, maxLines = 1)
            }
        }
        Hint(s.presetsHint)
        if (state.presets.isEmpty() && !state.presetsLoading) Hint(s.presetsEmpty)
        val cols = if (wide) 3 else 2
        state.presets.chunked(cols).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { item ->
                    val run = item.id == state.runningPresetId
                    val badge = if (item.id in 1..59) Color(0xFFE3A44F) else Color(0xFF4F8DE3)
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (run) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                            .border(if (run) 2.dp else 1.dp, if (run) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.busy && current?.usable == true) { poi.playPreset(item.id) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
                            Text("${item.id}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF16202B))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (run) s.running + (item.speed?.let { " · $it" } ?: "") else item.speed ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (run) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thư viện mẫu
// ─────────────────────────────────────────────────────────────────────────────

private const val GROUP_FAV = "__fav__"

@Composable
private fun PoiGalleryDialog(
    poi: PoiStudio,
    favorites: List<String>,
    onPick: (PoiGalleryItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
    s: PoiUiStrings
) {
    val gallery = poi.gallery
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val items = remember(query, group, favorites) {
        when (group) {
            GROUP_FAV -> gallery.search(query).filter { it.key in favorites }
            else -> gallery.search(query, group)
        }
    }
    TimecodeDialogFrame(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎨 ${s.galleryTitle} · ${gallery.items.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = s.close) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(s.searchHint) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips = listOf<Pair<String?, String>>(null to "${s.groupAll} · ${gallery.items.size}", GROUP_FAV to "${s.groupFavorites} · ${favorites.size}") +
                gallery.groups.map { g -> g.id to "${g.name} · ${gallery.items.count { it.group == g.id }}" }
            chips.forEach { (id, label) ->
                val sel = group == id
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (sel) FontWeight.Black else FontWeight.Medium,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { group = id }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        if (items.isEmpty()) {
            Hint(s.galleryEmpty)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.key }) { item ->
                val fav = item.key in favorites
                Column(
                    Modifier.clickable { onPick(item) },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                        rememberAssetThumb(poi, item.thumb)?.let {
                            Image(it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        IconButton(onClick = { onToggleFavorite(item.key) }, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = s.favoriteCd,
                                tint = if (fav) Color(0xFFFF5C7A) else Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Text(item.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (item.shape == PoiTools.Shape.DISC) s.shapeDisc else s.shapeFlat) + " · " + gallery.groupName(item.group),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gửi nhiều ảnh
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PoiSendManyDialog(
    imageCount: Int,
    deviceCount: Int,
    s: PoiUiStrings,
    onConfirm: (overwrite: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var overwrite by remember { mutableStateOf(false) }
    TimecodeDialogFrame(onDismissRequest = onDismiss) {
        Text("📚 ${s.sendManyTitle}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(s.sendManyIntro(imageCount, deviceCount), style = MaterialTheme.typography.bodyMedium)
        listOf(false to (s.writeAppend to s.writeAppendHint), true to (s.writeOverwrite to s.writeOverwriteHint)).forEach { (value, texts) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (overwrite == value) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { overwrite = value }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = overwrite == value, onClick = { overwrite = value })
                Column {
                    Text(texts.first, fontWeight = FontWeight.Bold)
                    Hint(texts.second, if (value) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onConfirm(overwrite) }, enabled = deviceCount > 0) { Text("📤 ${s.send}") }
        }
    }
}
