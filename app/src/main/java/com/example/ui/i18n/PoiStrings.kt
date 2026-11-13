package com.example.ui.i18n

import androidx.compose.runtime.Composable

/** Chuỗi giao diện Công cụ POI (vi/en), chọn theo ngôn ngữ app ([LocalAppStrings]). */
interface PoiUiStrings {
    val devicesTitle: String
    val devicesSub: String
    val refresh: String
    val allOff: String
    val identify: String
    val selectAll: String
    val selectNone: String
    val noDevices: String
    val hiddenPrefix: String
    val hiddenSuffix: String
    val needFirmware: String
    val notSupported: String
    val currentBadge: String
    val imagesUnit: String
    val offline: String

    val previewTitle: String
    val previewSpin: String
    val previewBmp: String
    val previewEmpty: String
    val fromDeviceNote: String

    val imageTitle: String
    val openImage: String
    val gallery: String
    val quickPicks: String
    val noImage: String

    val paramsTitle: String
    val pixels: String
    val pixelsHint: String
    val brightness: String
    val brightnessHint: String
    val size: String
    val shape: String
    val shapeFlat: String
    val shapeDisc: String
    val shapeFlatHint: String
    val shapeDiscHint: String
    val shapeLocked: String
    val hole: String
    val holeHint: String
    val speed: String
    val auto: String
    val imagesPerTurn: String
    val imagesPerTurnHint: String
    val reverse: String
    val reverseHint: String
    val saveSpeed: String
    val liveSpeedNote: String

    val sendTitle: String
    fun sendThis(count: Int): String
    val sendMany: String
    val sendNoTargets: String
    val sendNeedImage: String

    val presetsTitle: String
    val filterLogo: String
    val filterAll: String
    val presetsEmpty: String
    val presetsHint: String
    val deletePresets: String
    val running: String

    val galleryTitle: String
    val searchHint: String
    val groupAll: String
    val groupFavorites: String
    val galleryEmpty: String
    val favoriteCd: String

    val sendManyTitle: String
    fun sendManyIntro(images: Int, devices: Int): String
    val writeAppend: String
    val writeAppendHint: String
    val writeOverwrite: String
    val writeOverwriteHint: String
    val cancel: String
    val send: String
    val close: String
}

val PoiVi: PoiUiStrings = object : PoiUiStrings {
    override val devicesTitle = "MẠCH POI"
    override val devicesSub = "Chỉ ARGB HSL Controller 2X Pro (firmware ≥ 2611050). Tick để chọn mạch nhận ảnh, chạm để xem preset."
    override val refresh = "Làm mới"
    override val allOff = "Tắt LED"
    override val identify = "Nháy"
    override val selectAll = "Chọn hết"
    override val selectNone = "Bỏ chọn"
    override val noDevices = "Chưa có mạch ARGB HSL Controller 2X Pro nào. Thêm/quét mạch ở danh sách bên trái."
    override val hiddenPrefix = "Ẩn"
    override val hiddenSuffix = "mạch không phải 2X Pro"
    override val needFirmware = "Cần nâng cấp firmware ≥ 2611050"
    override val notSupported = "POI chỉ hỗ trợ 2X Pro"
    override val currentBadge = "ĐANG XEM"
    override val imagesUnit = "ảnh"
    override val offline = "offline"

    override val previewTitle = "XEM TRƯỚC"
    override val previewSpin = "Vòng quay"
    override val previewBmp = "Ảnh BMP"
    override val previewEmpty = "Chọn ảnh hoặc mẫu trong Thư viện để xem trước hình khi POI quay."
    override val fromDeviceNote = "Ảnh lấy từ preset trên mạch"

    override val imageTitle = "① ẢNH NGUỒN"
    override val openImage = "Mở ảnh"
    override val gallery = "Thư viện mẫu"
    override val quickPicks = "Chọn nhanh"
    override val noImage = "Chưa chọn ảnh"

    override val paramsTitle = "② THÔNG SỐ POI"
    override val pixels = "Pixel LED"
    override val pixelsHint = "Số LED trên 1 cánh POI (15–145), đề xuất ≤ 72"
    override val brightness = "Độ sáng"
    override val brightnessHint = "Thả tay → áp dụng ngay cho mạch đang xem · 100% = mức an toàn cho pin POI"
    override val size = "Dung lượng"
    override val shape = "Kiểu hình"
    override val shapeFlat = "Uốn theo vòng"
    override val shapeDisc = "Đúng hình"
    override val shapeFlatHint = "Quét thẳng ảnh: ảnh uốn cong theo vòng, lặp nhiều lần mỗi vòng."
    override val shapeDiscHint = "Đổi ảnh sang toạ độ cực: 1 hình đúng dáng chiếm cả vòng tròn."
    override val shapeLocked = "Ảnh lấy từ mạch đã là BMP cuối cùng nên không đổi kiểu hình được."
    override val hole = "Tâm trống"
    override val holeHint = "LED đầu cách tâm quay bao nhiêu % bán kính. Đặt sai thì hình méo ở vùng giữa."
    override val speed = "Tốc độ quay"
    override val auto = "Auto"
    override val imagesPerTurn = "Số ảnh/vòng"
    override val imagesPerTurnHint = "Ảnh vuông: 3 lần/vòng là ít méo nhất."
    override val reverse = "Đảo chiều"
    override val reverseHint = "Bật khi quay ngược chiều mà hình/chữ bị ngược gương."
    override val saveSpeed = "Lưu tốc độ vào preset"
    override val liveSpeedNote = "Đang chạy preset trên mạch: chỉnh tốc độ là mạch đổi ngay, bấm Lưu mới ghi vào preset."

    override val sendTitle = "③ GỬI LÊN MẠCH"
    override fun sendThis(count: Int) = "Gửi ảnh này · $count mạch"
    override val sendMany = "Gửi nhiều ảnh…"
    override val sendNoTargets = "Tick ít nhất 1 mạch POI ở phía trên."
    override val sendNeedImage = "Chọn ảnh hoặc mẫu trước."

    override val presetsTitle = "PRESET TRÊN MẠCH"
    override val filterLogo = "Logo 1–59"
    override val filterAll = "Tất cả"
    override val presetsEmpty = "Chưa có preset ảnh nào trên mạch này."
    override val presetsHint = "Chạm 1 preset để chạy trên mạch, xem trước ảnh và chỉnh tốc độ quay."
    override val deletePresets = "Xóa preset + ảnh…"
    override val running = "ĐANG CHẠY"

    override val galleryTitle = "THƯ VIỆN MẪU POI"
    override val searchHint = "Tìm theo tên (gõ không dấu cũng được)"
    override val groupAll = "Tất cả"
    override val groupFavorites = "♥ Yêu thích"
    override val galleryEmpty = "Không có mẫu nào khớp."
    override val favoriteCd = "Yêu thích"

    override val sendManyTitle = "GỬI NHIỀU ẢNH LÊN POI"
    override fun sendManyIntro(images: Int, devices: Int) =
        "$images ảnh → $devices mạch đã tick. Mọi ảnh dùng cùng thông số POI đang đặt (kiểu hình, pixel, tốc độ, độ sáng)."
    override val writeAppend = "Ghi tiếp ID trống"
    override val writeAppendHint = "Giữ preset 1–59 đang có, ghi vào ID trống kế tiếp."
    override val writeOverwrite = "Ghi đè từ ID 1"
    override val writeOverwriteHint = "Ghi đè preset từ ID 1 trở đi (preset cũ ở các ID đó bị thay)."
    override val cancel = "Hủy"
    override val send = "Gửi"
    override val close = "Đóng"
}

val PoiEn: PoiUiStrings = object : PoiUiStrings {
    override val devicesTitle = "POI DEVICES"
    override val devicesSub = "ARGB HSL Controller 2X Pro only (firmware ≥ 2611050). Tick to receive images, tap to browse presets."
    override val refresh = "Refresh"
    override val allOff = "LEDs off"
    override val identify = "Blink"
    override val selectAll = "Select all"
    override val selectNone = "Clear"
    override val noDevices = "No ARGB HSL Controller 2X Pro yet. Add or scan devices in the list on the left."
    override val hiddenPrefix = "Hidden"
    override val hiddenSuffix = "non-2X Pro device(s)"
    override val needFirmware = "Firmware ≥ 2611050 required"
    override val notSupported = "POI supports 2X Pro only"
    override val currentBadge = "VIEWING"
    override val imagesUnit = "images"
    override val offline = "offline"

    override val previewTitle = "PREVIEW"
    override val previewSpin = "Spinning"
    override val previewBmp = "BMP image"
    override val previewEmpty = "Pick an image or a gallery sample to preview it spinning."
    override val fromDeviceNote = "Image taken from a device preset"

    override val imageTitle = "① SOURCE IMAGE"
    override val openImage = "Open image"
    override val gallery = "Sample gallery"
    override val quickPicks = "Quick picks"
    override val noImage = "No image selected"

    override val paramsTitle = "② POI SETTINGS"
    override val pixels = "LED pixels"
    override val pixelsHint = "LEDs on one POI blade (15–145), ≤ 72 recommended"
    override val brightness = "Brightness"
    override val brightnessHint = "Release → applied to the viewed device · 100% = battery-safe level"
    override val size = "Size"
    override val shape = "Shape"
    override val shapeFlat = "Wrap around"
    override val shapeDisc = "True shape"
    override val shapeFlatHint = "Scans the image straight: it bends around the circle and repeats each turn."
    override val shapeDiscHint = "Converts to polar coordinates: one undistorted picture fills the whole circle."
    override val shapeLocked = "Images taken from a device are final BMPs, so the shape can't change."
    override val hole = "Center gap"
    override val holeHint = "How far the first LED sits from the spin center, in % of the radius."
    override val speed = "Spin speed"
    override val auto = "Auto"
    override val imagesPerTurn = "Images/turn"
    override val imagesPerTurnHint = "Square images: 3 per turn distort the least."
    override val reverse = "Reverse"
    override val reverseHint = "Turn on when spinning the other way mirrors the picture/text."
    override val saveSpeed = "Save speed to preset"
    override val liveSpeedNote = "A preset is running: speed changes apply live; press Save to store them."

    override val sendTitle = "③ SEND TO DEVICES"
    override fun sendThis(count: Int) = "Send this image · $count device(s)"
    override val sendMany = "Send many images…"
    override val sendNoTargets = "Tick at least one POI device above."
    override val sendNeedImage = "Pick an image or a sample first."

    override val presetsTitle = "PRESETS ON DEVICE"
    override val filterLogo = "Logo 1–59"
    override val filterAll = "All"
    override val presetsEmpty = "No image presets on this device yet."
    override val presetsHint = "Tap a preset to run it, preview its image and tune the spin speed."
    override val deletePresets = "Delete presets + images…"
    override val running = "RUNNING"

    override val galleryTitle = "POI SAMPLE GALLERY"
    override val searchHint = "Search by name"
    override val groupAll = "All"
    override val groupFavorites = "♥ Favorites"
    override val galleryEmpty = "No matching samples."
    override val favoriteCd = "Favorite"

    override val sendManyTitle = "SEND MANY IMAGES TO POI"
    override fun sendManyIntro(images: Int, devices: Int) =
        "$images image(s) → $devices ticked device(s). All use the current POI settings (shape, pixels, speed, brightness)."
    override val writeAppend = "Append to free IDs"
    override val writeAppendHint = "Keep existing presets 1–59, write to the next free IDs."
    override val writeOverwrite = "Overwrite from ID 1"
    override val writeOverwriteHint = "Overwrite presets starting at ID 1 (old presets at those IDs are replaced)."
    override val cancel = "Cancel"
    override val send = "Send"
    override val close = "Close"
}

@Composable
fun poiStrings(): PoiUiStrings = if (LocalAppStrings.current === EnStrings) PoiEn else PoiVi
