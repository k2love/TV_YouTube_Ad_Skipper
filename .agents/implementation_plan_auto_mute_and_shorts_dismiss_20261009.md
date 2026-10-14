# 구현 계획서: 강제 광고 자동 음소거(Auto-Mute) 및 쇼츠 팝업 자동 통과(Auto-Dismiss)

## 1. 개요
Lounge 프로토콜의 스킵 성공을 바탕으로,
1. 건너뛸 수 없는 15초/30초 강제 광고(`isSkipEnabled: false`) 송출 시 **자동 음소거(Auto-Mute) 및 2배속 재생 시도 $\rightarrow$ 본 영상 복귀 시 사운드 및 1배속 자동 복원**
2. 쇼츠(Shorts) 진입 시 나타나는 **"캐스팅 전송 해제" 확인 대화상자 0.1초 자동 통과(Auto-Dismiss)**
를 구현하여 사용자 편의성을 극대화합니다.

---

## 2. 세부 구현 계획

### 2.1 LoungeManager.kt (강제 광고 자동 음소거 & 복원 엔진)
- `setVolume(volume: Int)` 명령 전송 로직 구현 (`req0__sc = "setVolume"`, `req0_volume = volume`).
- `setPlaybackSpeed(speed: Double)` 명령 전송 로직 구현 (`req0__sc = "setPlaybackSpeed"`, `req0_playbackSpeed = speed`).
- 볼륨 추적: `onVolumeChanged` 이벤트 수신 시 현재 TV 볼륨(`currentVolume`, 기본값 50) 캐싱.
- 강제 광고 감지:
  - `onAdStateChange`, `adPlaying` 수신 시:
    - `isSkipEnabled == "true"`: 기존대로 `sendSkipAdCommand()` 즉시 실행.
    - `isSkipEnabled == "false"`: 15초 강제 광고 구간이므로 `isAutoMuted = true` 설정 후 `setVolume(0)` 및 `setPlaybackSpeed(2.0)` 발송.
- 본 영상 복귀 감지:
  - `onStateChange` (재생 상태 복귀) 또는 `onAdStateChange` 종료 시:
    - 만약 `isAutoMuted == true`이면, 백업해둔 원래 볼륨으로 `setVolume(savedVolume)` 복원 및 `setPlaybackSpeed(1.0)` 복원.

### 2.2 TvAdSkipService.kt (쇼츠 팝업 0.1초 자동 통과 엔진)
- 접근성 서비스 이벤트 수신 시, 다이얼로그 노드 트리 탐색:
  - "전송", "캐스팅", "연결을 해제", "연결 해제", "Disconnect" 관련 문구가 포함된 창 감지.
  - "해제", "연결 해제", "확인", "예", "Disconnect", "OK" 버튼을 0.1초 만에 `ACTION_CLICK` 실행.
  - 사용자의 리모컨 조작 없이 매끄럽게 쇼츠로 직행 지원.

### 2.3 MainActivity.kt 및 리소스 (UI 및 설정 제어)
- 스위치 옵션 제공:
  - `강제 광고 자동 음소거 (Auto-Mute)` (기본 ON)
  - `쇼츠 연결해제 팝업 자동 통과` (기본 ON)
- 실시간 이벤트 라벨에 음소거/복원 상태 출력 연동.

---

## 3. 검증 계획
1. `./gradlew assembleDebug` 빌드 성공 확인.
2. UTF-8 인코딩 및 한글 리터럴 손상 여부 전수 점검.
3. APK 패키징 및 용량 확인 (`TV_YouTube_Ad_Skipper.apk` 갱신).
