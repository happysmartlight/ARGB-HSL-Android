package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Bộ chuỗi đa ngôn ngữ của app (vi/en).
 *
 * Cách dùng trong composable:
 *   val strings = LocalAppStrings.current
 *   Text(strings.close)
 *
 * `LocalAppStrings` được provide ở gốc app (MainActivity) theo `viewModel.appLanguage`,
 * nên đổi ngôn ngữ trong Cài đặt là toàn bộ UI dùng AppStrings đổi theo ngay lập tức.
 * Chuỗi do ViewModel sinh (log, toast nghiệp vụ, lỗi mạng) chưa thuộc phạm vi này.
 */
interface AppStrings {
    // ---- Chung ----
    val back: String
    val close: String
    val cancel: String
    val confirm: String
    val online: String
    val offline: String
    val deviceUnit: String
    val secondsUnit: String
    val stepsUnit: String
    val errorPrefix: String
    val loadingRead: String
    val readError: String

    // ---- Tab điều khiển ----
    val tabConfig: String
    val tabAll: String
    val tabUpload: String
    val tabTimeline: String

    // ---- Top bar (content description) ----
    val cdPlayAll: String
    val cdManagePro: String
    val cdUpgradePro: String
    val cdSettings: String
    val cdRefresh: String
    val cdAddDevice: String

    // ---- Màn trống (chưa chọn thiết bị) ----
    val emptyControlTitle: String
    val emptyControlBody: String

    // ---- Dialog thêm thiết bị ----
    val addDeviceTitle: String
    val addDeviceNameLabel: String
    val addDeviceIpLabel: String
    val addDeviceValidationError: String
    val addDeviceLink: String

    // ---- Danh sách thiết bị ----
    val dlFoundAuto: String
    val dlAddAll: String
    val dlQuickAddCd: String
    val dlActiveDevices: String
    val dlAllOn: String
    val dlAllOff: String
    val dlPartialOn: String
    val dlToggleAllCd: String
    val dlEmptyTitle: String
    val dlEmptyBody: String
    val dlTapToLink: String
    val dlUnpairCd: String

    // ---- Màn điều khiển: header 4 tab ----
    val dcHeader0: String; val dcSub0: String
    val dcHeader1: String; val dcSub1: String
    val dcHeader2: String; val dcSub2: String
    val dcHeader3: String; val dcSub3: String
    val cdBackToList: String

    // ---- Panel ngoại tuyến ----
    val dcOfflineTitle: String
    val dcOfflineBody: String

    // ---- Tab 1: cấu hình đơn mạch ----
    val dcMasterBrightness: String
    val dcPowerCd: String
    val dcTurnOn: String
    val dcTurnOff: String
    val dcMainColor: String
    val dcQuickColors: String
    val colorRed: String; val colorOrange: String; val colorYellow: String
    val colorGreen: String; val colorCyan: String; val colorBlue: String
    val colorPurple: String; val colorWhite: String; val colorBlack: String
    val dcColorWheel: String
    val dcHue: String
    val dcSat: String
    val dcValueBrightness: String
    val dcDeviceInfo: String
    val dcProductName: String
    val dcDefaultProduct: String
    val dcBrand: String
    val dcFwVersion: String
    val dcVid: String
    val dcIpAccess: String
    val dcClientName: String
    val dcLedCount: String
    val dcPwr: String
    val dcMaxPwr: String
    val dcFps: String
    val dcPoiModeActive: String

    // ---- Khóa Pro theo tab ----
    val proLockTitleAll: String
    val proLockTitleUpload: String
    val proLockTitleTimeline: String
    val proLockDescAll: String
    val proLockDescUpload: String
    val proLockDescTimeline: String
    val proUpgradeShort: String

    // ---- Tab 2: đồng loạt ----
    val dcStageControl: String
    val dcParallelPower: String
    val dcAllOnBtn: String
    val dcAllOffBtn: String
    val dcGroupBrightness: String
    val dcGroupColor: String
    val sceneRed: String; val sceneOrange: String; val sceneYellow: String
    val sceneGreen: String; val sceneCyan: String; val sceneBlue: String
    val scenePurple: String; val sceneWhite: String; val sceneBlack: String
    val dcScenePalette: String
    val dcRunPlaylist: String
    val dcMusicSync: String
    val dcMusicLoaded: String
    val dcMusicHelp: String
    val dcAudioSource: String
    val dcPickMusic: String
    val dcRemoveMusic: String
    val dcRecentMusic: String
    val dcMusicNote: String
    val dcStatusPlaying: String
    val dcStatusPaused: String
    val dcResume: String
    val dcReplay: String
    val dcStopPlay: String
    val dcRunChoreography: String
    val btRemoteTitle: String
    val btReadyPrefix: String
    val btNoKey: String
    val btLearnNow: String
    val btHelp: String
    val btWaiting: String
    val btLearn: String
    val btTest: String
    val btClearKey: String
    val dcShowDuration: String
    val dcBuildingTimeline: String
    val dcTimelineTitle: String
    val dcScrubHint: String
    val cdReloadShort: String
    val dcLocked: String
    val dcUnlockScrub: String
    val dcZoom: String
    val dcNoOnlineDevices: String
    val dcScanningPresets: String
    val dcBgMusic: String
    val dcModeLockedRuler: String
    val dcModeChoreography: String
    val dcModeNative: String
    val dcLoadingScript: String
    val stRunning: String
    val stPaused: String
    val stDone: String
    val stReady: String
    val dcModeChoreoShort: String
    val dcModeNativeShort: String
    val dcElapsed: String
    val dcTotal: String
    val dcEffectTimeTitle: String
    val deviceFallback: String
    val dcEffectPrefix: String
    val dcSecondsLeft: String
    val dcRanPrefix: String
    val dcAutoOffWarning: String
    val dcAllLedOff: String
    val dcPause: String
    val dcRunTimeline: String

    // ---- Bộ nhớ thiết bị (tab 1) ----
    val memFilesystem: String
    val memStatus: String
    val memFree: String
    val memNearFull: String
    val memNearFullBody: String
    val memFindCleanup: String
    val memWifi: String
    val memLogoImages: String
    val memSystemPresets: String
    val memOtherPresets: String
    val presetUnit: String

    // ---- Tab 3: Upload POI & Cờ LED ----
    val upCreatePreset: String
    val upPoiLabel: String
    val upMatrixLabel: String
    val upPickImages: String
    val upClearAll: String
    val upPickHint: String
    val upSelectedPrefix: String
    val upImagesUnit: String
    val upRemoveImageCd: String
    val upPoiPixelsLabel: String
    val upPoiPixelsHint: String
    val upWidthLabel: String
    val upHeightLabel: String
    val upMatrixHint: String
    val upWriteAppend: String
    val upWriteOverwrite: String
    val upOverwriteWarning: String
    val upTargetDevices: String
    val upNoOnline: String
    val upFreeSlot: String
    val upUploading: String
    val upUploadPoi: String
    val upUploadMatrix: String
    val errShort: String
    val skippedShort: String

    // ---- Tab 4: Biên tập timeline ----
    val tlNoOnline: String
    val tlPickDevice: String
    val tlPresetsOf: String
    val tlHoldDrag: String
    val tlNoPresets: String
    val tlLocked: String
    val tlEdit: String
    val tlSelectedPrefix: String
    val tlTapToSelect: String
    val tlDeleteClip: String
    val tlDeleteTrack: String
    val tlUploading: String
    val tlUploadPlaylist: String
    val tlGoToAllTab: String

    // ---- Trang Cài đặt ----
    val settingsTitle: String
    val sectionLanguage: String
    val languageDemoNote: String
    val sectionGeneral: String
    val appInfo: String
    val appInfoSub: String
    val systemLogs: String
    val systemLogsSub: String
    val sectionAbout: String
    val version: String

    // ---- Trang Thông tin ứng dụng ----
    val infoTitle: String
    val infoVersionLabel: String
    val infoManufacturer: String
    val infoWebsite: String
    val infoZaloScan: String
    val infoOpenZalo: String
    val infoConnectionSection: String
    val infoNetwork: String
    val infoSignal: String
    val infoDeviceIp: String
    val infoTagline: String

    // ---- Trang Nhật ký hệ thống ----
    val logsTitle: String
    val logsDescription: String
    val logsEmpty: String
    val logsClear: String

    // ---- Trang Pro ----
    val proTitleActive: String
    val proTitleUpgrade: String
    val proSubtitle: String
    val proYearlyPlan: String
    val proAutoRenewNote: String
    val proBenefit1: String
    val proBenefit2: String
    val proBenefit3: String
    val proRestore: String
    val proBuy: String
    val proAlready: String
    val proReloadBilling: String
    val proManageOnPlay: String
    val proDebugOn: String
    val proDebugOff: String

    // ---- Trang Pro: hero + feature cards ----
    val proHeroTagline: String
    val proBadgeActive: String
    val proWhatYouGet: String
    val proFeat1Title: String; val proFeat1Desc: String
    val proFeat2Title: String; val proFeat2Desc: String
    val proFeat3Title: String; val proFeat3Desc: String
    val proFeat4Title: String; val proFeat4Desc: String
    val proFeat5Title: String; val proFeat5Desc: String
    val proFeat6Title: String; val proFeat6Desc: String
}

val ViStrings: AppStrings = object : AppStrings {
    override val back = "Quay lại"
    override val close = "Đóng"
    override val cancel = "Hủy"
    override val confirm = "Xác nhận"
    override val online = "Trực tuyến"
    override val offline = "Ngoại tuyến"
    override val deviceUnit = "thiết bị"
    override val secondsUnit = "giây"
    override val stepsUnit = "bước"
    override val errorPrefix = "Lỗi:"
    override val loadingRead = "Đang đọc..."
    override val readError = "Lỗi đọc"

    override val tabConfig = "Cấu Hình Mạch"
    override val tabAll = "Đồng Loạt (All)"
    override val tabUpload = "Upload POI & Cờ LED"
    override val tabTimeline = "Biên Tập Timeline"

    override val cdPlayAll = "Đồng loạt (All)"
    override val cdManagePro = "Quản lý Pro"
    override val cdUpgradePro = "Nâng cấp Pro"
    override val cdSettings = "Cài đặt hệ thống"
    override val cdRefresh = "Tải lại danh sách"
    override val cdAddDevice = "Thêm thiết bị"

    override val emptyControlTitle = "Chưa Chọn Thiết Bị ARGB HSL"
    override val emptyControlBody = "Vui lòng chọn bất kỳ thiết bị LED nào trong bảng điều khiển bên trái để tinh chỉnh cường độ, màu sắc và hiệu ứng ARGB nhanh chóng."

    override val addDeviceTitle = "Thêm Thiết Bị ARGB HSL"
    override val addDeviceNameLabel = "Tên thiết bị (ví dụ: LED Tivi)"
    override val addDeviceIpLabel = "Địa chỉ IP (ví dụ: 192.168.1.15)"
    override val addDeviceValidationError = "Vui lòng nhập đầy đủ Tên và địa chỉ IP hợp lệ!"
    override val addDeviceLink = "Liên kết"

    override val dlFoundAuto = "TÌM THẤY TỰ ĐỘNG"
    override val dlAddAll = "Thêm Tất Cả"
    override val dlQuickAddCd = "Thêm nhanh"
    override val dlActiveDevices = "THIẾT BỊ HOẠT ĐỘNG"
    override val dlAllOn = "Đang bật hết"
    override val dlAllOff = "Đang tắt hết"
    override val dlPartialOn = "Đang bật lẻ"
    override val dlToggleAllCd = "Bật tắt toàn bộ thiết bị"
    override val dlEmptyTitle = "Chưa kết nối thiết bị LED nào"
    override val dlEmptyBody = "Thêm thiết bị ARGB HSL mới bằng địa chỉ IP cục bộ của bạn để bắt đầu điều khiển."
    override val dlTapToLink = "Nhấn vào đây để liên kết"
    override val dlUnpairCd = "Hủy ghép nối"

    override val dcHeader0 = "CẤU HÌNH ĐƠN MẠCH"; override val dcSub0 = "Test màu & xem cấu hình từng thiết bị"
    override val dcHeader1 = "ĐỒNG LOẠT TOÀN BỘ (All)"; override val dcSub1 = "Điều khiển đồng thời tất cả các mạch"
    override val dcHeader2 = "UPLOAD POI & CỜ LED"; override val dcSub2 = "Tạo preset hình ảnh cho POI và ma trận cờ LED"
    override val dcHeader3 = "BIÊN TẬP TIMELINE"; override val dcSub3 = "Kéo thả preset vào track timeline của từng mạch"
    override val cdBackToList = "Quay lại danh sách"

    override val dcOfflineTitle = "Thiết Bị Đang Ngoại Tuyến"
    override val dcOfflineBody = "Vui lòng mở nguồn điện của LED ARGB, kiểm tra đường truyền WiFi nội bộ (phải chung dải mạng) và nhấn Tải Lại ở góc phải màn hình."

    override val dcMasterBrightness = "ĐỘ SÁNG TỔNG (Master Brightness)"
    override val dcPowerCd = "Nguồn LED"
    override val dcTurnOn = "MỞ"
    override val dcTurnOff = "TẮT"
    override val dcMainColor = "MÀU CHỦ ĐẠO"
    override val dcQuickColors = "Màu Nhanh (Presets)"
    override val colorRed = "Đỏ"; override val colorOrange = "Cam"; override val colorYellow = "Vàng"
    override val colorGreen = "Lục"; override val colorCyan = "Lam"; override val colorBlue = "Biển"
    override val colorPurple = "Tím"; override val colorWhite = "Trắng"; override val colorBlack = "Đen (Tắt)"
    override val dcColorWheel = "Bộ Trộn Màu (Bánh Xe)"
    override val dcHue = "Sắc độ (Hue)"
    override val dcSat = "Bão hòa (Sat)"
    override val dcValueBrightness = "Độ sáng màu (Value)"
    override val dcDeviceInfo = "THÔNG TIN THIẾT BỊ"
    override val dcProductName = "Tên dòng sản phẩm:"
    override val dcDefaultProduct = "Mạch ARGB HSL"
    override val dcBrand = "Thương hiệu:"
    override val dcFwVersion = "Phiên bản FW:"
    override val dcVid = "Mã định danh (VID):"
    override val dcIpAccess = "Địa chỉ IP truy cập:"
    override val dcClientName = "Tên tùy chỉnh client (CN):"
    override val dcLedCount = "Số lượng bóng LED:"
    override val dcPwr = "Dòng tiêu thụ hiện tại (pwr):"
    override val dcMaxPwr = "Dòng tối đa thiết lập (maxpwr):"
    override val dcFps = "Tốc độ khung hình (FPS):"
    override val dcPoiModeActive = "MODE POI ĐANG ĐƯỢC KÍCH HOẠT"

    override val proLockTitleAll = "Đồng Loạt là tính năng Pro"
    override val proLockTitleUpload = "Upload POI & Cờ LED là tính năng Pro"
    override val proLockTitleTimeline = "Biên Tập Timeline là tính năng Pro"
    override val proLockDescAll = "Mở khóa điều khiển toàn bộ mạch, timeline biên đạo theo nhạc, import timecode và dọn preset hàng loạt."
    override val proLockDescUpload = "Mở khóa công cụ tạo preset hình ảnh: upload ảnh thành hiệu ứng POI và cờ LED ma trận cho mọi mạch."
    override val proLockDescTimeline = "Mở khóa bảng biên tập timeline: kéo thả preset vào track từng mạch và biên dịch playlist đồng bộ."
    override val proUpgradeShort = "Nâng cấp"

    override val dcStageControl = "ĐIỀU KHIỂN ĐỒNG LOẠT (STAGE CONTROL)"
    override val dcParallelPower = "NGUỒN SÂN KHẤU SONG SONG"
    override val dcAllOnBtn = "MỞ TOÀN BỘ"
    override val dcAllOffBtn = "TẮT TOÀN BỘ"
    override val dcGroupBrightness = "TĂNG GIẢM SÁNG ĐỒNG LOẠT"
    override val dcGroupColor = "ĐỔI MÀU TOÀN BỘ THIẾT BỊ"
    override val sceneRed = "ĐỎ RỰC"; override val sceneOrange = "CAM CHÁY"; override val sceneYellow = "VÀNG NẮNG"
    override val sceneGreen = "XANH LÁ"; override val sceneCyan = "XANH NGỌC"; override val sceneBlue = "XANH LAM"
    override val scenePurple = "TÍM VIOLET"; override val sceneWhite = "TRẮNG TINH"; override val sceneBlack = "MÀU ĐEN (TẮT)"
    override val dcScenePalette = "BẢNG MÀU CHỈ UY SÂN KHẤU (SCENE PRESETS)"
    override val dcRunPlaylist = "KÍCH HOẠT PLAYLIST ĐỒNG LOẠT (RUN PLAYLIST)"
    override val dcMusicSync = "ĐỒNG BỘ ÁNH SÁNG THEO NHẠC NỀN"
    override val dcMusicLoaded = "ĐÃ ĐƯỢC NẠP NHẠC"
    override val dcMusicHelp = "Nhập nhạc của bạn từ thiết bị để tự động chạy khớp nhạc với kịch bản ánh sáng LED. Điểm cuối bài hát sẽ tự dừng nhạc và tắt toàn bộ LED."
    override val dcAudioSource = "Nguồn âm thanh hiện tại:"
    override val dcPickMusic = "CHỌN NHẠC"
    override val dcRemoveMusic = "XÓA NHẠC"
    override val dcRecentMusic = "Lịch sử nhạc đã nạp gần đây (Chọn nhanh):"
    override val dcMusicNote = "💡 Tiến trình nhạc nền được tích hợp trực tiếp, trùng khớp với thước thời gian Choreography bên dưới."
    override val dcStatusPlaying = "Trạng thái: Đang phát kịch bản trình diễn tổng"
    override val dcStatusPaused = "Trạng thái: Tạm dừng kịch bản trình diễn tổng"
    override val dcResume = "TIẾP TỤC"
    override val dcReplay = "CHẠY LẠI"
    override val dcStopPlay = "DỪNG PHÁT"
    override val dcRunChoreography = "CHẠY PLAYLIST TỔNG CHOREOGRAPHY"
    override val btRemoteTitle = "🎛️ Nút bấm Bluetooth"
    override val btReadyPrefix = "Đã sẵn sàng · phím:"
    override val btNoKey = "Chưa gán phím"
    override val btLearnNow = "Hãy bấm nút trên remote ngay bây giờ…"
    override val btHelp = "Ghép nối remote trong Cài đặt Bluetooth của Android trước, rồi bấm \"Học phím\" và nhấn nút trên remote.\n• 1 nhấn: Phát / Tạm dừng / Tiếp tục\n• Nhấn đúp: Dừng hẳn\n• Giữ lâu: Chạy lại từ đầu"
    override val btWaiting = "Đang chờ… (Huỷ)"
    override val btLearn = "Học phím"
    override val btTest = "Test kích hoạt"
    override val btClearKey = "Xoá phím đã gán"
    override val dcShowDuration = "⏱️ Thời gian kịch bản trình diễn (Tính toán tự động):"
    override val dcBuildingTimeline = "Đang dựng bảng timeline biên đạo..."
    override val dcTimelineTitle = "TIMELINE BIÊN ĐẠO"
    override val dcScrubHint = "Chạm/kéo trên thước để tua nhanh playhead"
    override val cdReloadShort = "Tải lại"
    override val dcLocked = "Khóa"
    override val dcUnlockScrub = "Mở tua"
    override val dcZoom = "Thu phóng:"
    override val dcNoOnlineDevices = "🔌 Không có thiết bị trực tuyến nào được tìm thấy. Vui lòng kết nối thiết bị để lập bản đồ biên đạo."
    override val dcScanningPresets = "Đang quét và đồng bộ tệp presets.json từ mạch..."
    override val dcBgMusic = "Nhạc nền"
    override val dcModeLockedRuler = "🔒 THƯỚC ĐÃ KHÓA"
    override val dcModeChoreography = "⚡ BIÊN ĐẠO CHỦ ĐỘNG"
    override val dcModeNative = "🔁 CHẠY NATIVE (PL 249)"
    override val dcLoadingScript = "Đang nạp kịch bản..."
    override val stRunning = "ĐANG DIỄN:"
    override val stPaused = "TẠM DỪNG:"
    override val stDone = "HOÀN TẤT:"
    override val stReady = "SẴN SÀNG: Thước kịch bản"
    override val dcModeChoreoShort = "⚡ B.ĐẠO CHỦ ĐỘNG"
    override val dcModeNativeShort = "🔁 NATIVE PL 249"
    override val dcElapsed = "Đã chạy:"
    override val dcTotal = "Tổng:"
    override val dcEffectTimeTitle = "⏱️ CẤN TRỪ THỜI GIAN HIỆU ỨNG THỰC TẾ (ĐỘ PHÂN GIẢI 1/10S):"
    override val deviceFallback = "Mạch"
    override val dcEffectPrefix = "Hiệu ứng:"
    override val dcSecondsLeft = "Giây còn lại"
    override val dcRanPrefix = "Đã chạy"
    override val dcAutoOffWarning = "⚠️ SÂN KHẤU TỰ ĐỘNG TẮT TOÀN BỘ LED KHI ĐẾN ĐÍCH!"
    override val dcAllLedOff = "TẮT HẾT LED"
    override val dcPause = "TẠM DỪNG"
    override val dcRunTimeline = "CHẠY TIMELINE"

    override val memFilesystem = "Bộ nhớ filesystem:"
    override val memStatus = "Tình trạng bộ nhớ:"
    override val memFree = "Dung lượng còn trống:"
    override val memNearFull = "BỘ NHỚ GẦN ĐẦY"
    override val memNearFullBody = "Filesystem đã dùng trên 85%. Hãy dọn bớt ảnh và logo (.bmp/.gif) không còn dùng để tránh đầy bộ nhớ, lỗi lưu preset."
    override val memFindCleanup = "Tìm & dọn ảnh BMP/GIF để xóa"
    override val memWifi = "WiFi thiết bị:"
    override val memLogoImages = "Logo/ảnh:"
    override val memSystemPresets = "Preset hệ thống:"
    override val memOtherPresets = "Preset khác:"
    override val presetUnit = "preset"

    override val upCreatePreset = "TẠO PRESET HÌNH ẢNH"
    override val upPoiLabel = "POI (thanh quay)"
    override val upMatrixLabel = "Cờ LED (ma trận)"
    override val upPickImages = "CHỌN ẢNH"
    override val upClearAll = "Xóa hết"
    override val upPickHint = "Chọn 1 hoặc nhiều ảnh (PNG/JPG/GIF). Mỗi ảnh = 1 preset."
    override val upSelectedPrefix = "Đã chọn"
    override val upImagesUnit = "ảnh"
    override val upRemoveImageCd = "Bỏ ảnh"
    override val upPoiPixelsLabel = "Pixel LEDs POI (15–145)"
    override val upPoiPixelsHint = "Đúng bằng số LED vật lý trên thanh POI. Khuyến nghị 15–72 để ảnh < 63KB."
    override val upWidthLabel = "Chiều rộng (cột)"
    override val upHeightLabel = "Chiều cao (hàng)"
    override val upMatrixHint = "Đúng bằng số cột × hàng LED của ma trận. Tối đa 256×256 (tự co nếu vượt). Hỗ trợ GIF động."
    override val upWriteAppend = "Ghi tiếp slot trống"
    override val upWriteOverwrite = "Ghi đè từ ID 1"
    override val upOverwriteWarning = "⚠ Sẽ xóa toàn bộ preset logo/ảnh (slot 1–59) và file ảnh cũ trước khi ghi lại từ ID 1."
    override val upTargetDevices = "MẠCH ĐÍCH"
    override val upNoOnline = "Không có mạch nào online."
    override val upFreeSlot = "trống"
    override val upUploading = "ĐANG UPLOAD…"
    override val upUploadPoi = "UPLOAD POI"
    override val upUploadMatrix = "UPLOAD CỜ LED"
    override val errShort = "Lỗi"
    override val skippedShort = "Bỏ"

    override val tlNoOnline = "Không có mạch nào online. Hãy bật thiết bị rồi quay lại."
    override val tlPickDevice = "CHỌN MẠCH ĐỂ LẤY PRESET"
    override val tlPresetsOf = "PRESET CỦA"
    override val tlHoldDrag = "(giữ & kéo xuống track)"
    override val tlNoPresets = "Chưa có preset (hoặc đang tải). Chỉ hiện preset ngoài slot hệ thống."
    override val tlLocked = "Khóa"
    override val tlEdit = "Sửa"
    override val tlSelectedPrefix = "Đang chọn:"
    override val tlTapToSelect = "Chạm vào 1 clip để chọn (rồi sửa thời lượng / xóa)."
    override val tlDeleteClip = "Xóa clip"
    override val tlDeleteTrack = "Xóa track"
    override val tlUploading = "ĐANG NẠP…"
    override val tlUploadPlaylist = "NẠP VÀO PLAYLIST 249"
    override val tlGoToAllTab = "SANG TAB ĐỒNG LOẠT (ALL) ĐỂ CHẠY"

    override val settingsTitle = "Cài Đặt Hệ Thống"
    override val sectionLanguage = "Ngôn ngữ"
    override val languageDemoNote = "Áp dụng cho các trang chính và 4 tab điều khiển. Chuỗi trạng thái/nghiệp vụ sâu sẽ chuyển dần."
    override val sectionGeneral = "Chung"
    override val appInfo = "Thông tin ứng dụng"
    override val appInfoSub = "Hướng dẫn, liên hệ & hỗ trợ"
    override val systemLogs = "Nhật ký hệ thống"
    override val systemLogsSub = "Hoạt động mạng & lịch sử lỗi"
    override val sectionAbout = "Về ứng dụng"
    override val version = "Phiên bản"

    override val infoTitle = "Thông Tin Ứng Dụng"
    override val infoVersionLabel = "Phiên bản:"
    override val infoManufacturer = "Nhà sản xuất:"
    override val infoWebsite = "Website:"
    override val infoZaloScan = "Kênh Zalo OA — quét mã để kết nối:"
    override val infoOpenZalo = "👉 Mở Zalo OA"
    override val infoConnectionSection = "THÔNG TIN KẾT NỐI"
    override val infoNetwork = "Mạng đang kết nối:"
    override val infoSignal = "Sóng/Tín hiệu:"
    override val infoDeviceIp = "IP thiết bị chạy app:"
    override val infoTagline = "Giải pháp chiếu sáng ARGB đồng bộ âm nhạc, điều khiển màu sắc mượt mà và không giới hạn hiệu ứng từ HSL."

    override val logsTitle = "Nhật Ký Hệ Thống"
    override val logsDescription = "Nhật ký lưu trữ các lệnh điều khiển kể từ khi mở app. Tự động dọn dẹp sau 3 ngày để tiết kiệm dung lượng thiết bị."
    override val logsEmpty = "Không có nhật ký nào."
    override val logsClear = "Dọn Sạch"

    override val proTitleActive = "ARGB HSL Pro đang hoạt động"
    override val proTitleUpgrade = "Nâng cấp ARGB HSL Pro"
    override val proSubtitle = "Gói theo năm cho điều khiển đồng loạt và biên đạo nhiều mạch."
    override val proYearlyPlan = "Pro hằng năm"
    override val proAutoRenewNote = "Gói Pro tự động gia hạn hằng năm qua Google Play. Bạn có thể hủy bất cứ lúc nào trong Play Store và tiếp tục dùng Pro đến hết kỳ đã thanh toán."
    override val proBenefit1 = "Điều khiển bật/tắt, màu, hiệu ứng và độ sáng cho tất cả mạch cùng lúc."
    override val proBenefit2 = "Import timecode, bake preset, playlist 249 và timeline đồng bộ theo nhạc."
    override val proBenefit3 = "Dọn preset hàng loạt để chuẩn bị show mới nhanh hơn."
    override val proRestore = "Khôi phục"
    override val proBuy = "Mua Pro"
    override val proAlready = "Đã Pro"
    override val proReloadBilling = "Tải lại Billing"
    override val proManageOnPlay = "Quản lý trên Play"
    override val proDebugOn = "Bật Pro debug"
    override val proDebugOff = "Tắt Pro debug"

    override val proHeroTagline = "Mở khóa toàn bộ sức mạnh biên đạo ánh sáng sân khấu"
    override val proBadgeActive = "ĐANG HOẠT ĐỘNG"
    override val proWhatYouGet = "QUYỀN LỢI GÓI PRO"
    override val proFeat1Title = "Điều khiển đồng loạt cả dàn LED"
    override val proFeat1Desc = "Bật/tắt, đổi màu, hiệu ứng và độ sáng cho tất cả mạch chỉ với 1 chạm — sân khấu luôn đều màu, không lệch nhịp."
    override val proFeat2Title = "Timeline biên đạo theo nhạc"
    override val proFeat2Desc = "Kéo thả preset vào track của từng mạch như phần mềm dựng phim, khớp ánh sáng với nhạc nền chính xác đến 1/10 giây."
    override val proFeat3Title = "Import Timecode chuyên nghiệp"
    override val proFeat3Desc = "Nạp file kịch bản show từ đội biên đạo, tự động bake preset và biên dịch playlist cho từng mạch."
    override val proFeat4Title = "Upload POI & Cờ LED ma trận"
    override val proFeat4Desc = "Biến ảnh/GIF thành hiệu ứng quay POI và cờ LED, đẩy đồng loạt xuống mọi mạch online trong một lần bấm."
    override val proFeat5Title = "Playlist 249 chạy native trên mạch"
    override val proFeat5Desc = "Show đã nạp chạy ngay trên phần cứng — không phụ thuộc điện thoại, mất WiFi giữa show vẫn diễn trọn vẹn."
    override val proFeat6Title = "Dọn preset hàng loạt"
    override val proFeat6Desc = "Quét và xóa preset cũ trên nhiều mạch cùng lúc, giải phóng bộ nhớ, sẵn sàng cho show mới trong vài phút."
}

val EnStrings: AppStrings = object : AppStrings {
    override val back = "Back"
    override val close = "Close"
    override val cancel = "Cancel"
    override val confirm = "Confirm"
    override val online = "Online"
    override val offline = "Offline"
    override val deviceUnit = "devices"
    override val secondsUnit = "seconds"
    override val stepsUnit = "steps"
    override val errorPrefix = "Error:"
    override val loadingRead = "Reading..."
    override val readError = "Read error"

    override val tabConfig = "Device Config"
    override val tabAll = "Group (All)"
    override val tabUpload = "Upload POI & LED Flag"
    override val tabTimeline = "Timeline Editor"

    override val cdPlayAll = "Group (All)"
    override val cdManagePro = "Manage Pro"
    override val cdUpgradePro = "Upgrade to Pro"
    override val cdSettings = "System settings"
    override val cdRefresh = "Refresh list"
    override val cdAddDevice = "Add device"

    override val emptyControlTitle = "No ARGB HSL Device Selected"
    override val emptyControlBody = "Select any LED device in the left panel to quickly adjust brightness, color and ARGB effects."

    override val addDeviceTitle = "Add ARGB HSL Device"
    override val addDeviceNameLabel = "Device name (e.g. TV LED)"
    override val addDeviceIpLabel = "IP address (e.g. 192.168.1.15)"
    override val addDeviceValidationError = "Please enter both a Name and a valid IP address!"
    override val addDeviceLink = "Link"

    override val dlFoundAuto = "AUTO-DISCOVERED"
    override val dlAddAll = "Add All"
    override val dlQuickAddCd = "Quick add"
    override val dlActiveDevices = "ACTIVE DEVICES"
    override val dlAllOn = "All on"
    override val dlAllOff = "All off"
    override val dlPartialOn = "Partially on"
    override val dlToggleAllCd = "Toggle all devices"
    override val dlEmptyTitle = "No LED device connected yet"
    override val dlEmptyBody = "Add a new ARGB HSL device using its local IP address to start controlling."
    override val dlTapToLink = "Tap here to link"
    override val dlUnpairCd = "Unpair"

    override val dcHeader0 = "SINGLE DEVICE CONFIG"; override val dcSub0 = "Test colors & view each device's configuration"
    override val dcHeader1 = "GROUP CONTROL (All)"; override val dcSub1 = "Control all devices simultaneously"
    override val dcHeader2 = "UPLOAD POI & LED FLAG"; override val dcSub2 = "Create image presets for POI and LED matrix flags"
    override val dcHeader3 = "TIMELINE EDITOR"; override val dcSub3 = "Drag & drop presets onto each device's timeline track"
    override val cdBackToList = "Back to list"

    override val dcOfflineTitle = "Device Is Offline"
    override val dcOfflineBody = "Please power on the ARGB LED, check your local WiFi (must be on the same subnet) and tap Refresh at the top right."

    override val dcMasterBrightness = "MASTER BRIGHTNESS"
    override val dcPowerCd = "LED power"
    override val dcTurnOn = "ON"
    override val dcTurnOff = "OFF"
    override val dcMainColor = "MAIN COLOR"
    override val dcQuickColors = "Quick Colors (Presets)"
    override val colorRed = "Red"; override val colorOrange = "Orange"; override val colorYellow = "Yellow"
    override val colorGreen = "Green"; override val colorCyan = "Cyan"; override val colorBlue = "Blue"
    override val colorPurple = "Purple"; override val colorWhite = "White"; override val colorBlack = "Black (Off)"
    override val dcColorWheel = "Color Mixer (Wheel)"
    override val dcHue = "Hue"
    override val dcSat = "Saturation"
    override val dcValueBrightness = "Color brightness (Value)"
    override val dcDeviceInfo = "DEVICE INFORMATION"
    override val dcProductName = "Product line:"
    override val dcDefaultProduct = "ARGB HSL Controller"
    override val dcBrand = "Brand:"
    override val dcFwVersion = "FW version:"
    override val dcVid = "Identifier (VID):"
    override val dcIpAccess = "Access IP address:"
    override val dcClientName = "Custom client name (CN):"
    override val dcLedCount = "LED count:"
    override val dcPwr = "Current draw (pwr):"
    override val dcMaxPwr = "Max current limit (maxpwr):"
    override val dcFps = "Frame rate (FPS):"
    override val dcPoiModeActive = "POI MODE IS ACTIVE"

    override val proLockTitleAll = "Group Control is a Pro feature"
    override val proLockTitleUpload = "Upload POI & LED Flag is a Pro feature"
    override val proLockTitleTimeline = "Timeline Editor is a Pro feature"
    override val proLockDescAll = "Unlock control of all devices, music-synced choreography timeline, timecode import and bulk preset cleanup."
    override val proLockDescUpload = "Unlock image preset tools: turn images into POI effects and LED matrix flags for every device."
    override val proLockDescTimeline = "Unlock the timeline editor: drag presets onto each device's track and compile a synchronized playlist."
    override val proUpgradeShort = "Upgrade"

    override val dcStageControl = "GROUP CONTROL (STAGE CONTROL)"
    override val dcParallelPower = "PARALLEL STAGE POWER"
    override val dcAllOnBtn = "ALL ON"
    override val dcAllOffBtn = "ALL OFF"
    override val dcGroupBrightness = "GROUP BRIGHTNESS"
    override val dcGroupColor = "SET COLOR ON ALL DEVICES"
    override val sceneRed = "BLAZING RED"; override val sceneOrange = "FIERY ORANGE"; override val sceneYellow = "SUNNY YELLOW"
    override val sceneGreen = "GREEN"; override val sceneCyan = "TURQUOISE"; override val sceneBlue = "BLUE"
    override val scenePurple = "VIOLET"; override val sceneWhite = "PURE WHITE"; override val sceneBlack = "BLACK (OFF)"
    override val dcScenePalette = "STAGE SCENE PALETTE (SCENE PRESETS)"
    override val dcRunPlaylist = "RUN GROUP PLAYLIST"
    override val dcMusicSync = "SYNC LIGHTS TO BACKGROUND MUSIC"
    override val dcMusicLoaded = "MUSIC LOADED"
    override val dcMusicHelp = "Import your music from this device to automatically run the LED light script in sync. At the end of the song, music stops and all LEDs turn off."
    override val dcAudioSource = "Current audio source:"
    override val dcPickMusic = "PICK MUSIC"
    override val dcRemoveMusic = "REMOVE MUSIC"
    override val dcRecentMusic = "Recently loaded music (quick pick):"
    override val dcMusicNote = "💡 Music progress is integrated directly and matches the Choreography time ruler below."
    override val dcStatusPlaying = "Status: Playing master show script"
    override val dcStatusPaused = "Status: Master show script paused"
    override val dcResume = "RESUME"
    override val dcReplay = "REPLAY"
    override val dcStopPlay = "STOP"
    override val dcRunChoreography = "RUN MASTER CHOREOGRAPHY PLAYLIST"
    override val btRemoteTitle = "🎛️ Bluetooth remote button"
    override val btReadyPrefix = "Ready · key:"
    override val btNoKey = "No key assigned"
    override val btLearnNow = "Press the remote button now…"
    override val btHelp = "Pair the remote in Android's Bluetooth settings first, then tap \"Learn key\" and press the remote button.\n• 1 press: Play / Pause / Resume\n• Double press: Stop\n• Long press: Restart from beginning"
    override val btWaiting = "Waiting… (Cancel)"
    override val btLearn = "Learn key"
    override val btTest = "Test trigger"
    override val btClearKey = "Clear assigned key"
    override val dcShowDuration = "⏱️ Show script duration (auto-calculated):"
    override val dcBuildingTimeline = "Building choreography timeline..."
    override val dcTimelineTitle = "CHOREOGRAPHY TIMELINE"
    override val dcScrubHint = "Tap/drag the ruler to scrub the playhead"
    override val cdReloadShort = "Reload"
    override val dcLocked = "Locked"
    override val dcUnlockScrub = "Scrub"
    override val dcZoom = "Zoom:"
    override val dcNoOnlineDevices = "🔌 No online devices found. Please connect devices to build the choreography map."
    override val dcScanningPresets = "Scanning and syncing presets.json from devices..."
    override val dcBgMusic = "Music"
    override val dcModeLockedRuler = "🔒 RULER LOCKED"
    override val dcModeChoreography = "⚡ ACTIVE CHOREOGRAPHY"
    override val dcModeNative = "🔁 NATIVE RUN (PL 249)"
    override val dcLoadingScript = "Loading script..."
    override val stRunning = "PLAYING:"
    override val stPaused = "PAUSED:"
    override val stDone = "FINISHED:"
    override val stReady = "READY: Script ruler"
    override val dcModeChoreoShort = "⚡ ACTIVE CHOREO"
    override val dcModeNativeShort = "🔁 NATIVE PL 249"
    override val dcElapsed = "Elapsed:"
    override val dcTotal = "Total:"
    override val dcEffectTimeTitle = "⏱️ LIVE EFFECT TIME COUNTDOWN (1/10S RESOLUTION):"
    override val deviceFallback = "Device"
    override val dcEffectPrefix = "Effect:"
    override val dcSecondsLeft = "seconds left"
    override val dcRanPrefix = "Elapsed"
    override val dcAutoOffWarning = "⚠️ STAGE AUTOMATICALLY TURNS OFF ALL LEDS AT THE END!"
    override val dcAllLedOff = "ALL LEDS OFF"
    override val dcPause = "PAUSE"
    override val dcRunTimeline = "RUN TIMELINE"

    override val memFilesystem = "Filesystem storage:"
    override val memStatus = "Storage status:"
    override val memFree = "Free space:"
    override val memNearFull = "STORAGE NEARLY FULL"
    override val memNearFullBody = "Filesystem is over 85% used. Clean up unused images and logos (.bmp/.gif) to avoid full storage and preset save errors."
    override val memFindCleanup = "Find & clean BMP/GIF images"
    override val memWifi = "Device WiFi:"
    override val memLogoImages = "Logos/images:"
    override val memSystemPresets = "System presets:"
    override val memOtherPresets = "Other presets:"
    override val presetUnit = "presets"

    override val upCreatePreset = "CREATE IMAGE PRESETS"
    override val upPoiLabel = "POI (spinning stick)"
    override val upMatrixLabel = "LED flag (matrix)"
    override val upPickImages = "PICK IMAGES"
    override val upClearAll = "Clear all"
    override val upPickHint = "Pick one or more images (PNG/JPG/GIF). Each image = 1 preset."
    override val upSelectedPrefix = "Selected"
    override val upImagesUnit = "images"
    override val upRemoveImageCd = "Remove image"
    override val upPoiPixelsLabel = "POI LED pixels (15–145)"
    override val upPoiPixelsHint = "Must equal the physical LED count on the POI stick. 15–72 recommended to keep image < 63KB."
    override val upWidthLabel = "Width (columns)"
    override val upHeightLabel = "Height (rows)"
    override val upMatrixHint = "Must equal the matrix's columns × rows. Max 256×256 (auto-shrinks if exceeded). Animated GIF supported."
    override val upWriteAppend = "Append to free slots"
    override val upWriteOverwrite = "Overwrite from ID 1"
    override val upOverwriteWarning = "⚠ All logo/image presets (slots 1–59) and old image files will be deleted before rewriting from ID 1."
    override val upTargetDevices = "TARGET DEVICES"
    override val upNoOnline = "No devices online."
    override val upFreeSlot = "free"
    override val upUploading = "UPLOADING…"
    override val upUploadPoi = "UPLOAD POI"
    override val upUploadMatrix = "UPLOAD LED FLAG"
    override val errShort = "Errors"
    override val skippedShort = "Skipped"

    override val tlNoOnline = "No devices online. Power on your devices and come back."
    override val tlPickDevice = "PICK A DEVICE TO LOAD PRESETS"
    override val tlPresetsOf = "PRESETS OF"
    override val tlHoldDrag = "(hold & drag onto a track)"
    override val tlNoPresets = "No presets yet (or still loading). Only presets outside system slots are shown."
    override val tlLocked = "Locked"
    override val tlEdit = "Edit"
    override val tlSelectedPrefix = "Selected:"
    override val tlTapToSelect = "Tap a clip to select it (then resize / delete)."
    override val tlDeleteClip = "Delete clip"
    override val tlDeleteTrack = "Delete track"
    override val tlUploading = "UPLOADING…"
    override val tlUploadPlaylist = "UPLOAD TO PLAYLIST 249"
    override val tlGoToAllTab = "GO TO GROUP (ALL) TAB TO RUN"

    override val settingsTitle = "System Settings"
    override val sectionLanguage = "Language"
    override val languageDemoNote = "Applies to main pages and all 4 control tabs. Deep status/business strings will follow."
    override val sectionGeneral = "General"
    override val appInfo = "App information"
    override val appInfoSub = "Guides, contact & support"
    override val systemLogs = "System logs"
    override val systemLogsSub = "Network activity & error history"
    override val sectionAbout = "About"
    override val version = "Version"

    override val infoTitle = "App Information"
    override val infoVersionLabel = "Version:"
    override val infoManufacturer = "Manufacturer:"
    override val infoWebsite = "Website:"
    override val infoZaloScan = "Zalo OA channel — scan to connect:"
    override val infoOpenZalo = "👉 Open Zalo OA"
    override val infoConnectionSection = "CONNECTION INFO"
    override val infoNetwork = "Connected network:"
    override val infoSignal = "Signal strength:"
    override val infoDeviceIp = "This device's IP:"
    override val infoTagline = "Music-synchronized ARGB lighting solution with smooth color control and unlimited effects from HSL."

    override val logsTitle = "System Logs"
    override val logsDescription = "Logs store control commands since the app was opened. Automatically cleaned up after 3 days to save device storage."
    override val logsEmpty = "No logs yet."
    override val logsClear = "Clear All"

    override val proTitleActive = "ARGB HSL Pro is active"
    override val proTitleUpgrade = "Upgrade to ARGB HSL Pro"
    override val proSubtitle = "Yearly plan for group control and multi-device choreography."
    override val proYearlyPlan = "Yearly Pro"
    override val proAutoRenewNote = "Pro renews yearly via Google Play. You can cancel anytime in the Play Store and keep Pro until the end of the paid period."
    override val proBenefit1 = "Control power, color, effects and brightness for all devices at once."
    override val proBenefit2 = "Import timecode, bake presets, playlist 249 and music-synced timeline."
    override val proBenefit3 = "Bulk-clean presets to prepare new shows faster."
    override val proRestore = "Restore"
    override val proBuy = "Buy Pro"
    override val proAlready = "Pro active"
    override val proReloadBilling = "Reload Billing"
    override val proManageOnPlay = "Manage on Play"
    override val proDebugOn = "Enable Pro debug"
    override val proDebugOff = "Disable Pro debug"

    override val proHeroTagline = "Unlock the full power of stage lighting choreography"
    override val proBadgeActive = "ACTIVE"
    override val proWhatYouGet = "WHAT YOU GET WITH PRO"
    override val proFeat1Title = "Group control for your whole LED rig"
    override val proFeat1Desc = "Power, color, effects and brightness for every device with one tap — the stage always stays in sync."
    override val proFeat2Title = "Music-synced choreography timeline"
    override val proFeat2Desc = "Drag presets onto each device's track like a video editor, matching lights to music down to 1/10 of a second."
    override val proFeat3Title = "Professional timecode import"
    override val proFeat3Desc = "Load show scripts from your choreography team — presets are baked and playlists compiled per device automatically."
    override val proFeat4Title = "POI & LED matrix flag upload"
    override val proFeat4Desc = "Turn images/GIFs into spinning POI effects and LED flags, pushed to every online device in one click."
    override val proFeat5Title = "Playlist 249 runs natively on-device"
    override val proFeat5Desc = "Uploaded shows run on the hardware itself — no phone required; even if WiFi drops mid-show, the show goes on."
    override val proFeat6Title = "Bulk preset cleanup"
    override val proFeat6Desc = "Scan and delete old presets across many devices at once, free up storage and be show-ready in minutes."
}

fun stringsFor(language: String): AppStrings = if (language == "en") EnStrings else ViStrings

val LocalAppStrings = staticCompositionLocalOf { ViStrings }
