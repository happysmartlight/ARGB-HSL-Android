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
internal fun TimelineEditorTab(
    devices: List<WledDevice>,
    selectedDeviceId: Int?,
    onSelectDevice: (Int) -> Unit,
    presetsByDevice: Map<Int, List<EditablePreset>>,
    clipsByDevice: Map<Int, List<TimelineClip>>,
    totalSeconds: Int,
    pxPerSec: Float,
    onPxPerSecChange: (Float) -> Unit,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onPreviewPreset: (deviceId: Int, preset: EditablePreset) -> Unit,
    onAddClip: (deviceId: Int, preset: EditablePreset, startSec: Float) -> Unit,
    onApplyLayout: (deviceId: Int, layout: List<Triple<Long, Float, Float>>) -> Unit,
    onRemoveClip: (deviceId: Int, clipId: Long) -> Unit,
    onClearDevice: (deviceId: Int) -> Unit,
    onExtend: () -> Unit,
    uploadState: EditorUploadUiState,
    onUpload: () -> Unit,
    onJumpToAllTab: () -> Unit,
    onDismissUpload: () -> Unit
) {
    val strings = LocalAppStrings.current
    val density = LocalDensity.current
    val ppsPx = pxPerSec * density.density
    val onlineDevices = devices.filter { it.isOnline }
    val selectedDevice = onlineDevices.find { it.id == selectedDeviceId }
    val presets = selectedDeviceId?.let { presetsByDevice[it] } ?: emptyList()

    val rulerHeight = 22.dp
    val laneHeight = 46.dp
    val gutterWidth = 96.dp
    val contentWidth = (totalSeconds * pxPerSec).dp.coerceAtLeast(1.dp)
    val boardScroll = rememberScrollState()

    // Drag-and-drop shared state (root coordinates)
    var dragging by remember { mutableStateOf<EditablePreset?>(null) }
    var dragPosRoot by remember { mutableStateOf(Offset.Zero) }
    var selectedLaneRect by remember { mutableStateOf<Rect?>(null) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }

    // Clip đang chọn (deviceId, clipId) — bật nút xóa ngoài timeline + hiện handle co giãn.
    var selectedClip by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    // Khi đã chọn/đang kéo clip → KHOÁ cuộn ngang để cuộn không "cướp" gesture gây giật.
    var clipDragActive by remember { mutableStateOf(false) }
    val selectedClipObj = selectedClip?.let { (d, id) -> clipsByDevice[d]?.find { it.id == id } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootPos = it.positionInRoot() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Chọn mạch (đổi list preset + track drop target) ----
            if (onlineDevices.isEmpty()) {
                Text(
                    text = strings.tlNoOnline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
                return@Column
            }
            Text(
                text = strings.tlPickDevice,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                onlineDevices.forEach { dev ->
                    val sel = dev.id == selectedDeviceId
                    if (sel) {
                        Button(onClick = { onSelectDevice(dev.id) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(38.dp)) {
                            Text(dev.name, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelectDevice(dev.id) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(38.dp)) {
                            Text(dev.name, maxLines = 1)
                        }
                    }
                }
            }

            // ---- Danh sách preset (nguồn kéo) ----
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${strings.tlPresetsOf} ${selectedDevice?.name ?: "—"} ${strings.tlHoldDrag}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (presets.isEmpty()) {
                        Text(
                            text = strings.tlNoPresets,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { preset ->
                                key(preset.id) {
                                    var chipRoot by remember { mutableStateOf(Offset.Zero) }
                                    val isDraggable = !isLocked && selectedDeviceId != null
                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { chipRoot = it.positionInRoot() }
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                            // Touch nhanh = phát thử preset; giữ lâu = kéo xuống track.
                                            .pointerInput(preset.id, selectedDeviceId, isLocked, pxPerSec) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    val longPress = awaitLongPressOrCancellation(down.id)
                                                    if (longPress == null) {
                                                        // Thả tay trước khi giữ lâu → tap nhanh → demo preset
                                                        selectedDeviceId?.let { onPreviewPreset(it, preset) }
                                                    } else if (isDraggable) {
                                                        // Giữ lâu → bắt đầu kéo
                                                        dragging = preset
                                                        dragPosRoot = chipRoot + longPress.position
                                                        drag(longPress.id) { change ->
                                                            dragPosRoot += change.positionChange()
                                                            change.consume()
                                                        }
                                                        val rect = selectedLaneRect
                                                        val p = dragging
                                                        val devId = selectedDeviceId
                                                        if (rect != null && p != null && devId != null && rect.contains(dragPosRoot)) {
                                                            val t = ((dragPosRoot.x - rect.left) / ppsPx).coerceIn(0f, totalSeconds.toFloat())
                                                            onAddClip(devId, p, t)
                                                        }
                                                        dragging = null
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "#${preset.id}  ${preset.name}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Thanh điều khiển: lock / zoom / extend ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val lockColor = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleLock() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Edit,
                        contentDescription = null,
                        tint = lockColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(if (isLocked) strings.tlLocked else strings.tlEdit, style = MaterialTheme.typography.labelSmall, color = lockColor, fontWeight = FontWeight.Bold)
                }
                Text("Zoom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Slider(
                    value = pxPerSec,
                    onValueChange = onPxPerSecChange,
                    valueRange = 8f..50f,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onExtend, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("+30s", style = MaterialTheme.typography.labelSmall)
                }
            }

            // ---- Thanh thao tác clip đang chọn (nút xóa nằm NGOÀI timeline) ----
            val selDur = selectedClipObj?.durationSec ?: 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedClipObj != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedClipObj != null) {
                    Text(
                        text = "${strings.tlSelectedPrefix} #${selectedClipObj.presetId} ${selectedClipObj.presetName} · ${"%.1f".format(selectedClipObj.startSec)}s→${"%.1f".format(selectedClipObj.startSec + selDur)}s",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = strings.tlTapToSelect,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        selectedClip?.let { (d, id) -> onRemoveClip(d, id) }
                        selectedClip = null
                    },
                    enabled = selectedClipObj != null && !isLocked,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onError)
                    Spacer(Modifier.width(4.dp))
                    Text(strings.tlDeleteClip, color = MaterialTheme.colorScheme.onError)
                }
            }

            // ---- Bảng track ----
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    // Cột nhãn thiết bị (cố định)
                    Column(modifier = Modifier.width(gutterWidth)) {
                        Spacer(Modifier.height(rulerHeight))
                        onlineDevices.forEach { dev ->
                            val sel = dev.id == selectedDeviceId
                            val count = clipsByDevice[dev.id]?.size ?: 0
                            Box(
                                modifier = Modifier
                                    .height(laneHeight)
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = 2.dp, end = 4.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                    )
                                    .clickable { onSelectDevice(dev.id) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column {
                                    Text(dev.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                    Text("$count clip", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    // Vùng cuộn ngang: thước + lanes. KHOÁ cuộn khi đã chọn 1 clip hoặc đang kéo
                    // (để cuộn ngang không "cướp" gesture của clip gây giật). Bỏ chọn (chạm vùng
                    // trống) để cuộn lại.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(boardScroll, enabled = selectedClip == null && !clipDragActive)
                    ) {
                        Column(modifier = Modifier.width(contentWidth)) {
                            EditorRuler(totalSeconds = totalSeconds, pxPerSec = pxPerSec, height = rulerHeight)
                            onlineDevices.forEach { dev ->
                                val laneClips = clipsByDevice[dev.id] ?: emptyList()
                                val sel = dev.id == selectedDeviceId

                                // Vị trí/độ rộng px (hoisted) — đọc ở LAYOUT-PHASE để mượt; cập nhật khi kéo
                                // để các clip khác DẠT realtime (ripple), không bao giờ cho chồng lấn.
                                val minClipWidthPx = with(density) { 5.dp.toPx() }
                                val clipSnapPx = with(density) { 10.dp.toPx() }
                                val posStates = remember(dev.id) { mutableMapOf<Long, MutableFloatState>() }
                                val widStates = remember(dev.id) { mutableMapOf<Long, MutableFloatState>() }
                                var draggingId by remember(dev.id) { mutableStateOf<Long?>(null) }
                                var draggingResize by remember(dev.id) { mutableStateOf(false) }
                                var dragAccumPx by remember(dev.id) { mutableFloatStateOf(0f) }
                                val baseStart = remember(dev.id) { mutableMapOf<Long, Float>() }
                                val baseWidth = remember(dev.id) { mutableMapOf<Long, Float>() }

                                // Tạo state cho clip mới + đồng bộ lại từ VM khi KHÔNG kéo.
                                val clipsKey = laneClips.joinToString(",") { "${it.id}:${it.startSec}:${it.durationSec}" }
                                LaunchedEffect(clipsKey, pxPerSec) {
                                    if (draggingId != null) return@LaunchedEffect
                                    val ids = HashSet<Long>()
                                    laneClips.forEach { c ->
                                        ids.add(c.id)
                                        posStates.getOrPut(c.id) { mutableFloatStateOf(0f) }.floatValue = c.startSec * ppsPx
                                        widStates.getOrPut(c.id) { mutableFloatStateOf(0f) }.floatValue = (c.durationSec * ppsPx).coerceAtLeast(minClipWidthPx)
                                    }
                                    posStates.keys.retainAll(ids); widStates.keys.retainAll(ids)
                                }

                                // Các hàm thao tác CHỈ dùng map đã remember (ổn định) → không bị stale closure.
                                fun recomputeLive() {
                                    val anchor = draggingId ?: return
                                    val baseW = baseWidth[anchor] ?: return
                                    val aw = if (draggingResize) (baseW + dragAccumPx).coerceAtLeast(minClipWidthPx) else baseW
                                    widStates[anchor]?.floatValue = aw
                                    val otherIds = baseStart.keys.filter { it != anchor }
                                    if (draggingResize) {
                                        val aStart = baseStart[anchor] ?: 0f
                                        posStates[anchor]?.floatValue = aStart
                                        var run = aStart + aw
                                        otherIds.sortedBy { baseStart[it]!! }.forEach { id ->
                                            val bs = baseStart[id]!!; val bw = baseWidth[id] ?: 0f
                                            if (bs >= aStart) { val s = maxOf(bs, run); posStates[id]?.floatValue = s; run = s + bw }
                                            else posStates[id]?.floatValue = bs
                                        }
                                    } else {
                                        // Phân nhóm trái/phải theo VỊ TRÍ GỐC (cố định lúc bắt đầu kéo) — KHÔNG
                                        // phân theo tâm mỗi frame, nên clip không "lật" qua lại gây giật/nhảy.
                                        val aFinger = ((baseStart[anchor] ?: 0f) + dragAccumPx).coerceAtLeast(0f)
                                        val anchorBase = baseStart[anchor] ?: 0f
                                        val leftIds = otherIds.filter { (baseStart[it] ?: 0f) < anchorBase }
                                        val rightIds = otherIds.filter { (baseStart[it] ?: 0f) >= anchorBase }
                                        val leftEnd = leftIds.maxOfOrNull { (baseStart[it] ?: 0f) + (baseWidth[it] ?: 0f) } ?: 0f
                                        // Kéo phải: đẩy các clip bên phải (ripple). Kéo trái: chặn ở mép phải nhóm trái.
                                        val aStart = maxOf(aFinger, leftEnd)
                                        posStates[anchor]?.floatValue = aStart
                                        leftIds.forEach { posStates[it]?.floatValue = baseStart[it]!! }
                                        var run = aStart + aw
                                        rightIds.sortedBy { baseStart[it]!! }.forEach { id ->
                                            val bs = baseStart[id]!!; val bw = baseWidth[id] ?: 0f
                                            val s = maxOf(bs, run); posStates[id]?.floatValue = s; run = s + bw
                                        }
                                    }
                                }
                                fun snapPx(px: Float, excludeId: Long): Float {
                                    var best = px; var bestDist = clipSnapPx
                                    posStates.keys.filter { it != excludeId }.forEach { id ->
                                        val s = posStates[id]?.floatValue ?: return@forEach
                                        val e = s + (widStates[id]?.floatValue ?: 0f)
                                        listOf(s, e).forEach { cand -> val d = kotlin.math.abs(cand - px); if (d < bestDist) { bestDist = d; best = cand } }
                                    }
                                    val secPx = (px / ppsPx).roundToInt() * ppsPx
                                    if (kotlin.math.abs(secPx - px) < bestDist) best = secPx
                                    return best
                                }
                                fun startDrag(id: Long, resize: Boolean) {
                                    clipDragActive = true
                                    selectedClip = dev.id to id
                                    draggingId = id; draggingResize = resize; dragAccumPx = 0f
                                    posStates.keys.forEach { cid ->
                                        baseStart[cid] = posStates[cid]?.floatValue ?: 0f
                                        baseWidth[cid] = widStates[cid]?.floatValue ?: minClipWidthPx
                                    }
                                }
                                fun dragBy(dx: Float) { dragAccumPx += dx; recomputeLive() }
                                fun endDrag() {
                                    val anchor = draggingId
                                    if (anchor != null) {
                                        if (draggingResize) {
                                            val aStart = baseStart[anchor] ?: 0f
                                            val curW = widStates[anchor]?.floatValue ?: 0f
                                            if (curW / ppsPx >= 0.6f) {
                                                val snappedEnd = snapPx(aStart + curW, anchor)
                                                dragAccumPx = (snappedEnd - aStart) - (baseWidth[anchor] ?: 0f)
                                                recomputeLive()
                                            }
                                        } else {
                                            val snappedStart = snapPx(posStates[anchor]?.floatValue ?: 0f, anchor)
                                            dragAccumPx = snappedStart - (baseStart[anchor] ?: 0f)
                                            recomputeLive()
                                        }
                                    }
                                    val layout = posStates.keys.mapNotNull { id ->
                                        val s = posStates[id]?.floatValue ?: return@mapNotNull null
                                        val w = widStates[id]?.floatValue ?: return@mapNotNull null
                                        Triple(id, s / ppsPx, w / ppsPx)
                                    }
                                    draggingId = null
                                    clipDragActive = false
                                    if (layout.isNotEmpty()) onApplyLayout(dev.id, layout)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(laneHeight)
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (sel && dragging != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                                        )
                                        .then(
                                            if (sel) Modifier.onGloballyPositioned { c ->
                                                selectedLaneRect = Rect(
                                                    c.positionInRoot(),
                                                    androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat())
                                                )
                                            } else Modifier
                                        )
                                        // Chạm vùng trống của lane → bỏ chọn clip.
                                        .pointerInput(Unit) {
                                            detectTapGestures { selectedClip = null }
                                        }
                                ) {
                                    laneClips.forEach { clip ->
                                        key(clip.id) {
                                            val sp = posStates.getOrPut(clip.id) { mutableFloatStateOf(clip.startSec * ppsPx) }
                                            val wp = widStates.getOrPut(clip.id) { mutableFloatStateOf((clip.durationSec * ppsPx).coerceAtLeast(minClipWidthPx)) }
                                            EditorClipBlock(
                                                clip = clip,
                                                isLocked = isLocked,
                                                isSelected = selectedClip == (dev.id to clip.id),
                                                startPxState = sp,
                                                widthPxState = wp,
                                                minWidthPx = minClipWidthPx,
                                                onSelect = { selectedClip = dev.id to clip.id },
                                                onDragStart = { resize -> startDrag(clip.id, resize) },
                                                onDragBy = { _, dx -> dragBy(dx) },
                                                onDragEnd = { endDrag() }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Hành động ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedDeviceId != null && (clipsByDevice[selectedDeviceId]?.isNotEmpty() == true)) {
                    OutlinedButton(
                        onClick = { onClearDevice(selectedDeviceId) },
                        enabled = !isLocked,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.tlDeleteTrack)
                    }
                }
                Button(
                    onClick = onUpload,
                    enabled = !uploadState.isRunning,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    if (uploadState.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.tlUploading, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.tlUploadPlaylist, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Chạy phải qua tab Đồng Loạt (All): nút chuyển nhanh + cuộn tới bảng timeline.
            OutlinedButton(
                onClick = onJumpToAllTab,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.tlGoToAllTab, fontWeight = FontWeight.Bold)
            }

            if (uploadState.finished || uploadState.error != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uploadState.error?.let {
                            Text("${strings.errorPrefix} $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        uploadState.results.forEach { r ->
                            Text(r, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        OutlinedButton(onClick = onDismissUpload, shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.End)) {
                            Text(strings.close)
                        }
                    }
                }
            }
        }

        // ---- Ghost theo ngón tay khi kéo ----
        dragging?.let { p ->
            val gx = dragPosRoot.x - rootPos.x
            val gy = dragPosRoot.y - rootPos.y
            val halfW = with(density) { 50.dp.toPx() }
            val halfH = with(density) { 16.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(Math.round(gx - halfW), Math.round(gy - halfH)) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("#${p.id} ${p.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun EditorRuler(totalSeconds: Int, pxPerSec: Float, height: androidx.compose.ui.unit.Dp) {
    val tickStep = when {
        pxPerSec < 12f -> 10
        pxPerSec < 25f -> 5
        else -> 2
    }
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .width((totalSeconds * pxPerSec).dp.coerceAtLeast(1.dp))
            .height(height)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            var s = 0
            while (s <= totalSeconds) {
                val x = (s * pxPerSec).dp.toPx()
                drawLine(
                    color = tickColor,
                    start = Offset(x, h),
                    end = Offset(x, h - 8.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
                s += tickStep
            }
        }
        for (sec in 0..totalSeconds step tickStep) {
            Text(
                text = "${sec}s",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (sec * pxPerSec).dp)
                    .padding(start = 2.dp)
            )
        }
    }
}

@Composable
internal fun EditorClipBlock(
    clip: TimelineClip,
    isLocked: Boolean,
    isSelected: Boolean,
    startPxState: MutableFloatState,
    widthPxState: MutableFloatState,
    minWidthPx: Float,
    onSelect: () -> Unit,
    onDragStart: (resize: Boolean) -> Unit,
    onDragBy: (resize: Boolean, dxPx: Float) -> Unit,
    onDragEnd: (resize: Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            // Vị trí & độ rộng đọc Ở LAYOUT-PHASE (không recompose mỗi frame) → mượt.
            // State do LANE sở hữu nên khi kéo, lane đẩy luôn các clip khác (ripple realtime).
            .offset { androidx.compose.ui.unit.IntOffset(startPxState.floatValue.roundToInt(), 0) }
            .layout { measurable, constraints ->
                val w = widthPxState.floatValue.roundToInt().coerceAtLeast(minWidthPx.roundToInt())
                val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(w, placeable.height) { placeable.place(0, 0) }
            }
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    if (isSelected) listOf(Color(0xFF00E0FF), Color(0xFFB36BFF))
                    else listOf(Color(0xFF00B0FF), Color(0xFF8B5CF6))
                )
            )
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(5.dp))
                else Modifier
            )
            .pointerInput(clip.id) { detectTapGestures { onSelect() } }
            .then(
                if (!isLocked) Modifier.pointerInput(clip.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart(false) },
                        onDrag = { change, amount -> change.consume(); onDragBy(false, amount.x) },
                        onDragEnd = { onDragEnd(false) },
                        onDragCancel = { onDragEnd(false) }
                    )
                } else Modifier
            )
    ) {
        Text(
            text = clip.presetName,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp, end = if (isSelected) 26.dp else 8.dp)
        )
        // Handle co giãn — to & dễ chạm, chỉ hiện khi clip được chọn.
        if (!isLocked && isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(20.dp)
                    .padding(vertical = 3.dp, horizontal = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .pointerInput(clip.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart(true) },
                            onDrag = { change, amount -> change.consume(); onDragBy(true, amount.x) },
                            onDragEnd = { onDragEnd(true) },
                            onDragCancel = { onDragEnd(true) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(14.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}
