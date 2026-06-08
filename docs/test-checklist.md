# Checklist kiểm thử (test sau trên thiết bị thật)

> Các tính năng mới đã code & biên dịch sạch nhưng **chưa test trên tablet/mạch thật**.
> Tick từng mục khi đã kiểm.

## A. Tab "Upload POI & Cờ LED"

- [ ] **POI**: chọn ảnh, nhập 72 pixel → Upload → trên thiết bị có file `.bmp`, preset slot 1.. dùng hiệu ứng `Poi HSL`, phát thử hiển thị đúng.
- [ ] POI ảnh quá cao → báo lỗi **vượt 63KB** (không upload).
- [ ] **Cờ LED tĩnh**: ảnh PNG, W×H ví dụ 32×16 → file `.gif`, preset `fx=53`, slot persist.
- [ ] **Cờ LED động**: chọn GIF nhiều khung → chạy đúng animation trên ma trận (chất lượng màu quantize 256 màu chấp nhận được?).
- [ ] **Đa thiết bị + chế độ ghi**: 2+ mạch online → "Ghi tiếp slot trống" không đè preset cũ; "Ghi đè từ ID 1" ghi lại từ slot 1.
- [ ] Thiết bị khoá **PIN 401** → báo lỗi rõ ràng, không crash.

## B. Tab "Biên Tập Timeline"

### Preset list + preview
- [ ] Chọn mạch online → hiện đúng preset (trừ slot hệ thống 100/248/249/250) với tên đúng.
- [ ] **Chạm nhanh** một preset → thiết bị **phát thử preset đó** ngay (demo).
- [ ] **Giữ lâu rồi kéo** preset xuống track của mạch đang chọn → tạo clip tại đúng mốc thời gian thả.
- [ ] Ngưỡng phân biệt **tap (preview)** vs **giữ-kéo** có dễ canh không (tap lâu có bị thành kéo?).

### Track / clip
- [ ] **Hiệu năng kéo**: kéo di chuyển & co giãn nhiều clip **liên tục** đều mượt ở **mọi lần** (không chỉ lần đầu).
- [ ] Clip **không chồng đè** lên nhau khi thả/di chuyển/co giãn.
- [ ] **Chạm clip để chọn** → viền sáng, hiện handle co giãn to ở mép phải, nút **"Xóa clip"** (ngoài timeline) được bật.
- [ ] **Handle co giãn** dễ chạm/kéo; với clip rất ngắn (zoom nhỏ) handle 24dp có che hết clip không?
- [ ] **Snap thông minh**: khi kéo/giãn, mép clip hút vào cạnh clip lân cận + mốc giây tròn; cảm giác "dính" tự nhiên (ngưỡng ~11dp cần tăng/giảm?).
- [ ] Chạm vùng trống của lane → bỏ chọn clip.
- [ ] "Xóa track" xoá hết clip của mạch đang chọn.
- [ ] Khoá thước (lock) → không sửa/di chuyển/xoá được; tap preview vẫn hoạt động.

### Lưu & chạy
- [ ] Nút **"NẠP VÀO PLAYLIST 249"** chỉ **nạp** (upload presets.json), **không tự chạy**.
- [ ] Sau khi nạp: presets.json mỗi mạch có slot 249 với `ps/dur/transition` đúng (gap → 248 OFF).
- [ ] Nút **"SANG TAB ĐỒNG LOẠT (ALL) ĐỂ CHẠY"** → chuyển sang tab 1 **và cuộn xuống đúng bảng timeline**; bấm Chạy phát đồng bộ.
- [ ] **Lưu cục bộ (Room)**: thoát tab / đổi mạch / **khởi động lại app** → clip vẫn còn.
- [ ] **Nâng cấp DB không mất thiết bị**: cài đè bản cũ (đang có thiết bị) → danh sách thiết bị còn nguyên (migration 3→4).

## C. Điểm thiết kế chờ xác nhận
- [ ] Clip mặc định **5 giây** khi mới thả — ổn không?
- [ ] Nạp 249 đang đặt **lặp vô hạn (loop)** — cần thêm tuỳ chọn chạy 1 lần?
- [ ] **Transition mặc định = 0** — cần thêm chỉnh transition cho từng clip?
