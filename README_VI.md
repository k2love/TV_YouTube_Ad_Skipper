# 📺 TV YouTube Ad Skipper (Tự động bỏ qua & tắt tiếng quảng cáo YouTube trên Android TV)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/vi/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Tiếng%20Việt-orange.svg)](#-thứ-tự-ưu-tiên-ngôn-ngữ)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **Tiện ích dành riêng cho Android TV giúp tự động bỏ qua quảng cáo trong ứng dụng YouTube TV chính thức trong vòng 0,001 giây khi đồng hồ đếm ngược kết thúc và tự động tắt tiếng phần cứng trong các quảng cáo bắt buộc.**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**Tiếng Việt**]

---

## 🚀 Bắt đầu nhanh (3 bước đơn giản)

1. **Tải xuống APK**: [Tải trực tiếp TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Chuyển sang TV**: Gửi từ điện thoại qua ứng dụng **'Send Files to TV' (SFTTV)** hoặc dùng USB.
3. **Bật dịch vụ trợ năng**: Trên TV: [Cài đặt] ➔ [Trợ năng] ➔ Bật **[Dịch vụ tự động bỏ qua quảng cáo YouTube TV]**!
   - *(Trên Google TV 12+: Xem [Hướng dẫn mở khóa cài đặt bị hạn chế](docs/vi/INSTALLATION.md#%EF%B8%8F-3-cài-đặt-quan-trọng-mở-khóa-cài-đặt-bị-hạn-chế-trên-google-tv))*.

👉 **[Hướng dẫn cài đặt chi tiết (docs/vi/INSTALLATION.md)](docs/vi/INSTALLATION.md)**

---

## 🌟 Tính năng chính

1. **Bỏ qua 0,001s & Tự động tắt tiếng**: Nhấp ngay khi nút «Bỏ qua quảng cáo» xuất hiện và tự động tắt tiếng trong các quảng cáo bắt buộc.
2. **💤 Kiến trúc ngủ sâu (Deep-Sleep) cho Google TV cấu hình thấp (0% giật hình ở 4K)**: Dừng hoàn toàn vòng lặp giám sát nền khi đang phát video chính (`serviceInfo.eventTypes = 0`) giúp giảm tải **0,0% CPU/GPU/IPC**. Loại bỏ vĩnh viễn việc chụp màn hình định kỳ giúp xử lý triệt để hiện tượng giật khung hình 4K 60fps HDR trên TV TCL, Hisense, v.v.
3. **🛒 Phát hiện quảng cáo mua sắm QR không dùng điểm ảnh qua YouTube Innertube API**: Thay vì chụp màn hình liên tục, ứng dụng truy vấn siêu dữ liệu nền 1 lần tại `/youtubei/v1/next` khi bắt đầu video. Duy trì 0 lần chụp màn hình trên 95%+ video thông thường và chỉ đóng thẻ QR chính xác trên các video có gắn sản phẩm (+4s và +8s).
4. **🎯 Giao thức Lounge chính thức (100% không lỗi)**: Kết nối qua mã TV nhận tín hiệu trực tiếp từ Google.
5. **🌐 Hỗ trợ 13 ngôn ngữ toàn cầu**: Nhận diện huy hiệu quảng cáo và nút bỏ qua trên 13 ngôn ngữ phổ biến.
6. **🚀 Tự động cập nhật không dây (OTA)**: Tự động kiểm tra bản phát hành mới trên GitHub khi khởi động và nâng cấp trong 1 cú nhấp.

👉 **[Hướng dẫn sử dụng chi tiết (docs/vi/USER_MANUAL.md)](docs/vi/USER_MANUAL.md)**

---

## 📜 Cập nhật phiên bản mới nhất (v0.8.1)

- **Kiến trúc Deep-Sleep**: Khắc phục hiện tượng rớt khung hình 4K 60fps trên Smart TV giá rẻ.
- **Tối ưu hóa cấu hình trợ năng**: Xóa bỏ các cờ nặng (`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) và nới lỏng thời gian chờ lên 500ms.
- **Nhận diện QR Innertube không chụp màn hình**: Kiểm tra siêu dữ liệu không tốn tài nguyên đồ họa.
- **Bảo toàn đầy đủ 13 ngôn ngữ**: Giữ nguyên toàn bộ mẫu biểu thức chính quy đa ngôn ngữ.
