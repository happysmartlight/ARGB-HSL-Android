package com.example.ui.i18n

import androidx.compose.runtime.Composable

/** Chuỗi mới của tab Biên Tập Timeline + trạng thái lặp ở tab Đồng Loạt (vi/en), theo [LocalAppStrings]. */
interface TimelineUiStrings {
    val searchHint: String
    val filterAll: String
    val filterPoi: String
    val filterFlag: String
    val filterEffect: String
    val refreshPresets: String
    val noMatch: String
    val presetCount: String

    val duration: String
    val transition: String
    val applyToTrack: String
    val transitionHint: String
    val missingPreset: String

    val loop: String
    val loopOnHint: String
    val loopOffHint: String
    val lengthLabel: String

    val loadFromDevices: String
    val loading: String
    val loadConfirmTitle: String
    val loadConfirmBody: String
    val loadConfirmOk: String
    val cancel: String
    val loadResultTitle: String
    val uploadResultTitle: String

    val allLoopBadge: String
    val allOnceBadge: String
    val allLoopWarning: String
}

object TimelineVi : TimelineUiStrings {
    override val searchHint = "Tìm preset (tên hoặc số ID)…"
    override val filterAll = "Tất cả"
    override val filterPoi = "POI"
    override val filterFlag = "Cờ LED"
    override val filterEffect = "Hiệu ứng"
    override val refreshPresets = "Tải lại danh sách preset"
    override val noMatch = "Không có preset khớp bộ lọc."
    override val presetCount = "preset"

    override val duration = "Thời lượng"
    override val transition = "Chuyển cảnh"
    override val applyToTrack = "Áp cho cả track"
    override val transitionHint = "Chuyển cảnh nằm trong thời lượng clip (firmware không cộng thêm)."
    override val missingPreset = "⚠ Preset này không còn trên mạch — kéo lại từ danh sách."

    override val loop = "Lặp lại liên tục"
    override val loopOnHint = "Playlist 249 lặp tới khi bấm Dừng"
    override val loopOffHint = "Chạy 1 lần, hết thì tắt LED"
    override val lengthLabel = "Độ dài (clip kết thúc muộn nhất):"

    override val loadFromDevices = "ĐỌC 249 TỪ MẠCH"
    override val loading = "Đang đọc…"
    override val loadConfirmTitle = "Đọc playlist 249 từ mạch?"
    override val loadConfirmBody = "Track của các mạch đọc được sẽ được thay bằng playlist 249 đang lưu trên mạch. " +
        "Mạch offline hoặc chưa có playlist 249 giữ nguyên clip."
    override val loadConfirmOk = "Đọc & thay track"
    override val cancel = "Hủy"
    override val loadResultTitle = "Kết quả đọc playlist 249"
    override val uploadResultTitle = "Kết quả nạp playlist 249"

    override val allLoopBadge = "🔁 LẶP"
    override val allOnceBadge = "1 LẦN"
    override val allLoopWarning = "🔁 Playlist 249 lặp liên tục — bấm Dừng / Tắt LED để kết thúc."
}

object TimelineEn : TimelineUiStrings {
    override val searchHint = "Search presets (name or ID)…"
    override val filterAll = "All"
    override val filterPoi = "POI"
    override val filterFlag = "LED flag"
    override val filterEffect = "Effects"
    override val refreshPresets = "Reload preset list"
    override val noMatch = "No preset matches the filter."
    override val presetCount = "presets"

    override val duration = "Duration"
    override val transition = "Transition"
    override val applyToTrack = "Apply to whole track"
    override val transitionHint = "The transition runs inside the clip duration (firmware does not add it)."
    override val missingPreset = "⚠ This preset is no longer on the device — drag it again from the list."

    override val loop = "Loop continuously"
    override val loopOnHint = "Playlist 249 repeats until you press Stop"
    override val loopOffHint = "Plays once, then turns the LEDs off"
    override val lengthLabel = "Length (latest clip end):"

    override val loadFromDevices = "LOAD 249 FROM DEVICES"
    override val loading = "Loading…"
    override val loadConfirmTitle = "Load playlist 249 from devices?"
    override val loadConfirmBody = "Tracks of the devices that have a playlist 249 will be replaced with the playlist stored " +
        "on the device. Offline devices and devices without playlist 249 keep their clips."
    override val loadConfirmOk = "Load & replace tracks"
    override val cancel = "Cancel"
    override val loadResultTitle = "Playlist 249 load result"
    override val uploadResultTitle = "Playlist 249 upload result"

    override val allLoopBadge = "🔁 LOOP"
    override val allOnceBadge = "ONCE"
    override val allLoopWarning = "🔁 Playlist 249 loops continuously — press Stop / LEDs off to end it."
}

@Composable
fun timelineStrings(): TimelineUiStrings = if (LocalAppStrings.current === EnStrings) TimelineEn else TimelineVi
