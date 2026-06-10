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
fun DeviceControlSection(
    device: WledDevice,
    activeDetails: WledResponse?,
    viewModel: WledViewModel,
    onBack: () -> Unit,
    isWideScreen: Boolean,
    proState: ProSubscriptionState,
    onRequirePro: () -> Unit
) {
    val strings = LocalAppStrings.current
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

    // Chuyển từ tab Biên Tập → tab Đồng Loạt (All) rồi cuộn tới bảng timeline.
    var pendingJumpToTimeline by remember { mutableStateOf(false) }
    var allTabViewportTopY by remember { mutableFloatStateOf(0f) }
    var timelineBoardY by remember { mutableStateOf<Float?>(null) }

    // ---- State cho tab "Upload POI & Cờ LED" ----
    var imageUploadMode by remember { mutableStateOf(ImageUploadMode.POI) }
    var poiPixelsText by remember { mutableStateOf("72") }
    var flagWidthText by remember { mutableStateOf("32") }
    var flagHeightText by remember { mutableStateOf("16") }
    var imageWriteMode by remember { mutableStateOf(ImageWriteMode.APPEND_EMPTY) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var deselectedUploadDeviceIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris = (selectedImageUris + uris).distinct()
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
        if (selectedTab == 1 || selectedTab == 2) {
            viewModel.refreshOnlineDevicePresetStats()
        }
    }

    // Khi bấm "sang tab Đồng Loạt để chạy": chờ tab 1 dựng xong bảng timeline rồi cuộn tới đúng vị trí.
    LaunchedEffect(pendingJumpToTimeline, selectedTab, timelineBoardY) {
        if (pendingJumpToTimeline && selectedTab == 1) {
            val boardY = timelineBoardY
            if (boardY != null) {
                val target = (allScrollState.value + (boardY - allTabViewportTopY)).roundToInt().coerceAtLeast(0)
                allScrollState.animateScrollTo(target)
                pendingJumpToTimeline = false
            }
        }
    }

    val editorSelectedDeviceId by viewModel.editorSelectedDeviceId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedTab, onlineDevicesKey, editorSelectedDeviceId) {
        if (selectedTab == 3) {
            val onlineList = devices.filter { it.isOnline }
            // Mặc định chọn mạch đang điều khiển nếu nó online, nếu không thì mạch online đầu tiên.
            val target = onlineList.find { it.id == editorSelectedDeviceId }
                ?: onlineList.find { it.id == device.id }
                ?: onlineList.firstOrNull()
            if (target != null) {
                if (editorSelectedDeviceId != target.id) {
                    viewModel.selectEditorDevice(target.id)
                } else {
                    viewModel.fetchEditorPresets(target)
                }
            }
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
                        contentDescription = strings.cdBackToList,
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
                    0 -> Pair(strings.dcHeader0, strings.dcSub0)
                    1 -> Pair(strings.dcHeader1, strings.dcSub1)
                    2 -> Pair(strings.dcHeader2, strings.dcSub2)
                    else -> Pair(strings.dcHeader3, strings.dcSub3)
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
                        contentDescription = strings.offline,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = strings.dcOfflineTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = strings.dcOfflineBody,
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
                    .onGloballyPositioned { allTabViewportTopY = it.positionInRoot().y }
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
                                        text = strings.dcMasterBrightness,
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
                    val lockedTitle = when (selectedTab) {
                        1 -> strings.proLockTitleAll
                        2 -> strings.proLockTitleUpload
                        else -> strings.proLockTitleTimeline
                    }
                    val lockedDescription = when (selectedTab) {
                        1 -> strings.proLockDescAll
                        2 -> strings.proLockDescUpload
                        else -> strings.proLockDescTimeline
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
                                            text = if (device.isOnline) strings.online else strings.offline,
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
                                        contentDescription = strings.dcPowerCd,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (device.isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (device.isOn) strings.dcTurnOff else strings.dcTurnOn,
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
                                        text = strings.dcMainColor,
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
                            text = strings.dcQuickColors,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val quickColors = listOf(
                            Pair(strings.colorRed, "#FF0000"), Pair(strings.colorOrange, "#FF5E00"), Pair(strings.colorYellow, "#FFD600"),
                            Pair(strings.colorGreen, "#00FF00"), Pair(strings.colorCyan, "#00FFFF"), Pair(strings.colorBlue, "#0000FF"),
                            Pair(strings.colorPurple, "#B000FF"), Pair(strings.colorWhite, "#FFFFFF"), Pair(strings.colorBlack, "#000000")
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
                            text = strings.dcColorWheel,
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
                                    ColorStatChip(label = strings.dcHue, value = "${hsvHue.toInt()}°", modifier = Modifier.weight(1f))
                                    ColorStatChip(label = strings.dcSat, value = "${(hsvSat * 100).toInt()}%", modifier = Modifier.weight(1f))
                                }

                                // Value slider (độ sáng của màu) — track gradient đen → màu hiện tại
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(strings.dcValueBrightness, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                            text = strings.dcDeviceInfo,
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
                                    Text(strings.dcProductName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.product ?: strings.dcDefaultProduct, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.dcBrand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(activeDetails?.info?.brand ?: "ARGB Happy Smart Light", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.dcFwVersion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.fwVersion ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.dcVid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(device.vid?.toString() ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.dcIpAccess, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                                        Text(strings.dcClientName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
                                            Text(strings.dcLedCount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text(leds.count?.toString() ?: "N/A", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(strings.dcPwr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text("${leds.pwr ?: 0} mA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(strings.dcMaxPwr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text("${leds.maxpwr ?: 0} mA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        }
                                        
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(strings.dcFps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            val fpsVal = leds.fps ?: 0
                                            if (fpsVal == 0) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("0 FPS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF1744))
                                                    Text(
                                                        text = strings.dcPoiModeActive,
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
                        val btRemoteEnabled by viewModel.btRemoteEnabled.collectAsStateWithLifecycle()
                        val btRemoteKeyCode by viewModel.btRemoteKeyCode.collectAsStateWithLifecycle()
                        val btRemoteLearning by viewModel.btRemoteLearning.collectAsStateWithLifecycle()
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
                            text = strings.dcStageControl,
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
                                    text = strings.dcParallelPower,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    NeonActionButton(
                                        text = strings.dcAllOnBtn,
                                        icon = Icons.Default.PlayArrow,
                                        neonColor = MaterialTheme.colorScheme.primary, // Cyan neon
                                        onClick = { viewModel.togglePowerAll(true) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NeonActionButton(
                                        text = strings.dcAllOffBtn,
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
                                            text = strings.dcGroupBrightness,
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
                            text = strings.dcGroupColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        val syncColors = listOf(
                            Pair(strings.sceneRed, "#FF0000"), Pair(strings.sceneOrange, "#FF5E00"), Pair(strings.sceneYellow, "#FFD600"),
                            Pair(strings.sceneGreen, "#00FF00"), Pair(strings.sceneCyan, "#00FFFF"), Pair(strings.sceneBlue, "#0000FF"),
                            Pair(strings.scenePurple, "#8B5CF6"), Pair(strings.sceneWhite, "#FFFFFF"), Pair(strings.sceneBlack, "#000000")
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
                                    Text(strings.dcScenePalette, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                            text = strings.dcRunPlaylist,
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
                                                    text = strings.dcMusicSync,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                            
                                            if (selectedAudioUri != null) {
                                                Text(
                                                    text = strings.dcMusicLoaded,
                                                    color = Color(0xFF00C853),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }

                                        Text(
                                            text = strings.dcMusicHelp,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = strings.dcAudioSource,
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
                                                Text(strings.dcPickMusic, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                                                    Text(strings.dcRemoveMusic, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        if (audioHistory.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = strings.dcRecentMusic,
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
                                                                text = String.format(Locale.US, "%d:%02d", minutes, seconds),
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
                                            text = strings.dcMusicNote,
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
                                        strings.dcStatusPlaying
                                    } else {
                                        strings.dcStatusPaused
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
                                            Text(if (isPausedTopControl) strings.dcResume else strings.dcReplay, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.stopPlaylistTimeline() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(52.dp).weight(1f)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.dcStopPlay, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
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
                                        Text(strings.dcRunChoreography, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }
                                }

                                // --- Nút bấm Bluetooth (HID) điều khiển Play từ xa ---
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = strings.btRemoteTitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Switch(
                                                checked = btRemoteEnabled,
                                                onCheckedChange = { viewModel.setBtRemoteEnabled(it) }
                                            )
                                        }

                                        if (btRemoteEnabled) {
                                            val keyAssigned = btRemoteKeyCode >= 0
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (keyAssigned) Color(0xFF00C853) else Color(0xFFFF9100))
                                                )
                                                Text(
                                                    text = if (keyAssigned)
                                                        "${strings.btReadyPrefix} ${viewModel.btKeyLabel(btRemoteKeyCode)}"
                                                    else
                                                        strings.btNoKey,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (keyAssigned) Color(0xFF00C853) else Color(0xFFFF9100)
                                                )
                                            }

                                            Text(
                                                text = if (btRemoteLearning)
                                                    strings.btLearnNow
                                                else
                                                    strings.btHelp,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (btRemoteLearning) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (btRemoteLearning) viewModel.cancelBtRemoteLearning()
                                                        else viewModel.startBtRemoteLearning()
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f).height(44.dp)
                                                ) {
                                                    Text(
                                                        if (btRemoteLearning) strings.btWaiting else strings.btLearn,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { viewModel.triggerRemoteAction() },
                                                    enabled = keyAssigned,
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f).height(44.dp)
                                                ) {
                                                    Text(strings.btTest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }

                                            if (keyAssigned) {
                                                TextButton(onClick = { viewModel.clearBtRemoteKey() }) {
                                                    Text(strings.btClearKey, fontSize = 11.sp)
                                                }
                                            }
                                        }
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
                                            text = strings.dcShowDuration,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        val minSecString = String.format(Locale.US, "%02d:%02d", playlistTotalSeconds / 60, playlistTotalSeconds % 60)
                                        Text(
                                            text = "$playlistTotalSeconds ${strings.secondsUnit} ($minSecString)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // CHOREOGRAPHY BOARD TRACK VIEW
                                Spacer(
                                    modifier = Modifier
                                        .height(12.dp)
                                        .fillMaxWidth()
                                        .onGloballyPositioned { timelineBoardY = it.positionInRoot().y }
                                )

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
                                                    text = strings.dcBuildingTimeline,
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
                                                        text = strings.dcTimelineTitle,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                                Text(
                                                    text = strings.dcScrubHint,
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
                                                    contentDescription = strings.cdReloadShort,
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
                                                    text = if (isTimelineLocked) strings.dcLocked else strings.dcUnlockScrub,
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
                                                        text = strings.dcZoom,
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
                                                    text = strings.dcNoOnlineDevices,
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
                                                            text = strings.dcScanningPresets,
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
                                                                    text = strings.dcBgMusic,
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
                                                                            text = if (isTimelineLocked) strings.dcModeLockedRuler else if (isChoreographyMode) strings.dcModeChoreography else strings.dcModeNative,
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
                                                                            text = timeline?.error ?: strings.dcLoadingScript,
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
                                                        statusText = "${strings.stRunning} $playlistName"
                                                        statusColor = Color(0xFF00C853) // Vivid Green
                                                        pulseDot = true
                                                    }
                                                    PlaylistPlaybackState.Paused -> {
                                                        statusText = "${strings.stPaused} $playlistName"
                                                        statusColor = Color(0xFFFF9100) // Orange
                                                        pulseDot = true
                                                    }
                                                    PlaylistPlaybackState.Completed -> {
                                                        statusText = "${strings.stDone} $playlistName"
                                                        statusColor = MaterialTheme.colorScheme.primary
                                                        pulseDot = false
                                                    }
                                                    PlaylistPlaybackState.Idle -> {
                                                        statusText = "${strings.stReady} $playlistName"
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
                                                    Text("$playlistStepsCount ${strings.stepsUnit}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                            // Dynamic active mode badge (Choreography vs Native PL 249)
                                            Badge(
                                                containerColor = if (isChoreographyMode) Color(0xFFFF9100) else Color(0xFF00B0FF),
                                                contentColor = Color.Black
                                            ) {
                                                Text(
                                                    text = if (isChoreographyMode) strings.dcModeChoreoShort else strings.dcModeNativeShort,
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
                                                val elString = String.format(Locale.US, "%02d:%04.1f", (playlistElapsedSeconds.toInt() / 60), (playlistElapsedSeconds % 60f))
                                                val totString = String.format(Locale.US, "%02d:%02d", playlistTotalSeconds / 60, playlistTotalSeconds % 60)
                                                Text(
                                                    text = "${strings.dcElapsed} $elString",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Text(
                                                    text = "${strings.dcTotal} $totString",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (activeStepsMap.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            Text(
                                                text = strings.dcEffectTimeTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp
                                            )
                                            
                                            activeStepsMap.forEach { (deviceId, details) ->
                                                val devName = devices.find { it.id == deviceId }?.name ?: "${strings.deviceFallback} #$deviceId"
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
                                                            text = "${strings.dcEffectPrefix} ${details.presetName} (PS ${details.presetId})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 9.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                    
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = String.format(Locale.US, "%.1f ${strings.dcSecondsLeft}", details.remainingDuration),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF00C853)
                                                        )
                                                        Text(
                                                            text = String.format(Locale.US, "${strings.dcRanPrefix} %.1f / %.1f s", details.elapsedInStep, details.totalDuration),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 9.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = strings.dcAutoOffWarning,
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
                                                Text(strings.dcAllLedOff, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)
                                            }

                                            val isPaused = playlistPlaybackState == PlaylistPlaybackState.Paused
                                            val buttonText = when {
                                                playlistPlaybackState == PlaylistPlaybackState.Running -> strings.dcPause
                                                isPaused -> strings.dcResume
                                                playlistPlaybackState == PlaylistPlaybackState.Completed -> strings.dcReplay
                                                else -> strings.dcRunTimeline
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
                        val imageUploadState by viewModel.imageUploadState.collectAsStateWithLifecycle()
                        val uploadOnlineStats by viewModel.onlineDevicePresetStats.collectAsStateWithLifecycle()
                        ImageUploadTab(
                            devices = devices,
                            onlineStats = uploadOnlineStats,
                            mode = imageUploadMode,
                            onModeChange = { imageUploadMode = it },
                            poiPixelsText = poiPixelsText,
                            onPoiPixelsChange = { poiPixelsText = it },
                            flagWidthText = flagWidthText,
                            onFlagWidthChange = { flagWidthText = it },
                            flagHeightText = flagHeightText,
                            onFlagHeightChange = { flagHeightText = it },
                            writeMode = imageWriteMode,
                            onWriteModeChange = { imageWriteMode = it },
                            selectedUris = selectedImageUris,
                            onPickImages = { imagePickerLauncher.launch("image/*") },
                            onRemoveUri = { selectedImageUris = selectedImageUris - it },
                            onClearUris = { selectedImageUris = emptyList() },
                            deselectedDeviceIds = deselectedUploadDeviceIds,
                            onToggleDevice = { id ->
                                deselectedUploadDeviceIds = if (id in deselectedUploadDeviceIds) {
                                    deselectedUploadDeviceIds - id
                                } else {
                                    deselectedUploadDeviceIds + id
                                }
                            },
                            uploadState = imageUploadState,
                            onUpload = {
                                val targetIds = devices
                                    .filter { it.isOnline && it.id !in deselectedUploadDeviceIds }
                                    .map { it.id }
                                    .toSet()
                                viewModel.startImageUpload(
                                    mode = imageUploadMode,
                                    uris = selectedImageUris,
                                    poiPixels = poiPixelsText.toIntOrNull() ?: 0,
                                    flagWidth = flagWidthText.toIntOrNull() ?: 0,
                                    flagHeight = flagHeightText.toIntOrNull() ?: 0,
                                    writeMode = imageWriteMode,
                                    targetDeviceIds = targetIds
                                )
                            },
                            onDismissResult = { viewModel.dismissImageUpload() }
                        )
                    }

                    3 -> {
                        val editorClips by viewModel.editorClips.collectAsStateWithLifecycle()
                        val editorPresets by viewModel.editorPresets.collectAsStateWithLifecycle()
                        val editorTotalSeconds by viewModel.editorTotalSeconds.collectAsStateWithLifecycle()
                        val editorUploadState by viewModel.editorUploadState.collectAsStateWithLifecycle()
                        val isTimelineLockedEditor by viewModel.isTimelineLocked.collectAsStateWithLifecycle()
                        TimelineEditorTab(
                            devices = devices,
                            selectedDeviceId = editorSelectedDeviceId,
                            onSelectDevice = { viewModel.selectEditorDevice(it) },
                            presetsByDevice = editorPresets,
                            clipsByDevice = editorClips,
                            totalSeconds = editorTotalSeconds,
                            pxPerSec = pxPerSec,
                            onPxPerSecChange = { pxPerSec = it },
                            isLocked = isTimelineLockedEditor,
                            onToggleLock = { viewModel.setTimelineLocked(!isTimelineLockedEditor) },
                            onPreviewPreset = { deviceId, preset -> viewModel.previewPreset(deviceId, preset.id) },
                            onAddClip = { deviceId, preset, startSec ->
                                viewModel.addClip(deviceId, preset.id, preset.name, startSec)
                            },
                            onApplyLayout = { deviceId, layout -> viewModel.setClipLayout(deviceId, layout) },
                            onRemoveClip = { deviceId, clipId -> viewModel.removeClip(deviceId, clipId) },
                            onClearDevice = { viewModel.clearDeviceClips(it) },
                            onExtend = { viewModel.extendEditorTotalSeconds() },
                            uploadState = editorUploadState,
                            onUpload = {
                                // Chỉ NẠP vào playlist 249, không tự chạy. Chạy ở tab Đồng Loạt (All).
                                viewModel.compileAndUploadEditorTimelines(loop = true)
                            },
                            onJumpToAllTab = {
                                viewModel.setSelectedTab(1)
                                pendingJumpToTimeline = true
                            },
                            onDismissUpload = { viewModel.dismissEditorUpload() }
                        )
                    }
                }
                }
            }
        }
    }
}
}

@Composable
internal fun DeviceInfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
internal fun DeviceInfoRow(
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
internal fun DeviceMemoryInfoRows(
    fs: WledFilesystemInfo?,
    wifiSignal: Int?,
    stats: DevicePresetStorageStats,
    onFindImagesToDelete: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
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
            label = strings.memFilesystem,
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
                label = strings.memStatus,
                value = storageStatusText(percent),
                valueColor = statusColor
            )
            DeviceInfoDivider()
            DeviceInfoRow(
                label = strings.memFree,
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
                        text = "${strings.memNearFull} ($percent%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = strings.memNearFullBody,
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
                    text = strings.memFindCleanup,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DeviceInfoDivider()
        DeviceInfoRow(
            label = strings.memWifi,
            value = wifiSignal?.let { "$it%" } ?: "N/A"
        )

        DeviceInfoDivider()
        val presetValueColor = if (stats.error == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        DeviceInfoRow(
            label = strings.memLogoImages,
            value = when {
                stats.isLoading -> strings.loadingRead
                stats.error != null -> strings.readError
                else -> "${stats.logoUsed} preset"
            },
            valueColor = presetValueColor
        )
        DeviceInfoDivider()
        DeviceInfoRow(
            label = "Preset timecode:",
            value = when {
                stats.isLoading -> strings.loadingRead
                stats.error != null -> strings.readError
                else -> "${stats.timecodeUsed}/${stats.timecodeCapacity} slot"
            },
            valueColor = presetValueColor
        )
        DeviceInfoDivider()
        DeviceInfoRow(
            label = strings.memSystemPresets,
            value = when {
                stats.isLoading -> strings.loadingRead
                stats.error != null -> strings.readError
                else -> "${stats.systemUsed}/${stats.systemCapacity} slot"
            },
            valueColor = presetValueColor
        )
        if (!stats.isLoading && stats.error == null && stats.otherUsed > 0) {
            DeviceInfoDivider()
            DeviceInfoRow(label = strings.memOtherPresets, value = "${stats.otherUsed} ${strings.presetUnit}")
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
