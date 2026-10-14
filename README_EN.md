# 📺 TV YouTube Ad Skipper (Android TV YouTube Ad Auto Skipper & Mute)

[![Version](https://img.shields.io/badge/version-v0.8.0-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Korean%20(Default)%20%2B%2012%20Global-orange.svg)](#-language-priority)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **An Android TV utility that automatically skips ads in the official YouTube TV app within 0.001 seconds when the skip countdown ends, and completely auto-mutes the hardware audio during non-skippable ads.**

[**한국어 (Korean Default)**](README.md) | [**English (README_EN.md)**]

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

### 2. 🎯 Official Lounge Protocol Mode (Recommended: 100% Error-Free)
- Bypasses screen-scanning by linking directly to YouTube\'s official **'Link with TV code'** session.
- Directly receives ad state change signals (`isSkipEnabled=true`) from Google servers to skip ads with zero error.

### 3. 👁️ Computer Vision Pixel Color Sensor (Shorts-Safe Visual Mode)
- Even without Lounge pairing, monitors the bottom-right yellow circular countdown and clicks the white skip button pinpoint-accurately.
- Smart visual filtering prevents false positives caused by yellow subtitles or bright video backgrounds.

### 4. 🛡️ Auto-Dismiss Shorts Popups & QR Shopping Ads
- Automatically dismisses Shorts casting disconnect prompts within 0.1s.
- Auto-closes shopping QR affiliate overlays at bottom-left coordinates (12.4%, 89.3%).

### 5. 🚀 Wireless Over-The-Air (OTA) Updates
- Automatically detects new releases from GitHub on app startup and allows 1-click in-app updating.

👉 **[Detailed User Manual & Mode Instructions (docs/USER_MANUAL.md)](docs/USER_MANUAL.md)**

---

## 📜 Latest Updates (v0.8.0)

- **Multilingual Localization**: Added support for 13 global languages (Korean default, English, Japanese, Spanish, Hindi, Chinese, Russian, Portuguese, French, German, Indonesian, Arabic, Vietnamese).
- **International YouTube Ad Detection**: Extended regex patterns for ad badges and skip buttons across various localized YouTube TV apps.
- **Privacy & Dependency Cleanup**: Removed external Google Drive log upload code; optimized for local file diagnostics.
- **False Positive Protection**: Isolated pixel sensors during non-ad playback to eliminate accidental muting.

👉 **[Full Version Changelog (CHANGELOG.md)](CHANGELOG.md)**

---

## 🌐 Language Priority

This project maintains **Korean as the primary and system default fallback**, followed by localized resources in order:

1. 🇰🇷 **Korean (ko)** - *Primary & System Default*
2. 🇺🇸 **English (en)**
3. 🇯🇵 **Japanese (ja)**
4. 🇪🇸 **Spanish (es)**
5. 🇮🇳 **Hindi (hi)**
6. 🇨🇳 **Chinese (zh-rCN / zh-rTW)**
7. 🇷🇺 **Russian (ru)**
8. 🇵🇹 **Portuguese (pt)**
9. 🇫🇷 **French (fr)**
10. 🇩🇪 **German (de)**
11. 🇮🇩 **Indonesian (in / id)**
12. 🇸🇦 **Arabic (ar)**
13. 🇻🇳 **Vietnamese (vi)**

---

## 📄 License

This project is licensed under the MIT License.
