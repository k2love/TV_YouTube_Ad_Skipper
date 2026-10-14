# 변경 결과 리포트: 광고 종료 후 볼륨 정상 복원(10) 버그 수정 (v0.7.1)

## 1. 개요 및 변경 배경
- **증상**: v0.7.0 업데이트 후 강제 광고 시작 시 0ms 칼음소거는 원활히 작동하나, 광고가 종료된 후(스킵 버튼 클릭 후 또는 스킵 불가 강제 광고 재생 종료 후) 볼륨이 10으로 복원되지 않고 영구 음소거(0) 상태로 남아 소리가 나오지 않는 문제가 발생했습니다.
- **해결 목표**:
  1. 스킵 가능 광고 스킵 즉시 볼륨 복원.
  2. 스킵 불가 광고(5초/15초 등) 자연 종료 시 화면 내 광고 UI 부재 감지 후 2초 내에 볼륨(10) 자동 복원.
  3. 광고 중간(3~5초 구간) 볼륨 튀어오름 방지 가드 완전 유지.

---

## 2. 핵심 수정 내역

### A. `LoungeManager.kt`
1. **스킵 후 볼륨 복원 자가 모순 제거 (`sendSkipAdCommand`)**:
   - `if (!isAdPlaying && !uiStillHasAd && isAutoMuted)`에서 광고 중 스킵되어 항상 `true`였던 `isAdPlaying` 조건을 제거했습니다.
   - 스킵 명령 후 450ms 대기(총 800ms) 뒤 화면에 광고 UI나 카운트다운이 없으면 즉시 `isAdPlaying = false`, `isCurrentAdSkippable = false`, `restoreAudio()`를 호출하도록 수정했습니다.
2. **접근성 스킵 터치 직후 확실한 복원 스케줄링 (`onAccessibilitySkipClicked`)**:
   - 리모트 네트워크 응답 여부와 무관하게 접근성 서비스에서 스킵 버튼을 클릭한 직후 600ms 후 화면 내 광고 UI가 없으면 즉시 볼륨을 복원합니다.
3. **UI 센티널 기반 스킵 불가 광고 자연 종료 연동 (`onAdEndedFromLocalUi`)**:
   - 화면에서 광고 UI 및 노란 카운트다운이 2.0초 이상 부재함을 감지했을 때 호출되는 전용 핸들러를 추가했습니다.
   - 최소 재생 시간(3.5초 이상)을 검증하여 영상 전환 찰나의 오판을 방지하고 안전하게 볼륨을 복원합니다.
4. **Lounge 이벤트 `adState == "0"` 분류 정정**:
   - YouTube Player API에서 `0`은 `ENDED` 상태이므로 슬롯 종료 블록(`1082`, `STOPPED`, `ENDED`)에 `adState == "0"`을 추가하고, `adIsPlaying` 조건에서 제거했습니다.
5. **메인 영상 검증 자가 차단 제거 (`scheduleMainVideoRestoreVerification`)**:
   - 광고 직후 항상 참이 되어 복원을 캔슬하던 `recentAdSignal < 2200L` 조건을 제거하고, 순수하게 화면 내 실제 광고 UI 유무(`!uiHasAd`)만 검증하도록 정밀화했습니다.
6. **로컬 하드웨어 볼륨 복원 시 언뮤트 상태 재확인 (`restoreLocalHardwareAudio`)**:
   - 볼륨 설정 후 `am.isStreamMute()`가 활성화되어 있는 경우 `ADJUST_UNMUTE`를 재호출하여 일부 TV OS에서 음소거 플래그가 잔존하는 현상을 방지했습니다.

### B. `TvAdSkipService.kt`
1. **스킵 직후 광고 잔류 UI 타임스탬프 즉시 초기화 (`onAdSkipped`)**:
   - 스킵 버튼 클릭 즉시 `lastAdUiSeenUptimeMs = 0L`, `lastYellowCountdownSeenUptimeMs = 0L`로 초기화하여 스킵 후 볼륨 복원 윈도우가 딜레이 없이 즉시 열리도록 조치했습니다.
2. **화면 내 광고 자연 종료 센티널 연동 (`triggerPeriodicVisualCheck` 및 `onAccessibilityEvent`)**:
   - `!scan.isAdUiVisible && !scan.isCountdownVisible` 상태일 때, 음소거 중(`isAutoMuted`)이고 2.0초 이상 광고 UI와 노란 원형 카운트다운이 모두 없으면 `lounge.onAdEndedFromLocalUi()`를 호출하여 스킵 불가 광고가 끝났을 때 스스로 볼륨을 복원하도록 구현했습니다.

---

## 3. 검증 결과
- **컴파일 및 패키징**: `./gradlew assembleDebug` 빌드 성공 (BUILD SUCCESSFUL)
- **한글 인코딩 전수 점검**: UTF-8 한글 리터럴 깨짐 0건 확인
- **최신 빌드 파일 갱신**: `TV_YouTube_Ad_Skipper.apk` (6,565,833 bytes) 생성 완료
