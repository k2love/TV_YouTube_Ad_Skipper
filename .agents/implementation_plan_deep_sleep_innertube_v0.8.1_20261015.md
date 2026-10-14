# 구현 계획서: 저사양 Google TV 최적화(Deep Sleep & Innertube QR) 통합 및 빌드 검증 (v0.8.1)

- **버전**: `v0.8.1` (`versionCode: 81`)
- **작성 일자**: 2026-10-15
- **기준 브랜치**: `main` (직전 커밋: `9c67021`)
- **목적**: 사용자가 제공한 저사양 TV(TCL·Hisense 등) 4K 60fps 프레임 드랍 완벽 해결 패치(완전 동면 아키텍처 및 Innertube 메타데이터 기반 QR 감지)를 기존 v0.8.0(13개 글로벌 다국어 지원, 구글 드라이브 제거, 한국어 기본 하네스 규칙)과 온전히 융합하여 빌드 및 릴리스

---

## 1. 개요 및 배경
- 사용자가 외부(Gemini Web)에서 생성된 v0.7.1 패치를 다운로드하여 프로젝트 폴더(`E:\AI_Projects\## TV_YouTube_Ad_Skipper`)에 덮어씌웠음.
- 해당 패치는 저사양 Google TV의 4K 60fps 비디오 재생 중 프레임 드랍('툭툭 끊김')을 해결하기 위한 3대 핵심 방향을 담고 있음:
  1. **방향 1**: 본 영상 재생 중 `TvAdSkipService` 완전 동면 (`onAdEndedSleep` / `onAdStartedWakeUp`), `serviceInfo.eventTypes = 0` 동적 제어, 센티넬 루프 정지.
  2. **방향 2**: 본 영상 시청 중 주기적 화면 캡처(`takeScreenshot`) 완전 폐지 (HWC 오버레이 플레인 보호).
  3. **방향 3**: `accessibility_service_config.xml`에서 고부하 플래그(`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) 영구 제거, `notificationTimeout` 500ms 완화.
  4. **비픽셀 QR 감지**: 화면 캡처 대신 `videoId` 수신 시 YouTube Innertube API(`/youtubei/v1/next`)로 쇼핑 태그를 1회 사전 조회하여 쇼핑 영상에 한해 핀포인트 닫기.
- **통합 시 해결 과제**:
  - 패치 파일이 v0.7.1 베이스로 작성되어 v0.8.0의 **13개 언어 다국어 스킵/광고 배지 키워드** 및 **구글 드라이브 코드 제거 내역**이 롤백되어 있음.
  - 이를 `v0.8.1`로 판올림하고, 다국어 기능과 저사양 TV 최적화를 완벽히 병합해야 함.

---

## 2. 파일별 변경 계획

### 2.1 `app/src/main/res/xml/accessibility_service_config.xml`
- `accessibilityEventTypes="typeWindowStateChanged"`로 단일화 (`typeWindowContentChanged` 제거).
- `accessibilityFlags="flagDefault|flagReportViewIds"` (`flagRetrieveInteractiveWindows` 제거).
- `notificationTimeout="500"`으로 완화.

### 2.2 `app/src/main/java/com/example/tvadskipper/LoungeManager.kt`
- YouTube Innertube API(`/youtubei/v1/next?prettyPrint=false`) 클라이언트 및 `checkVideoShoppingMetadataAsync(videoId)` 도입.
- `nowPlaying` 수신 시 비디오 ID 변경 감지 -> 쇼핑 태그(`shoppingOverlay`, `merchandiseShelf`, `productListRenderer`, `수수료`) 사전 스캔 -> 쇼핑 태그 감지 시 `+4.0초`, `+8.0초` 시점에 `TvAdSkipService.performTargetedQrDismissFromMetadata(videoId)` 호출.
- 광고 상태 변화 시 `TvAdSkipService.instance?.onAdStartedWakeUp()` (광고 시작 시 5~30초 가동) 및 `TvAdSkipService.instance?.onAdEndedSleep()` (본 영상 진입 시 즉시 완전 동면) 호출 연동.

### 2.3 `app/src/main/java/com/example/tvadskipper/TvAdSkipService.kt`
- 완전 동면 라이프사이클:
  - `adSentinelRunnable`: 본 영상 시청 중에는 정지.
  - `onAdStartedWakeUp()`: 광고 시에만 일시 깨우기.
  - `onAdEndedSleep()`: 본 영상 시작 즉시 완전 동면.
  - `setDynamicDormantState(dormant = true)`: 본 영상 중 `eventTypes = 0`으로 변경하여 안드로이드 OS/유튜브 접근성 이벤트 원천 차단.
- **다국어 보존**:
  - v0.8.0의 13개 언어 광고 배지 정규식(`adBadgePattern`), 13개 언어 스킵 키워드(`skipKeywords`), 다국어 카운트다운 정규식(`countdownRegex`), 오탐 방지 로직(`isRealAdBadge`)을 최신 코드와 완벽하게 통합.
- 본 영상 시청 중 상시 `takeScreenshot` 제거 유지.

### 2.4 `app/src/main/java/com/example/tvadskipper/AppLogger.kt`
- 구글 드라이브 업로드 코드(`uploadLogsToGoogleDrive`, `GOOGLE_APPS_SCRIPT_URL`) 재유입 방지 (순수 로컬 파일 로깅 유지).

### 2.5 `app/build.gradle.kts` & `version.json`
- `versionCode = 81`
- `versionName = "0.8.1"`
- 릴리스 노트 갱신 (저사양 TV 완전 동면 + 비픽셀 Innertube QR 쇼핑 감지 + 글로벌 다국어 지원).

---

## 3. 검증 계획
1. **빌드 검증**: `./gradlew assembleDebug` 실행하여 컴파일 오류 0건 확인.
2. **인코딩 검사**: UTF-8 무결성 및 한글 리터럴 깨짐(`?` 등) 전수 검사.
3. **APK 동기화**: `app-debug.apk`를 루트의 `TV_YouTube_Ad_Skipper.apk`로 복사.
4. **미러링 동기화**: K: 드라이브 작업 결과를 E: 드라이브에도 완벽 동기화.
