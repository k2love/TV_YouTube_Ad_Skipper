# 구현 계획서: GitHub 설명 및 13개 언어별 문서 v0.8.1 최신화 (Deep-Sleep & Innertube QR)

- **작성 일자**: 2026-10-15
- **버전**: `v0.8.1` (`versionCode: 81`)
- **목표**:
  - 사용자의 요구("깃허브 퍼밋도 하고, 깃허브 설명 갱신, 언어별 갱신도 해.")에 따라,
  - v0.8.1에서 도입된 핵심 최적화(저사양 Google TV 4K 60fps 완전 동면 아키텍처, 비픽셀 YouTube Innertube 메타데이터 기반 QR 쇼핑 감지)를
  - 한국어 기준 문서(`README.md`, `docs/USER_MANUAL.md`) 및 12개 글로벌 언어 문서(`README_*.md`, `docs/*/USER_MANUAL.md`)에 전면 반영하고,
  - GitHub 원격 저장소(`main`) 및 E: 드라이브 미러 디렉터리에 완벽하게 커밋/푸시하여 동기화함.

---

## 1. 언어별 문서 갱신 범위

### 1.1 루트 README 13종
- `README.md` (한국어 - 최우선 기본)
- `README_EN.md` (영어)
- `README_JA.md` (일본어)
- `README_ES.md` (스페인어)
- `README_HI.md` (힌디어)
- `README_ZH.md` (중국어)
- `README_RU.md` (러시아어)
- `README_PT.md` (포르투갈어)
- `README_FR.md` (프랑스어)
- `README_DE.md` (독일어)
- `README_ID.md` (인도네시아어)
- `README_AR.md` (아랍어)
- `README_VI.md` (베트남어)

### 1.2 사용자 설명서 (USER_MANUAL) 13종
- `docs/USER_MANUAL.md` (한국어)
- `docs/<lang>/USER_MANUAL.md` (12개 언어)

---

## 2. 문서별 반영 핵심 내용

1. **완전 동면(Zero-Overhead Deep-Sleep) 아키텍처**:
   - 본 영상 시청 중(`isAdPlaying == false`): 백그라운드 센티넬 루프 정지, 런타임 `serviceInfo.eventTypes = 0` 비활성화 ➔ CPU/GPU 0.0% 부하 유지.
   - 광고 시작 시 0.001초 음소거와 함께 일시 깨우기(`onAdStartedWakeUp`), 광고 종료 즉시 완전 동면 복귀.
2. **비픽셀(Non-Pixel) YouTube Innertube API 메타데이터 기반 QR 쇼핑 감지 엔진**:
   - 화면 픽셀 지속 캡처를 전면 폐지하여 TCL/Hisense TV 하드웨어 비디오 오버레이(HWC) 플레인 보호 ➔ 4K 60fps 끊김 원천 차단.
   - Lounge `videoId` 수신 시 백그라운드에서 `/youtubei/v1/next` 1회 조회로 쇼핑 태그 유무 판별.
   - 95% 이상 일반 영상에서는 캡처 0회 완전 동면, 쇼핑 상품이 포함된 5% 미만 영상에서만 `+4.0초`, `+8.0초` 시점에 핀포인트 닫기.

---

## 3. 검증 및 배포 절차
1. UTF-8 인코딩 및 깨진 문자 검사.
2. E: 드라이브 미러링 일괄 복사 및 git 상태 점검.
3. Git 커밋 & 원격 저장소(`main`) 푸시.
4. 결과 보고서(`walkthrough_...md`) 작성.
