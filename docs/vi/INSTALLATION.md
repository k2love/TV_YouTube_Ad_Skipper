# 📥 TV YouTube Ad Skipper - Hướng dẫn cài đặt

Hướng dẫn cài đặt `TV YouTube Ad Skipper` trên **Android TV / Google TV**.

---

## 🚀 1. Tải xuống APK
- **Tải trực tiếp**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. Các phương pháp cài đặt

### Phương pháp A: Chuyển không dây từ điện thoại (Khuyên dùng ⭐⭐⭐)
1. Cài đặt **'Send Files to TV' (SFTTV)** trên TV và điện thoại từ Play Store.
2. Cài đặt ứng dụng quản lý tệp trên TV (**'File Commander'**).
3. Tải APK về điện thoại và gửi sang TV qua SFTTV.
4. Mở thư mục `Download` trên TV và tiến hành cài đặt.

### Phương pháp B: Dùng USB
Sao chép APK vào USB, cắm vào TV và cài đặt qua trình quản lý tệp.

### Phương pháp C: ADB không dây từ máy tính
```powershell
adb connect <IP_CỦA_TV>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Cài đặt quan trọng: Mở khóa cài đặt bị hạn chế trên Google TV
Trên Google TV 12/13/14+, hệ thống có thể báo **"Cài đặt bị hạn chế"**:
1. Vào [Cài đặt] ➔ **[Ứng dụng]** ➔ **[Xem tất cả ứng dụng]**.
2. Chọn **[TV Ad Skipper]**.
3. Cuộn xuống dưới cùng và nhấp vào **[Cho phép cài đặt bị hạn chế]**.
4. Sau đó bật dịch vụ tại [Cài đặt] ➔ [Hệ thống] ➔ [Trợ năng].
