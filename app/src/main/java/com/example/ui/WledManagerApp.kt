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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.example.viewmodel.TimecodeMockDevice
import com.example.viewmodel.TimecodeImportUiState
import com.example.viewmodel.TimecodeUploadResult
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
    onInfo: () -> Unit,
    onLogs: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit
) {
    val barHeight = if (isWideScreen) 38.dp else 40.dp
    val iconButtonSize = if (isWideScreen) 30.dp else 31.dp
    val iconSize = if (isWideScreen) 16.dp else 17.dp
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
                contentDescription = "Đồng loạt (All)",
                modifier = Modifier.testTag("play_shortcut_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            if (proState.isPro) {
                Surface(
                    modifier = Modifier
                        .testTag("pro_status_badge")
                        .semantics { contentDescription = "Quản lý Pro" }
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
                    contentDescription = "Nâng cấp Pro",
                    modifier = Modifier.testTag("pro_upgrade_button"),
                    iconSize = iconSize,
                    buttonSize = iconButtonSize
                )
            }
            CompactTopIconButton(
                onClick = onInfo,
                imageVector = Icons.Default.Info,
                contentDescription = "Thông tin ứng dụng",
                modifier = Modifier.testTag("app_info_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            CompactTopIconButton(
                onClick = onLogs,
                imageVector = Icons.Default.Build,
                contentDescription = "Nhật ký hệ thống",
                modifier = Modifier.testTag("app_logs_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            CompactTopIconButton(
                onClick = onRefresh,
                imageVector = Icons.Default.Refresh,
                contentDescription = "Tải lại danh sách",
                modifier = Modifier.testTag("refresh_all_button"),
                iconSize = iconSize,
                buttonSize = iconButtonSize
            )
            CompactTopIconButton(
                onClick = onAdd,
                imageVector = Icons.Default.Add,
                contentDescription = "Thêm thiết bị",
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
private fun CompactControlTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        "Cấu Hình Mạch",
        "Đồng Loạt (All)",
        "Nút Custom"
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
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val unlinkedDiscoveredDevices by viewModel.unlinkedDiscoveredDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val activeDetails by viewModel.activeDeviceDetails.collectAsStateWithLifecycle()
    val proState by viewModel.proSubscriptionState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    val systemLogs by viewModel.systemLogs.collectAsStateWithLifecycle()
    val addDeviceState by viewModel.addDeviceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requirePro = { showProDialog = true }

    // Rộng màn hình để đưa ra phân giải Responsive Master-Detail
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 720
    val dimens = LocalAppDimens.current

    Scaffold(
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
                onInfo = { showInfoDialog = true },
                onLogs = { showLogsDialog = true },
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
                            .width(340.dp)
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

    if (showProDialog) {
        ProSubscriptionDialog(
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
            onRestore = { viewModel.restoreProSubscription() },
            onRefresh = { viewModel.refreshProSubscription() },
            onManageSubscription = { openPlaySubscriptionCenter(context) },
            onDebugToggle = { viewModel.setDebugProEntitlement(!proState.isPro) },
            onDismiss = { showProDialog = false }
        )
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
                        text = "Thêm Thiết Bị ARGB HSL",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên thiết bị (ví dụ: LED Tivi)") },
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
                        label = { Text("Địa chỉ IP (ví dụ: 192.168.1.15)") },
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
                            text = "Vui lòng nhập đầy đủ Tên và địa chỉ IP hợp lệ!",
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
                            Text("Hủy", color = MaterialTheme.colorScheme.outline)
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
                            Text("Liên kết", color = MaterialTheme.colorScheme.onPrimary)
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

    if (showInfoDialog) {
        val wifiSsid = viewModel.getWifiSsid()
        val wifiSignal = viewModel.getWifiSignalStrength()
        val deviceIp = viewModel.getLocalIpAddress()
        Dialog(onDismissRequest = { showInfoDialog = false }) {
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ArgbHslLogo(
                        modifier = Modifier.size(72.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ARGB",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "HSL",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Happy Smart Light",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Phiên bản:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v1.2.0 (Premium VN)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Nhà sản xuất:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Happy Smart Light",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Website:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "happysmartlight.com",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        )

                        Text(
                            text = "THÔNG TIN KẾT NỐI",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mạng đang kết nối:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = wifiSsid,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sóng/Tín hiệu:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = wifiSignal,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "IP thiết bị chạy app:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = deviceIp,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )

                    Text(
                        text = "Giải pháp chiếu sáng ARGB đồng bộ âm nhạc, điều khiển màu sắc mượt mà và không giới hạn hiệu ứng từ HSL.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Button(
                        onClick = { showInfoDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("close_info_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Đóng", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }

    if (showLogsDialog) {
        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        Dialog(onDismissRequest = { showLogsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121824)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(550.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Nhật Ký Hệ Thống",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        IconButton(
                            onClick = { showLogsDialog = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = Color.LightGray
                            )
                        }
                    }

                    Text(
                        text = "Nhật ký lưu trữ các lệnh điều khiển kể từ khi mở app. Tự động dọn dẹp sau 3 ngày để tiết kiệm dung lượng thiết bị.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0A0F1D), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (systemLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Không có nhật ký nào.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(systemLogs) { log ->
                                    val timeStr = dateFormat.format(Date(log.timestamp))
                                    val levelColor = when (log.level) {
                                        "ERROR" -> Color(0xFFEF5350)
                                        "WARN" -> Color(0xFFFFB74D)
                                        else -> Color(0xFF81C784)
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "[$timeStr]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Cyan
                                        )
                                        
                                        Text(
                                            text = log.level,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = levelColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(48.dp)
                                        )
                                        
                                        Text(
                                            text = log.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearLogs() },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFFEF5350)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dọn Sạch")
                        }

                        Button(
                            onClick = { showLogsDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Đóng")
                        }
                    }
                }
            }
        }
    }
}

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
                    text = "TÌM THẤY TỰ ĐỘNG (${unlinkedDiscoveredDevices.size})",
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
                            text = "Thêm Tất Cả",
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
                                        contentDescription = "Thêm nhanh",
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
                text = "THIẾT BỊ HOẠT ĐỘNG",
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
                        text = "$onlineCount thiết bị",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (devices.isNotEmpty()) {
                    val allOn = devices.all { it.isOn }
                    val anyOn = devices.any { it.isOn }
                    val powerLabel = when {
                        allOn -> "Đang bật hết"
                        !anyOn -> "Đang tắt hết"
                        else -> "Đang bật lẻ"
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
                                    contentDescription = "Bật tắt toàn bộ thiết bị"
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
                        contentDescription = "Chưa có thiết bị",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Chưa kết nối thiết bị LED nào",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Thêm thiết bị ARGB HSL mới bằng địa chỉ IP cục bộ của bạn để bắt đầu điều khiển.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onAddDeviceClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Text("Nhấn vào đây để liên kết", color = MaterialTheme.colorScheme.primary)
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
                    val statusText = if (device.isOnline) "Trực tuyến" else "Ngoại tuyến"
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
                    contentDescription = "Hủy ghép nồi",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun DeviceControlSection(
    device: WledDevice,
    activeDetails: WledResponse?,
    viewModel: WledViewModel,
    onBack: () -> Unit,
    isWideScreen: Boolean,
    proState: ProSubscriptionState,
    onRequirePro: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val configScrollState = rememberScrollState()
    val allScrollState = rememberScrollState()
    val customScrollState = rememberScrollState()
    val scrollState = when (selectedTab) {
        0 -> configScrollState
        1 -> allScrollState
        else -> customScrollState
    }

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val activePresetStats by viewModel.activeDevicePresetStats.collectAsStateWithLifecycle()
    val presetDeleteState by viewModel.presetDeleteState.collectAsStateWithLifecycle()
    val bulkPresetDeleteState by viewModel.bulkPresetDeleteState.collectAsStateWithLifecycle()
    val fileCleanupState by viewModel.fileCleanupState.collectAsStateWithLifecycle()

    val dimens = LocalAppDimens.current

    val syncPlaylistIdInput = "249"
    var syncBrightnessAll by remember { mutableFloatStateOf(128f) }
    var pxPerSec by remember { mutableFloatStateOf(39.5f) }

    val context = LocalContext.current

    LaunchedEffect(device.id) {
        if (device.isOnline) {
            viewModel.refreshActiveDeviceMemoryAndPresetStats(device)
        }
    }

    val timecodeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val jsonString = inputStream?.bufferedReader().use { reader -> reader?.readText() }
                if (jsonString != null) {
                    viewModel.prepareTimecodeImport(jsonString)
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Lỗi đọc file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Timecode import → mock-to-real device mapping
    val timecodeImportState by viewModel.timecodeImportState.collectAsStateWithLifecycle()

    // Short error/validation messages (parse error, "chưa gán"...) still go through a toast.
    LaunchedEffect(timecodeImportState.resultMessage) {
        val msg = timecodeImportState.resultMessage
        if (!msg.isNullOrBlank() && !timecodeImportState.showDialog) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearTimecodeResult()
        }
    }

    if (timecodeImportState.showDialog) {
        TimecodeMappingDialog(
            state = timecodeImportState,
            devices = devices,
            onConfirm = { mapping -> viewModel.confirmTimecodeMapping(mapping) },
            onDismiss = { viewModel.cancelTimecodeImport() }
        )
    }

    // Rich, full result summary after the upload completes.
    if (timecodeImportState.showResult && timecodeImportState.results.isNotEmpty()) {
        TimecodeResultDialog(
            state = timecodeImportState,
            onDismiss = { viewModel.clearTimecodeResult() }
        )
    }

    PresetDeleteDialog(
        state = presetDeleteState,
        onConfirm = { preview -> viewModel.confirmPresetDeletion(device, preview.action) },
        onDismiss = { viewModel.dismissPresetDeletionDialog() }
    )

    PresetBulkDeleteDialog(
        state = bulkPresetDeleteState,
        onConfirm = { preview -> viewModel.confirmPresetDeletionForOnlineDevices(preview.action) },
        onDismiss = { viewModel.dismissBulkPresetDeletionDialog() }
    )

    if (fileCleanupState.isVisible) {
        FileCleanupDialog(
            state = fileCleanupState,
            onToggle = { path -> viewModel.toggleFileCleanupSelection(path) },
            onSelectAll = { select -> viewModel.setAllFileCleanupSelection(select) },
            onDelete = { viewModel.deleteSelectedDeviceFiles(device) },
            onDismiss = { viewModel.dismissFileCleanup() }
        )
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var name = "Âm thanh tùy chọn"
            var durationSec = 120
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIdx)
                    }
                }
            } catch (e: Exception) {}

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val timeString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = timeString?.toLongOrNull() ?: 120000L
                durationSec = (durationMs / 1000).toInt()
                retriever.release()
            } catch (e: Exception) {
                try {
                    val mp = android.media.MediaPlayer.create(context, uri)
                    if (mp != null) {
                        durationSec = mp.duration / 1000
                        mp.release()
                    }
                } catch (ex: Exception) {}
            }
            viewModel.setCustomAudio(uri, name, durationSec)
        }
    }

    // Use a stable key of which devices are online (by ID and IP) to avoid re-triggering on background ping updates
    val onlineDevicesKey = remember(devices) {
        devices.filter { it.isOnline }.joinToString(",") { "${it.id}_${it.ipAddress}" }
    }

    LaunchedEffect(selectedTab, syncPlaylistIdInput, onlineDevicesKey) {
        if (selectedTab == 1) {
            val playlistId = syncPlaylistIdInput.toIntOrNull() ?: 249
            kotlinx.coroutines.delay(300) // Debounce typing input
            viewModel.fetchTimelinesForAllDevices(playlistId)
        }
    }

    LaunchedEffect(selectedTab, onlineDevicesKey) {
        if (selectedTab == 1) {
            viewModel.refreshOnlineDevicePresetStats()
        }
    }

    // HSV Color state for advanced picker
    var hsvHue by remember(device.id, device.hexColor) {
        val floatArr = FloatArray(3)
        try {
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(device.hexColor), floatArr)
        } catch (e: Exception) {
            floatArr[0] = 0f; floatArr[1] = 1f; floatArr[2] = 1f
        }
        mutableFloatStateOf(floatArr[0])
    }
    var hsvSat by remember(device.id, device.hexColor) {
        val floatArr = FloatArray(3)
        try {
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(device.hexColor), floatArr)
        } catch (e: Exception) {
            floatArr[0] = 0f; floatArr[1] = 1f; floatArr[2] = 1f
        }
        mutableFloatStateOf(floatArr[1])
    }
    var hsvVal by remember(device.id, device.hexColor) {
        val floatArr = FloatArray(3)
        try {
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(device.hexColor), floatArr)
        } catch (e: Exception) {
            floatArr[0] = 0f; floatArr[1] = 1f; floatArr[2] = 1f
        }
        mutableFloatStateOf(floatArr[2])
    }

    // Effect params (speed, intensity)
    var effectSpeed by remember(device.id) { mutableFloatStateOf(128f) }
    var effectIntensity by remember(device.id) { mutableFloatStateOf(128f) }

    val currentColor = remember(hsvHue, hsvSat, hsvVal) {
        val c = HSVToColor(floatArrayOf(hsvHue, hsvSat, hsvVal))
        Color(c)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (!isWideScreen) {
            ArgbHslLogo(
                modifier = Modifier
                    .size(260.dp)
                    .alpha(0.12f)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Control Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isWideScreen) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại danh sách",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                val (title, subtitle) = when (selectedTab) {
                    0 -> Pair("CẤU HÌNH ĐƠN MẠCH", "Test màu & xem cấu hình từng thiết bị")
                    1 -> Pair("ĐỒNG LOẠT TOÀN BỘ (All)", "Điều khiển đồng thời tất cả các mạch")
                    else -> Pair("KỊCH BẢN & NÚT CUSTOM", "Hành động tùy chỉnh toàn hệ thống")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isWideScreen) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick device switcher — đổi nhanh sang mạch khác mà không cần Back
            // (chỉ hiện ở các tab điều khiển 1 thiết bị, khi có nhiều hơn 1 mạch)
            if (devices.size > 1 && selectedTab == 0) {
                DeviceSwitcher(
                    devices = devices,
                    current = device,
                    onSelect = { viewModel.selectDevice(it) }
                )
            }
        }

        // Segment Tabs Control
        CompactControlTabRow(
            selectedTab = selectedTab,
            onTabSelected = viewModel::setSelectedTab
        )

        if (!device.isOnline && selectedTab == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Ngoại tuyến",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Thiết Bị Đang Ngoại Tuyến",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Vui lòng mở nguồn điện của LED ARGB, kiểm tra đường truyền WiFi nội bộ (phải chung dải mạng) và nhấn Tải Lại ở góc phải màn hình.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Main control page area based on selected tabs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
            ) {
                // Master Brightness Slider (Sáng - Tắt) (Only show for single-device controlling tabs)
                if (selectedTab == 0) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(IconLightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "ĐỘ SÁNG TỔNG (Master Brightness)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    text = "${((device.brightness / 255f) * 100).toInt()}%",
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            var tempSliderBri by remember(device.id, device.brightness) { mutableStateOf(device.brightness.toFloat()) }
                            Slider(
                                value = tempSliderBri,
                                onValueChange = { tempSliderBri = it },
                                onValueChangeFinished = { viewModel.updateBrightness(device, tempSliderBri.toInt()) },
                                valueRange = 0f..255f,
                                modifier = Modifier.testTag("brightness_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                if (!proState.isPro && selectedTab != 0) {
                    val lockedTitle = if (selectedTab == 1) {
                        "Đồng Loạt là tính năng Pro"
                    } else {
                        "Nút Custom là tính năng Pro"
                    }
                    val lockedDescription = if (selectedTab == 1) {
                        "Mở khóa điều khiển toàn bộ mạch, timeline biên đạo theo nhạc, import timecode và dọn preset hàng loạt."
                    } else {
                        "Mở khóa khu vực phím tắt/custom action để chuẩn bị các kịch bản vận hành sân khấu nhanh."
                    }
                    ProLockedFeaturePanel(
                        state = proState,
                        title = lockedTitle,
                        description = lockedDescription,
                        onUpgrade = onRequirePro,
                        onRestore = { viewModel.restoreProSubscription() }
                    )
                } else {
                when (selectedTab) {
                    0 -> {
                        // Current device info box
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = device.ipAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = if (device.isOnline) "Trực tuyến" else "Ngoại tuyến",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (device.isOnline && device.wifiSignal != null) {
                                            Text(
                                                text = "📶 ${device.wifiSignal}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                // Quick Power toggle for this device
                                Button(
                                    onClick = { viewModel.togglePower(device, !device.isOn) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (device.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = IconPower,
                                        contentDescription = "Nguồn LED",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (device.isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (device.isOn) "TẮT" else "MỞ",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = if (device.isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        // Section: Colors & Presets
                        // Visual Color Preview Card — glowing swatch + hex/RGB readout
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Glowing color swatch
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = currentColor, ambientColor = currentColor)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(currentColor.copy(alpha = 0.95f), currentColor)
                                            )
                                        )
                                        .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                )

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "MÀU CHỦ ĐẠO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                    Text(
                                        text = device.hexColor.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "RGB " +
                                            "${(currentColor.red * 255).toInt()}, " +
                                            "${(currentColor.green * 255).toInt()}, " +
                                            "${(currentColor.blue * 255).toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        // Grid of quick preset colors
                        Text(
                            text = "Màu Nhanh (Presets)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val quickColors = listOf(
                            Pair("Đỏ", "#FF0000"), Pair("Cam", "#FF5E00"), Pair("Vàng", "#FFD600"),
                            Pair("Lục", "#00FF00"), Pair("Lam", "#00FFFF"), Pair("Biển", "#0000FF"),
                            Pair("Tím", "#B000FF"), Pair("Trắng", "#FFFFFF"), Pair("Đen (Tắt)", "#000000")
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (dimens.screenWidthDp >= 720) 5 else 3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(quickColors) { (name, hex) ->
                                val colorItem = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = device.hexColor.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                            else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.updateColor(device, hex)
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(colorItem)
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        if (hex == "#FFFFFF") MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                        else Color.White.copy(alpha = 0.2f)
                                                    ),
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Modern Color Wheel Picker (sắc độ + bão hòa trên đĩa tròn)
                        Text(
                            text = "Bộ Trộn Màu (Bánh Xe)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                ColorWheelPicker(
                                    hue = hsvHue,
                                    saturation = hsvSat,
                                    onChange = { h, s ->
                                        hsvHue = h
                                        hsvSat = s
                                        // Nếu màu đang tối thui (Value≈0) thì nâng sáng lên
                                        // để màu vừa chọn trên bánh xe hiển thị được.
                                        if (hsvVal <= 0.01f) hsvVal = 1f
                                    },
                                    onChangeFinished = {
                                        viewModel.updateColor(device, rgbToHexFromHSV(hsvHue, hsvSat, hsvVal))
                                    },
                                    wheelSize = if (dimens.screenWidthDp >= 720) 280.dp else 240.dp
                                )

                                // Readout chips: Sắc độ & Bão hòa
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ColorStatChip(label = "Sắc độ (Hue)", value = "${hsvHue.toInt()}°", modifier = Modifier.weight(1f))
                                    ColorStatChip(label = "Bão hòa (Sat)", value = "${(hsvSat * 100).toInt()}%", modifier = Modifier.weight(1f))
                                }

                                // Value slider (độ sáng của màu) — track gradient đen → màu hiện tại
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Độ sáng màu (Value)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text("${(hsvVal * 100).toInt()}%", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color.Black,
                                                        Color(HSVToColor(floatArrayOf(hsvHue, hsvSat, 1f)))
                                                    )
                                                )
                                            )
                                    )
                                    Slider(
                                        value = hsvVal,
                                        onValueChange = { hsvVal = it },
                                        onValueChangeFinished = {
                                            viewModel.updateColor(device, rgbToHexFromHSV(hsvHue, hsvSat, hsvVal))
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.Transparent,
                                            inactiveTrackColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "THÔNG TIN THIẾT BỊ",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Tên dòng sản phẩm:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.product ?: "Mạch ARGB HSL", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Thương hiệu:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(activeDetails?.info?.brand ?: "ARGB Happy Smart Light", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Phiên bản FW:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.fwVersion ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Mã định danh (VID):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.vid?.toString() ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Địa chỉ IP truy cập:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.ipAddress, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }

                                DeviceInfoDivider()
                                DeviceMemoryInfoRows(
                                    fs = activeDetails?.info?.fs,
                                    wifiSignal = activeDetails?.info?.wifi?.signal ?: device.wifiSignal,
                                    stats = activePresetStats,
                                    onFindImagesToDelete = { viewModel.openDeviceFileCleanup(device) }
                                )
                                
                                val info = activeDetails?.info
                                if (info != null) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tên tùy chỉnh client (CN):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text(info.cn ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    val leds = info.leds
                                    if (leds != null) {
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Số lượng bóng LED:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text(leds.count?.toString() ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Dòng tiêu thụ hiện tại (pwr):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text("${leds.pwr ?: 0} mA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Dòng tối đa thiết lập (maxpwr):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text("${leds.maxpwr ?: 0} mA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Tốc độ khung hình (FPS):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            val fpsVal = leds.fps ?: 0
                                            if (fpsVal == 0) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("0 FPS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF1744))
                                                    Text(
                                                        text = "MODE POI ĐANG ĐƯỢC KÍCH HOẠT",
                                                        fontWeight = FontWeight.Black,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFFF1744)
                                                    )
                                                }
                                            } else {
                                                Text("$fpsVal FPS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        PresetCleanupPanel(
                            stats = activePresetStats,
                            isBusy = presetDeleteState.isPreparing || presetDeleteState.isDeleting,
                            onAction = { action -> viewModel.preparePresetDeletion(device, action) }
                        )
                    }

                    1 -> {
                        val isPlaylistRunning by viewModel.isPlaylistRunning.collectAsStateWithLifecycle()
                        val playlistPlaybackState by viewModel.playlistPlaybackState.collectAsStateWithLifecycle()
                        val playlistElapsedSecondsState = viewModel.playlistElapsedSeconds.collectAsStateWithLifecycle()
                        val playlistElapsedSeconds by playlistElapsedSecondsState
                        val playlistTotalSeconds by viewModel.playlistTotalSeconds.collectAsStateWithLifecycle()
                        val playlistName by viewModel.playlistName.collectAsStateWithLifecycle()
                        val playlistStepsCount by viewModel.playlistStepsCount.collectAsStateWithLifecycle()
                        val devicesTimelines by viewModel.devicesTimelines.collectAsStateWithLifecycle()
                        val isLoadingTimelines by viewModel.isLoadingTimelines.collectAsStateWithLifecycle()
                        val activeStepsMap by viewModel.activeStepsMap.collectAsStateWithLifecycle()
                        val isTimelineLocked by viewModel.isTimelineLocked.collectAsStateWithLifecycle()
                        val isChoreographyMode by viewModel.isChoreographyMode.collectAsStateWithLifecycle()
                        val onlinePresetStats by viewModel.onlineDevicePresetStats.collectAsStateWithLifecycle()
                        val selectedAudioName by viewModel.selectedAudioName.collectAsStateWithLifecycle()
                        val selectedAudioUri by viewModel.selectedAudioUri.collectAsStateWithLifecycle()
                        val audioHistory by viewModel.audioHistory.collectAsStateWithLifecycle()

                        // Section: Synchronized Stage Controls (Multi-Device Parallel Control)
                        Text(
                            text = "ĐIỀU KHIỂN ĐỒNG LOẠT (STAGE CONTROL)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        // 1. Parallel Power Synced Actions (Turn ON All, Turn OFF All)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "NGUỒN SÂN KHẤU SONG SONG",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    NeonActionButton(
                                        text = "MỞ TOÀN BỘ",
                                        icon = Icons.Default.PlayArrow,
                                        neonColor = MaterialTheme.colorScheme.primary, // Cyan neon
                                        onClick = { viewModel.togglePowerAll(true) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NeonActionButton(
                                        text = "TẮT TOÀN BỘ",
                                        icon = Icons.Default.Close,
                                        neonColor = MaterialTheme.colorScheme.secondary, // Magenta neon
                                        onClick = { viewModel.togglePowerAll(false) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 2. Synchronized Brightness Slider
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(IconLightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "TĂNG GIẢM SÁNG ĐỒNG LOẠT",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = "${((syncBrightnessAll / 255f) * 100).toInt()}%",
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = syncBrightnessAll,
                                    onValueChange = {
                                        syncBrightnessAll = it
                                    },
                                    onValueChangeFinished = {
                                        viewModel.updateBrightnessAll(syncBrightnessAll.toInt())
                                    },
                                    valueRange = 0f..255f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        // 3. Parallel Color Grid Presets
                        Text(
                            text = "ĐỔI MÀU TOÀN BỘ THIẾT BỊ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        val syncColors = listOf(
                            Pair("ĐỎ RỰC", "#FF0000"), Pair("CAM CHÁY", "#FF5E00"), Pair("VÀNG NẮNG", "#FFD600"),
                            Pair("XANH LÁ", "#00FF00"), Pair("XANH NGỌC", "#00FFFF"), Pair("XANH LAM", "#0000FF"),
                            Pair("TÍM VIOLET", "#8B5CF6"), Pair("TRẮNG TINH", "#FFFFFF"), Pair("MÀU ĐEN (TẮT)", "#000000")
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("BẢNG MÀU CHỈ UY SÂN KHẤU (SCENE PRESETS)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (dimens.screenWidthDp >= 720) 5 else 3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(syncColors) { (name, hex) ->
                                        val colorItem = Color(android.graphics.Color.parseColor(hex))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .border(
                                                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    viewModel.updateColorAll(hex)
                                                }
                                                .padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(colorItem)
                                                        .border(
                                                            BorderStroke(
                                                                1.dp,
                                                                if (hex == "#FFFFFF") MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                                else Color.White.copy(alpha = 0.2f)
                                                            ),
                                                            CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Synchronized Run Playlist ID
                        Text(
                            text = "KÍCH HOẠT PLAYLIST ĐỒNG LOẠT (RUN PLAYLIST)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // --- AUDIO SYNC CONTROLS & WAVEFORM VISUALIZER ---
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "ĐỒNG BỘ ÁNH SÁNG THEO NHẠC NỀN",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                            
                                            if (selectedAudioUri != null) {
                                                Text(
                                                    text = "ĐÃ ĐƯỢC NẠP NHẠC",
                                                    color = Color(0xFF00C853),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Nhập nhạc của bạn từ thiết bị để tự động chạy khớp nhạc với kịch bản ánh sáng LED. Điểm cuối bài hát sẽ tự dừng nhạc và tắt toàn bộ LED.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Nguồn âm thanh hiện tại:",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (selectedAudioUri != null) Icons.Default.Check else Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = if (selectedAudioUri != null) Color(0xFF00C853) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = selectedAudioName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        audioPickerLauncher.launch("audio/*")
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("WledManager", "Error launching picker", e)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("CHỌN NHẠC", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }

                                            if (selectedAudioUri != null) {
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.clearCustomAudio()
                                                    },
                                                    modifier = Modifier.weight(1f).height(38.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("XÓA NHẠC", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        if (audioHistory.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Lịch sử nhạc đã nạp gần đây (Chọn nhanh):",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                                audioHistory.forEach { historyItem ->
                                                    val isCurrentlySelected = selectedAudioUri?.toString() == historyItem.uriString
                                                    Card(
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = if (isCurrentlySelected) {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                            } else {
                                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                                            }
                                                        ),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isCurrentlySelected) {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                            } else {
                                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                                            }
                                                        ),
                                                        onClick = {
                                                            try {
                                                                val parsedUri = Uri.parse(historyItem.uriString)
                                                                viewModel.setCustomAudio(parsedUri, historyItem.name, historyItem.duration)
                                                            } catch (e: Exception) {
                                                                android.util.Log.e("WledManager", "Error selecting history item", e)
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isCurrentlySelected) Icons.Default.PlayArrow else Icons.Default.Star,
                                                                    contentDescription = null,
                                                                    tint = if (isCurrentlySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = historyItem.name,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    color = if (isCurrentlySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                                    fontWeight = if (isCurrentlySelected) FontWeight.Bold else FontWeight.Normal,
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                            
                                                            val minutes = historyItem.duration / 60
                                                            val seconds = historyItem.duration % 60
                                                            Text(
                                                                text = String.format("%d:%02d", minutes, seconds),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Audio progress instructions placeholder
                                        Text(
                                            text = "💡 Tiến trình nhạc nền được tích hợp trực tiếp, trùng khớp với thước thời gian Choreography bên dưới.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                        )
                                    }
                                }

                                // Playlist ID is locked to 249 as requested


                                if (playlistPlaybackState == PlaylistPlaybackState.Running || playlistPlaybackState == PlaylistPlaybackState.Paused) {
                                    val stageStatusText = if (playlistPlaybackState == PlaylistPlaybackState.Running) {
                                        "Trạng thái: Đang phát kịch bản trình diễn tổng"
                                    } else {
                                        "Trạng thái: Tạm dừng kịch bản trình diễn tổng"
                                    }
                                    val stageStatusColor = if (playlistPlaybackState == PlaylistPlaybackState.Running) {
                                        Color(0xFF00C853)
                                    } else {
                                        Color(0xFFFF9100)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(stageStatusColor)
                                        )
                                        Text(
                                            text = stageStatusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = stageStatusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (playlistPlaybackState == PlaylistPlaybackState.Running || playlistPlaybackState == PlaylistPlaybackState.Paused) {
                                    val isPausedTopControl = playlistPlaybackState == PlaylistPlaybackState.Paused
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (isPausedTopControl) {
                                                    viewModel.resumePlaylistTimeline()
                                                } else {
                                                    viewModel.startPlaylistAllWithTimeline(249, forceRestart = true)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(52.dp).weight(1f)
                                        ) {
                                            Icon(
                                                if (isPausedTopControl) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isPausedTopControl) "TIẾP TỤC" else "CHẠY LẠI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.stopPlaylistTimeline() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(52.dp).weight(1f)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("DỪNG PHÁT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.startPlaylistAllWithTimeline(249) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("CHẠY PLAYLIST TỔNG CHOREOGRAPHY", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }
                                }

                                // Custom running duration information (automatically calculated from playlist 249 or imported track)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⏱️ Thời gian kịch bản trình diễn (Tính toán tự động):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        val minSecString = String.format("%02d:%02d", playlistTotalSeconds / 60, playlistTotalSeconds % 60)
                                        Text(
                                            text = "$playlistTotalSeconds giây ($minSecString)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // CHOREOGRAPHY BOARD TRACK VIEW
                                Spacer(modifier = Modifier.height(12.dp))

                                // Mount the heavy DAW timeline board LAST: switch into the tab instantly,
                                // let the transition settle, then build the board (and let its device
                                // fetch finish) behind a placeholder. Loading the timeline is not urgent.
                                var boardReady by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    kotlinx.coroutines.delay(350) // let the tab switch render fully first
                                    boardReady = true
                                }

                                if (!boardReady) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                                Text(
                                                    text = "Đang dựng bảng timeline biên đạo...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // --- Title row: name + reload on the right ---
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                    Text(
                                                        text = "TIMELINE BIÊN ĐẠO",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                                Text(
                                                    text = "Chạm/kéo trên thước để tua nhanh playhead",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }

                                            // Reload timelines from devices
                                            FilledTonalIconButton(
                                                onClick = {
                                                    val playlistId = syncPlaylistIdInput.toIntOrNull() ?: 249
                                                    viewModel.fetchTimelinesForAllDevices(playlistId)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Tải lại",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // --- Action toolbar: Import (primary) + Lock toggle ---
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.Button(
                                                onClick = { timecodeLauncher.launch("application/json") },
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                modifier = Modifier.weight(1f).height(40.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("IMPORT TIMECODE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Lock/unlock ruler pill
                                            val lockColor = if (isTimelineLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(lockColor.copy(alpha = 0.12f))
                                                    .border(1.dp, lockColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                                    .clickable { viewModel.setTimelineLocked(!isTimelineLocked) }
                                                    .padding(start = 10.dp, end = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isTimelineLocked) Icons.Default.Lock else Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = lockColor,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = if (isTimelineLocked) "Khóa" else "Mở tua",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = lockColor
                                                )
                                                Switch(
                                                    checked = isTimelineLocked,
                                                    onCheckedChange = { viewModel.setTimelineLocked(it) },
                                                    modifier = Modifier
                                                        .scale(0.7f)
                                                        .semantics {
                                                            contentDescription = "Khóa thước timeline"
                                                            stateDescription = if (isTimelineLocked) "Đang khóa" else "Đang mở tua"
                                                        },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = MaterialTheme.colorScheme.error,
                                                        checkedTrackColor = MaterialTheme.colorScheme.errorContainer,
                                                        uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                                                        uncheckedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                                    )
                                                )
                                            }
                                        }

                                        // Horizontal Zoom & Presets controls for tablet-native layouts
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // Two flexible rows so this never overflows on narrow phones:
                                            // row 1 = label + stretchy slider + %, row 2 = preset chips spread evenly.
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val zoomPercent = (((pxPerSec - 8f) / 42f) * 100f).toInt().coerceIn(0, 100)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Thu phóng:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                    Slider(
                                                        value = pxPerSec,
                                                        onValueChange = { pxPerSec = it },
                                                        valueRange = 8f..50f,
                                                        modifier = Modifier.weight(1f).height(24.dp),
                                                        colors = SliderDefaults.colors(
                                                            thumbColor = MaterialTheme.colorScheme.primary,
                                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                                        )
                                                    )
                                                    Text(
                                                        text = "$zoomPercent%",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    listOf(
                                                        Pair("25%", 18.5f),
                                                        Pair("50%", 29f),
                                                        Pair("75%", 39.5f),
                                                        Pair("100%", 50f)
                                                    ).forEach { (label, zoomVal) ->
                                                        val isSelected = Math.abs(pxPerSec - zoomVal) < 1f
                                                        InputChip(
                                                            selected = isSelected,
                                                            onClick = { pxPerSec = zoomVal },
                                                            label = {
                                                                Text(
                                                                    label,
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            },
                                                            modifier = Modifier.weight(1f).height(28.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        val onlineDevices = devices.filter { it.isOnline }
                                        if (onlineDevices.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                                    .padding(12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "🔌 Không có thiết bị trực tuyến nào được tìm thấy. Vui lòng kết nối thiết bị để lập bản đồ biên đạo.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else {
                                            if (isLoadingTimelines) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(100.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                        Text(
                                                            text = "Đang quét và đồng bộ tệp presets.json từ mạch...",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            } else {
                                                val contentWidth = (playlistTotalSeconds * pxPerSec).dp
                                                val totalTimelineWidth = contentWidth + 48.dp
                                                
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    // Frozen Left Column (Device names and audio track)
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.width(85.dp).padding(top = 32.dp)
                                                    ) {
                                                        // 1. Audio Track Header Row
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(44.dp)
                                                                .padding(vertical = 2.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PlayArrow,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Text(
                                                                    text = "Nhạc nền",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }

                                                        // 2. Separator line matching the scrollable side
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(0.8.dp)
                                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                                        )

                                                        // 3. WLED Devices list
                                                        onlineDevices.forEach { dev ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(38.dp)
                                                                    .padding(vertical = 2.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                Column {
                                                                    Text(
                                                                        text = dev.name,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 10.5.sp,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                    ) {
                                                                        Box(modifier = Modifier.size(4.dp).background(Color(0xFF00C853), CircleShape))
                                                                        Text(
                                                                            text = dev.ipAddress,
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            fontSize = 8.sp,
                                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    // Scrollable Right Column Grid and ruler
                                                    val timelineScrollState = rememberScrollState()
                                                    var viewportWidthPx by remember { mutableStateOf(0) }
                                                    val isScrubbing = remember { mutableStateOf(false) }
                                                    val densityVal = LocalDensity.current.density
                                                    
                                                    LaunchedEffect(viewportWidthPx, isPlaylistRunning, isScrubbing.value) {
                                                        if (viewportWidthPx > 0 && (isPlaylistRunning || isScrubbing.value)) {
                                                            androidx.compose.runtime.snapshotFlow { playlistElapsedSecondsState.value }
                                                                .collect { currentSec ->
                                                                    val playheadPx = currentSec * pxPerSec * densityVal
                                                                    val targetScrollPx = playheadPx - (viewportWidthPx / 2f)
                                                                    val maxScroll = timelineScrollState.maxValue
                                                                    val coercedScroll = targetScrollPx.toInt().coerceIn(0, maxScroll)
                                                                    if (timelineScrollState.value != coercedScroll) {
                                                                        timelineScrollState.scrollTo(coercedScroll)
                                                                    }
                                                                }
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(IntrinsicSize.Min)
                                                            .onSizeChanged { viewportWidthPx = it.width }
                                                            .horizontalScroll(timelineScrollState)
                                                    ) {
                                                        Column(
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.width(totalTimelineWidth).padding(horizontal = 24.dp)
                                                        ) {
                                                            // Timeline Ruler ticks
                                                            val density = LocalDensity.current.density
                                                            var dragAccumulatedSeconds by remember { mutableStateOf(0f) }

                                                            val seekAction = { offsetX: Float ->
                                                                if (!isTimelineLocked) {
                                                                    val xDp = offsetX / density
                                                                    val sec = xDp / pxPerSec
                                                                    val targetSec = sec.coerceIn(0f, playlistTotalSeconds.toFloat())
                                                                    viewModel.seekPlaylistElapsedSeconds(targetSec)
                                                                    dragAccumulatedSeconds = targetSec
                                                                }
                                                            }
                                                            
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(24.dp)
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                    .pointerInput(playlistTotalSeconds, pxPerSec) {
                                                                        detectDragGestures(
                                                                            onDragStart = { offset ->
                                                                                if (!isTimelineLocked) {
                                                                                    isScrubbing.value = true
                                                                                    seekAction(offset.x)
                                                                                }
                                                                            },
                                                                            onDragEnd = {
                                                                                isScrubbing.value = false
                                                                            },
                                                                            onDragCancel = {
                                                                                isScrubbing.value = false
                                                                            },
                                                                            onDrag = { change, dragAmount ->
                                                                                if (!isTimelineLocked) {
                                                                                    change.consume()
                                                                                    val deltaSec = dragAmount.x / (pxPerSec * density)
                                                                                    dragAccumulatedSeconds = (dragAccumulatedSeconds + deltaSec).coerceIn(0f, playlistTotalSeconds.toFloat())
                                                                                    viewModel.seekPlaylistElapsedSeconds(dragAccumulatedSeconds)
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                            ) {
                                                                // Finer labels: the more you zoom in, the smaller the step between
                                                                // labelled seconds (down to every 2s), with 1-second minor ticks.
                                                                val tickStep = when {
                                                                    pxPerSec >= 38 -> 2
                                                                    pxPerSec >= 24 -> 5
                                                                    pxPerSec >= 13 -> 10
                                                                    else -> 20
                                                                }
                                                                val minorStep = if (pxPerSec >= 24) 1 else 2
                                                                val majorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                                val minorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                                                val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                                val rulerDensity = LocalDensity.current.density

                                                                // Draw ALL tick marks in a single Canvas instead of hundreds of Box
                                                                // composables — major win for first composition of long timelines.
                                                                Canvas(modifier = Modifier.matchParentSize()) {
                                                                    val h = size.height
                                                                    val pps = pxPerSec * rulerDensity
                                                                    var s = 0
                                                                    while (s <= playlistTotalSeconds) {
                                                                        if (s % tickStep != 0 && s % minorStep == 0) {
                                                                            val x = s * pps
                                                                            drawLine(
                                                                                color = minorTickColor,
                                                                                start = Offset(x, h),
                                                                                end = Offset(x, h - 4.dp.toPx()),
                                                                                strokeWidth = 1.dp.toPx()
                                                                            )
                                                                        }
                                                                        s++
                                                                    }
                                                                    s = 0
                                                                    while (s <= playlistTotalSeconds) {
                                                                        val x = s * pps
                                                                        drawLine(
                                                                            color = majorTickColor,
                                                                            start = Offset(x, h),
                                                                            end = Offset(x, h - 8.dp.toPx()),
                                                                            strokeWidth = 1.5.dp.toPx()
                                                                        )
                                                                        s += tickStep
                                                                    }
                                                                }
                                                                // Only the major-tick second labels remain as composables (~total/tickStep)
                                                                for (sec in 0..playlistTotalSeconds step tickStep) {
                                                                    Text(
                                                                        text = "${sec}s",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontSize = 8.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = labelColor,
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopStart)
                                                                            .offset(x = (sec * pxPerSec).dp)
                                                                            .padding(start = 2.dp)
                                                                    )
                                                                }
                                                            }

                                                            // NEW INTEGRATED AUDIO WAVEFORM TRACK (DAW STYLE)
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(44.dp)
                                                                    .padding(vertical = 2.dp)
                                                                    .background(
                                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                                                        RoundedCornerShape(6.dp)
                                                                    )
                                                                    .border(
                                                                        width = 1.dp,
                                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    )
                                                                    .pointerInput(playlistTotalSeconds, pxPerSec) {
                                                                        detectDragGestures(
                                                                            onDragStart = { offset ->
                                                                                if (!isTimelineLocked) {
                                                                                    isScrubbing.value = true
                                                                                    seekAction(offset.x)
                                                                                }
                                                                            },
                                                                            onDragEnd = {
                                                                                isScrubbing.value = false
                                                                            },
                                                                            onDragCancel = {
                                                                                isScrubbing.value = false
                                                                            },
                                                                            onDrag = { change, dragAmount ->
                                                                                if (!isTimelineLocked) {
                                                                                    change.consume()
                                                                                    val deltaSec = dragAmount.x / (pxPerSec * density)
                                                                                    dragAccumulatedSeconds = (dragAccumulatedSeconds + deltaSec).coerceIn(0f, playlistTotalSeconds.toFloat())
                                                                                    viewModel.seekPlaylistElapsedSeconds(dragAccumulatedSeconds)
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                                    .pointerInput(playlistTotalSeconds, pxPerSec) {
                                                                        detectTapGestures { offset ->
                                                                            seekAction(offset.x)
                                                                        }
                                                                    }
                                                            ) {
                                                                val activeColorTop = Color(0xFF00F0FF)
                                                                val activeColorBot = Color(0xFFFF007F)
                                                                val inactiveColorTop = Color(0xFF00B0FF)
                                                                val inactiveColorBot = Color(0xFF8B5CF6)
                                                                val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

                                                                // Precompute bar amplitudes once (depends only on audio + length, not on
                                                                // playback time) so the Canvas no longer recomputes ~900 sin/random every frame.
                                                                val waveformAmplitudes = remember(selectedAudioName, playlistTotalSeconds) {
                                                                    val sps = 5
                                                                    val steps = playlistTotalSeconds * sps
                                                                    val rnd = java.util.Random(selectedAudioName.hashCode().toLong())
                                                                    FloatArray(steps) { step ->
                                                                        val barRatio = if (steps > 1) step.toFloat() / (steps - 1) else 0.5f
                                                                        val envelope = Math.sin(barRatio * Math.PI).toFloat()
                                                                        val noise = rnd.nextFloat() * 0.25f
                                                                        val combinedWave = Math.sin(barRatio * Math.PI * 18.0).toFloat() * 0.45f +
                                                                                           Math.sin(barRatio * Math.PI * 52.0).toFloat() * 0.3f +
                                                                                           noise
                                                                        (0.22f + Math.abs(combinedWave) * envelope * 0.78f).coerceIn(0.12f, 1.0f)
                                                                    }
                                                                }

                                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                                    val heightPx = size.height
                                                                    val elSec = playlistElapsedSeconds

                                                                    val pxPerSecFloat = pxPerSec * density
                                                                    val stepsPerSecond = 5
                                                                    val totalSteps = waveformAmplitudes.size
                                                                    val segmentWidthPx = pxPerSecFloat / stepsPerSecond
                                                                    val barWidthPx = (segmentWidthPx * 0.55f).coerceIn(1.2f, 15f)

                                                                    for (step in 0 until totalSteps) {
                                                                        val barTime = step.toFloat() / stepsPerSecond
                                                                        val amplitude = waveformAmplitudes[step]

                                                                        val isPlayed = barTime <= elSec
                                                                        val barHeight = amplitude * (heightPx * 0.75f)
                                                                        
                                                                        // Mathematically aligned position exactly locked to the timeline
                                                                        val xPos = barTime * pxPerSecFloat + (segmentWidthPx / 2f)
                                                                        val centerY = heightPx / 2f
                                                                        val topY = centerY - barHeight / 2f
                                                                        val botY = centerY + barHeight / 2f
                                                                        
                                                                        val lineBrush = if (isPlayed) {
                                                                            if (isPlaylistRunning) {
                                                                                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(activeColorTop, activeColorBot), startY = topY, endY = botY)
                                                                            } else {
                                                                                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(inactiveColorTop, inactiveColorBot), startY = topY, endY = botY)
                                                                            }
                                                                        } else {
                                                                            androidx.compose.ui.graphics.SolidColor(unplayedColor)
                                                                        }
                                                                        
                                                                        drawLine(
                                                                            brush = lineBrush,
                                                                            start = Offset(xPos, centerY - barHeight / 2f),
                                                                            end = Offset(xPos, centerY + barHeight / 2f),
                                                                            strokeWidth = barWidthPx,
                                                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                                        )
                                                                    }
                                                                }
                                                                
                                                                // Lock / Active Mode indicators overlaying on waveform card
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.TopEnd)
                                                                        .padding(6.dp)
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(
                                                                            if (isTimelineLocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                                                                            else if (isChoreographyMode) Color(0xFFFF9100).copy(alpha = 0.9f)
                                                                            else Color(0xFF00C853).copy(alpha = 0.9f)
                                                                        )
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if (isTimelineLocked) Icons.Default.Lock else if (isChoreographyMode) Icons.Default.Build else Icons.Default.PlayArrow,
                                                                            contentDescription = null,
                                                                            tint = if (isTimelineLocked) MaterialTheme.colorScheme.error else Color.Black,
                                                                            modifier = Modifier.size(10.dp)
                                                                        )
                                                                        Text(
                                                                            text = if (isTimelineLocked) "🔒 THƯỚC ĐÃ KHÓA" else if (isChoreographyMode) "⚡ BIÊN ĐẠO CHỦ ĐỘNG" else "🔁 CHẠY NATIVE (PL 249)",
                                                                            fontSize = 8.sp,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = if (isTimelineLocked) MaterialTheme.colorScheme.error else Color.Black
                                                                        )
                                                                    }
                                                                }

                                                                // Floating Track Overlay info
                                                                Text(
                                                                    text = "🎵 $selectedAudioName",
                                                                    fontSize = 8.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                                    modifier = Modifier
                                                                        .align(Alignment.BottomStart)
                                                                        .padding(start = 6.dp, bottom = 2.dp)
                                                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }

                                                            // Separator line
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(0.8.dp)
                                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                                            )

                                                            // Tracks block
                                                            onlineDevices.forEach { dev ->
                                                                val timeline = devicesTimelines[dev.id]
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(38.dp)
                                                                        .padding(vertical = 2.dp)
                                                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(5.dp)),
                                                                    contentAlignment = Alignment.CenterStart
                                                                ) {
                                                                    if (timeline != null && timeline.isLoaded) {
                                                                        Row(modifier = Modifier.fillMaxSize()) {
                                                                            timeline.steps.forEach { step ->
                                                                                TimelineStepItem(
                                                                                    step = step,
                                                                                    pxPerSec = pxPerSec,
                                                                                    isPlaylistRunning = isPlaylistRunning,
                                                                                    playlistElapsedSecondsState = playlistElapsedSecondsState
                                                                                )
                                                                            }
                                                                        }
                                                                    } else {
                                                                        Text(
                                                                            text = timeline?.error ?: "Đang nạp kịch bản...",
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            fontSize = 8.5.sp,
                                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                                            modifier = Modifier.padding(start = 6.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Playhead line overlaid over tracks inside horizontal scroll Container
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .offset {
                                                                    val pxOffset = playlistElapsedSecondsState.value * pxPerSec * density
                                                                    androidx.compose.ui.unit.IntOffset((pxOffset - 8.dp.toPx() + 24.dp.toPx()).toInt(), 0)
                                                                }
                                                                .width(16.dp)
                                                                .fillMaxHeight()
                                                        ) {
                                                            // Vertical playline (centered at TopCenter)
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopCenter)
                                                                    .padding(top = 14.dp)
                                                                    .width(2.5.dp)
                                                                    .fillMaxHeight()
                                                                    .background(
                                                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                                            colors = listOf(
                                                                                Color(0xFFFF2A85), // Neon Pink at the top
                                                                                Color(0xFF00F5FF), // Neon Cyan
                                                                                Color(0xFF00F5FF).copy(alpha = 0.4f),
                                                                                Color(0xFF00F5FF).copy(alpha = 0.0f) // fade out at the bottom
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                            // Modern glowing handle - Tech Diamond tag matching Happy Smart Light style
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopCenter)
                                                                    .size(width = 12.dp, height = 16.dp)
                                                                    .background(
                                                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                                            colors = listOf(Color(0xFFFF2A85), Color(0xFF00F5FF))
                                                                        ),
                                                                        shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp, topStart = 2.dp, topEnd = 2.dp)
                                                                    )
                                                                    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp, topStart = 2.dp, topEnd = 2.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                // Small glowing center core
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(4.dp)
                                                                        .background(Color.White, CircleShape)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                }

                                // REAL-TIME TIMELINE ACTION TRACK (ALWAYS VISIBLE)!
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    ),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                val infiniteTransition = rememberInfiniteTransition()
                                                val alpha by infiniteTransition.animateFloat(
                                                    initialValue = 0.3f,
                                                    targetValue = 1f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(1000, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    )
                                                )

                                                val statusText: String
                                                val statusColor: Color
                                                val pulseDot: Boolean

                                                when (playlistPlaybackState) {
                                                    PlaylistPlaybackState.Running -> {
                                                        statusText = "ĐANG DIỄN: $playlistName"
                                                        statusColor = Color(0xFF00C853) // Vivid Green
                                                        pulseDot = true
                                                    }
                                                    PlaylistPlaybackState.Paused -> {
                                                        statusText = "TẠM DỪNG: $playlistName"
                                                        statusColor = Color(0xFFFF9100) // Orange
                                                        pulseDot = true
                                                    }
                                                    PlaylistPlaybackState.Completed -> {
                                                        statusText = "HOÀN TẤT: $playlistName"
                                                        statusColor = MaterialTheme.colorScheme.primary
                                                        pulseDot = false
                                                    }
                                                    PlaylistPlaybackState.Idle -> {
                                                        statusText = "SẴN SÀNG: Thước kịch bản $playlistName"
                                                        statusColor = Color(0xFF00B0FF) // Cyber Blue
                                                        pulseDot = false
                                                    }
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(
                                                            statusColor.copy(alpha = if (pulseDot) alpha else 0.8f),
                                                            CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isPlaylistRunning) MaterialTheme.colorScheme.primary else statusColor
                                                )
                                            }
                                            if (playlistStepsCount > 0) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ) {
                                                    Text("$playlistStepsCount bước", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                            // Dynamic active mode badge (Choreography vs Native PL 249)
                                            Badge(
                                                containerColor = if (isChoreographyMode) Color(0xFFFF9100) else Color(0xFF00B0FF),
                                                contentColor = Color.Black
                                            ) {
                                                Text(
                                                    text = if (isChoreographyMode) "⚡ B.ĐẠO CHỦ ĐỘNG" else "🔁 NATIVE PL 249",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        val progress = if (playlistTotalSeconds > 0) {
                                            playlistElapsedSeconds.toFloat() / playlistTotalSeconds.toFloat()
                                        } else {
                                            0f
                                        }

                                        // Progress bar timeline tracking
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            LinearProgressIndicator(
                                                progress = { progress.coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(5.dp)),
                                                color = Color(0xFF00C853), // Vivid green playhead
                                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                val elString = String.format("%02d:%04.1f", (playlistElapsedSeconds.toInt() / 60), (playlistElapsedSeconds % 60f))
                                                val totString = String.format("%02d:%02d", playlistTotalSeconds / 60, playlistTotalSeconds % 60)
                                                Text(
                                                    text = "Đã chạy: $elString",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Text(
                                                    text = "Tổng: $totString",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (activeStepsMap.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            Text(
                                                text = "⏱️ CẤN TRỪ THỜI GIAN HIỆU ỨNG THỰC TẾ (ĐỘ PHÂN GIẢI 1/10S):",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp
                                            )
                                            
                                            activeStepsMap.forEach { (deviceId, details) ->
                                                val devName = devices.find { it.id == deviceId }?.name ?: "Mạch #$deviceId"
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = devName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                        Text(
                                                            text = "Hiệu ứng: ${details.presetName} (PS ${details.presetId})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 9.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                    
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = String.format("%.1f Giây còn lại", details.remainingDuration),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF00C853)
                                                        )
                                                        Text(
                                                            text = String.format("Đã chạy %.1f / %.1f s", details.elapsedInStep, details.totalDuration),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = "⚠️ SÂN KHẤU TỰ ĐỘNG TẮT TOÀN BỘ LED KHI ĐẾN ĐÍCH!",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFD50000),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.stopPlaylistTimeline()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("TẮT HẾT LED", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)
                                            }

                                            val isPaused = playlistPlaybackState == PlaylistPlaybackState.Paused
                                            val buttonText = when {
                                                playlistPlaybackState == PlaylistPlaybackState.Running -> "TẠM DỪNG"
                                                isPaused -> "TIẾP TỤC"
                                                playlistPlaybackState == PlaylistPlaybackState.Completed -> "CHẠY LẠI"
                                                else -> "CHẠY TIMELINE"
                                            }
                                            val buttonColor = when {
                                                playlistPlaybackState == PlaylistPlaybackState.Running -> Color(0xFFFF9100)
                                                else -> MaterialTheme.colorScheme.primary
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    when (playlistPlaybackState) {
                                                        PlaylistPlaybackState.Running -> {
                                                            viewModel.pausePlaylistTimeline()
                                                        }
                                                        PlaylistPlaybackState.Paused -> {
                                                            viewModel.resumePlaylistTimeline()
                                                        }
                                                        PlaylistPlaybackState.Completed -> {
                                                            viewModel.startPlaylistAllWithTimeline(249, forceRestart = true)
                                                        }
                                                        PlaylistPlaybackState.Idle -> {
                                                            viewModel.startPlaylistAllWithTimeline(249)
                                                        }
                                                    }
                                                },
                                                border = BorderStroke(1.5.dp, buttonColor),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                            ) {
                                                if (playlistPlaybackState == PlaylistPlaybackState.Running) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        modifier = Modifier.size(16.dp).padding(vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(buttonColor, RoundedCornerShape(1.dp)))
                                                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(buttonColor, RoundedCornerShape(1.dp)))
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = buttonColor
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = buttonText,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = buttonColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        BulkPresetCleanupPanel(
                            devices = devices,
                            onlineStats = onlinePresetStats,
                            isBusy = bulkPresetDeleteState.isPreparing || bulkPresetDeleteState.isDeleting,
                            onRefresh = { viewModel.refreshOnlineDevicePresetStats() },
                            onAction = { action -> viewModel.preparePresetDeletionForOnlineDevices(action) }
                        )
                    }

                    2 -> {
                        // Section: Custom Button Configuration (Future Development Placeholder Grid)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = "PHÍM TẮT CUSTOM CHUYÊN NGHIỆP",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Thiết lập các cụm nút đại tác vụ sân khấu. Bạn có thể tự phối chuỗi lệnh HTTP, gán độ trễ và gom thiết bị theo nhóm biểu diễn riêng.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Giai đoạn tiếp theo: Tích hợp bàn trộn phím vật lý MIDI qua giao thức USB-OTG.",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(dimens.cardPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(dimens.iconSize)
                                )
                                Text(
                                    text = "CHƯA CÓ PHÍM CUSTOM",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Khu vực này sẽ chỉ hiển thị các phím tắt thật sau khi có cấu hình lưu sẵn.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
}

@Composable
private fun DeviceInfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DeviceMemoryInfoRows(
    fs: WledFilesystemInfo?,
    wifiSignal: Int?,
    stats: DevicePresetStorageStats,
    onFindImagesToDelete: (() -> Unit)? = null
) {
    val usedBytes = fs?.usedBytes
    val totalBytes = fs?.totalBytes
    val percent = if (usedBytes != null && totalBytes != null && totalBytes > 0L) {
        ((usedBytes * 100L) / totalBytes).toInt()
    } else {
        null
    }
    val freeBytes = if (usedBytes != null && totalBytes != null && totalBytes >= usedBytes) {
        totalBytes - usedBytes
    } else {
        null
    }
    val statusColor = when {
        percent == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        percent < 70 -> Color(0xFF00A86B)
        percent < 85 -> Color(0xFFFFB300)
        else -> Color(0xFFD50000)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DeviceInfoRow(
            label = "Bộ nhớ filesystem:",
            value = if (percent != null && usedBytes != null && totalBytes != null) {
                "$percent% (${formatDeviceStorageKb(usedBytes)} / ${formatDeviceStorageKb(totalBytes)})"
            } else {
                "N/A"
            },
            valueColor = statusColor
        )
        if (percent != null) {
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            )
            DeviceInfoRow(
                label = "Tình trạng bộ nhớ:",
                value = storageStatusText(percent),
                valueColor = statusColor
            )
            DeviceInfoDivider()
            DeviceInfoRow(
                label = "Dung lượng còn trống:",
                value = formatDeviceStorageKb(freeBytes),
                valueColor = statusColor
            )
        }

        // Cảnh báo khi bộ nhớ filesystem chiếm > 85% — nhắc dọn ảnh/logo.
        if (percent != null && percent >= 85) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        text = "BỘ NHỚ GẦN ĐẦY ($percent%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Filesystem đã dùng trên 85%. Hãy dọn bớt ảnh và logo (.bmp/.gif) không còn dùng để tránh đầy bộ nhớ, lỗi lưu preset.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
            }
        }

        // Nút tìm & dọn ảnh .bmp/.gif trên thiết bị (chọn từng file để xóa).
        if (onFindImagesToDelete != null) {
            OutlinedButton(
                onClick = onFindImagesToDelete,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (percent != null && percent >= 85) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (percent != null && percent >= 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tìm & dọn ảnh BMP/GIF để xóa",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DeviceInfoDivider()
        DeviceInfoRow(
            label = "WiFi thiết bị:",
            value = wifiSignal?.let { "$it%" } ?: "N/A"
        )

        DeviceInfoDivider()
        val presetValueColor = if (stats.error == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        DeviceInfoRow(
            label = "Logo/ảnh:",
            value = when {
                stats.isLoading -> "Đang đọc..."
                stats.error != null -> "Lỗi đọc"
                else -> "${stats.logoUsed} preset"
            },
            valueColor = presetValueColor
        )
        DeviceInfoDivider()
        DeviceInfoRow(
            label = "Preset timecode:",
            value = when {
                stats.isLoading -> "Đang đọc..."
                stats.error != null -> "Lỗi đọc"
                else -> "${stats.timecodeUsed}/${stats.timecodeCapacity} slot"
            },
            valueColor = presetValueColor
        )
        DeviceInfoDivider()
        DeviceInfoRow(
            label = "Preset hệ thống:",
            value = when {
                stats.isLoading -> "Đang đọc..."
                stats.error != null -> "Lỗi đọc"
                else -> "${stats.systemUsed}/${stats.systemCapacity} slot"
            },
            valueColor = presetValueColor
        )
        if (!stats.isLoading && stats.error == null && stats.otherUsed > 0) {
            DeviceInfoDivider()
            DeviceInfoRow(label = "Preset khác:", value = "${stats.otherUsed} preset")
        }
        PresetCapacityWarningCard(
            stats = stats,
            deviceName = null,
            modifier = Modifier.fillMaxWidth()
        )
        if (stats.error != null) {
            Text(
                text = stats.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PresetCapacityWarningCard(
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
private fun PresetCleanupPanel(
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
private fun BulkPresetCleanupPanel(
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
private fun PresetDeleteActionButton(
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
private fun PresetDeleteDialog(
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
private fun PresetBulkDeleteDialog(
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
private fun PresetDeviceErrorList(errors: List<PresetDeviceDeleteError>) {
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
private fun formatFileBytes(bytes: Long): String {
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
private fun FileCleanupDialog(
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
private fun formatDeviceStorageKb(kiloBytes: Long?): String {
    if (kiloBytes == null) return "N/A"
    val kb = kiloBytes.toDouble()
    return if (kb < 1024.0) {
        String.format(Locale.US, "%.0f KB", kb)
    } else {
        String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }
}

private fun storageStatusText(percent: Int): String {
    return when {
        percent < 70 -> "Còn tốt"
        percent < 85 -> "Gần đầy"
        else -> "Cần dọn bộ nhớ"
    }
}

private fun presetCapacityWarnings(stats: DevicePresetStorageStats): List<String> {
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

private fun percentOf(used: Int, capacity: Int): Int {
    if (capacity <= 0) return 0
    return ((used * 100f) / capacity).toInt()
}

private fun presetDeleteTitle(action: PresetDeleteAction): String {
    return when (action) {
        PresetDeleteAction.LOGO_IMAGES -> "XÓA ALL PRESET LOGO/ẢNH"
        PresetDeleteAction.TIMECODE_GROUP -> "XÓA ALL PRESET THUỘC NHÓM PRESET"
        PresetDeleteAction.ALL_EXCEPT_SYSTEM -> "XÓA ALL PRESET TRỪ HỆ THỐNG"
    }
}

private fun presetDeleteSafetyNote(action: PresetDeleteAction): String {
    return when (action) {
        PresetDeleteAction.LOGO_IMAGES -> "Chỉ xóa slot 1-59 và file ảnh được nhóm này tham chiếu nếu không còn preset khác dùng."
        PresetDeleteAction.TIMECODE_GROUP -> "Xóa slot 60-240 và playlist 249; file ảnh dùng chung sẽ được giữ lại."
        PresetDeleteAction.ALL_EXCEPT_SYSTEM -> "Xóa mọi preset thường, cho phép xóa playlist 249, nhưng giữ slot 100, 248, 250 và file ảnh còn được slot được giữ tham chiếu."
    }
}

private fun summarizePresetIds(ids: List<Int>): String {
    if (ids.isEmpty()) return "Không có"
    if (ids.size <= 18) return ids.joinToString(", ")
    return ids.take(18).joinToString(", ") + "..."
}

private fun WledInfo.veriInfo(): String {
    return "WS2812B/ARGB (Hệ thống điều hợp tự động)"
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
private fun ProLockedFeaturePanel(
    state: ProSubscriptionState,
    title: String,
    description: String,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit
) {
    val dimens = LocalAppDimens.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pro_locked_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimens.iconSize)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                textAlign = TextAlign.Center
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = state.priceText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Khôi phục")
                }
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nâng cấp")
                }
            }
        }
    }
}

@Composable
private fun ProSubscriptionDialog(
    state: ProSubscriptionState,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
    onManageSubscription: () -> Unit,
    onDebugToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val dimens = LocalAppDimens.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPro) Icons.Default.CheckCircle else Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isPro) "ARGB HSL Pro đang hoạt động" else "Nâng cấp ARGB HSL Pro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Gói theo năm cho điều khiển đồng loạt và biên đạo nhiều mạch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Pro hằng năm",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.productId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.62f)
                            )
                        }
                        Text(
                            text = state.priceText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProBenefitRow("Điều khiển bật/tắt, màu, hiệu ứng và độ sáng cho tất cả mạch cùng lúc.")
                    ProBenefitRow("Import timecode, bake preset, playlist 249 và timeline đồng bộ theo nhạc.")
                    ProBenefitRow("Dọn preset hàng loạt để chuẩn bị show mới nhanh hơn.")
                }

                val messageColor = if (state.errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                }
                val messageText = state.errorMessage ?: state.statusMessage
                if (!messageText.isNullOrBlank()) {
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodySmall,
                        color = messageColor
                    )
                }

                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestore,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Khôi phục")
                    }
                    Button(
                        onClick = onPurchase,
                        enabled = state.canBuy && !state.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isPro) "Đã Pro" else "Mua Pro")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Text("Tải lại Billing")
                    }
                    TextButton(onClick = onManageSubscription) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Quản lý trên Play")
                    }
                }

                if (BuildConfig.DEBUG) {
                    OutlinedButton(
                        onClick = onDebugToggle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isPro) "Tắt Pro debug" else "Bật Pro debug")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProBenefitRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.weight(1f)
        )
    }
}

private fun openPlaySubscriptionCenter(context: Context) {
    val uri = Uri.parse(
        "https://play.google.com/store/account/subscriptions" +
            "?sku=${ProSubscriptionManager.PRO_PRODUCT_ID}&package=${context.packageName}"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            android.widget.Toast
                .makeText(context, "Không mở được trang quản lý subscription.", android.widget.Toast.LENGTH_LONG)
                .show()
        }
}

private fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
fun EmptyControlState() {
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
                text = "Chưa Chọn Thiết Bị ARGB HSL",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Text(
                text = "Vui lòng chọn bất kỳ thiết bị LED nào trong bảng điều khiển bên trái để tinh chỉnh cường độ, màu sắc và hiệu ứng ARGB nhanh chóng.",
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
private fun materialPathIcon(name: String, pathStr: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(pathData = addPathNodes(pathStr), fill = SolidColor(Color.Black))
    }.build()

private val IconPower: ImageVector by lazy {
    materialPathIcon(
        "PowerSettingsNew",
        "M13 3h-2v10h2V3zm4.83 2.17l-1.42 1.42C17.99 7.86 19 9.81 19 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.19 1.01-4.14 2.58-5.42L6.17 5.17C4.23 6.82 3 9.26 3 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.74-1.23-5.18-3.17-6.83z"
    )
}

private val IconLightMode: ImageVector by lazy {
    materialPathIcon(
        "LightMode",
        "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"
    )
}

private val IconUnfoldMore: ImageVector by lazy {
    materialPathIcon(
        "UnfoldMore",
        "M12 5.83L15.17 9l1.41-1.41L12 3 7.41 7.59 8.83 9 12 5.83zm0 12.34L8.83 15l-1.41 1.41L12 21l4.59-4.59L15.17 15 12 18.17z"
    )
}

fun rgbToHexFromHSV(h: Float, s: Float, v: Float): String {
    val intColor = HSVToColor(floatArrayOf(h, s, v))
    return String.format("#%02X%02X%02X", (intColor shr 16) and 0xFF, (intColor shr 8) and 0xFF, intColor and 0xFF)
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
private fun ColorWheelPicker(
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
private fun ColorStatChip(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun DeviceSwitcher(
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
private fun TimecodeMappingRow(
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
    val durationText = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

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
