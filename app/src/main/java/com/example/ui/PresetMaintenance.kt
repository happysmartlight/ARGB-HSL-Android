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
import com.example.viewmodel.PresetBulkDeletePreview
import com.example.viewmodel.PresetBulkDeleteUiState
import com.example.viewmodel.PresetDeleteAction
import com.example.viewmodel.PresetDeviceDeleteError
import com.example.viewmodel.PresetDeletePreview
import com.example.viewmodel.PresetDeleteUiState
import com.example.viewmodel.PlaylistPlaybackState
import com.example.viewmodel.ImageUploadMode
import com.example.viewmodel.ImageWriteMode
import com.example.viewmodel.ImageUploadUiState
import com.example.viewmodel.ImageUploadDeviceResult
import com.example.viewmodel.EditablePreset
import com.example.viewmodel.TimelineClip
import com.example.viewmodel.EditorUploadUiState
import com.example.viewmodel.TimecodeMockDevice
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
    onAction: (PresetDeleteAction) -> Unit
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetDeleteActionButton(
                    title = "XÓA ALL PRESET LOGO/ẢNH",
                    subtitle = "Slot 1-59, chỉ xóa file ảnh không còn preset khác dùng",
                    enabled = !isBusy,
                    onClick = { onAction(PresetDeleteAction.LOGO_IMAGES) }
                )
                PresetDeleteActionButton(
                    title = "XÓA ALL PRESET THUỘC NHÓM PRESET",
                    subtitle = "Slot 60-240 và playlist 249, bỏ qua slot được giữ",
                    enabled = !isBusy,
                    onClick = { onAction(PresetDeleteAction.TIMECODE_GROUP) }
                )
                PresetDeleteActionButton(
                    title = "XÓA ALL PRESET TRỪ HỆ THỐNG",
                    subtitle = "Giữ slot 100, 248, 250; cho phép xóa playlist 249",
                    enabled = !isBusy,
                    onClick = { onAction(PresetDeleteAction.ALL_EXCEPT_SYSTEM) }
                )
            }
        }
    }
}

@Composable
internal fun BulkPresetCleanupPanel(
    devices: List<WledDevice>,
    onlineStats: Map<Int, DevicePresetStorageStats>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onAction: (PresetDeleteAction) -> Unit
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetDeleteActionButton(
                    title = "XÓA LOGO/ẢNH TRÊN TẤT CẢ ONLINE",
                    subtitle = "Mỗi mạch xóa slot 1-59 và file ảnh không còn dùng chung",
                    enabled = !isBusy && onlineDevices.isNotEmpty(),
                    onClick = { onAction(PresetDeleteAction.LOGO_IMAGES) }
                )
                PresetDeleteActionButton(
                    title = "XÓA NHÓM PRESET TRÊN TẤT CẢ ONLINE",
                    subtitle = "Mỗi mạch xóa slot 60-240 và playlist 249",
                    enabled = !isBusy && onlineDevices.isNotEmpty(),
                    onClick = { onAction(PresetDeleteAction.TIMECODE_GROUP) }
                )
                PresetDeleteActionButton(
                    title = "XÓA ALL PRESET ONLINE TRỪ HỆ THỐNG",
                    subtitle = "Giữ slot 100, 248, 250; cho phép xóa playlist 249",
                    enabled = !isBusy && onlineDevices.isNotEmpty(),
                    onClick = { onAction(PresetDeleteAction.ALL_EXCEPT_SYSTEM) }
                )
            }
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

@Composable
internal fun PresetDeleteDialog(
    state: PresetDeleteUiState,
    onConfirm: (PresetDeletePreview) -> Unit,
    onDismiss: () -> Unit
) {
    val hasContent = state.isPreparing || state.isDeleting || state.preview != null ||
        state.resultMessage != null || state.error != null
    if (!hasContent) return

    Dialog(onDismissRequest = { if (!state.isPreparing && !state.isDeleting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    state.isPreparing || state.isDeleting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Column {
                                Text(
                                    text = if (state.isDeleting) "ĐANG XÓA PRESET" else "ĐANG KIỂM TRA PRESET",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = if (state.isDeleting) "App đang xóa tuần tự và refresh lại bộ nhớ." else "App đang đọc presets.json mới nhất để tính số preset và file ảnh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            }
                        }
                    }

                    state.preview != null -> {
                        val preview = state.preview
                        val canDelete = preview.presetIds.isNotEmpty() || preview.fileRefs.isNotEmpty()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = presetDeleteTitle(preview.action),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "${preview.deviceName} · ${preview.deviceIp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            }
                        }

                        HorizontalDivider()

                        DeviceInfoRow("Preset sẽ xóa:", "${preview.presetIds.size} slot")
                        DeviceInfoRow("File ảnh sẽ xóa:", "${preview.fileRefs.size} file")
                        DeviceInfoRow("Slot được giữ:", preview.protectedSlots.joinToString(", "))
                        if (preview.presetIds.isNotEmpty()) {
                            Text(
                                text = "Slot mục tiêu: ${summarizePresetIds(preview.presetIds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                            )
                        }
                        Text(
                            text = presetDeleteSafetyNote(preview.action),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Hủy")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onConfirm(preview) },
                                enabled = canDelete,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (canDelete) "Xóa ngay" else "Không có gì để xóa")
                            }
                        }
                    }

                    else -> {
                        val isError = state.error != null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF00A86B)
                            )
                            Text(
                                text = if (isError) "THAO TÁC THẤT BẠI" else "ĐÃ DỌN PRESET",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = state.error ?: state.resultMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss) {
                                Text("Đã hiểu")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PresetBulkDeleteDialog(
    state: PresetBulkDeleteUiState,
    onConfirm: (PresetBulkDeletePreview) -> Unit,
    onDismiss: () -> Unit
) {
    val hasContent = state.isPreparing || state.isDeleting || state.preview != null ||
        state.resultMessage != null || state.error != null
    if (!hasContent) return

    Dialog(onDismissRequest = { if (!state.isPreparing && !state.isDeleting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    state.isPreparing || state.isDeleting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Column {
                                Text(
                                    text = if (state.isDeleting) "ĐANG XÓA PRESET ONLINE" else "ĐANG KIỂM TRA THIẾT BỊ ONLINE",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = if (state.isDeleting) "App đang xóa tuần tự trên từng thiết bị online." else "App đang đọc presets.json mới nhất trên từng mạch.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            }
                        }
                    }

                    state.preview != null -> {
                        val preview = state.preview
                        val totalPresets = preview.devicePreviews.sumOf { it.presetIds.size }
                        val totalFiles = preview.devicePreviews.sumOf { it.fileRefs.size }
                        val canDelete = totalPresets > 0 || totalFiles > 0

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = presetDeleteTitle(preview.action),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Áp dụng cho ${preview.devicePreviews.size} thiết bị online đã đọc được",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            }
                        }

                        HorizontalDivider()
                        DeviceInfoRow("Tổng preset sẽ xóa:", "$totalPresets slot")
                        DeviceInfoRow("Tổng file ảnh sẽ xóa:", "$totalFiles file")
                        DeviceInfoRow("Thiết bị lỗi khi kiểm tra:", "${preview.errors.size}")

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            preview.devicePreviews.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.deviceName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            item.deviceIp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                                        )
                                    }
                                    Text(
                                        text = "${item.presetIds.size} preset · ${item.fileRefs.size} file",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            PresetDeviceErrorList(preview.errors)
                        }

                        Text(
                            text = "Slot 100, 248, 250 luôn được giữ; playlist 249 sẽ bị xóa nếu nằm trong nhóm mục tiêu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) { Text("Hủy") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onConfirm(preview) },
                                enabled = canDelete,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (canDelete) "Xóa tất cả online" else "Không có gì để xóa")
                            }
                        }
                    }

                    else -> {
                        val isError = state.error != null
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF00A86B)
                            )
                            Text(
                                text = if (isError) "THAO TÁC THẤT BẠI" else "ĐÃ DỌN PRESET ONLINE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = state.error ?: state.resultMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        PresetDeviceErrorList(state.resultErrors)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDismiss) { Text("Đã hiểu") }
                        }
                    }
                }
            }
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

internal fun presetDeleteTitle(action: PresetDeleteAction): String {
    return when (action) {
        PresetDeleteAction.LOGO_IMAGES -> "XÓA ALL PRESET LOGO/ẢNH"
        PresetDeleteAction.TIMECODE_GROUP -> "XÓA ALL PRESET THUỘC NHÓM PRESET"
        PresetDeleteAction.ALL_EXCEPT_SYSTEM -> "XÓA ALL PRESET TRỪ HỆ THỐNG"
    }
}

internal fun presetDeleteSafetyNote(action: PresetDeleteAction): String {
    return when (action) {
        PresetDeleteAction.LOGO_IMAGES -> "Chỉ xóa slot 1-59 và file ảnh được nhóm này tham chiếu nếu không còn preset khác dùng."
        PresetDeleteAction.TIMECODE_GROUP -> "Xóa slot 60-240 và playlist 249; file ảnh dùng chung sẽ được giữ lại."
        PresetDeleteAction.ALL_EXCEPT_SYSTEM -> "Xóa mọi preset thường, cho phép xóa playlist 249, nhưng giữ slot 100, 248, 250 và file ảnh còn được slot được giữ tham chiếu."
    }
}

internal fun summarizePresetIds(ids: List<Int>): String {
    if (ids.isEmpty()) return "Không có"
    if (ids.size <= 18) return ids.joinToString(", ")
    return ids.take(18).joinToString(", ") + "..."
}

internal fun WledInfo.veriInfo(): String {
    return "WS2812B/ARGB (Hệ thống điều hợp tự động)"
}
