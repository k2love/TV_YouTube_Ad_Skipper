# 구현 계획서: 강제 광고 완벽 음소거 및 쇼츠 후 캐스팅 자동 재연결 강화

## 1. 개요
1. **강제 광고 완벽 음소거**:
   - `onStateChange`에 의한 0.5초 조기 음소거 복원 버그 원천 차단 (`isAdPlaying` 상태 머신 엄격 격리).
   - 충돌을 유발하는 2배속 명령 제거.
   - 안드로이드 TV 시스템 `AudioManager`를 연동하여 하드웨어 미디어 스트림(`STREAM_MUSIC`)을 직접 0으로 잠금.
2. **쇼츠 후 캐스팅 자동 재연결(Auto-Reconnect)**:
   - 쇼츠 시청으로 세션이 일시 끊기더라도, 영구 저장된 `screenId`와 `loungeToken`을 활용하여 일반 영상 복귀 시 백그라운드에서 12자리 코드 재입력 없이 자동 재연결.

---

## 2. 세부 구현 계획

### 2.1 LoungeManager.kt (음소거 완벽화 & 자동 재연결)
- `android.media.AudioManager` 연동:
  - `muteAudio()`: 시스템 볼륨 기억 후 `audioManager.setStreamVolume(STREAM_MUSIC, 0, 0)` 및 Lounge `setVolume(0)`.
  - `restoreAudio()`: `audioManager.setStreamVolume(STREAM_MUSIC, savedVol, 0)` 및 원래 볼륨 복원.
- 상태 머신 격리:
  - `isAdPlaying` 플래그 관리: 광고 시작 시 true, 광고 완전 종료 시 false.
  - `onStateChange` 수신 시 `isAdPlaying == true`이면 절대로 복원 로직을 수행하지 않고 무시.
- 2배속 명령 발송 코드 완전 제거.
- 자동 재연결 루프:
  - `loungeScreenDisconnected` 수신 시 세션 변수만 초기화하고, 백그라운드 루프에서 5초마다 TV 연결 가능 여부를 확인하여 자동 복구.

### 2.2 MainActivity.kt 및 레이아웃
- UI 텍스트 정리: 2배속 문구 제거 및 "강제 광고 완벽 자동 음소거(Auto-Mute)"로 갱신.
- [즉시 재연결] 버튼 연동 제공.

---

## 3. 검증 계획
1. `./gradlew assembleDebug` 빌드 성공 확인.
2. UTF-8 한글 리터럴 전수 점검.
3. `apksigner` 서명 검증 통과 및 `TV_YouTube_Ad_Skipper.apk` 갱신.
