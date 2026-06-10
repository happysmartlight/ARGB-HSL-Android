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


/**
 * Dialog that lets the user map each Mock device found in the imported timecode file
 * (left) to a real registered WLED device (right). On confirm it returns a
 * mockId -> realIp map to the ViewModel, which bakes + compiles + uploads presets.json.
 */
@Composable
fun TimecodeMappingDialog(
    state: TimecodeImportUiState,
    devices: List<WledDevice>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    // mockId -> selected real device IP ("" = chưa gán)
    val selections = remember { mutableStateMapOf<String, String>() }
    val assignedCount = selections.values.count { it.isNotBlank() }

    Dialog(onDismissRequest = { if (!state.isProcessing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GÁN THIẾT BỊ TIMECODE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Tìm thấy ${state.mockDevices.size} thiết bị Mock. Gán mỗi Mock với một thiết bị thật để nạp playlist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider()

                if (devices.isEmpty()) {
                    Text(
                        text = "⚠️ Chưa có thiết bị thật nào trong danh sách. Hãy thêm/quét thiết bị trước khi gán.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Mapping rows (scrollable if many mock devices)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.mockDevices.forEach { mock ->
                        val selectedIp = selections[mock.id] ?: ""
                        // Devices already assigned to OTHER mocks — hide them from this row's
                        // dropdown so a real device can't be picked twice.
                        val takenByOthers = selections
                            .filterKeys { it != mock.id }
                            .values
                            .filter { it.isNotBlank() }
                            .toSet()
                        val availableDevices = devices.filter {
                            it.ipAddress == selectedIp || it.ipAddress !in takenByOthers
                        }
                        TimecodeMappingRow(
                            mock = mock,
                            devices = availableDevices,
                            selectedIp = selectedIp,
                            enabled = !state.isProcessing,
                            onSelect = { ip -> selections[mock.id] = ip }
                        )
                    }
                }

                HorizontalDivider()

                // Footer actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Đang nạp lên thiết bị...", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
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
    }
}

@Composable
internal fun TimecodeMappingRow(
    mock: TimecodeMockDevice,
    devices: List<WledDevice>,
    selectedIp: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.find { it.ipAddress == selectedIp }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left: mock device
        Column(modifier = Modifier.weight(1f)) {
            Text(mock.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${mock.id} • ${mock.clipCount} clip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))

        // Right: real device dropdown
        Box(modifier = Modifier.weight(1.2f)) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedDevice?.name ?: "— Chọn thiết bị —",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    color = if (selectedDevice != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
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
                                    device.ipAddress,
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

/**
 * Full, styled summary shown after a timecode upload finishes — one card per device
 * with success/failure state, clip count and total show duration.
 */
@Composable
fun TimecodeResultDialog(
    state: TimecodeImportUiState,
    onDismiss: () -> Unit
) {
    val results = state.results
    val successCount = results.count { it.success }
    val allOk = successCount == results.size
    val accent = if (allOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary

    val totalSec = state.totalSeconds.toInt()
    val durationText = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header with big status icon
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

                // Per-device result cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = rowColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                // mock → real device
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = result.mockName,
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
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (result.success)
                                        "✓ Đã nạp ${result.clipCount} clip vào playlist 249 • ${result.deviceIp}"
                                    else
                                        "✗ Lỗi: ${result.error} • ${result.deviceIp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Hint on what to do next when at least one device succeeded
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
    }
}
