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

### 2. 🎯 공식 Lounge 프로토콜 모드 (추천 1순위: 100% 무오류)
- 화면을 캡처하거나 스캔하지 않고, YouTube의 공식 **'TV 코드로 연결'** 세션을 통해 구글 서버로부터 광고 완료 신호(`isSkipEnabled=true`)를 직접 받아 스킵합니다.
- 화면 자막이나 UI 변경에 100% 구애받지 않는 순정 알고리즘입니다.

### 3. 👁️ 컴퓨터 비전 픽셀 색상 센서 (쇼츠 방해 없는 시각 감지)
- Lounge 미연결 시에도 우하단 노란 원형 카운터 종료 후 흰색 스킵 버튼만 핀포인트 1회 클릭합니다.
- 일반 영상 속 노란 자막이나 로고 등으로 인한 오음소거(False Positive)를 원천 차단합니다.

### 4. 🛡️ 쇼츠 팝업 및 QR 쇼핑 광고 자동 통과
- 쇼츠(Shorts) 캐스팅 시 방해되는 연결 해제 팝업 0.1초 자동 통과.
- 화면 좌측 하단을 가리는 제휴 쇼핑 QR 배너 오버레이 0.1초 정밀 좌표 자동 닫기.

### 5. 🚀 GitHub 무선 자동 업데이트 (OTA)
- 새 버전이 배포되면 앱 시작 시 자동으로 감지하여 원클릭으로 인앱 업데이트를 지원합니다.

👉 **[상세 사용법 및 모드별 설정 설명서 바로가기 (docs/USER_MANUAL.md)](docs/USER_MANUAL.md)**

---

## 📜 최근 버전 업데이트 요약 (v0.8.0)

- **글로벌 다국어 언어팩 전면 지원**: 한국어를 최우선 기본값으로 설정하고 영어, 일본어, 스페인어, 힌디어, 중국어 등 13개 주요 언어팩 탑재.
- **다국어 광고 배지 및 스킵 버튼 감지 정규식 통합**: 전 세계 각국 유튜브 TV 앱의 실제 광고 라벨 및 스킵 버튼 매칭.
- **구글 드라이브 로그 전송 기능 완전 제거**: 외부 서버 종속성 및 개인정보 전송을 제거하고 안전한 로컬 런타임 진단 뷰어로 정돈.
- **정상 영상 재생 중 오음소거(False Positive) 원천 방지**: 비광고 시청 중 픽셀 검사를 철저히 격리.

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
