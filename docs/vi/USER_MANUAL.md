# 📖 TV YouTube Ad Skipper - Hướng dẫn sử dụng

`TV YouTube Ad Skipper` là tiện ích nền toàn diện cho Android TV giúp tự động bỏ qua quảng cáo YouTube và tắt tiếng âm thanh phần cứng ngay lập tức.

---

## 🎯 1. Hai chế độ hoạt động chính

### 🌟 [Chế độ 1] Giao thức Lounge chính thức (Khuyên dùng: 100% không lỗi)
- **Nguyên lý**: Kết nối trực tiếp qua phiên mã TV chính thức của YouTube. Nhận tín hiệu từ máy chủ Google và bỏ qua quảng cáo trong 0,001 giây mà không cần quét hình ảnh màn hình.
- **Các bước liên kết**:
  1. Mở YouTube trên TV, vào [Cài đặt] ➔ **[Liên kết bằng mã TV]**.
  2. Ghi lại **mã gồm 12 chữ số**.
  3. Mở TV Ad Skipper, nhập 12 chữ số rồi nhấn **[Liên kết TV]**.
  4. Trạng thái chuyển sang màu xanh lá xác nhận hoàn tất và sẽ tự động kết nối lại khi TV khởi động lại.

---

### 💤 [Mới] Kiến trúc ngủ sâu (Deep-Sleep) cho Google TV cấu hình thấp
Giải quyết triệt để hiện tượng giật khung hình và rớt khung hình khi phát video 4K 60fps HDR trên Smart TV giá rẻ (TCL, Hisense, v.v.).

- **Ngủ 100% khi phát video chính**: Vòng lặp giám sát dừng hoàn toàn (`serviceInfo.eventTypes = 0`) giúp giảm tải **0,0% CPU/GPU/IPC**.
- **Không chụp màn hình định kỳ**: Bảo vệ lớp phủ video phần cứng (HWC) không bị hạ cấp.
- **Chỉ thức dậy ngay lập tức khi có quảng cáo**: Tắt tiếng trong 0,001s, hỗ trợ bỏ qua và lập tức trở lại trạng thái ngủ sâu.

---

### 🛒 [Mới] Phát hiện quảng cáo mua sắm QR không dùng điểm ảnh qua YouTube Innertube API
- **Nguyên lý**: Truy vấn siêu dữ liệu nền 1 lần tại `/youtubei/v1/next` khi bắt đầu video mà không cần chụp màn hình lặp lại.
- **Không tốn tài nguyên**: 0 lần chụp màn hình trên 95%+ video thông thường và chỉ đóng chính xác ở giây `+4,0` và `+8,0` trên các video có sản phẩm.

---

### 👁️ [Chế độ 2] Cảm biến màu điểm ảnh (Thị giác máy tính)
- **Nguyên lý**: Giám sát vòng tròn đếm ngược màu vàng và nhấp chính xác vào nút bỏ qua màu trắng.
- **Chống nhận diện sai**: Hoàn toàn không hoạt động khi phát video thông thường.

---

## 🛡️ 2. Các cài đặt chính

- **Tự động tắt tiếng quảng cáo**: Tắt tiếng quảng cáo bắt buộc và khôi phục âm lượng ban đầu chính xác.
- **Tự động bỏ qua pop-up Shorts**: Đóng hộp thoại xác nhận trong 0,1s.
- **Đóng quảng cáo mua sắm QR**: Chỉ đóng biểu ngữ trên các video mua sắm phù hợp.
- **Tự động cập nhật không dây (OTA)**: Tự động kiểm tra bản phát hành mới trên GitHub khi khởi động.
