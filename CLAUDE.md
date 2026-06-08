# Project: ARGB HSL — WLED LED Controller (Android)

Ứng dụng Android (Jetpack Compose) điều khiển các mạch LED ARGB HSL chạy firmware
WLED qua HTTP. Hỗ trợ điều khiển từng thiết bị, điều khiển đồng loạt, và biên đạo
timeline đồng bộ nhiều thiết bị theo nhạc nền.

## ⚠️ Thiết bị mục tiêu: MÀN HÌNH LỚN

**App được thiết kế và tối ưu để dùng trên thiết bị MÀN HÌNH LỚN (máy tính bảng /
điện thoại màn to).** Khi phát triển, đánh giá UI, hay quyết định bố cục, **ưu tiên
trải nghiệm trên màn lớn**. Màn nhỏ (~5") chỉ cần *chạy được không vỡ*, không phải
là mục tiêu chính.

- Có sẵn hệ thống responsive (xem bên dưới) để màn nhỏ không vỡ, nhưng **không hy
  sinh bố cục/độ rõ trên màn lớn để chiều màn nhỏ**.
- Mốc `WindowSizeClass`: `Compact` < 360dp · `Medium` 360–719dp · `Expanded` ≥ 720dp
  (tablet — đây là trọng tâm).

## Hệ thống responsive (dùng cái này, đừng hardcode dp/sp)

- `app/src/main/java/com/example/ui/theme/Dimens.kt` — `AppDimens` + `LocalAppDimens`
  (padding, spacing, icon size, button height, fontScale… theo kích thước màn).
  Lấy ở composable: `val dimens = LocalAppDimens.current`.
- `app/src/main/java/com/example/ui/theme/Type.kt` — `appTypography(scale)` co giãn
  mọi cỡ chữ; đã wire trong `Theme.kt` nên `MaterialTheme.typography.*` tự co theo màn.
- Khi thêm UI: dùng `dimens.*`, `Modifier.weight()`, `fillMaxWidth()`; tránh `width()`
  cố định và `fontSize = X.sp` cứng (sẽ không tự co).

## Build

- Android Studio: Build → Build APK(s). APK debug nằm ở
  `app/build/outputs/apk/debug/app-debug.apk`.
- CLI (Windows, cần JDK của Android Studio):
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug`
- Chỉ kiểm tra biên dịch nhanh: `:app:compileDebugKotlin`.

## Các file chính

- `app/src/main/java/com/example/ui/WledManagerApp.kt` — toàn bộ UI Compose (màn danh
  sách thiết bị, màn điều khiển với **4 tab**: Cấu Hình Mạch / Đồng Loạt ALL /
  Upload POI & Cờ LED / Biên Tập Timeline).
- `app/src/main/java/com/example/viewmodel/WledViewModel.kt` — state + logic mạng (WLED
  HTTP API, import timecode, biên dịch playlist 249, upload presets.json, upload ảnh→preset,
  biên tập timeline kéo thả).
- `app/src/main/java/com/example/data/` — Room DB, repository, WLED API/discovery,
  `TimelineClipEntity` (clip timeline), `MockDevice` (thiết bị ảo debug).
- `app/src/main/java/com/example/util/` — xử lý ảnh: `PoiImage` (BMP 24-bit), `MatrixImage`
  (GIF), `gif/` (encoder/decoder GIF nhúng, Java thuần).
- `app/src/main/java/com/example/ui/theme/` — màu, typography, dimens responsive.

## Tab "Upload POI & Cờ LED" (tab 3 — index 2)

Biến ảnh → preset hình ảnh, upload đồng loạt mọi mạch online. Spec đầy đủ:
`docs/poi-preset-workflow.md`.
- **POI**: ảnh → xoay 90° + resize theo số pixel (15–145) → **BMP 24-bit** → hiệu ứng
  `Poi HSL`. Chặn nếu ≥ 63KB.
- **Cờ LED (Matrix)**: ảnh tĩnh/động → resize W×H (≤256×256) → **GIF** (`fx=53`), có
  **WAIT-PERSIST** (poll `/presets.json`) chống mất preset.
- Slot 1–59 (`logoPresetRange`); 2 chế độ ghi: ghi đè từ ID 1 / ghi tiếp slot trống.
- Encoder BMP/GIF tự cài trong `util/` (Android không có sẵn) — **không thêm dependency**.

## Tab "Biên Tập Timeline" (tab 4 — index 3)

Biên đạo timeline ngay trong app bằng **kéo thả** (không cần import file timecode).
- Trên: list preset của 1 mạch (touch nhanh = phát thử preview; giữ lâu = kéo xuống track).
- Dưới: bảng track kiểu DAW, mỗi mạch online = 1 lane. Chạm clip để **chọn** → viền sáng +
  handle co giãn + nút "Xóa clip" (ngoài timeline). Kéo thân = di chuyển, kéo mép = co giãn,
  có **snap** vào cạnh clip lân cận + mốc giây tròn. Chống chồng lấn ở ViewModel.
- Hiệu năng kéo: đọc state ở layout/placement phase (`offset{}` / `Modifier.layout{}`),
  commit về VM/Room 1 lần lúc thả. State clip **ổn định theo `clip.id`** + resync bằng
  `LaunchedEffect` (đừng key `remember` theo `clip.startSec`, sẽ lệch state gây lag).
- Lưu cục bộ: Room `timeline_clips` (`TimelineClipEntity` + DAO). DB version 4, có
  **migration 3→4** (chỉ thêm bảng, KHÔNG xoá thiết bị cũ).
- Nút **"Nạp vào playlist 249"** chỉ upload (không chạy); nút **"Sang tab Đồng Loạt"**
  chuyển `setSelectedTab(1)` + cuộn tới bảng timeline để chạy.

## Thiết bị ảo (MOCK) — CHỈ DEBUG, không có ở release

`app/src/main/java/com/example/data/MockDevice.kt`. Để thử nghiệm UI khi không có mạch
thật (vd chạy emulator).
- Cổng an toàn: `MockDevice.isMock(ip) = BuildConfig.DEBUG && ip == "mock.local"`. Ở release
  luôn `false` → không seed, không IP nào khớp → canned-data là code chết.
- Debug build tự **seed 1 thiết bị ảo** (`ensureMockDeviceSeeded` ở init VM) + bật Pro debug
  một lần để mở các tab.
- Các call mạng (ping, `/json`, presets.json, upload/psave/delete, preview, timeline) đều
  short-circuit khi `isMock` → trả canned-data / no-op success.

## Chạy thử trên máy ảo (emulator)

- SDK: `%LOCALAPPDATA%\Android\Sdk`. Ảo hoá WHPX dùng được. JAVA_HOME =
  `C:\Program Files\Android\Android Studio\jbr` (cần cho `sdkmanager`/`avdmanager`/gradle).
- AVD tablet đã tạo: **`argb_tablet`** (Pixel Tablet, `system-images;android-34;google_apis;x86_64`).
- Boot: `emulator -avd argb_tablet`; cài: `adb install -r <apk>`; mở:
  `adb shell monkey -p com.happysmartlight.argb -c android.intent.category.LAUNCHER 1`.
- `applicationId = com.happysmartlight.argb`, activity `com.example.MainActivity`.

## Checklist test trên thiết bị thật

`docs/test-checklist.md` — gom các điểm cần kiểm khi có mạch WLED thật (POI/Cờ LED,
timeline editor, migration DB…).

## Tính năng Import Timecode (tab Đồng Loạt ALL)

Spec định dạng file: `timecode-json-android-plugin.md`. Luồng: chọn file → phát hiện
thiết bị Mock → dialog gán Mock→thiết bị thật → bake snapshot vào slot trống (60–240,
né slot reserved) → biên dịch playlist vào slot 249 → upload presets.json mỗi thiết bị.

## Lưu ý khác

- Thước timeline mặc định **khóa** (tránh lỡ tay tua); người dùng mở khóa bằng switch.
- Nhạc nền mặc định: chỉ tải khi thiếu file (`downloadDefaultAudio` có `onlyIf`).
