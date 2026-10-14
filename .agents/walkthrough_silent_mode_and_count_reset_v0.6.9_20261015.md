# 작업 완료 리포트: 순수 무음 모드 적용 및 광고 스킵 부팅 카운트/초기화 기능 탑재 (v0.6.9)

## 1. 개요 및 변경 배경
- **문제점 1 (하단 중앙 자막 라인 토스트 방해)**: 
  유튜브 공식 Lounge 서비스(`LoungeSkipService`)에서 광고 건너뛰기 성공 및 자동 음소거/복원 시마다 `Toast.makeText`를 호출하여 화면 하단 중앙(자막 영역)에 문구가 노출되면서 동영상 시청을 크게 방해함.
- **문제점 2 (우측 상단 잔상 배지)**:
  v0.6.8에서 토스트를 대체하기 위해 도입했던 우측 상단 접근성 오버레이 배지(`showMiniBadge`)가 Android TV 컴포지터 특성상 소멸 애니메이션 종료 콜백이 중단되며 화면에 영구 고착되는 현상 발생.
- **문제점 3 (광고 스킵 비정상 누적 수치 1900회 등)**:
  과거 루프 오류 등으로 인해 영구 저장된 누적 스킵 수치(1900회 이상)가 계속 표시되어 정확하지 않고, 사용자가 리셋할 수도 없었음.

---

## 2. 수술적 해결 내용

### 1) 동영상 재생 중 화면 내 모든 시각적 팝업/토스트 완전 제거 (순수 무음 백그라운드 모드)
- **`LoungeSkipService.kt`**:
  - `onAdSkippedCallback` 및 `onAutoMuteCallback` 내부의 `Toast.makeText(...).show()` 호출 전면 삭제.
  - 영상 재생 중 자막 라인(하단 중앙)을 가리는 모든 시각 알림을 원천 차단하고 `AppLogger` 백그라운드 기록만 수행.
- **`TvAdSkipService.kt`**:
  - `showMiniBadge` 함수, `windowManager`, `miniBadgeView`, 소멸 타이머 전면 제거.
  - 화면 우측 상단에 배지가 멈춰 있거나 잔상이 남는 문제를 물리적으로 100% 원천 차단.

### 2) 광고 스킵 횟수 집계 방식 개편 및 '초기화' 버튼 추가
- **부팅 후 세션 단위 카운트 (휘발성) 적용**:
  - `LoungeManager.kt`: 내부 휘발성 세션 변수 `bootSkipCount` 도입, 초기화 시 및 부팅 시 0으로 시작.
  - `TvAdSkipService.kt`: `onServiceConnected` 시점에 카운트를 0으로 시작.
- **설정 화면 내 '초기화' 버튼 신설**:
  - `activity_main.xml`: 스킵 통계 카드에 리모컨으로 쉽게 선택 가능한 `btnResetSkipCount` ("초기화") 버튼 및 가로 정렬 배치.
  - `strings.xml`: 레이블을 `"광고 스킵 횟수: %d회 (부팅 후)"`로 명확화.
  - `MainActivity.kt`: 사용자가 버튼 또는 통계 텍스트를 누르면 언제든지 카운트가 즉시 0으로 리셋되도록 연동.

---

## 3. 검증 결과
- **빌드 테스트**: `./gradlew assembleDebug` 정상 성공 (BUILD SUCCESSFUL).
- **한글 인코딩 점검**: 모든 한글 문자열이 UTF-8로 완벽하게 보존됨을 확인.
- **APK 산출물**: `TV_YouTube_Ad_Skipper.apk` 최신 빌드(v0.6.9, versionCode: 69)로 갱신 완료.
