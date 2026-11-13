package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color.HSVToColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.billing.ProSubscriptionManager
import com.example.billing.ProSubscriptionState
import com.example.data.*
import com.example.viewmodel.WledEffect
import com.example.viewmodel.WledPalette
import com.example.viewmodel.WledViewModel
import com.example.viewmodel.AddDeviceState
import com.example.viewmodel.DeviceImageFile
import com.example.viewmodel.FileCleanupUiState
import com.example.viewmodel.DevicePresetStorageStats
import com.example.viewmodel.PresetPurgeItem
import com.example.viewmodel.PresetPurgeUiState
import com.example.viewmodel.PresetDeleteAction
import com.example.viewmodel.PresetDeviceDeleteError
import com.example.viewmodel.PresetDeletePreview
import com.example.viewmodel.PlaylistPlaybackState
import com.example.viewmodel.ImageUploadMode
import com.example.viewmodel.ImageWriteMode
import com.example.viewmodel.ImageUploadUiState
import com.example.viewmodel.ImageUploadDeviceResult
import com.example.viewmodel.EditablePreset
import com.example.viewmodel.TimelineClip
import com.example.viewmodel.EditorUploadUiState
import com.example.viewmodel.TimecodeTrackItem
import com.example.viewmodel.TimecodeImportUiState
import com.example.viewmodel.TimecodeUploadResult
import com.example.ui.theme.LocalAppDimens
import java.text.SimpleDateFormat
import java.util.*


@Composable
internal fun PresetCapacityWarningCard(
    stats: DevicePresetStorageStats,
    deviceName: String?,
    modifier: Modifier = Modifier
) {
    val warnings = presetCapacityWarnings(stats)
    if (warnings.isEmpty()) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (deviceName == null) "CẢNH BÁO PRESET GẦN ĐẦY" else "$deviceName gần đầy preset",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error
            )
            warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
internal fun PresetCleanupPanel(
    stats: DevicePresetStorageStats,
    isBusy: Boolean,
    onOpenPurge: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "DỌN PRESET AN TOÀN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "Logo/ảnh ${stats.logoUsed} preset · Timecode ${stats.timecodeUsed}/${stats.timecodeCapacity} slot · Hệ thống ${stats.systemUsed}/${stats.systemCapacity} slot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )

            PresetDeleteActionButton(
                title = "XÓA PRESET + ẢNH…",
                subtitle = "Chọn preset cần xoá trên mạch này — ảnh BMP/GIF của preset bị xoá cùng, ảnh preset khác còn dùng được giữ lại",
                enabled = !isBusy,
                onClick = onOpenPurge
            )
        }
    }
}

@Composable
internal fun BulkPresetCleanupPanel(
    devices: List<WledDevice>,
    onlineStats: Map<Int, DevicePresetStorageStats>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onOpenPurge: () -> Unit
) {
    val onlineDevices = devices.filter { it.isOnline }
    val warningDevices = onlineDevices.mapNotNull { device ->
        val stats = onlineStats[device.id]
        if (stats != null && presetCapacityWarnings(stats).isNotEmpty()) device to stats else null
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DỌN PRESET AN TOÀN TẤT CẢ ONLINE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${onlineDevices.size} thiết bị online",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isBusy,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Tải lại thống kê preset", modifier = Modifier.size(18.dp))
                }
            }

            if (warningDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    warningDevices.take(4).forEach { (device, stats) ->
                        PresetCapacityWarningCard(
                            stats = stats,
                            deviceName = device.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (warningDevices.size > 4) {
                        Text(
                            text = "Còn ${warningDevices.size - 4} thiết bị khác cũng vượt ngưỡng 90%.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val totalLogo = onlineDevices.sumOf { onlineStats[it.id]?.logoUsed ?: 0 }
            val totalTimecode = onlineDevices.sumOf { onlineStats[it.id]?.timecodeUsed ?: 0 }
            Text(
                text = "Tổng online: Logo/ảnh $totalLogo preset · Timecode $totalTimecode slot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )

            PresetDeleteActionButton(
                title = "XÓA PRESET + ẢNH…",
                subtitle = "Chọn preset cần xoá trên mọi mạch online — ảnh BMP/GIF của preset bị xoá cùng, ảnh preset khác còn dùng được giữ lại",
                enabled = !isBusy && onlineDevices.isNotEmpty(),
                onClick = onOpenPurge
            )
        }
    }
}

@Composable
internal fun PresetDeleteActionButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.58f else 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun purgeGroupTitle(group: PresetCleanup.Group): String = when (group) {
    PresetCleanup.Group.LOGO -> "Logo / ảnh POI · Cờ LED (1–59)"
    PresetCleanup.Group.TIMECODE -> "Timecode (60–240, 256+)"
    PresetCleanup.Group.PLAYLIST -> "Playlist 249"
    PresetCleanup.Group.OTHER -> "Khác"
}

private fun purgeGroupChip(group: PresetCleanup.Group): String = when (group) {
    PresetCleanup.Group.LOGO -> "Logo/ảnh"
    PresetCleanup.Group.TIMECODE -> "Timecode"
    PresetCleanup.Group.PLAYLIST -> "Playlist 249"
    PresetCleanup.Group.OTHER -> "Khác"
}

/** Chip chọn nhanh cả nhóm (bấm lại để bỏ chọn). */
@Composable
private fun PurgeGroupChip(text: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, tint.copy(alpha = if (checked) 0.8f else 0.3f), RoundedCornerShape(50))
            .background(if (checked) MaterialTheme.colorScheme.error.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
    }
}

/**
 * Dialog "Xóa preset + ảnh" (tab Đồng Loạt ALL): tích chọn preset (gộp theo ID trên mọi mạch
 * online, chia nhóm), tuỳ chọn ảnh thừa, xác nhận 2 bước rồi xoá. Trên tablet danh sách chia
 * nhiều cột.
 */
@Composable
internal fun PresetPurgeDialog(
    state: PresetPurgeUiState,
    onConfirm: (Set<Int>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isVisible) return
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    TimecodeDialogFrame(
        onDismissRequest = { if (!state.isBusy) onDismiss() },
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    ) {
        when {
            state.isScanning || state.isDeleting -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isDeleting) "ĐANG XÓA PRESET + ẢNH" else "ĐANG ĐỌC PRESET TRÊN ${state.scopeLabel.uppercase()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (state.isDeleting) (state.progressNote ?: "")
                                   else "Đọc presets.json và danh sách ảnh trên từng mạch...",
                            style = MaterialTheme.typography.bodySmall,
                            color = dim
                        )
                    }
                }
            }

            state.resultMessage != null || state.error != null -> {
                val isError = state.error != null || state.resultErrors.isNotEmpty()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF00A86B)
                    )
                    Text(
                        text = when {
                            state.error != null -> "KHÔNG THỂ DỌN PRESET"
                            isError -> "ĐÃ DỌN (CÓ LỖI)"
                            else -> "ĐÃ XÓA PRESET + ẢNH"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = state.error ?: state.resultMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                state.resultLines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = dim) }
                PresetDeviceErrorList(state.resultErrors)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("Đã hiểu") }
                }
            }

            else -> PresetPurgePicker(state, onConfirm, onDismiss)
        }
    }
}

@Composable
private fun ColumnScope.PresetPurgePicker(
    state: PresetPurgeUiState,
    onConfirm: (Set<Int>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    var selected by remember(state.items) { mutableStateOf(emptySet<Int>()) }
    var withOrphans by remember(state.items) { mutableStateOf(false) }
    var confirming by remember(state.items) { mutableStateOf(false) }
    val groups = state.items.groupBy { it.group }
    val order = listOf(PresetCleanup.Group.LOGO, PresetCleanup.Group.TIMECODE, PresetCleanup.Group.PLAYLIST, PresetCleanup.Group.OTHER)
    val selectedItems = state.items.filter { it.pid in selected }
    val selectedImages = selectedItems.flatMap { it.images }.distinct().size

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "XÓA PRESET + ẢNH",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Chọn preset cần xoá trên ${state.scopeLabel}. Ảnh BMP/GIF của preset bị xoá được xoá cùng; " +
                    "ảnh mà preset khác còn dùng được giữ lại. Luôn chừa slot hệ thống 100, 248, 250.",
                style = MaterialTheme.typography.bodySmall,
                color = dim
            )
        }
    }

    state.scanWarnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    PresetDeviceErrorList(state.scanErrors)

    if (state.items.isEmpty() && state.orphanCount == 0) {
        Text("Không có preset hay ảnh thừa nào để xoá trên các mạch online. 🎉", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss) { Text("Đóng") }
        }
        return
    }

    // Chọn nhanh theo nhóm.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        order.forEach { g ->
            val ids = groups[g]?.map { it.pid }?.toSet() ?: return@forEach
            val all = selected.containsAll(ids)
            PurgeGroupChip("${purgeGroupChip(g)} · ${ids.size}", all, enabled = !confirming) {
                selected = if (all) selected - ids else selected + ids
            }
        }
        val allIds = state.items.map { it.pid }.toSet()
        TextButton(
            onClick = { selected = if (selected.containsAll(allIds)) emptySet() else allIds },
            enabled = !confirming && allIds.isNotEmpty()
        ) { Text(if (allIds.isNotEmpty() && selected.containsAll(allIds)) "Bỏ chọn hết" else "Chọn hết") }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        order.forEach { g ->
            val list = groups[g] ?: return@forEach
            item(span = { GridItemSpan(maxLineSpan) }, key = "h_$g") {
                Text(
                    text = "${purgeGroupTitle(g)} — ${list.size} preset",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(list, key = { it.pid }) { item ->
                val checked = item.pid in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (checked) MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        )
                        .clickable(enabled = !confirming) {
                            selected = if (checked) selected - item.pid else selected + item.pid
                        }
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        enabled = !confirming,
                        onCheckedChange = { on -> selected = if (on) selected + item.pid else selected - item.pid }
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "${item.pid} · ${item.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                if (item.images.isNotEmpty()) append("kèm ảnh ${item.images.joinToString { it.trimStart('/') }}")
                                if (item.images.isNotEmpty() && state.deviceCount > 1) append(" · ")
                                if (state.deviceCount > 1) append("${item.deviceCount}/${state.deviceCount} mạch")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = dim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (state.orphanCount > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                .clickable(enabled = !confirming) { withOrphans = !withOrphans }
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = withOrphans, enabled = !confirming, onCheckedChange = { withOrphans = it })
            Column(modifier = Modifier.weight(1f)) {
                Text("🧹 Ảnh thừa không thuộc preset nào", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${state.orphanCount} file · ${formatFileBytes(state.orphanBytes)}" +
                        if (state.deviceCount > 1) " trên ${state.orphanDevices} mạch" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim
                )
            }
        }
    }

    HorizontalDivider()

    val nothing = selected.isEmpty() && !withOrphans
    val summary = buildString {
        append("Sẽ xoá ${selected.size} preset")
        if (selectedImages > 0) append(" + tối đa $selectedImages ảnh đi kèm")
        if (withOrphans) append(" + ${state.orphanCount} ảnh thừa")
        append(" trên ${state.scopeLabel}.")
    }
    if (confirming) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Xác nhận xoá?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            Text(summary, style = MaterialTheme.typography.bodySmall)
            Text(
                "Không hoàn tác được trên mạch. App sao lưu presets.json cũ trong máy trước khi xoá.",
                style = MaterialTheme.typography.bodySmall,
                color = dim
            )
        }
    } else {
        Text(if (nothing) "Chưa chọn preset nào." else summary, style = MaterialTheme.typography.bodySmall, color = dim)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { if (confirming) confirming = false else onDismiss() }) {
            Text(if (confirming) "Quay lại" else "Hủy")
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { if (confirming) onConfirm(selected, withOrphans) else confirming = true },
            enabled = !nothing,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (confirming) "XÁC NHẬN XOÁ" else "Xóa ${selected.size} preset + ảnh")
        }
    }
}

@Composable
internal fun PresetDeviceErrorList(errors: List<PresetDeviceDeleteError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        errors.take(5).forEach { error ->
            Text(
                text = "${error.deviceName} (${error.deviceIp}): ${error.message}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (errors.size > 5) {
            Text(
                text = "Còn ${errors.size - 5} lỗi khác.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Định dạng kích thước file theo BYTE (file size từ /edit?list= trả về byte). */
internal fun formatFileBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        String.format(Locale.US, "%.1f KB", kb)
    } else {
        String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }
}

/**
 * Dialog "Tìm & dọn ảnh BMP/GIF": liệt kê file .bmp/.gif trên thiết bị, cho tích chọn
 * từng file rồi xóa. Dung lượng file hiển thị theo byte (khác fs.u/fs.t là KB).
 */
@Composable
internal fun FileCleanupDialog(
    state: FileCleanupUiState,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!state.isDeleting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dọn ảnh BMP/GIF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        state.deviceName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Đang liệt kê file trên thiết bị...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    state.error != null && state.files.isEmpty() -> {
                        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    state.files.isEmpty() -> {
                        Text(
                            text = "Không tìm thấy file .bmp/.gif nào trên thiết bị. 🎉",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    else -> {
                        val totalBytes = state.files.sumOf { it.sizeBytes }
                        val selBytes = state.files.filter { it.path in state.selected }.sumOf { it.sizeBytes }
                        val allSelected = state.selected.size == state.files.size

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSelectAll(!allSelected) }, enabled = !state.isDeleting) {
                                Text(if (allSelected) "Bỏ chọn tất cả" else "Chọn tất cả")
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${state.files.size} file · ${formatFileBytes(totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.files, key = { it.path }) { file ->
                                val checked = file.path in state.selected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                        .clickable(enabled = !state.isDeleting) { onToggle(file.path) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { if (!state.isDeleting) onToggle(file.path) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.path.removePrefix("/"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatFileBytes(file.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }

                        if (state.selected.isNotEmpty()) {
                            Text(
                                text = "Sẽ xóa ${state.selected.size} file · giải phóng ~${formatFileBytes(selBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (state.resultMessage != null) {
                    Text(state.resultMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (state.error != null && state.files.isNotEmpty()) {
                    Text(state.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !state.isDeleting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Đóng")
                    }
                    Button(
                        onClick = onDelete,
                        enabled = !state.isDeleting && state.selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Xóa đã chọn")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Định dạng dung lượng filesystem của WLED. Lưu ý: WLED trả về `info.fs.u` và
 * `info.fs.t` ĐÃ ở đơn vị kilobyte (KB), nên ở đây nhận thẳng KB (không chia 1024 nữa).
 */
internal fun formatDeviceStorageKb(kiloBytes: Long?): String {
    if (kiloBytes == null) return "N/A"
    val kb = kiloBytes.toDouble()
    return if (kb < 1024.0) {
        String.format(Locale.US, "%.0f KB", kb)
    } else {
        String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }
}

internal fun storageStatusText(percent: Int): String {
    return when {
        percent < 70 -> "Còn tốt"
        percent < 85 -> "Gần đầy"
        else -> "Cần dọn bộ nhớ"
    }
}

internal fun presetCapacityWarnings(stats: DevicePresetStorageStats): List<String> {
    if (stats.isLoading || stats.error != null) return emptyList()
    val warnings = mutableListOf<String>()
    val logoPercent = percentOf(stats.logoUsed, stats.logoCapacity)
    val timecodePercent = percentOf(stats.timecodeUsed, stats.timecodeCapacity)
    if (logoPercent >= 90) {
        warnings += "Logo/ảnh đã dùng ${stats.logoUsed}/${stats.logoCapacity} slot ($logoPercent%)."
    }
    if (timecodePercent >= 90) {
        warnings += "Timecode đã dùng ${stats.timecodeUsed}/${stats.timecodeCapacity} slot ($timecodePercent%)."
    }
    return warnings
}

internal fun percentOf(used: Int, capacity: Int): Int {
    if (capacity <= 0) return 0
    return ((used * 100f) / capacity).toInt()
}

internal fun summarizePresetIds(ids: List<Int>): String {
    if (ids.isEmpty()) return "Không có"
    if (ids.size <= 18) return ids.joinToString(", ")
    return ids.take(18).joinToString(", ") + "..."
}

internal fun WledInfo.veriInfo(): String {
    return "WS2812B/ARGB (Hệ thống điều hợp tự động)"
}
