# 구현 계획서: 강제 광고 0ms 즉시 음소거, 3~5초 볼륨 최대(10) 폭발 버그 차단 및 유튜브 버벅임 최적화 (v0.7.0)

- **기능 슬러그**: `instant_mute_and_stutter_fix_v0.7.0`
- **버전/날짜**: `v0.7.0 / 20261015`

## 1. 문제 진단 및 근본 원인 (Root Causes)

### 1) 광고 시작 후 1~2초 뒤에야 음소거되거나 아예 음소거가 안 되는 현상
- **네트워크 왕복 지연 (1~2초 딜레이)**: 기존 `muteAudio()`는 로컬 TV 하드웨어 오디오(`AudioManager.STREAM_MUSIC`)를 제어하지 않고 구글 클라우드 Lounge 서버로 `setVolume(0)` HTTP POST 요청만 보냈기 때문에, 서버 왕복 시간(0.8~2.0초) 동안 광고 첫머리가 그대로 출력됨.
- **`lastSkipAdTime < 2500L` 및 `if (isAutoMuted) return` 차단 버그**:
  - 1차 광고 스킵 직후 2차 강제 광고가 연달아 시작될 때 `lastSkipAdTime < 2500L` 가드에 걸려 음소거가 아예 무시됨.
  - 광고 초기화(`adState=0`) 시점에 단일 발송한 `setVolume(0)`이 플레이어 버퍼링 중에 씹히면, 이미 `isAutoMuted = true`가 되어 본 재생(`adState=1`) 패킷이 와도 재전송하지 않음.

### 2) 강제 광고 시작 3~5초 후 음소거가 풀리고 볼륨이 최대(10 / 100%)로 올라가는 현상 (4대 복합 원인)
- **[원인 A] `scheduleAdSkipCountdownRetries()`의 5.2초 무조건 화면 터치 & 강제 복원**:
  - 모든 광고 시작 시 무조건 가동된 타이머가 **5.2초 시점에 `TvAdSkipService.instance?.triggerDirectScreenSkip()`을 호출**함.
  - `triggerDirectScreenSkip()`은 무조건 `onAdSkipped()` -> **`forceRestoreAudio()`**를 호출하여, 15초/20초/30초 강제 광고 도중 정확히 5.2초에 시스템 볼륨을 **`10`(`targetVol = 10`, `FLAG_SHOW_UI`)**으로 올리고 유튜브 볼륨을 **`100`**으로 강제 복원함.
- **[원인 B] 픽셀 센서의 노란색 카운터 미검증 및 흰색 배경 오판 (`inspectBitmapAndTrigger`)**:
  - 우하단에 노란색 원형 카운트다운(`yellowPixels`)이 돌아가고 있는 중에도 `whitePixels >= 45 && blackPixels >= 5`를 먼저 검사함.
  - 광고 시작 2~4초 후 우하단 배경이 밝아지면 스킵 버튼으로 오판해 클릭하고 `onAdSkipped()` -> `forceRestoreAudio()`(볼륨 10/100)를 호출함.
- **[원인 C] `onStateChange: state=5 (VIDEO_CUED)` 오판**:
  - 프리롤 광고 재생 중 대기 중인 본 영상의 상태값인 `state=5 (CUED)`가 광고 시작 2~4초 후 수신될 때 이를 영상 종료로 오판해 `isAdPlaying = false` 및 `restoreAudio()`를 호출함.
- **[원인 D] 광고 중 지연 도착하는 본 영상 `nowPlaying` 패킷 오판**:
  - 광고 시작 2~4초 후 본 영상 `videoId`만 담긴 `nowPlaying` 패킷이 올 때, 경과 시간 및 화면 내 광고 UI 존재 여부를 확인하지 않고 즉시 `restoreAudio()`를 호출함.

### 3) 버전업 이후 유튜브 영상 재생 버벅임 (프레임 드랍 & 리모컨 렉)
- **[병목 A] 일반 영상 시청 중 1.15초마다 실행되던 4K/1080p 전체화면 캡처**:
  - `onAccessibilityEvent` 내부의 `if (!isChatVisible && (isAdContext || qrAdAutoDismiss))` 조건에서 `qrAdAutoDismiss`가 기본 `true`이므로, **광고가 아닐 때도 매 1.15초마다 `takeScreenshot`이 실행**됨.
  - 게다가 `mainExecutor`(메인 UI 스레드)에서 4K(33MB) / 1080p(8.3MB) 비트맵 전체를 CPU RAM으로 복사(`Bitmap.copy`)하고 수만 번의 JNI `getPixel`을 호출해 심각한 GC 멈춤과 프레임 드랍 유발.
- **[병목 B] 매 접근성 이벤트마다 75회 이상 발생하던 Binder IPC 호출**:
  - `notificationTimeout="50"` 및 `flagIncludeNotImportantViews` 상태에서, 매 이벤트마다 `findAccessibilityNodeInfosByText`를 75회 이상 개별 호출하여 유튜브 메인 렌더 스레드를 블로킹함.
- **[병목 C] 메인 스레드 동기식 파일 로깅 (`AppLogger.kt`)**:
  - 모든 로그 호출 시 메인 스레드에서 동기식으로 `FileOutputStream`을 열고 닫음.

---

## 2. 해결 및 수술 내역

1. **0.001초 즉시 하이브리드 음소거 (`LoungeManager.kt` & `TvAdSkipService.kt`)**:
   - 광고 감지(Lounge 패킷 또는 로컬 접근성 텍스트/노란 카운터 감지) 즉시 로컬 `AudioManager.STREAM_MUSIC`을 OSD 없이(`flags=0`) 0으로 잠금(지연 시간 0.001초).
   - 동시에 Lounge `setVolume(0)`을 `0ms`, `280ms`, `550ms` 3단계로 확인 전송하여 초기 버퍼링 중 패킷 유실 원천 방지.
   - `onVolumeChanged`에 Mute-Guard를 추가하여 광고 중 플레이어가 자체 볼륨을 올리려 하면 즉시 0으로 재잠금.
2. **3~5초 볼륨 최대(10/100) 폭발 4대 원인 완전 제거**:
   - `scheduleAdSkipCountdownRetries()`에서 블라인드 화면 터치(`triggerDirectScreenSkip`) 및 `forceRestoreAudio` 연결 고리 완전 제거.
   - `TvAdSkipService.onAdSkipped()`에서 무조건 볼륨을 10으로 올리던 `forceRestoreAudio()` 호출 제거.
   - 픽셀 센서에서 `yellowPixels >= 12`(노란색 원형 카운터 존재)를 최우선 검사하여, 노란 원이 떠 있는 동안에는 절대 스킵 클릭/볼륨 복원을 하지 않도록 철통 가드 적용 + `whitePixels in 55..650` 상한선 적용.
   - `state == 5 (CUED)` 및 광고 초기(4.8초 이내) / 화면 내 광고 노출 중(`isAdOrCountdownCurrentlyOnScreen()`) 도착하는 `nowPlaying` 패킷이 음소거를 해제하지 못하도록 차단.
3. **유튜브 버벅임 제로화 (최적화)**:
   - `onAccessibilityEvent`에서 비광고 시청 중 `analyzeScreenPixelSensor` 호출 제거.
   - `takeScreenshot` 콜백을 전용 백그라운드 스레드(`screenshotExecutor`)로 분리하고, GPU 버퍼를 `480x270` (0.5MB)으로 축소한 뒤 단일 `getPixels` 배열로 스캔하여 메모리 할당 98% 절감 및 메인 스레드 점유 0ms 달성.
   - 75회 반복되던 `findAccessibilityNodeInfosByText` IPC 호출을 **단 1회의 단일 패스 트리 순회(`scanNodeTreeSinglePass`)** + `450ms` 이벤트 스로틀링으로 압축.
   - `accessibility_service_config.xml`에 `packageNames="com.google.android.youtube.tv,com.google.android.youtube"`, `notificationTimeout="250"`, `flagIncludeNotImportantViews` 제거 적용.
   - `AppLogger.kt` 디스크 쓰기를 백그라운드 단일 스레드로 비동기화.
