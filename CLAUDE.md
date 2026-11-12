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
  (repo không có `local.properties` → đặt thêm `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`).
- Chỉ kiểm tra biên dịch nhanh: `:app:compileDebugKotlin`. Unit test (Robolectric): `:app:testDebugUnitTest`.

## Các file chính

- `app/src/main/java/com/example/ui/WledManagerApp.kt` — toàn bộ UI Compose (màn danh
  sách thiết bị, màn điều khiển với **4 tab**: Cấu Hình Mạch / Đồng Loạt ALL /
  Upload POI & Cờ LED / Biên Tập Timeline).
- `app/src/main/java/com/example/viewmodel/WledViewModel.kt` — state + logic mạng (WLED
  HTTP API, import timecode, biên dịch playlist 249, upload presets.json, upload ảnh→preset,
  biên tập timeline kéo thả).
- `app/src/main/java/com/example/data/` — Room DB, repository, WLED API/discovery,
  `TimelineClipEntity` (clip timeline), `MockDevice` (thiết bị ảo debug),
  `TimecodeRecipe.kt` (logic thuần, có unit test: dải slot/`pmax`, JSON gọn, so preset,
  recipe, MAC, lập kế hoạch bake + biên dịch playlist 249).
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
- Lưu cục bộ: Room `timeline_clips` (`TimelineClipEntity` + DAO). DB version **5**:
  **migration 3→4** (thêm bảng) + **4→5** (thêm cột `mac`, `pmax` cho `wled_devices`) —
  KHÔNG xoá thiết bị cũ (`AppDatabaseMigrationTest`).
- Nút **"Nạp vào playlist 249"** chỉ upload (không chạy); nút **"Sang tab Đồng Loạt"**
  chuyển `setSelectedTab(1)` + cuộn tới bảng timeline để chạy. Từ chối ghi nếu clip trỏ
  preset không còn trên mạch.

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
- Emulator tới được mạch thật trong LAN (thêm thiết bị bằng IP; mDNS không chạy trong emulator).
  Git Bash: đặt `MSYS_NO_PATHCONV=1` cho lệnh `adb ... /sdcard/...`; có máy thật cắm USB thì dùng
  `adb -s emulator-5554`.
- `applicationId = com.happysmartlight.argb`, activity `com.example.MainActivity`.

## Checklist test trên thiết bị thật

`docs/test-checklist.md` — gom các điểm cần kiểm khi có mạch WLED thật (POI/Cờ LED,
timeline editor, migration DB…).

## Dòng mạch & ID preset

- Sản phẩm hợp lệ (`WledDevice.VALID_PRODUCTS`, đồng bộ desktop `scan_worker.py`): ARGB HSL
  Controller, 2X, **2X Pro**, 4X, 8X. ⚠ `pingDevice` **xoá** thiết bị có product lạ → thêm dòng
  mạch mới phải sửa danh sách này.
- `WledDevice.mac` (12 hex thường, `normMac`) + `pmax` lưu mỗi lần ping từ `/json/info`.
- Firmware ≥ 2611090 (2X Pro / 4X): preset ID tới `fs.pmax` (1024). Thiếu `pmax` = 250, KHÔNG suy
  từ `vid`. **Không bao giờ** ghi/xoá/tham chiếu 251–255; không `pdel` 250. Nguồn sự thật:
  `PresetSlots` (logo 1–59, timecode 60–240 rồi 256..pmax, hệ thống 100/248/249/250).

## Tính năng Import Timecode (tab Đồng Loạt ALL)

Spec: `docs/timecode-json-android-plugin.md` + **`docs/android-timecode-recipe-task.md`** (file
desktop v3.17+: `clips[].recipe`, `recipe_files`, `tracks[].mac`, `audio_rel`, `bake_snapshot`
trên track thật). Luồng (`WledViewModel.prepareTimecodeImport` / `confirmTimecodeMapping`):

1. Giải mã → mọi track có clip (mock **và** mạch thật). Track thật tự gán theo **MAC** (mạch đổi
   IP vẫn nhận), rồi IP; IP cũ trả MAC khác thì không dùng; 1 mạch không nhận 2 track.
2. Mỗi mạch (song song): `/json/info` (mac, pmax) → `presets.json` → `TimecodePlanner.plan`:
   OK / trỏ lại slot có cùng nội dung / ghi lại (mất preset, khác mạch, `bake_snapshot`) /
   dùng preset có sẵn (file cũ không recipe) / lỗi. So nội dung bỏ qua `n` cấp ngoài, KHÔNG bỏ
   `seg[].n`. Có clip lỗi (thiếu preset, thiếu ảnh — kiểm `/edit?list`) → **không ghi gì**.
3. Upload ảnh → sao lưu presets.json cũ (`files/presets_backup/<mac>/`, giữ 10) → ghi slot
   trống → 248/249 → kiểm mọi ID trong `ps` tồn tại → upload **dạng gọn** (`PresetJson.presetsJsonText`)
   → **đọc lại** xác nhận (3 lần × 0.4 s). JSON gửi UTF-8 thật (`charset=utf-8`).
4. Lưu file **đã mã hoá** + phân công vào `files/timecode_last_import.json` để ghi lại sau này.

- Trước **Phát**: mỗi mạch online chưa kiểm trong lần kết nối này (`verifiedIps`, xoá khi offline /
  đổi MAC / bấm Refresh) → kiểm playlist 249; thiếu preset → ghi lại từ lần nạp gần nhất; chỉ
  thiếu 248 → tự ghi; còn lại → toast cảnh báo.
- Phát đồng loạt: `postStateTogether` dựng request xong mới mở "cổng" gửi cùng lúc (đo trên 2
  mạch thật: lệch < 1 ms). Body `{"on":true,"seg":[{"id":0,"frz":false}],"ps":249}`.
- Nhạc: Android không đọc được thư mục chứa file timeline → tự chọn nếu tên file trùng lịch sử
  nhạc, không thì dialog kết quả có nút "Chọn nhạc".

## Lưu ý khác

- Thước timeline mặc định **khóa** (tránh lỡ tay tua); người dùng mở khóa bằng switch.
- Nhạc nền mặc định: chỉ tải khi thiếu file (`downloadDefaultAudio` có `onlyIf`).
