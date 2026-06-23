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
fun ArgbHslLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Scale factor from original 108 coordinate system to Canvas size
        val scaleX = width / 108f
        val scaleY = height / 108f
        
        // Left interlocking ribbon path
        val pathLeft = androidx.compose.ui.graphics.Path().apply {
            moveTo(32.5f * scaleX, 72.5f * scaleY)
            lineTo(32.5f * scaleX, 40.5f * scaleY)
            lineTo(54.0f * scaleX, 27.5f * scaleY)
            lineTo(54.0f * scaleX, 45.5f * scaleY)
        }
        
        val leftBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFF43F5E), // Rose 500
                Color(0xFFD946EF), // Fuchsia 500
                Color(0xFF8B5CF6)  // Violet 500
            ),
            start = Offset(32.5f * scaleX, 72.5f * scaleY),
            end = Offset(54.0f * scaleX, 27.5f * scaleY)
        )
        
        // Right interlocking ribbon path
        val pathRight = androidx.compose.ui.graphics.Path().apply {
            moveTo(75.5f * scaleX, 35.5f * scaleY)
            lineTo(75.5f * scaleX, 67.5f * scaleY)
            lineTo(54.0f * scaleX, 80.5f * scaleY)
            lineTo(54.0f * scaleX, 62.5f * scaleY)
        }
        
        val rightBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF38BDF8), // Light Sky Blue
                Color(0xFF06B6D4), // Cyan 500
                Color(0xFF2563EB)  // Royal Blue 600
            ),
            start = Offset(75.5f * scaleX, 35.5f * scaleY),
            end = Offset(54.0f * scaleX, 80.5f * scaleY)
        )
        
        // Draw Left Path with rounded corners
        drawPath(
            path = pathLeft,
            brush = leftBrush,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 12f * scaleX,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        
        // Draw Right Path with rounded corners
        drawPath(
            path = pathRight,
            brush = rightBrush,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 12f * scaleX,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun CompactAppTopBar(
    devices: List<WledDevice>,
    selectedDevice: WledDevice?,
    isWideScreen: Boolean,
    horizontalPadding: Dp,
    proState: ProSubscriptionState,
    onPlayAll: () -> Unit,
    onUpgrade: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit
) {
    val strings = LocalAppStrings.current
    // Touch target tối thiểu thoải mái trên tablet (mục tiêu chính của app);
    // trước đây 30-31dp là quá nhỏ so với chuẩn Material (48dp).
    val barHeight = if (isWideScreen) 46.dp else 44.dp
    val iconButtonSize = if (isWideScreen) 40.dp else 36.dp
    val iconSize = if (isWideScreen) 20.dp else 18.dp
    val selectedName = selectedDevice?.name
    val onlineCount = devices.count { it.isOnline }

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(barHeight)
                .padding(horizontal = horizontalPadding.coerceAtMost(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ArgbHslLogo(modifier = Modifier.size(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "ARGB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = "HSL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                if (isWideScreen && selectedName != null) {
                    Text(
                        text = "  ${selectedName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Spacer(modifier = Modifier.width(2.dp))
                }
                if (isWideScreen && onlineCount > 0) {
                    Text(
                        text = "$onlineCount online",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00A86B),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            CompactTopIconButton(
                onClick = onPlayAll,
                imageVector = Icons.Default.PlayArrow,
                contentDescription = strings.cdPlayAll,
                modifier = Modifier.testTag("play_shortcut_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            if (proState.isPro) {
                Surface(
                    modifier = Modifier
                        .testTag("pro_status_badge")
                        .semantics { contentDescription = strings.cdManagePro }
                        .clickable(onClick = onUpgrade),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }
            } else {
                CompactTopIconButton(
                    onClick = onUpgrade,
                    imageVector = Icons.Default.Star,
                    contentDescription = strings.cdUpgradePro,
                    modifier = Modifier.testTag("pro_upgrade_button"),
                    iconSize = iconSize,
                    buttonSize = iconButtonSize
                )
            }
            // Thông tin app + Nhật ký hệ thống đã gom vào trang Cài đặt (bớt chật top bar)
            CompactTopIconButton(
                onClick = onSettings,
                imageVector = Icons.Default.Settings,
                contentDescription = strings.cdSettings,
                modifier = Modifier.testTag("app_settings_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            CompactTopIconButton(
                onClick = onRefresh,
                imageVector = Icons.Default.Refresh,
                contentDescription = strings.cdRefresh,
                modifier = Modifier.testTag("refresh_all_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            CompactTopIconButton(
                onClick = onAdd,
                imageVector = Icons.Default.Add,
                contentDescription = strings.cdAddDevice,
                modifier = Modifier.testTag("add_device_fab"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
        }
    }
}

@Composable
private fun CompactTopIconButton(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    buttonSize: Dp
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
internal fun CompactControlTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val strings = LocalAppStrings.current
    val tabs = listOf(
        strings.tabConfig,
        strings.tabAll,
        strings.tabUpload,
        strings.tabTimeline
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(if (selected) 0.62f else 1f)
                        .height(if (selected) 2.dp else 1.dp)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                            }
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WledManagerApp(viewModel: WledViewModel) {
    val strings = LocalAppStrings.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val unlinkedDiscoveredDevices by viewModel.unlinkedDiscoveredDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val activeDetails by viewModel.activeDeviceDetails.collectAsStateWithLifecycle()
    val proState by viewModel.proSubscriptionState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    // Các trang con mở dạng TRANG riêng (thay thế UI chính, không chồng dialog gây nặng máy)
    var showInfoScreen by remember { mutableStateOf(false) }
    var showLogsScreen by remember { mutableStateOf(false) }
    var showProScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val systemLogs by viewModel.systemLogs.collectAsStateWithLifecycle()
    val addDeviceState by viewModel.addDeviceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requirePro = { showProScreen = true }

    // Rộng màn hình để đưa ra phân giải Responsive Master-Detail
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 720
    val dimens = LocalAppDimens.current

    // Routing trang con: mỗi trang THAY THẾ hẳn UI chính (UI chính được giải phóng,
    // không chồng lớp). Thứ tự ưu tiên: Pro > Info > Logs > Cài đặt > UI chính.
    if (showProScreen) {
        ProScreen(
            state = proState,
            onPurchase = {
                val activity = context.findActivity()
                if (activity != null) {
                    viewModel.launchProPurchase(activity)
                } else {
                    android.widget.Toast
                        .makeText(context, "Không mở được Google Play từ màn hình hiện tại.", android.widget.Toast.LENGTH_LONG)
                        .show()
                }
            },
            onSelectPlan = { viewModel.selectProPlan(it) },
            onRestore = { viewModel.restoreProSubscription() },
            onRefresh = { viewModel.refreshProSubscription() },
            onManageSubscription = { openPlaySubscriptionCenter(context) },
            onDebugToggle = { viewModel.setDebugProEntitlement(!proState.isPro) },
            onBack = { showProScreen = false }
        )
    } else if (showInfoScreen) {
        InfoScreen(
            wifiSsid = viewModel.getWifiSsid(),
            wifiSignal = viewModel.getWifiSignalStrength(),
            deviceIp = viewModel.getLocalIpAddress(),
            onBack = { showInfoScreen = false }
        )
    } else if (showLogsScreen) {
        LogsScreen(
            logs = systemLogs,
            onClear = { viewModel.clearLogs() },
            onBack = { showLogsScreen = false }
        )
    } else if (showSettingsScreen) {
        SettingsScreen(
            language = appLanguage,
            onLanguageChange = { viewModel.setAppLanguage(it) },
            onOpenInfo = { showInfoScreen = true },
            onOpenLogs = { showLogsScreen = true },
            onBack = { showSettingsScreen = false }
        )
    } else Scaffold(
        topBar = {
            CompactAppTopBar(
                devices = devices,
                selectedDevice = selectedDevice,
                isWideScreen = isWideScreen,
                horizontalPadding = dimens.screenPadding,
                proState = proState,
                onPlayAll = {
                    if (!proState.isPro) {
                        requirePro()
                    } else {
                        viewModel.setSelectedTab(1)
                        if (selectedDevice == null) {
                            val target = devices.find { it.isOnline } ?: devices.firstOrNull()
                            if (target != null) {
                                viewModel.selectDevice(target)
                            }
                        }
                    }
                },
                onUpgrade = requirePro,
                onSettings = { showSettingsScreen = true },
                onRefresh = { viewModel.refreshAllDevices() },
                onAdd = { showAddDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isWideScreen) {
                // Wide Screen: Split Layout Side-by-Side
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(dimens.deviceListPaneWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        DeviceListSection(
                            devices = devices,
                            unlinkedDiscoveredDevices = unlinkedDiscoveredDevices,
                            selectedDevice = selectedDevice,
                            onSelectDevice = { viewModel.selectDevice(it) },
                            onDeleteDevice = { viewModel.deleteDevice(it) },
                            onTogglePower = { dev, on -> viewModel.togglePower(dev, on) },
                            onTogglePowerAll = { on -> viewModel.togglePowerAll(on) },
                            isPro = proState.isPro,
                            onRequirePro = requirePro,
                            onAddDeviceClick = { showAddDialog = true },
                            onQuickAddDevice = { name, ip -> viewModel.addDevice(name, ip) }
                        )
                    }

                    // Separation line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (selectedDevice != null) {
                            DeviceControlSection(
                                device = selectedDevice!!,
                                activeDetails = activeDetails,
                                viewModel = viewModel,
                                onBack = { viewModel.selectDevice(null) },
                                isWideScreen = true,
                                proState = proState,
                                onRequirePro = requirePro
                            )
                        } else {
                            EmptyControlState()
                        }
                    }
                }
            } else {
                // Single view layout: switch state between list and details
                AnimatedContent(
                    targetState = selectedDevice?.id,
                    transitionSpec = {
                        if (targetState != null) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> width } + fadeOut()
                        }
                    },
                    label = "ScreenTransition"
                ) { targetId ->
                    if (targetId == null) {
                        DeviceListSection(
                            devices = devices,
                            unlinkedDiscoveredDevices = unlinkedDiscoveredDevices,
                            selectedDevice = null,
                            onSelectDevice = { viewModel.selectDevice(it) },
                            onDeleteDevice = { viewModel.deleteDevice(it) },
                            onTogglePower = { dev, on -> viewModel.togglePower(dev, on) },
                            onTogglePowerAll = { on -> viewModel.togglePowerAll(on) },
                            isPro = proState.isPro,
                            onRequirePro = requirePro,
                            onAddDeviceClick = { showAddDialog = true },
                            onQuickAddDevice = { name, ip -> viewModel.addDevice(name, ip) }
                        )
                    } else {
                        val targetDevice = devices.find { it.id == targetId } ?: selectedDevice
                        if (targetDevice != null) {
                            DeviceControlSection(
                                device = targetDevice,
                                activeDetails = activeDetails,
                                viewModel = viewModel,
                                onBack = { viewModel.selectDevice(null) },
                                isWideScreen = false,
                                proState = proState,
                                onRequirePro = requirePro
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var ip by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = strings.addDeviceTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.addDeviceNameLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    OutlinedTextField(
                        value = ip,
                        onValueChange = { 
                            ip = it
                            showError = false
                        },
                        label = { Text(strings.addDeviceIpLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_ip_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (showError) {
                        Text(
                            text = strings.addDeviceValidationError,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = { showAddDialog = false }
                        ) {
                            Text(strings.cancel, color = MaterialTheme.colorScheme.outline)
                        }
                        Button(
                            onClick = {
                                if (name.isNotBlank() && ip.isNotBlank()) {
                                    viewModel.addDevice(name, ip)
                                    showAddDialog = false
                                } else {
                                    showError = true
                                }
                            },
                            modifier = Modifier.testTag("confirm_add_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(strings.addDeviceLink, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(addDeviceState) {
        if (addDeviceState is AddDeviceState.Success) {
            viewModel.resetAddDeviceState()
        }
    }

    when (val state = addDeviceState) {
        is AddDeviceState.Validating -> {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111625)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Đang xác thực thiết bị...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Hệ thống đang kiểm tra bảo mật phần cứng ARGB HSL Controller chính hãng Happy Smart Light.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
        is AddDeviceState.InvalidDevice -> {
            val invalidDeviceState = state as AddDeviceState.InvalidDevice
            Dialog(onDismissRequest = { viewModel.resetAddDeviceState() }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1115)),
                    border = BorderStroke(1.5.dp, Color(0xFFEF5350).copy(alpha = 0.8f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Cảnh Báo",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "THIẾT BỊ KHÔNG HỢP LỆ",
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Phát hiện thiết bị tại địa chỉ IP:\n${invalidDeviceState.ip}\nkhông phải sản phẩm chính hãng hoặc phần mềm mạch quá cũ.",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Để đảm bảo an toàn thiết bị, duy trì hiệu năng đồng bộ sân khấu và bảo vệ quyền lợi khách hàng, hệ thống từ chối liên kết và cho phép thiết bị không chính hãng hoạt động.\n\nVui lòng hệ liên hệ HAPPY SMART LIGHT để nâng cấp mới mạch nhận diện chính hãng ARGB HSL.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.resetAddDeviceState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Đóng hộp thoại", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        is AddDeviceState.ConnectionError -> {
            val connectionState = state as AddDeviceState.ConnectionError
            Dialog(onDismissRequest = { viewModel.resetAddDeviceState() }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111625)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Lỗi Kết Nối",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Không Thể Kết Nối Thiết Bị",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = connectionState.message,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.resetAddDeviceState() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Đã Hiểu", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        is AddDeviceState.DuplicateDevice -> {
            val duplicateState = state as AddDeviceState.DuplicateDevice
            Dialog(onDismissRequest = { viewModel.resetAddDeviceState() }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B11)),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD54F).copy(alpha = 0.8f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Trùng lặp",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "THIẾT BỊ ĐÃ TỒN TẠI",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Địa chỉ IP:\n${duplicateState.ip}\nđã tồn tại trong danh sách quản lý của thiết bị.",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = duplicateState.message,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.resetAddDeviceState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Đã hiểu", color = Color(0xFF1D1B11), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        else -> {}
    }

}







@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyControlState() {
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Chọn thiết bị",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Text(
                text = strings.emptyControlTitle,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text = strings.emptyControlBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TimelineStepItem(
    step: com.example.viewmodel.DevicePlaylistStep,
    pxPerSec: Float,
    isPlaylistRunning: Boolean,
    playlistElapsedSecondsState: androidx.compose.runtime.State<Float>
) {
    val stepWidthFloat = step.endSecond - step.startSecond
    val stepWidth = (stepWidthFloat * pxPerSec).dp
    val stepHue = (step.presetId * 43) % 360
    
    val isStepActive by remember(isPlaylistRunning) {
        derivedStateOf {
            isPlaylistRunning && (playlistElapsedSecondsState.value >= step.startSecond.toFloat() && playlistElapsedSecondsState.value < step.endSecond.toFloat())
        }
    }
    
    val isFinished by remember(isPlaylistRunning) {
        derivedStateOf {
            playlistElapsedSecondsState.value >= step.endSecond.toFloat()
        }
    }
    
    val stepProgress by remember(isPlaylistRunning) {
        derivedStateOf {
            if (!isPlaylistRunning || playlistElapsedSecondsState.value < step.startSecond) 0f
            else if (playlistElapsedSecondsState.value >= step.endSecond) 1f
            else {
                val elapsedInStep = playlistElapsedSecondsState.value - step.startSecond
                (elapsedInStep / stepWidthFloat).coerceIn(0f, 1f)
            }
        }
    }

    // High-Tech Dark Colors with Neon Accents
    val baseColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(stepHue.toFloat(), 0.5f, 0.4f))).copy(alpha = if (isFinished) 0.15f else 0.4f)
    val neonColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(stepHue.toFloat(), 0.8f, 1.0f)))
    val borderColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(stepHue.toFloat(), 0.6f, 0.6f))).copy(alpha = if (isFinished) 0.15f else 0.35f)
    
    val animatedBorderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isStepActive) neonColor else borderColor,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
    )
    
    val animatedScaleY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isStepActive) 1.15f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    )

    val activeTextColor = Color.White
    val inactiveTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isFinished) 0.3f else 0.7f)

    Box(
        modifier = Modifier
            .width(stepWidth)
            .fillMaxHeight()
            .padding(horizontal = 0.5.dp)
            .scale(scaleX = 1f, scaleY = animatedScaleY)
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = if (isStepActive) 1.5.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .drawBehind {
                drawRect(color = baseColor)
                
                // Draw neon sweep progress if active or finished
                if (stepProgress > 0f) {
                    val sweepWidth = size.width * stepProgress
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(neonColor.copy(alpha = 0.1f), neonColor.copy(alpha = if (isFinished) 0.2f else 0.7f)),
                            startX = 0f,
                            endX = sweepWidth
                        ),
                        size = androidx.compose.ui.geometry.Size(width = sweepWidth, height = size.height)
                    )
                    
                    // Draw scanning laser edge
                    if (!isFinished && isStepActive) {
                        drawLine(
                            color = Color.White,
                            start = Offset(sweepWidth, 0f),
                            end = Offset(sweepWidth, size.height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = step.presetName,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 7.5.sp,
                fontWeight = if (isStepActive) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isStepActive) activeTextColor else inactiveTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stepWidthFloat.toInt()}s",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 6.sp,
                color = if (isStepActive) activeTextColor.copy(alpha = 0.8f) else inactiveTextColor.copy(alpha = 0.6f)
            )
        }
    }
}

// Helpers
// --- Material icons không có trong bộ icons.core (tự dựng từ vector path chính thức,
// tránh phải kéo cả thư viện material-icons-extended nặng cho vài icon). ---
internal fun materialPathIcon(name: String, pathStr: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(pathData = addPathNodes(pathStr), fill = SolidColor(Color.Black))
    }.build()

internal val IconPower: ImageVector by lazy {
    materialPathIcon(
        "PowerSettingsNew",
        "M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42C17.99 7.86 19 9.81 19 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.19 1.01-4.14 2.58-5.42L6.17 5.17C4.23 6.82 3 9.26 3 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.74-1.23-5.18-3.17-6.83z"
    )
}

internal val IconLightMode: ImageVector by lazy {
    materialPathIcon(
        "LightMode",
        "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"
    )
}

internal val IconUnfoldMore: ImageVector by lazy {
    materialPathIcon(
        "UnfoldMore",
        "M12 5.83L15.17 9l1.41-1.41L12 3 7.41 7.59 8.83 9 12 5.83zm0 12.34L8.83 15l-1.41 1.41L12 21l4.59-4.59L15.17 15 12 18.17z"
    )
}

fun rgbToHexFromHSV(h: Float, s: Float, v: Float): String {
    val intColor = HSVToColor(floatArrayOf(h, s, v))
    return String.format(Locale.US, "#%02X%02X%02X", (intColor shr 16) and 0xFF, (intColor shr 8) and 0xFF, intColor and 0xFF)
}

/**
 * Bánh xe màu hiện đại (HSV wheel) như app đèn thông minh.
 * Góc trên đĩa = Sắc độ (Hue), bán kính tính từ tâm = Độ bão hòa (Saturation).
 * Kéo/chạm để chọn; [onChange] bắn liên tục khi kéo, [onChangeFinished] khi nhả tay
 * (để gọi network 1 lần, tránh spam thiết bị WLED).
 *
 * @param value chỉ dùng để làm tối overlay cho khớp độ sáng màu — không thay đổi value.
 */
@Composable
internal fun ColorWheelPicker(
    hue: Float,
    saturation: Float,
    onChange: (hue: Float, sat: Float) -> Unit,
    onChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 240.dp
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { wheelSize.toPx() } / 2f

    // Coroutine của pointerInput(Unit) sống dai (không restart). rememberUpdatedState giữ
    // một State ổn định nhưng luôn trỏ tới lambda MỚI NHẤT, nên dù khối gesture cũ vẫn
    // chạy, nó vẫn ghi vào đúng state hiện hành sau khi màu được đồng bộ lại từ thiết bị.
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinished by rememberUpdatedState(onChangeFinished)

    // Sắc độ chạy quanh đĩa theo chiều kim đồng hồ (khớp atan2 với hệ toạ độ y hướng xuống).
    val hueColors = listOf(
        Color.Red, Color.Yellow, Color.Green,
        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )
    val thumbColor = Color(HSVToColor(floatArrayOf(hue, saturation, 1f)))

    // Cập nhật hue/sat từ vị trí chạm (px, gốc trái-trên của Canvas).
    fun update(pos: Offset) {
        val dx = pos.x - radiusPx
        val dy = pos.y - radiusPx
        val sat = (hypot(dx, dy) / radiusPx).coerceIn(0f, 1f)
        var ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (ang < 0f) ang += 360f
        currentOnChange(ang, sat)
    }

    Box(
        modifier = modifier
            .size(wheelSize)
            .pointerInput(Unit) {
                // Một gesture duy nhất: chạm xuống → cập nhật ngay; di chuyển → kéo mượt
                // liên tục; nhả tay → gọi network 1 lần. Không dùng touch-slop nên kéo
                // ăn ngay từ pixel đầu tiên.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    down.consume()
                    var pointerUp = false
                    while (!pointerUp) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            update(change.position)
                            change.consume()
                        } else {
                            pointerUp = true
                        }
                    }
                    currentOnFinished()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f

            // 1) Vành sắc độ
            drawCircle(
                brush = Brush.sweepGradient(hueColors, center = center),
                radius = r,
                center = center
            )
            // 2) Độ bão hòa: trắng ở tâm, mờ dần ra rìa
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
            // 4) Viền mảnh cho gọn
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = r,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            // 5) Con trỏ (thumb)
            val ang = Math.toRadians(hue.toDouble())
            val tr = saturation.coerceIn(0f, 1f) * r
            val tx = center.x + (tr * cos(ang)).toFloat()
            val ty = center.y + (tr * sin(ang)).toFloat()
            drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = 13.dp.toPx(), center = Offset(tx, ty))
            drawCircle(color = Color.White, radius = 11.dp.toPx(), center = Offset(tx, ty))
            drawCircle(color = thumbColor, radius = 8.dp.toPx(), center = Offset(tx, ty))
        }
    }
}

/** Ô hiển thị giá trị màu (Sắc độ / Bão hòa) gọn dưới bánh xe. */
@Composable
internal fun ColorStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Nút đổi nhanh thiết bị ngay trên header — bấm mở danh sách các mạch để chuyển
 * mà không cần Back ra màn danh sách (tối ưu cho màn dọc 9:16).
 */
@Composable
internal fun DeviceSwitcher(
    devices: List<WledDevice>,
    current: WledDevice,
    onSelect: (WledDevice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (current.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Text(
                    text = current.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Icon(
                    imageVector = IconUnfoldMore,
                    contentDescription = "Đổi thiết bị",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            devices.forEach { d ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (d.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                            )
                            Text(
                                text = d.name,
                                fontWeight = if (d.id == current.id) FontWeight.Bold else FontWeight.Normal
                            )
                            if (d.id == current.id) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(d)
                    }
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Không có lịch sử"
    val sdf = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}


/**
 * A neon-styled action button matching the Happy Smart Light cyber theme.
 * Layered for a complete glass-neon look: outer colored glow → dark glass body with a
 * vertical neon sheen → gradient neon rim → top gloss highlight → icon in a glowing
 * badge + neon label. Press feedback dims the glow and slightly shrinks the button.
 */
@Composable
fun NeonActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    neonColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val buttonHeight = LocalAppDimens.current.buttonHeight
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(if (pressed) 0.955f else 1f, label = "neonScale")
    val glow by animateDpAsState(if (pressed) 3.dp else 16.dp, label = "neonGlow")
    val rimAlpha by animateFloatAsState(if (pressed) 1f else 0.85f, label = "neonRim")

    // Dark glass base so the neon reads like a lit sign rather than a flat tint.
    val glassTop = Color(0xFF1A1A2B)
    val glassBottom = Color(0xFF0C0C16)

    Box(
        modifier = modifier
            .scale(scale)
            .height(buttonHeight)
            .shadow(elevation = glow, shape = shape, spotColor = neonColor, ambientColor = neonColor)
            .clip(shape)
            // 1) Dark glass body
            .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 2) Neon sheen rising from the bottom edge
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to neonColor.copy(alpha = 0.10f),
                        1f to neonColor.copy(alpha = 0.34f)
                    )
                )
        )
        // 3) Top gloss highlight for a glassy finish
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.4f to Color.Transparent
                    )
                )
        )
        // 4) Gradient neon rim
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            neonColor.copy(alpha = rimAlpha),
                            neonColor.copy(alpha = rimAlpha * 0.45f)
                        )
                    ),
                    shape = shape
                )
        )
        // 5) Content: glowing icon badge + neon label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(neonColor.copy(alpha = 0.16f))
                    .border(1.dp, neonColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = neonColor, modifier = Modifier.size(17.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Black,
                color = neonColor,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
