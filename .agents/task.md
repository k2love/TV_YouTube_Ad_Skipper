# Task Board: 저사양 Google TV 최적화(Deep Sleep & Innertube QR) 통합 및 빌드 검증 (v0.8.1)

- [x] 1. 패치 파일 분석 및 통합 계획 수립 <!-- id: 1 -->
  - [x] 사용자가 E 드라이브에 적용한 v0.7.1 패치 내역 전수 파악 (Deep Sleep, Innertube QR 메타데이터, config 경량화)
  - [x] v0.8.0 기존 기능(13개 글로벌 다국어 키워드, 구글 드라이브 제거 상태, 한국어 우선 하네스 규칙) 보존 대상 식별
  - [x] 계획서 작성 (`.agents/implementation_plan_deep_sleep_innertube_v0.8.1_20261015.md`)
- [x] 2. 소스 코드 수술적 정밀 통합 (K: 및 E: 드라이브) <!-- id: 2 -->
  - [x] `accessibility_service_config.xml`: 프레임 드랍 유발 플래그 제거 및 타임아웃 완화
  - [x] `LoungeManager.kt`: Innertube API 쇼핑 메타데이터 사전 판별 + Deep Sleep 라이프사이클(`onAdStartedWakeUp`, `onAdEndedSleep`) 연동
  - [x] `TvAdSkipService.kt`: 완전 동면(`setDynamicDormantState`, 런타임 eventTypes=0, 센티넬 정지) + 13개 언어 다국어 감지 및 오탐 방지 로직 병합
  - [x] `AppLogger.kt`: 구글 드라이브 업로드 코드 유입 방지 및 순수 로컬 로거 유지
  - [x] `app/build.gradle.kts` & `version.json`: `versionCode 81`, `versionName 0.8.1`로 판올림
- [x] 3. 빌드 및 동작 무결성 검증 <!-- id: 3 -->
  - [x] `./gradlew assembleDebug` 빌드 확인 (BUILD SUCCESSFUL)
  - [x] 한글 리터럴 및 UTF-8 인코딩 손상 여부 전수 검사
  - [x] 최신 APK(`TV_YouTube_Ad_Skipper.apk`) 생성 및 갱신
- [x] 4. 문서화, Git 커밋, GitHub 푸시 및 E: 드라이브 미러링 <!-- id: 4 -->
  - [x] `CHANGELOG.md` 및 `README.md`에 v0.8.1 릴리스 내역 반영
  - [x] `.agents/walkthrough_deep_sleep_innertube_v0.8.1_20261015.md` 작성
  - [ ] Git 커밋 & 원격 저장소(`main`) 푸시 및 E: 드라이브 동기화
