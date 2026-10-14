# 📺 TV YouTube Ad Skipper (Android TV YouTube Ad Auto Skipper & Mute)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Korean%20(Default)%20%2B%2012%20Global-orange.svg)](#-language-priority)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **An Android TV utility that automatically skips ads in the official YouTube TV app within 0.001 seconds when the skip countdown ends, and completely auto-mutes the hardware audio during non-skippable ads.**

[**🇰🇷 한국어 (기본)**](README.md) | [**🇺🇸 English**](README_EN.md) | [**🇯🇵 日本語**](README_JA.md) | [**🇪🇸 Español**](README_ES.md) | [**🇮🇳 हिन्दी**](README_HI.md) | [**🇨🇳 简体中文**](README_ZH.md) | [**🇷🇺 Русский**](README_RU.md) | [**🇵🇹 Português**](README_PT.md) | [**🇫🇷 Français**](README_FR.md) | [**🇩🇪 Deutsch**](README_DE.md) | [**🇮🇩 Bahasa Indonesia**](README_ID.md) | [**🇸🇦 العربية**](README_AR.md) | [**🇻🇳 Tiếng Việt**](README_VI.md)

---

## 🚀 Quick Start (3-Step Installation)

1. **Download APK**: [Direct Download TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Transfer to TV**: Send via **'Send Files to TV' (SFTTV)** app from your smartphone or transfer via a USB drive.
3. **Enable Accessibility**: Go to TV [Settings] ➔ [Accessibility] ➔ Enable **[TV YouTube Ad Skip Service]**!
   - *(If permissions are restricted on Google TV 12+: See [Google TV Restricted Settings Guide](docs/INSTALLATION.md#%EF%B8%8F-3-필수-설정-google-tv-권한-잠금-해제))*

👉 **[Detailed Installation & Setup Guide (docs/INSTALLATION.md)](docs/INSTALLATION.md)**

---

## 🌟 Key Features

### 1. 0.001s Instant Ad Skip & 0ms Audio Auto-Mute
- Clicks the "Skip Ad" button instantly within 0.001s as soon as it appears.
- Auto-mutes TV audio stream during non-skippable ads and restores your exact previous volume when main content starts.

### 2. 💤 Zero-Overhead Deep-Sleep Architecture for Low-End Google TVs (0% 4K Stutter)
- Completely halts background sentinel polling loops when main content is playing (`isAdPlaying == false`), dynamically setting `serviceInfo.eventTypes = 0` to block accessibility event generation by Android OS and YouTube (Cobalt), achieving **0.0% CPU/GPU/IPC overhead**.
- Permanently eliminates periodic screenshot capturing (`takeScreenshot`) during main video playback, preventing hardware video overlay (HWC) plane breakdown and completely resolving 4K 60fps HDR micro-stutter on budget TVs (TCL, Hisense, etc.).

### 3. 🛒 Non-Pixel YouTube Innertube API QR Shopping Ad Detection
- Replaces continuous screen captures with a single-shot background query to YouTube's Innertube API (`/youtubei/v1/next`) on video start to check for shopping metadata (`shoppingOverlay`, `merchandiseShelf`, etc.).
- Keeps **0 screen captures** and stays 100% dormant on 95%+ regular videos, only pinpoint-closing QR cards at `+4.0s` and `+8.0s` on the <5% of videos that actually contain tagged products.

### 4. 🎯 Official Lounge Protocol Mode (Recommended: 100% Error-Free)
- Bypasses screen-scanning by linking directly to YouTube\'s official **'Link with TV code'** session.
- Directly receives ad state change signals (`isSkipEnabled=true`) from Google servers to skip ads with zero error.

### 5. 🌐 13 Global Languages & Multi-Regional Ad Detection
- Supports full multilingual ad badges and skip buttons across 13 major languages (Korean default, English, Japanese, Chinese, Spanish, Portuguese, French, German, Russian, Indonesian, Hindi, Arabic, Vietnamese).

### 6. 🚀 Wireless Over-The-Air (OTA) Updates
- Automatically detects new releases from GitHub on app startup and allows 1-click in-app updating.

👉 **[Detailed User Manual & Mode Instructions (docs/USER_MANUAL.md)](docs/USER_MANUAL.md)**

---

## 📜 Latest Updates (v0.8.1)

- **Zero-Overhead Deep-Sleep Architecture**: Completely disables accessibility events, polling, and screenshots during main video playback to eliminate 4K 60fps frame drops on budget Google TVs.
- **Removed High-Overhead Accessibility Flags**: Permanently removed `flagRetrieveInteractiveWindows` and `typeWindowContentChanged` from `accessibility_service_config.xml`, relaxed notification timeout to 500ms.
- **Non-Pixel YouTube Innertube QR Shopping Detection**: Zero screenshot overhead on 95%+ of videos via single-shot `/youtubei/v1/next` metadata pre-checking.
- **Preserved Multilingual Coverage**: Full 13-language ad badge and skip button detection retained.
- **Local Diagnostics Only**: Google Drive remote log upload removed; clean local runtime logging preserved.

👉 **[Full Version Changelog (CHANGELOG.md)](CHANGELOG.md)**

---

## 🌐 Language Priority

This project maintains **Korean as the primary and system default fallback**, followed by localized resources in order:

| Language | Documentation (README) | Installation Guide | User Manual |
| :--- | :---: | :---: | :---: |
| 1. 🇰🇷 **Korean (ko)** *(Default)* | [README.md](README.md) | [설치 가이드](docs/INSTALLATION.md) | [사용설명서](docs/USER_MANUAL.md) |
| 2. 🇺🇸 **English (en)** | [README_EN.md](README_EN.md) | [Installation Guide](docs/en/INSTALLATION.md) | [User Manual](docs/en/USER_MANUAL.md) |
| 3. 🇯🇵 **Japanese (ja)** | [README_JA.md](README_JA.md) | [インストールガイド](docs/ja/INSTALLATION.md) | [ユーザーマニュアル](docs/ja/USER_MANUAL.md) |
| 4. 🇪🇸 **Spanish (es)** | [README_ES.md](README_ES.md) | [Guía de instalación](docs/es/INSTALLATION.md) | [Manual de usuario](docs/es/USER_MANUAL.md) |
| 5. 🇮🇳 **Hindi (hi)** | [README_HI.md](README_HI.md) | [इंस्टॉलेशन गाइड](docs/hi/INSTALLATION.md) | [उपयोगकर्ता मैनुअल](docs/hi/USER_MANUAL.md) |
| 6. 🇨🇳 **Chinese (zh)** | [README_ZH.md](README_ZH.md) | [安装指南](docs/zh/INSTALLATION.md) | [用户手册](docs/zh/USER_MANUAL.md) |
| 7. 🇷🇺 **Russian (ru)** | [README_RU.md](README_RU.md) | [Инструкция](docs/ru/INSTALLATION.md) | [Руководство](docs/ru/USER_MANUAL.md) |
| 8. 🇵🇹 **Portuguese (pt)** | [README_PT.md](README_PT.md) | [Guia de instalação](docs/pt/INSTALLATION.md) | [Manual do usuário](docs/pt/USER_MANUAL.md) |
| 9. 🇫🇷 **French (fr)** | [README_FR.md](README_FR.md) | [Guide d\'installation](docs/fr/INSTALLATION.md) | [Manuel d\'utilisation](docs/fr/USER_MANUAL.md) |
| 10. 🇩🇪 **German (de)** | [README_DE.md](README_DE.md) | [Installationsanleitung](docs/de/INSTALLATION.md) | [Benutzerhandbuch](docs/de/USER_MANUAL.md) |
| 11. 🇮🇩 **Indonesian (id)** | [README_ID.md](README_ID.md) | [Panduan Instalasi](docs/id/INSTALLATION.md) | [Panduan Pengguna](docs/id/USER_MANUAL.md) |
| 12. 🇸🇦 **Arabic (ar)** | [README_AR.md](README_AR.md) | [دليل التثبيت](docs/ar/INSTALLATION.md) | [دليل المستخدم](docs/ar/USER_MANUAL.md) |
| 13. 🇻🇳 **Vietnamese (vi)** | [README_VI.md](README_VI.md) | [Hướng dẫn cài đặt](docs/vi/INSTALLATION.md) | [Hướng dẫn sử dụng](docs/vi/USER_MANUAL.md) |

---

## 📄 License

This project is licensed under the MIT License.
