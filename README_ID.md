# 📺 TV YouTube Ad Skipper (Lewati & Bisukan Iklan YouTube Otomatis di Android TV)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/id/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Bahasa%20Indonesia-orange.svg)](#-prioritas-bahasa)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **Utilitas Android TV yang melewati iklan di aplikasi resmi YouTube TV secara otomatis dalam 0,001 detik saat timer berakhir, dan membisukan suara selama iklan wajib tayang.**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**Bahasa Indonesia**]

---

## 🚀 Panduan Cepat (3 Langkah Mudah)

1. **Unduh APK**: [Unduh langsung TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Kirim ke TV**: Kirim dari smartphone melalui aplikasi **'Send Files to TV' (SFTTV)** atau flashdisk USB.
3. **Aktifkan Aksesibilitas**: Di TV: [Setelan] ➔ [Aksesibilitas] ➔ Aktifkan **[Layanan Lewati Iklan TV YouTube]**!
   - *(Di Google TV 12+: Lihat [Panduan Membuka Setelan Terbatas](docs/id/INSTALLATION.md#%EF%B8%8F-3-setelan-penting-membuka-setelan-terbatas-di-google-tv))*.

👉 **[Panduan Instalasi Lengkap (docs/id/INSTALLATION.md)](docs/id/INSTALLATION.md)**

---

## 🌟 Fitur Utama

1. **Lewati Iklan 0,001s & Bisukan Otomatis**: Klik instan saat tombol «Lewati Iklan» muncul dan bisukan audio iklan wajib secara otomatis.
2. **💤 Arsitektur Tidur Pulas (Deep-Sleep) untuk Google TV Entry-Level (0% Patah-patah pada 4K)**: Menghentikan loop pengawas latar belakang sepenuhnya saat video utama diputar (`serviceInfo.eventTypes = 0`) untuk beban **0,0% CPU/GPU/IPC**. Penghentian permanen tangkapan layar berkala menghapus stutter pada video 4K 60fps HDR di TV TCL, Hisense, dll.
3. **🛒 Deteksi Iklan Belanja QR Non-Piksel via YouTube Innertube API**: Menggantikan tangkapan layar terus-menerus dengan 1 kali kueri metadata latar belakang di `/youtubei/v1/next` saat video dimulai. Menjaga 0 tangkapan layar pada 95%+ video umum dan hanya menutup kartu QR pada video berproduk (+4d dan +8d).
4. **🎯 Mode Protokol Lounge Resmi (100% Bebas Galat)**: Terima sinyal server Google via kode TV tanpa pemindaian layar.
5. **🌐 Mendukung 13 Bahasa Global**: Deteksi akurat lencana iklan dan tombol lewati dalam 13 bahasa dunia.
6. **🚀 Pembaruan Nirkabel (OTA)**: Deteksi otomatis versi baru dari GitHub saat mulai dan perbarui dalam 1 klik.

👉 **[Panduan Pengguna Lengkap (docs/id/USER_MANUAL.md)](docs/id/USER_MANUAL.md)**

---

## 📜 Pembaruan Versi Terbaru (v0.8.1)

- **Arsitektur Deep-Sleep**: Mengatasi frame drop 4K 60fps pada Smart TV murah.
- **Pembersihan Konfigurasi Aksesibilitas**: Menghapus flag berat (`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) dan melonggarkan batas waktu ke 500ms.
- **Deteksi QR Belanja Innertube Tanpa Piksel**: Kueri metadata tanpa beban grafis.
- **Cakupan 13 Bahasa Utuh**: Pola regex multibahasa dipertahankan sepenuhnya.
