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

## D. Mạch 2X Pro + file timeline desktop v3.17 (recipe / MAC / pmax 1024)

Đã kiểm 2026-11-12 trên 2 mạch thật (2X Pro `192.168.0.21`, 4X `192.168.0.130`, firmware
2611103, `pmax` 1024) qua emulator `argb_tablet`, file sinh bằng `timecode_crypto` của desktop:

- [x] Thêm mạch **2X Pro** bằng IP → nhận, không bị xoá khi ping. Thẻ thông tin hiện MAC + "ID preset 1–250, 256–1024".
- [x] Thống kê Timecode theo `pmax`: 949 slot (60–240 + 256–1024).
- [x] Dialog gán: track thật tự gán theo MAC; track 4X ghi sai IP trong file → nhận ra "đổi IP → .130"; track mock chờ chọn.
- [x] Clip có recipe trùng preset sẵn có → dùng lại slot, preset gốc không bị đổi.
- [x] Clip có `preset_id` trỏ slot đang chứa nội dung KHÁC → ghi slot mới, slot cũ không bị phát.
- [x] `bake_snapshot` trên track thật được bake; ảnh trong `recipe_files` được tải lên.
- [x] presets.json sau khi ghi: dạng gọn, `"0":{}` đầu, tên tiếng Việt lưu đúng chữ (không thành mã escape).
- [x] Xoá 1 preset trên mạch → Refresh → Phát: app tự ghi lại từ file timecode rồi mới phát.
- [x] Phát 2 mạch: cùng chạy `pl=249`, lệch thời điểm gửi 0.72 ms (log app).
- [ ] Mạch **firmware cũ** (không có `fs.pmax`) → không bao giờ dùng ID > 250.
- [ ] Mạch thiếu ảnh mà file không kèm ảnh → báo lỗi clip, không ghi gì.
- [ ] Nhạc: file nhạc trùng tên trong lịch sử → tự chọn; không có → nút "Chọn nhạc".
- [ ] Tab Biên Tập: "Nạp vào playlist 249" trên mạch thật (đọc lại xác nhận, chặn preset đã mất).
- [ ] Cài đè bản cũ trên máy thật đang có thiết bị → danh sách thiết bị còn nguyên (migration 4→5; đã có unit test).
- [ ] Máy thật (không phải emulator): mDNS tìm thấy 2X Pro.

## E. Nút "Xóa preset + ảnh" (tab Đồng Loạt ALL + Cấu Hình Mạch)

Đã kiểm 2026-11-13 trên tablet Xiaomi + 2 mạch thật (preset thử 60–63, ảnh dùng chung):

- [x] Mỗi tab chỉ còn 1 nút; dialog gộp preset theo ID, nhóm + chip chọn nhanh, mục ảnh thừa.
- [x] Dialog không vượt màn tablet nằm ngang (nút Xóa luôn hiện, danh sách tự cuộn).
- [x] Xoá 1 preset có ảnh dùng chung với preset khác → ảnh được giữ; ảnh dùng riêng → xoá.
- [x] Tab ALL xoá song song trên các mạch, báo kết quả từng mạch; preset/ảnh không chọn còn nguyên.
- [ ] Tích "Ảnh thừa" → xoá ảnh không preset nào dùng (đã có unit test, chưa thử trên mạch thật).
- [ ] Mạch khoá PIN `/edit` → vẫn xoá preset, giữ ảnh, có cảnh báo.

## F. Công cụ POI mới (chỉ 2X Pro, kiểu hình, tốc độ quay, thư viện mẫu)

Đã kiểm 2026-11-13 trên tablet Xiaomi + 2X Pro thật (FW 2611103, PSRAM) + 4X:

- [x] Danh sách POI chỉ có 2X Pro; dòng "Ẩn 1 mạch không phải 2X Pro: ARGB-4X". Cờ LED vẫn có 4X.
- [x] Số liệu mạch: FW, WiFi, bộ nhớ, nhiệt độ, số ảnh, BMP ≤ 512 KB (PSRAM).
- [x] Chọn mẫu thư viện (306 mẫu, tìm không dấu "rong" → Rồng…), hàng Chọn nhanh đưa mẫu vừa dùng lên đầu.
- [x] Xem trước quay ~30 FPS; Uốn theo vòng 3 ảnh/vòng; dung lượng 72×72 = 15.2 KB khớp desktop.
- [x] Chạm preset 1 → mạch chạy, tải BMP về xem trước, hiện 226 RPM · 4 ảnh/vòng.
- [x] Chỉnh RPM khi đang chạy → mạch đổi `ix` ngay, preset không đổi khi chưa bấm Lưu.
- [x] Gửi ảnh → ID trống kế tiếp, `fx 142` + `ix/c3/o3` + `start/stop` đúng; Lưu tốc độ ghi `ix` mới vào preset.
- [ ] Kiểu Đúng hình trên POI thật đang quay: hình đúng dáng, chỉnh "Tâm trống" cho khớp.
- [ ] Gửi nhiều ảnh (ghi tiếp / ghi đè từ ID 1) tới 2 cây POI cùng lúc.
- [ ] 2X Pro firmware cũ (< 2611050) → hiện xám "Cần nâng cấp firmware", không tick được.
- [ ] Nút Nháy: mạch nháy trắng 3 lần rồi trở lại preset đang chạy.
- [ ] Màn nhỏ (điện thoại): các phần xếp 1 cột, không vỡ.

