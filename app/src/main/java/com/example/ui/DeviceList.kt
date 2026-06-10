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
fun DeviceListSection(
    devices: List<WledDevice>,
    unlinkedDiscoveredDevices: List<WledDiscoveryManager.DiscoveredDevice>,
    selectedDevice: WledDevice?,
    onSelectDevice: (WledDevice) -> Unit,
    onDeleteDevice: (WledDevice) -> Unit,
    onTogglePower: (WledDevice, Boolean) -> Unit,
    onTogglePowerAll: (Boolean) -> Unit,
    isPro: Boolean,
    onRequirePro: () -> Unit,
    onAddDeviceClick: () -> Unit,
    onQuickAddDevice: (String, String) -> Unit
) {
    val strings = LocalAppStrings.current
    val dimens = LocalAppDimens.current
    val isPortrait = LocalConfiguration.current.screenWidthDp < 720

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isPortrait) {
            ArgbHslLogo(
                modifier = Modifier
                    .size((dimens.screenWidthDp * 0.66f).dp.coerceIn(180.dp, 300.dp))
                    .alpha(0.12f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.screenPadding)
        ) {
        if (unlinkedDiscoveredDevices.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.dlFoundAuto} (${unlinkedDiscoveredDevices.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextButton(
                    onClick = {
                        unlinkedDiscoveredDevices.forEach { discovered ->
                            onQuickAddDevice(discovered.name, discovered.ipAddress)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.dlAddAll,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(unlinkedDiscoveredDevices) { discovered ->
                    Card(
                        modifier = Modifier
                            .width(180.dp)
                            .testTag("discovered_card_${discovered.ipAddress}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Text(
                                    text = discovered.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = discovered.ipAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { onQuickAddDevice(discovered.name, discovered.ipAddress) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = strings.dlQuickAddCd,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = strings.dlActiveDevices,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val onlineCount = devices.count { it.isOnline }
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$onlineCount ${strings.deviceUnit}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (devices.isNotEmpty()) {
                    val allOn = devices.all { it.isOn }
                    val anyOn = devices.any { it.isOn }
                    val powerLabel = when {
                        allOn -> strings.dlAllOn
                        !anyOn -> strings.dlAllOff
                        else -> strings.dlPartialOn
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = powerLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (anyOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = allOn,
                            onCheckedChange = { isChecked ->
                                if (isPro) {
                                    onTogglePowerAll(isChecked)
                                } else {
                                    onRequirePro()
                                }
                            },
                            modifier = Modifier
                                .testTag("toggle_all_switch")
                                .semantics {
                                    contentDescription = strings.dlToggleAllCd
                                    stateDescription = powerLabel
                                }
                                .scale(0.85f)
                        )
                    }
                }
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.dlEmptyTitle,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Text(
                        text = strings.dlEmptyTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = strings.dlEmptyBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onAddDeviceClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Text(strings.dlTapToLink, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { isVisible = true }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVisible,
                        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -100 }) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { 100 }) + androidx.compose.animation.fadeOut()
                    ) {
                        val isSelected = selectedDevice?.id == device.id
                        DeviceRowCard(
                            device = device,
                            isSelected = isSelected,
                            onSelect = { onSelectDevice(device) },
                            onDelete = { onDeleteDevice(device) },
                            onTogglePower = { onTogglePower(device, it) }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun DeviceRowCard(
    device: WledDevice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onTogglePower: (Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    val infiniteTransition = rememberInfiniteTransition(label = "PulsingGlow")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Glowing border color depending on device hex color representation if online
    val targetCardBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else if (device.isOnline) {
        try { Color(android.graphics.Color.parseColor(device.hexColor)).copy(alpha = 0.5f) } catch (e: Exception) { MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) }
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    val animatedBorderColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetCardBorderColor,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "borderColorAnim"
    )

    val targetCardBg = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    }

    val animatedBgColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetCardBg,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "bgColorAnim"
    )

    val cardBorder = BorderStroke(if (isSelected) 1.5.dp else 1.dp, animatedBorderColor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("device_card_${device.id}"),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Glowing Circle
            Box(
                modifier = Modifier
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (device.isOnline) {
                    val statusColor = if (device.isOn) {
                        try { Color(android.graphics.Color.parseColor(device.hexColor)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                    } else {
                        Color.Gray
                    }
                    // Outer pulse
                    if (device.isOn) {
                        Box(
                            modifier = Modifier
                                .size((16 * scalePulse).dp)
                                .background(statusColor.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                    // Inner color
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }

            // Name & IP Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (device.isOnline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val statusText = if (device.isOnline) strings.online else strings.offline
                    val statusColor = if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    if (device.isOnline && device.wifiSignal != null) {
                        Text(
                            text = "📶 ${device.wifiSignal}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Simple quick power toggle directly on row
            if (device.isOnline) {
                Switch(
                    checked = device.isOn,
                    onCheckedChange = onTogglePower,
                    modifier = Modifier
                        .scale(0.8f)
                        .semantics {
                            contentDescription = "Bật tắt ${device.name}"
                            stateDescription = if (device.isOn) "Đang bật" else "Đang tắt"
                        }
                        .testTag("device_switch_${device.id}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            } else {
                Text(
                    text = "OFFLINE",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Delete action button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp).testTag("delete_device_${device.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = strings.dlUnpairCd,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
