# 📥 TV YouTube Ad Skipper - Panduan Instalasi

Panduan langkah demi langkah untuk menginstal `TV YouTube Ad Skipper` di **Android TV / Google TV**.

---

## 🚀 1. Unduh APK
- **Tautan Langsung**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. Metode Instalasi

### Metode A: Transfer Nirkabel dari HP (Disarankan ⭐⭐⭐)
1. Pasang **'Send Files to TV' (SFTTV)** di TV dan HP dari Play Store.
2. Pasang pengelola berkas di TV (**'File Commander'**).
3. Unduh APK di HP dan kirim ke TV melalui SFTTV.
4. Buka folder `Download` di TV dan pasang APK.

### Metode B: Melalui Flashdisk USB
Salin APK ke flashdisk, colokkan ke TV, dan pasang via pengelola berkas.

### Metode C: ADB Nirkabel dari PC
```powershell
adb connect <IP_TV>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Setelan Penting: Membuka Setelan Terbatas di Google TV
Di Google TV 12/13/14+, sistem dapat menampilkan peringatan **"Setelan terbatas"**:
1. Buka [Setelan] ➔ **[Aplikasi]** ➔ **[Lihat semua aplikasi]**.
2. Pilih **[TV Ad Skipper]**.
3. Gulir ke bawah dan klik **[Izinkan setelan terbatas]**.
4. Aktifkan layanan di [Setelan] ➔ [Sistem] ➔ [Aksesibilitas].
