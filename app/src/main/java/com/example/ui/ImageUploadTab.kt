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
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppDimens
import java.text.SimpleDateFormat
import java.util.*


@Composable
internal fun ImageUploadTab(
    devices: List<WledDevice>,
    onlineStats: Map<Int, DevicePresetStorageStats>,
    mode: ImageUploadMode,
    onModeChange: (ImageUploadMode) -> Unit,
    poiPixelsText: String,
    onPoiPixelsChange: (String) -> Unit,
    flagWidthText: String,
    onFlagWidthChange: (String) -> Unit,
    flagHeightText: String,
    onFlagHeightChange: (String) -> Unit,
    writeMode: ImageWriteMode,
    onWriteModeChange: (ImageWriteMode) -> Unit,
    selectedUris: List<Uri>,
    onPickImages: () -> Unit,
    onRemoveUri: (Uri) -> Unit,
    onClearUris: () -> Unit,
    deselectedDeviceIds: Set<Int>,
    onToggleDevice: (Int) -> Unit,
    uploadState: ImageUploadUiState,
    onUpload: () -> Unit,
    onDismissResult: () -> Unit
) {
    val strings = LocalAppStrings.current
    val dimens = LocalAppDimens.current
    val onlineDevices = devices.filter { it.isOnline }
    val selectedDeviceCount = onlineDevices.count { it.id !in deselectedDeviceIds }

    val poiPixels = poiPixelsText.toIntOrNull()
    val poiValid = poiPixels != null && poiPixels in 15..145
    val flagW = flagWidthText.toIntOrNull()
    val flagH = flagHeightText.toIntOrNull()
    val flagValid = flagW != null && flagW >= 1 && flagH != null && flagH >= 1
    val paramsValid = if (mode == ImageUploadMode.POI) poiValid else flagValid

    val isRunning = uploadState.isRunning
    val canUpload = !isRunning && selectedUris.isNotEmpty() && selectedDeviceCount > 0 && paramsValid

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Tiêu đề ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = strings.upCreatePreset,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
        }

        // ---- Chọn mode POI / Cờ LED ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UploadModeButton(
                label = strings.upPoiLabel,
                selected = mode == ImageUploadMode.POI,
                enabled = !isRunning,
                onClick = { onModeChange(ImageUploadMode.POI) },
                modifier = Modifier.weight(1f)
            )
            UploadModeButton(
                label = strings.upMatrixLabel,
                selected = mode == ImageUploadMode.FLAG,
                enabled = !isRunning,
                onClick = { onModeChange(ImageUploadMode.FLAG) },
                modifier = Modifier.weight(1f)
            )
        }

        // ---- Chọn ảnh ----
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickImages,
                        enabled = !isRunning,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.upPickImages, fontWeight = FontWeight.Bold)
                    }
                    if (selectedUris.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearUris,
                            enabled = !isRunning,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(strings.upClearAll)
                        }
                    }
                }
                if (selectedUris.isEmpty()) {
                    Text(
                        text = strings.upPickHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "${strings.upSelectedPrefix} ${selectedUris.size} ${strings.upImagesUnit}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    selectedUris.forEachIndexed { index, uri ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${index + 1}. ${uri.lastPathSegment ?: uri.toString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveUri(uri) },
                                enabled = !isRunning,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = strings.upRemoveImageCd, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ---- Tham số theo mode ----
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (mode == ImageUploadMode.POI) {
                    OutlinedTextField(
                        value = poiPixelsText,
                        onValueChange = { onPoiPixelsChange(it.filter { c -> c.isDigit() }.take(3)) },
                        label = { Text(strings.upPoiPixelsLabel) },
                        singleLine = true,
                        isError = poiPixelsText.isNotEmpty() && !poiValid,
                        enabled = !isRunning,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = strings.upPoiPixelsHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = flagWidthText,
                            onValueChange = { onFlagWidthChange(it.filter { c -> c.isDigit() }.take(3)) },
                            label = { Text(strings.upWidthLabel) },
                            singleLine = true,
                            isError = flagWidthText.isNotEmpty() && (flagW == null || flagW < 1),
                            enabled = !isRunning,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = flagHeightText,
                            onValueChange = { onFlagHeightChange(it.filter { c -> c.isDigit() }.take(3)) },
                            label = { Text(strings.upHeightLabel) },
                            singleLine = true,
                            isError = flagHeightText.isNotEmpty() && (flagH == null || flagH < 1),
                            enabled = !isRunning,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = strings.upMatrixHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }

        // ---- Chế độ ghi slot ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UploadModeButton(
                label = strings.upWriteAppend,
                selected = writeMode == ImageWriteMode.APPEND_EMPTY,
                enabled = !isRunning,
                onClick = { onWriteModeChange(ImageWriteMode.APPEND_EMPTY) },
                modifier = Modifier.weight(1f)
            )
            UploadModeButton(
                label = strings.upWriteOverwrite,
                selected = writeMode == ImageWriteMode.OVERWRITE_FROM_1,
                enabled = !isRunning,
                onClick = { onWriteModeChange(ImageWriteMode.OVERWRITE_FROM_1) },
                modifier = Modifier.weight(1f)
            )
        }
        if (writeMode == ImageWriteMode.OVERWRITE_FROM_1) {
            Text(
                text = strings.upOverwriteWarning,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }

        // ---- Chọn thiết bị online ----
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${strings.upTargetDevices} ($selectedDeviceCount/${onlineDevices.size} online)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onlineDevices.isEmpty()) {
                    Text(
                        text = strings.upNoOnline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    onlineDevices.forEach { device ->
                        val checked = device.id !in deselectedDeviceIds
                        val stats = onlineStats[device.id]
                        val freeLogo = if (stats != null) (stats.logoCapacity - stats.logoUsed).coerceAtLeast(0) else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isRunning) { onToggleDevice(device.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { if (!isRunning) onToggleDevice(device.id) },
                                enabled = !isRunning
                            )
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = freeLogo?.let { "${strings.upFreeSlot} $it" } ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // ---- Nút Upload ----
        Button(
            onClick = onUpload,
            enabled = canUpload,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.upUploading, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (mode == ImageUploadMode.POI) strings.upUploadPoi else strings.upUploadMatrix,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ---- Tiến độ / kết quả ----
        if (isRunning || uploadState.finished || uploadState.error != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRunning) {
                        val total = uploadState.total.coerceAtLeast(1)
                        LinearProgressIndicator(
                            progress = { (uploadState.completed.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "${uploadState.completed}/${uploadState.total} · ${uploadState.progressNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    uploadState.error?.let { err ->
                        Text(
                            text = "${strings.errorPrefix} $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uploadState.warnings.isNotEmpty()) {
                        uploadState.warnings.forEach { w ->
                            Text(
                                text = "• $w",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (uploadState.results.isNotEmpty()) {
                        uploadState.results.forEach { r ->
                            val parts = buildString {
                                append("OK ${r.ok}")
                                if (r.failed > 0) append(" · ${strings.errShort} ${r.failed}")
                                if (r.skipped > 0) append(" · ${strings.skippedShort} ${r.skipped}")
                                if (r.error != null) append(" · ${r.error}")
                            }
                            Text(
                                text = "${r.deviceName}: $parts",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.failed > 0 || r.error != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (uploadState.finished || uploadState.error != null) {
                        OutlinedButton(
                            onClick = onDismissResult,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(strings.close)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UploadModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.height(42.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.height(42.dp)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
