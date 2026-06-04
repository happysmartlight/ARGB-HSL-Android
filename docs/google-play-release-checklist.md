# Google Play Release Checklist

Ngay trong app đã thêm Play Billing cho gói Pro hằng năm. Các mục dưới đây là phần cần làm trước khi đẩy lên Google Play Console.

## Cấu hình sản phẩm trả phí

- Vào Play Console > Monetize with Play > Products > Subscriptions.
- Tạo subscription ID: `argb_hsl_pro`.
- Tạo base plan ID: `annual_auto`.
- Loại base plan: auto-renewing.
- Chu kỳ: yearly.
- Giá chính: `79.00 USD/year`, sau đó kiểm tra giá quy đổi theo từng quốc gia.
- Activate subscription và base plan trước khi test purchase trong app.
- Thêm license testers ở Play Console > Setup > License testing để test bằng thẻ test của Google Play.

Nguồn Google: subscription gồm `subscription`, `base plan`, `offer`, và base plan có thể là annual auto-renewing. Base plan ID không đổi được sau khi activate.

## Release build

- Tăng `versionCode` và `versionName` trước mỗi bản gửi review.
- Build Android App Bundle:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:bundleRelease
```

- Ký release bằng upload key riêng, không dùng debug key.
- App hiện target SDK 36, đạt yêu cầu Google Play hiện tại là target Android 15/API 35 trở lên cho app mới/cập nhật.
- Giữ `usesCleartextTraffic="true"` vì app điều khiển WLED qua HTTP nội bộ. Trong mô tả review nên nói rõ app chỉ gọi thiết bị LED trong LAN.

## Store listing

- Tên app: ARGB HSL.
- Mô tả ngắn: điều khiển WLED/ARGB HSL qua mạng nội bộ, hỗ trợ từng mạch và đồng loạt.
- Ảnh chụp màn hình ưu tiên tablet/màn lớn vì đây là thiết bị mục tiêu của app.
- Icon/app screenshots không dùng hình LED/WLED bên thứ ba nếu chưa có quyền.
- Thêm thông tin subscription rõ ràng: `ARGB HSL Pro - $79/year`, tự gia hạn qua Google Play, người dùng hủy trong Google Play Subscription Center.

## Policy và khai báo bắt buộc

- Privacy Policy URL: cần một trang web công khai, nêu rõ app lưu cấu hình thiết bị/IP cục bộ, gọi WLED trong LAN, và không bán dữ liệu.
- Data Safety: khai báo đúng việc app xử lý địa chỉ IP thiết bị, file âm thanh/timecode người dùng chọn, log hệ thống nội bộ nếu có.
- App access: nếu reviewer cần test tính năng Pro, thêm hướng dẫn dùng license tester hoặc bật quyền test trong Play Console.
- Content rating: hoàn thành questionnaire trong Play Console.
- Target audience: chọn đúng nhóm tuổi; app là công cụ điều khiển LED, không hướng đến trẻ em.
- Financial features: vì dùng subscription, đảm bảo màn paywall hiển thị giá/năm và nút quản lý subscription.

Nguồn Google: chính sách subscription yêu cầu không gây hiểu lầm về dịch vụ subscription và nên có đường dẫn quản lý subscription khi dùng Google Play Billing.

## Testing track

- Upload bản đầu tiên lên Internal testing để kiểm tra install, Billing, restore và quyền LAN.
- Nếu tài khoản Google Play Developer là personal account tạo sau 2023-11-13, cần Closed testing với ít nhất 12 tester đã opt-in liên tục 14 ngày trước khi xin production access.
- Test checklist tối thiểu:
  - mở app trên tablet/màn lớn;
  - thêm thiết bị WLED thật;
  - bật/tắt từng mạch;
  - thử tab Pro khi chưa mua;
  - mua bằng license tester;
  - restore purchase;
  - mở tab Đồng Loạt và chạy pause/resume;
  - import timecode và upload playlist 249;
  - gỡ/cài lại app rồi restore Pro.

## Rủi ro cần xử lý trước public launch

- Entitlement Pro hiện được kiểm tra bằng Play Billing client trong app. Cách này đủ để test/internal rollout, nhưng bản public nên thêm backend verify purchase token bằng Google Play Developer API và Real-time Developer Notifications để chống giả mạo tốt hơn.
- Nếu Play Console chưa activate `argb_hsl_pro` + `annual_auto`, app sẽ hiển thị lỗi chưa tìm thấy subscription và nút mua bị khóa.
- Nếu app cần chạy trên mạng không có Google Play Store, Billing sẽ báo unavailable; tính năng điều khiển từng mạch vẫn dùng được.

## Tài liệu Google đã đối chiếu

- Google Play Billing Library release notes: https://developer.android.com/google/play/billing/release-notes
- Integrate Google Play Billing: https://developer.android.com/google/play/billing/integrate.html
- Create and manage subscriptions: https://support.google.com/googleplay/android-developer/answer/140504
- Subscription policy: https://support.google.com/googleplay/android-developer/answer/9900533
- Target API level requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Testing requirements for new personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465
