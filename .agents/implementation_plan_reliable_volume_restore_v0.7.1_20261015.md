# 구현 계획서: 광고 종료 후 볼륨 정상 복원(10) 버그 수정 (v0.7.1)

광고 재생 시작 시 0.001초 로컬 하드웨어 음소거는 정상 동작하지만, 광고가 끝났을 때(스킵 클릭 후 또는 강제 광고 자연 종료 후) 볼륨이 10으로 복원되지 않고 영구 무음(0)으로 멈춰있는 결함을 해결합니다.

## 1. 문제 분석 및 원인 규명

1. **`LoungeManager.sendSkipAdCommand()` 내 자가 모순 조건**:
   - `if (!isAdPlaying && !uiStillHasAd && isAutoMuted)`
   - 광고 도중 스킵되었으므로 `isAdPlaying`은 `true` 상태입니다. 그러나 이 함수 내에서 `isAdPlaying = false`로 변경하는 코드가 없으므로 `!isAdPlaying`은 항상 `false`가 되어 `restoreAudio()`가 절대 실행되지 않습니다.
   - 또한 TV에서 이미 접근성 터치로 스킵되어 리모트 네트워크 명령(`sendCommand("skipAd")`)이 400 에러를 반환하면 볼륨 복원 블록 자체가 실행되지 않습니다.
2. **`TvAdSkipService.onAdSkipped()`의 잔존 UI 타임스탬프 간섭**:
   - 접근성 터치로 스킵이 발생했을 때 `lastAdUiSeenUptimeMs`를 초기화하지 않아, 2500ms 동안 `isAdOrCountdownCurrentlyOnScreen()`이 계속 `true`를 반환하여 스킵 직후 볼륨 복원이 차단되었습니다.
3. **스킵 불가 강제 광고(5초/15초 범퍼 등)의 자연 종료 감지 부재**:
   - 사용자가 스킵하지 않고 끝까지 시청하는 광고의 경우, YouTube Leanback/Cobalt 앱은 Lounge 네트워크로 `1082`나 `ENDED` 이벤트를 보내지 않는 경우가 많습니다.
   - `TvAdSkipService`가 화면을 1.5초마다 검사하여 광고 UI가 사라졌음을 확인하고 있음에도, 이를 `LoungeManager`에 전달하는 로직이 없어 오디오가 45초 세이프티 타임아웃까지 무음으로 방치되었습니다.
4. **`LoungeManager.kt` 내 `adState == "0"` 오분류**:
   - YouTube Player API에서 `adState == "0"`은 `ENDED`(종료) 상태이나, v0.7.0 코드에서 이를 `adIsPlaying = true` 조건으로 분류하여 광고 종료 신호를 오히려 광고 재생 신호로 오인했습니다.
5. **`scheduleMainVideoRestoreVerification` 내 `recentAdSignal` 자가 차단**:
   - `(now - lastAdSignalTime) < 2200L` 조건으로 인해, 방금 광고가 끝난 시점에는 항상 이 조건이 참이 되어 복원 작업을 스스로 캔슬해버렸습니다.

---

## 2. 수술적 수정 계획

### A. `LoungeManager.kt`
- `onAdEndedFromLocalUi()` 메서드 신규 추가:
  - `TvAdSkipService`에서 광고 UI 및 노란 카운트다운이 2.0초 이상 부재함을 보고하면, 최소 재생 시간(3.5초 이상) 검증 후 `isAdPlaying = false`, `restoreAudio()` 실행.
- `onAccessibilitySkipClicked()` 보강:
  - 스킵 클릭 650ms 후 화면에 광고가 없으면 확실하게 `isAdPlaying = false`, `restoreAudio()` 실행.
- `sendSkipAdCommand()` 수정:
  - `!isAdPlaying` 불필요한 조건 제거, 스킵 후 화면 광고 UI 부재 시 즉시 `isAdPlaying = false`, `restoreAudio()` 실행.
- `handleAppEvent("onAdStateChange")`:
  - `adState == "0"`을 슬롯 종료 조건(`1082`, `STOPPED`, `ENDED`)에 편입하고 `adIsPlaying` 조건에서 제거.
- `scheduleMainVideoRestoreVerification()`:
  - `recentAdSignal` 자가 차단 제거, 화면 내 실제 광고 UI 유무(`!uiHasAd`)만 검증.

### B. `TvAdSkipService.kt`
- `onAdSkipped()`:
  - `lastAdUiSeenUptimeMs = 0L`, `lastYellowCountdownSeenUptimeMs = 0L`로 즉시 초기화하여 스킵 직후 볼륨 복원이 즉각 승인되도록 조치.
- `triggerPeriodicVisualCheck()` 및 `onAccessibilityEvent()`:
  - `!scan.isAdUiVisible && !scan.isCountdownVisible`일 때, 음소거 상태(`isAutoMuted`)이고 2.0초 이상 광고 UI가 없으면 `lounge.onAdEndedFromLocalUi()`를 호출하여 강제 광고 자연 종료 감지.

### C. 버전 갱신 및 배포
- `app/build.gradle.kts`: `versionCode = 71`, `versionName = "0.7.1"`
- `version.json`: `version 0.7.1`
- 빌드 검증 (`./gradlew assembleDebug`)
- `TV_YouTube_Ad_Skipper.apk` 갱신, GitHub 푸시 및 E: 드라이브 동기화

---

## 3. 검증 기준
1. 스킵 가능 광고: 5초 후 스킵 버튼 클릭 시 화면 전환과 동시에 볼륨이 저장된 수치(10)로 즉시 복원되는지 확인.
2. 스킵 불가 광고: 5초/15초 광고 종료 시 화면에서 광고 UI가 사라진 뒤 2초 내에 볼륨(10)이 정상 복원되는지 확인.
3. 광고 중간(3~5초): 노란 카운트다운 진행 중에는 볼륨이 절대 튀어오르지 않고 0ms 무음이 유지되는지 확인.
4. Gradle 빌드 성공 (BUILD SUCCESSFUL) 및 한글 인코딩 무결성 확인.
