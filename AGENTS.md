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
  sách thiết bị, màn điều khiển với 3 tab: Cấu Hình Mạch / Đồng Loạt ALL / …, timeline biên đạo).
- `app/src/main/java/com/example/viewmodel/WledViewModel.kt` — state + logic mạng (WLED
  HTTP API, import timecode, biên dịch playlist 249, upload presets.json).
- `app/src/main/java/com/example/data/` — Room DB, repository, WLED API/discovery.
- `app/src/main/java/com/example/ui/theme/` — màu, typography, dimens responsive.

## Tính năng Import Timecode (tab Đồng Loạt ALL)

Spec định dạng file: `timecode-json-android-plugin.md`. Luồng: chọn file → phát hiện
thiết bị Mock → dialog gán Mock→thiết bị thật → bake snapshot vào slot trống (60–240,
né slot reserved) → biên dịch playlist vào slot 249 → upload presets.json mỗi thiết bị.

## Lưu ý khác

- Thước timeline mặc định **khóa** (tránh lỡ tay tua); người dùng mở khóa bằng switch.
- Nhạc nền mặc định: chỉ tải khi thiếu file (`downloadDefaultAudio` có `onlyIf`).
