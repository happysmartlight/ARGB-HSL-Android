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
import androidx.compose.ui.window.DialogProperties
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
import com.example.viewmodel.TimecodeAudioStatus
import com.example.viewmodel.TimecodeImportUiState
import com.example.viewmodel.TimecodeUploadResult
import com.example.ui.theme.LocalAppDimens
import java.text.SimpleDateFormat
import java.util.*

/** Dialog rộng theo màn: tablet (Expanded) dùng ~70% bề ngang để đủ chỗ thông tin. */
@Composable
internal fun TimecodeDialogFrame(
    onDismissRequest: () -> Unit,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimens = LocalAppDimens.current
    val widthFraction = when {
        dimens.isCompact -> 0.96f
        dimens.screenWidthDp >= 720 -> 0.72f
        else -> 0.92f
    }
    // Không cao quá màn hình (tablet nằm ngang thấp): phần danh sách bên trong co lại bằng weight,
    // để dòng tóm tắt + nút bấm luôn nhìn thấy.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = borderColor?.let { BorderStroke(1.dp, it) },
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .widthIn(max = 980.dp)
                .heightIn(max = maxHeight)
        ) {
            Column(
                modifier = Modifier.padding(dimens.cardPadding + 4.dp),
                verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing + 4.dp),
                content = content
            )
        }
    }
}

/**
 * Gán từng track trong file timecode (mock + mạch thật) với một thiết bị đã lưu. Track mạch thật
 * được ViewModel tự nhận sẵn theo MAC (mạch đổi IP vẫn nhận ra) hoặc IP; người dùng có thể đổi.
 * Xác nhận → trả về chỉ số track → IP thiết bị.
 */
@Composable
fun TimecodeMappingDialog(
    state: TimecodeImportUiState,
    devices: List<WledDevice>,
    onConfirm: (Map<Int, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val dimens = LocalAppDimens.current
    // track key -> IP thiết bị ("" = chưa gán). Khởi tạo theo gợi ý tự nhận.
    val selections = remember(state.tracks) {
        mutableStateMapOf<Int, String>().apply {
            state.tracks.forEach { t -> t.autoIp?.let { put(t.key, it) } }
        }
    }
    val assignedCount = selections.values.count { it.isNotBlank() }
    val realCount = state.tracks.count { !it.isMock }
    val mockCount = state.tracks.size - realCount

    TimecodeDialogFrame(onDismissRequest = { if (!state.isProcessing) onDismiss() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GÁN THIẾT BỊ TIMECODE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = buildString {
                        append("File có ${state.tracks.size} track")
                        val parts = listOfNotNull(
                            if (realCount > 0) "$realCount mạch thật" else null,
                            if (mockCount > 0) "$mockCount mock" else null
                        )
                        if (parts.isNotEmpty()) append(" (${parts.joinToString(", ")})")
                        append(". Mạch thật được tự nhận theo MAC — kiểm tra lại rồi bấm Nạp.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        state.audioFileName?.let { audio ->
            Text(
                text = "🎵 Nhạc của timeline: $audio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider()

        if (devices.isEmpty()) {
            Text(
                text = "⚠️ Chưa có thiết bị thật nào trong danh sách. Hãy thêm/quét thiết bị trước khi gán.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = if (dimens.isCompact) 380.dp else 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing + 2.dp)
        ) {
            state.tracks.forEach { track ->
                val selectedIp = selections[track.key] ?: ""
                // Mạch đã gán cho track KHÁC bị ẩn khỏi dropdown: 1 mạch chỉ nhận 1 track.
                val takenByOthers = selections
                    .filterKeys { it != track.key }
                    .values
                    .filter { it.isNotBlank() }
                    .toSet()
                val availableDevices = devices.filter {
                    it.ipAddress == selectedIp || it.ipAddress !in takenByOthers
                }
                TimecodeMappingRow(
                    track = track,
                    devices = availableDevices,
                    selectedIp = selectedIp,
                    enabled = !state.isProcessing,
                    onSelect = { ip -> selections[track.key] = ip }
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    state.progressNote ?: "Đang nạp lên thiết bị...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            } else {
                TextButton(onClick = onDismiss) { Text("Hủy") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(selections.toMap()) },
                    enabled = assignedCount > 0
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nạp ($assignedCount)")
                }
            }
        }
    }
}

@Composable
internal fun TimecodeMappingRow(
    track: TimecodeTrackItem,
    devices: List<WledDevice>,
    selectedIp: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.find { it.ipAddress == selectedIp }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val kindColor = if (track.isMock) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Trái: track trong file
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (track.isMock) "MOCK" else "MẠCH",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = kindColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(kindColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
                Text(
                    track.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = buildString {
                    append(track.sourceIp)
                    append(" • ${track.clipCount} clip")
                    if (track.recipeCount > 0) append(" • ${track.recipeCount} có nội dung preset")
                    if (track.pendingBakeCount > 0) append(" • ${track.pendingBakeCount} chưa ghi")
                },
                style = MaterialTheme.typography.bodySmall,
                color = dim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (track.mac.isNotEmpty()) {
                Text("MAC ${formatMac(track.mac)}", style = MaterialTheme.typography.bodySmall, color = dim)
            }
            track.matchNote?.let { note ->
                val matched = track.autoIp != null
                Text(
                    text = (if (matched) "✓ " else "⚠ ") + note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (matched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
        }

        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))

        // Phải: thiết bị thật
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedDevice?.name ?: "— Chọn thiết bị —",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        color = if (selectedDevice != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    selectedDevice?.let {
                        Text(
                            text = it.ipAddress + if (it.isOnline) "" else " • offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.isOnline) dim else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("— Bỏ gán —") },
                    onClick = { onSelect(""); expanded = false }
                )
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(device.name, fontWeight = FontWeight.Medium)
                                Text(
                                    buildString {
                                        append(device.ipAddress)
                                        device.product?.let { p ->
                                            append(" • ")
                                            append(p.removePrefix("ARGB HSL Controller").trim().ifEmpty { "1X" })
                                        }
                                        if (!device.isOnline) append(" • offline")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        },
                        onClick = { onSelect(device.ipAddress); expanded = false }
                    )
                }
            }
        }
    }
}

/** "a4cb8fe1ae9c" → "A4:CB:8F:E1:AE:9C". */
internal fun formatMac(mac: String): String =
    if (mac.length == 12) mac.chunked(2).joinToString(":").uppercase() else mac.uppercase()

/**
 * Tóm tắt sau khi nạp timecode: mỗi mạch 1 thẻ (thành công/lỗi, chi tiết đối chiếu preset,
 * cảnh báo), kèm trạng thái nhạc của timeline.
 */
@Composable
fun TimecodeResultDialog(
    state: TimecodeImportUiState,
    onPickAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val dimens = LocalAppDimens.current
    val results = state.results
    val successCount = results.count { it.success }
    val allOk = successCount == results.size
    val accent = if (allOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary

    val totalSec = state.totalSeconds.toInt()
    val durationText = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)

    TimecodeDialogFrame(onDismissRequest = onDismiss, borderColor = accent.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allOk) "NẠP TIMECODE THÀNH CÔNG" else "NẠP HOÀN TẤT (CÓ LỖI)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Đã nạp $successCount/${results.size} thiết bị • Thời lượng kịch bản $durationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = if (dimens.isCompact) 360.dp else 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            results.forEach { result ->
                val rowColor = if (result.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowColor.copy(alpha = 0.08f))
                        .border(1.dp, rowColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = rowColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = result.trackName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                            Text(
                                text = result.deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = rowColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Text(
                            text = if (result.success)
                                "✓ Đã nạp ${result.clipCount} clip vào playlist 249 • ${result.deviceIp}"
                            else
                                "✗ Lỗi • ${result.deviceIp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        result.details.forEach { line ->
                            Text("• $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        result.warnings.forEach { line ->
                            Text("⚠ $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        result.error?.let { err ->
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Nhạc của timeline (§4.7): file nằm trên máy tính → tự chọn nếu đã từng mở, không thì nhắc chọn.
        val audio = state.audioFileName
        if (audio != null && state.audioStatus != TimecodeAudioStatus.NONE) {
            val missing = state.audioStatus == TimecodeAudioStatus.MISSING
            val tone = if (missing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(tone.copy(alpha = 0.10f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when (state.audioStatus) {
                        TimecodeAudioStatus.AUTO_SELECTED -> "🎵 Đã tự chọn nhạc \"$audio\" (đã từng mở trên máy này)."
                        TimecodeAudioStatus.ALREADY_SELECTED -> "🎵 Nhạc \"$audio\" đang được chọn."
                        else -> "🎵 Timeline dùng nhạc \"$audio\" — chép file này vào máy rồi chọn để phát kèm."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (missing) {
                    OutlinedButton(onClick = onPickAudio, shape = RoundedCornerShape(10.dp)) {
                        Text("Chọn nhạc")
                    }
                }
            }
        }

        if (successCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    text = "Timeline biên đạo đã được reload tự động từ playlist 249. Nhấn \"CHẠY PLAYLIST TỔNG CHOREOGRAPHY\" để bắt đầu trình diễn đồng loạt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Đã hiểu")
            }
        }
    }
}
