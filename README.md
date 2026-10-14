# 📺 TV YouTube Ad Skipper (안드로이드 TV 유튜브 광고 자동 스킵 & 음소거)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-한국어%20(기본)%20%2B%2012개국-orange.svg)](#-지원-언어-우선순위)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **공식 YouTube TV 앱에서 광고 건너뛰기 타이머가 끝나면 0.001초 만에 자동으로 스킵하고, 강제 광고 중에는 완벽하게 소리를 음소거(Mute)해 주는 안드로이드 TV 전용 유틸리티입니다.**

[**🇰🇷 한국어 (기본)**](README.md) | [**🇺🇸 English**](README_EN.md) | [**🇯🇵 日本語**](README_JA.md) | [**🇪🇸 Español**](README_ES.md) | [**🇮🇳 हिन्दी**](README_HI.md) | [**🇨🇳 简体中文**](README_ZH.md) | [**🇷🇺 Русский**](README_RU.md) | [**🇵🇹 Português**](README_PT.md) | [**🇫🇷 Français**](README_FR.md) | [**🇩🇪 Deutsch**](README_DE.md) | [**🇮🇩 Bahasa Indonesia**](README_ID.md) | [**🇸🇦 العربية**](README_AR.md) | [**🇻🇳 Tiếng Việt**](README_VI.md)

---

## 🚀 빠른 시작 (3단계 초간단 설치)

1. **APK 다운로드**: [TV_YouTube_Ad_Skipper.apk 직접 다운로드](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **TV에 설치**: 스마트폰에서 **'Send Files to TV' (SFTTV)** 앱 또는 **USB 메모리**를 통해 TV로 전송 후 설치.
3. **접근성 권한 활성화**: TV [설정] ➔ [접근성] ➔ **[TV 유튜브 광고 건너뛰기 서비스]** 켜기!
   - *(Google TV 12+ 에서 권한이 잠겨 있는 경우: [Google TV 제한된 설정 해제 가이드](docs/INSTALLATION.md#%EF%B8%8F-3-필수-설정-google-tv-권한-잠금-해제) 참고)*

👉 **[상세 설치 및 TV 기기별 설정 가이드 바로가기 (docs/INSTALLATION.md)](docs/INSTALLATION.md)**

---

## 🌟 주요 기능 (Key Features)

### 1. 0.001초 초고속 광고 스킵 & 0ms 칼음소거
- 광고 건너뛰기 버튼이 노출되는 즉시 0.001초 만에 자동 클릭합니다.
- 스킵 불가능한 강제 광고 진행 중에는 TV 오디오를 자동으로 음소거하고, 본 영상이 시작되면 이전 볼륨(기본 10)으로 오차 없이 복원합니다.

### 2. 💤 저사양 Google TV 전용 '완전 동면(Deep-Sleep) 아키텍처' (4K 60fps 끊김 0%)
- 본 영상 시청 중(`isAdPlaying == false`)에는 백그라운드 센티넬 루프를 100% 정지하고, 런타임에 `serviceInfo.eventTypes = 0`으로 비활성화하여 안드로이드 OS 및 유튜브 앱의 접근성 이벤트를 완전히 차단합니다.
- 본 영상 시청 중 주기적 화면 캡처(`takeScreenshot`)를 완전 폐지하여, 보급형 Google TV(TCL, Hisense 등)에서 4K 60fps HDR 영상 재생 시 발생하던 주기적 프레임 드랍('툭툭 끊김')을 원천 차단합니다.

### 3. 🛒 비픽셀(Non-Pixel) YouTube Innertube API 기반 QR 쇼핑 광고 감지
- 화면 픽셀을 상시 캡처해 감시하는 대신, 영상 진입 시 YouTube Innertube API(`/youtubei/v1/next`)로 쇼핑 태그 유무를 1회 사전 조회합니다.
- 쇼핑 태그가 없는 95% 이상의 일반 영상은 캡처 0회로 100% 동면을 유지하며, 쇼핑 상품이 등록된 5% 미만의 영상에서만 `+4.0초`, `+8.0초` 시점에 핀포인트로 닫기를 수행합니다.

### 4. 🎯 공식 Lounge 프로토콜 모드 (추천 1순위: 100% 무오류)
- 화면을 캡처하거나 스캔하지 않고, YouTube의 공식 **'TV 코드로 연결'** 세션을 통해 구글 서버로부터 광고 완료 신호(`isSkipEnabled=true`)를 직접 받아 스킵합니다.
- 화면 자막이나 UI 변경에 100% 구애받지 않는 순정 알고리즘입니다.

### 5. 🌐 전 세계 13개 글로벌 언어 광고 배지 및 인터페이스 지원
- 한국어를 최우선 기본값으로 설정하고 영어, 일본어, 중국어, 스페인어, 포르투갈어, 프랑스어, 독일어, 러시아어, 인도네시아어, 힌디어, 아랍어, 베트남어 등 전 세계 13개 언어의 광고 배지 및 스킵 버튼 다국어 감지 알고리즘을 지원합니다.

### 6. 🚀 GitHub 무선 자동 업데이트 (OTA)
- 새 버전이 배포되면 앱 시작 시 자동으로 감지하여 원클릭으로 인앱 업데이트를 지원합니다.

👉 **[상세 사용법 및 모드별 설정 설명서 바로가기 (docs/USER_MANUAL.md)](docs/USER_MANUAL.md)**

---

## 📜 최근 버전 업데이트 요약 (v0.8.1)

- **저사양 Google TV 4K 60fps 끊김 해결 (완전 동면 아키텍처)**: 본 영상 시청 중 접근성 이벤트 수신·센티넬 루프·화면 캡처를 전면 정지하여 CPU/GPU 0.0% 부하 달성.
- **`accessibility_service_config.xml` 고부하 플래그 영구 제거**: 프레임 드랍을 유발하던 `flagRetrieveInteractiveWindows` 및 `typeWindowContentChanged` 제거, 타임아웃 500ms 완화.
- **비픽셀 YouTube Innertube API 기반 QR 쇼핑 광고 감지**: 상시 캡처 폐지, Lounge `videoId` 사전 메타데이터 조회 기법 적용.
- **글로벌 다국어 언어팩 지원 보존**: 한국어 기본 + 12개 주요 글로벌 언어 광고 감지 및 인터페이스 완벽 유지.
- **구글 드라이브 업로드 코드 제거 및 로컬 런타임 진단 로그 뷰어 유지**.

👉 **[전체 버전 히스토리 확인 (CHANGELOG.md)](CHANGELOG.md)**

---

## 🌐 지원 언어 우선순위

본 프로젝트는 **한국어(Korean)를 최우선이자 시스템 기본값(Default Fallback)**으로 설정하며, 기기 설정 언어에 따라 아래 우선순위 순으로 자동 전환됩니다:

| 언어 (Language) | 설명서 (README) | 설치 가이드 (Install) | 상세 매뉴얼 (Manual) |
| :--- | :---: | :---: | :---: |
| 1. 🇰🇷 **한국어 (Korean)** *(기본)* | [README.md](README.md) | [설치 가이드](docs/INSTALLATION.md) | [사용설명서](docs/USER_MANUAL.md) |
| 2. 🇺🇸 **영어 (English)** | [README_EN.md](README_EN.md) | [Installation Guide](docs/en/INSTALLATION.md) | [User Manual](docs/en/USER_MANUAL.md) |
| 3. 🇯🇵 **일본어 (日本語)** | [README_JA.md](README_JA.md) | [インストールガイド](docs/ja/INSTALLATION.md) | [ユーザーマニュアル](docs/ja/USER_MANUAL.md) |
| 4. 🇪🇸 **스페인어 (Español)** | [README_ES.md](README_ES.md) | [Guía de instalación](docs/es/INSTALLATION.md) | [Manual de usuario](docs/es/USER_MANUAL.md) |
| 5. 🇮🇳 **힌디어 (हिन्दी)** | [README_HI.md](README_HI.md) | [इंस्टॉलेशन गाइड](docs/hi/INSTALLATION.md) | [उपयोगकर्ता मैनुअल](docs/hi/USER_MANUAL.md) |
| 6. 🇨🇳 **중국어 (简体中文)** | [README_ZH.md](README_ZH.md) | [安装指南](docs/zh/INSTALLATION.md) | [用户手册](docs/zh/USER_MANUAL.md) |
| 7. 🇷🇺 **러시아어 (Русский)** | [README_RU.md](README_RU.md) | [Инструкция](docs/ru/INSTALLATION.md) | [Руководство](docs/ru/USER_MANUAL.md) |
| 8. 🇵🇹 **포르투갈어 (Português)** | [README_PT.md](README_PT.md) | [Guia de instalação](docs/pt/INSTALLATION.md) | [Manual do usuário](docs/pt/USER_MANUAL.md) |
| 9. 🇫🇷 **프랑스어 (Français)** | [README_FR.md](README_FR.md) | [Guide d\'installation](docs/fr/INSTALLATION.md) | [Manuel d\'utilisation](docs/fr/USER_MANUAL.md) |
| 10. 🇩🇪 **독일어 (Deutsch)** | [README_DE.md](README_DE.md) | [Installationsanleitung](docs/de/INSTALLATION.md) | [Benutzerhandbuch](docs/de/USER_MANUAL.md) |
| 11. 🇮🇩 **인도네시아어 (Bahasa Indonesia)** | [README_ID.md](README_ID.md) | [Panduan Instalasi](docs/id/INSTALLATION.md) | [Panduan Pengguna](docs/id/USER_MANUAL.md) |
| 12. 🇸🇦 **아랍어 (العربية)** | [README_AR.md](README_AR.md) | [دليل التثبيت](docs/ar/INSTALLATION.md) | [دليل المستخدم](docs/ar/USER_MANUAL.md) |
| 13. 🇻🇳 **베트남어 (Tiếng Việt)** | [README_VI.md](README_VI.md) | [Hướng dẫn cài đặt](docs/vi/INSTALLATION.md) | [Hướng dẫn sử dụng](docs/vi/USER_MANUAL.md) |

---

## 🛠️ 개발 및 빌드 환경

```powershell
# 프로젝트 클론
git clone https://github.com/k2love/TV_YouTube_Ad_Skipper.git

# 디버그 APK 빌드
./gradlew assembleDebug

# 빌드 결과물
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 라이선스 (License)

This project is licensed under the MIT License.
