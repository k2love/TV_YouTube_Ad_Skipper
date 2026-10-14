# 📖 TV YouTube Ad Skipper - Panduan Pengguna

`TV YouTube Ad Skipper` adalah utilitas latar belakang serbaguna untuk Android TV yang melewati iklan secara instan dan membisukan audio hardware di aplikasi resmi YouTube.

---

## 🎯 1. Mode Operasi Ganda

### 🌟 [Mode 1] Mode Protokol Lounge Resmi (Direkomendasikan: 100% Bebas Galat)
- **Cara Kerja**: Terhubung langsung melalui sesi kode TV resmi YouTube. Menerima sinyal server Google dan melewati iklan dalam 0,001 detik tanpa pemindaian visual layar.
- **Langkah Penautan**:
  1. Buka YouTube di TV Anda, masuk ke [Setelan] ➔ **[Tautkan dengan kode TV]**.
  2. Catat **kode 12 digit**.
  3. Buka TV Ad Skipper, masukkan 12 digit lalu klik **[Tautkan TV]**.
  4. Status hijau menandakan penautan sukses dan otomatis tersambung kembali saat TV dimulai ulang.

---

### 💤 [Baru] Arsitektur Tidur Pulas (Deep-Sleep) untuk Google TV Entry-Level
Menghilangkan micro-stutter dan patah-patah saat memutar video 4K 60fps HDR pada Smart TV hemat biaya (TCL, Hisense, dll.).

- **100% Tidur Saat Konten Utama**: Loop pemantauan berhenti total (`serviceInfo.eventTypes = 0`) untuk beban **0,0% CPU/GPU/IPC**.
- **Tanpa Tangkapan Layar Berkala**: Menghindari degradasi hardware video overlay (HWC).
- **Bangun Seketika Hanya Saat Iklan**: Mute cepat 0,001s, bantuan lewati dan langsung kembali ke tidur pulas.

---

### 🛒 [Baru] Deteksi Iklan Belanja QR Non-Piksel via YouTube Innertube API
- **Cara Kerja**: Kueri metadata latar belakang 1 kali di `/youtubei/v1/next` saat video dimulai tanpa tangkapan layar berulang.
- **Beban Nol**: 0 tangkapan layar pada 95%+ video umum dan penutupan presisi pada detik `+4,0` dan `+8,0` hanya pada video berproduk.

---

### 👁️ [Mode 2] Sensor Warna Piksel (Visi Komputer)
- **Cara Kerja**: Memantau timer lingkaran kuning dan mengeklik tombol putih tepat saat hitungan berakhir.
- **Anti-Salah Deteksi**: Nonaktif sepenuhnya saat memutar video biasa.

---

## 🛡️ 2. Setelan Utama

- **Bisukan Iklan Otomatis**: Membisukan iklan dan mengembalikan volume semula secara akurat.
- **Lewati Pop-up Shorts**: Menutup dialog konfirmasi dalam 0,1s.
- **Tutup Iklan Belanja QR**: Menutup banner belanja hanya pada video yang sesuai.
- **Pembaruan Nirkabel (OTA)**: Pengecekan otomatis versi baru dari GitHub saat mulai.
