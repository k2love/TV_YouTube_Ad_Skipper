# 구현 계획서: 순수 무음 모드 적용 및 광고 스킵 부팅 카운트/초기화 기능 구현 (v0.6.9)

## 1. 개요 및 목적
- **목적**:
  1. 유튜브 영상 시청 중 화면 하단 중앙(자막 영역)에 노출되던 광고 스킵/음소거 안내 토스트 및 화면 우측 상단에 멈춰 있던 오버레이 배지를 전면 제거하여 완전 무음·무간섭 백그라운드 스킵 환경 제공.
  2. 설정 화면에 표시되는 비정상 누적 수치(예: 1900회 등)를 부팅 후 세션 단위 카운트로 개편하고, 사용자가 언제든 0으로 초기화할 수 있는 '초기화' 버튼 추가.
  3. v0.6.9 빌드 검증 및 GitHub 배포.

## 2. 변경 대상 파일 및 상세 계획

### 1) `LoungeSkipService.kt`
- **변경 사항**:
  - `loungeManager.onAdSkippedCallback` 및 `loungeManager.onAutoMuteCallback` 내 `Toast.makeText(...).show()` 호출 전면 삭제.
  - 영상 재생 중 자막 라인(하단 중앙)을 가리는 모든 시각적 팝업을 차단하고, 백그라운드 로깅(`AppLogger`)으로만 기록 유지.

### 2) `TvAdSkipService.kt`
- **변경 사항**:
  - `showMiniBadge` 함수, `miniBadgeView`, `windowManager`, `miniBadgeDismissRunnable` 전면 제거 (우측 상단 잔상 배지 원천 차단).
  - `onServiceConnected()` 시점에 `bootSkipCount`를 0으로 초기화하여 부팅/서비스 재시작 시 휘발성 세션 카운트 유지.
  - 스킵 시 `bootSkipCount` 증가 및 내부 로깅 처리.
  - 외부에서 카운트를 리셋할 수 있는 `resetSkipCount()` 메서드 제공.

### 3) `LoungeManager.kt`
- **변경 사항**:
  - 비정상 누적 SharedPreferences 값을 제거하고, 부팅 후 세션 카운트 변수(`bootSkipCount: Int = 0`) 도입.
  - `getSkipCount()`가 `bootSkipCount`를 반환하도록 변경.
  - 카운트 0 리셋 메서드 `resetSkipCount()` 추가.

### 4) `app/src/main/res/layout/activity_main.xml` & `strings.xml`
- **변경 사항**:
  - `tvLoungeStats` 텍스트 레이블을 "광고 스킵 횟수: %d회 (부팅 후)"로 수정.
  - 바로 옆에 리모컨으로 쉽게 누를 수 있는 `btnResetSkipCount` ("초기화") 버튼 추가.

### 5) `MainActivity.kt`
- **변경 사항**:
  - `btnResetSkipCount` 클릭 리스너 연결: 클릭 시 `loungeManager.resetSkipCount()`, `TvAdSkipService.instance?.resetSkipCount()`, UI 즉시 갱신 및 완료 안내 토스트 표시.

### 6) `app/build.gradle.kts` & `version.json`
- **변경 사항**:
  - `versionCode` = 69, `versionName` = "0.6.9" 갱신.
  - `version.json` 변경 내역 및 다운로드 정보 업데이트.

## 3. 검증 계획
- [ ] `./gradlew assembleDebug` 빌드 정상 확인
- [ ] 소스 코드 내 한글 리터럴 인코딩 정상 여부 전수 검사
- [ ] `TV_YouTube_Ad_Skipper.apk` 갱신 및 Git 커밋/푸시 (`origin/main`)
