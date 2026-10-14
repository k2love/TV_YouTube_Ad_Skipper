# 작업 완료 리포트: 강제 광고 0ms 칼음소거, 3~5초 볼륨 폭발 해결 및 버벅임 최적화 (v0.7.0)

- **버전**: `v0.7.0` (`versionCode: 70`)
- **대상 파일**:
  - `app/src/main/java/com/example/tvadskipper/LoungeManager.kt`
  - `app/src/main/java/com/example/tvadskipper/TvAdSkipService.kt`
  - `app/src/main/java/com/example/tvadskipper/AppLogger.kt`
  - `app/src/main/res/xml/accessibility_service_config.xml`
  - `app/build.gradle.kts` & `version.json`

## 핵심 개선 요약
1. **광고 시작 0.001초 즉시 볼륨 0 (음소거 지연 및 누락 해결)**:
   - 로컬 UI(접근성 텍스트/노란 카운터)와 Lounge 신호가 양방향 연동되어, 어느 쪽이든 광고를 감지하는 즉시 로컬 `AudioManager.STREAM_MUSIC`을 무음(OSD 팝업 없음)으로 잠그고 Lounge `setVolume(0)`을 3단계(`0ms`, `+280ms`, `+550ms`)로 확인 발송합니다.
   - `lastSkipAdTime` 쿨다운이 후속 강제 광고 음소거를 막던 버그를 제거했습니다.
2. **강제 광고 3~5초 시점 볼륨 최대(10 / 100%) 조기 복원 버그 해결**:
   - **원인 1 제거**: 5.2초 카운트다운 타이머(`scheduleAdSkipCountdownRetries`)가 강제 광고 도중 무조건 `triggerDirectScreenSkip()` -> `onAdSkipped()` -> `forceRestoreAudio()`(시스템 볼륨 10 + 유튜브 볼륨 100)를 실행하던 치명적 버그를 차단했습니다.
   - **원인 2 제거**: 픽셀 센서에서 노란색 원형 카운터(`yellowPixels >= 12`)를 최우선 판별하여, 노란 카운터가 남아 있는 동안에는 밝은 배경이 나와도 절대 스킵으로 오판하지 않습니다.
   - **원인 3 & 4 제거**: 본 영상 대기 상태(`state=5 CUED`) 및 광고 시작 직후 지연 도착하는 본 영상 `nowPlaying` 패킷이 강제 광고 중 음소거를 풀지 못하도록 화면 광고 UI 상태(`isAdOrCountdownCurrentlyOnScreen()`)와 교차 검증합니다.
3. **유튜브 재생 버벅임(프레임 드랍) 완벽 해결**:
   - 일반 영상 재생 중 `onAccessibilityEvent`마다 1.15초 주기로 4K/1080p 전체화면 캡처가 실행되던 버그(`|| qrAdAutoDismiss`)를 제거했습니다.
   - 스크린샷 분석을 메인 스레드에서 백그라운드 스레드(`screenshotExecutor`) + `480x270` 경량 버퍼(`reusablePixelBuffer`)로 이전했습니다.
   - 매 이벤트마다 75회 이상 호출되던 `findAccessibilityNodeInfosByText` IPC를 1회 단일 패스 스캔(`scanNodeTreeSinglePass`)과 `450ms` 스로틀링으로 압축했습니다.
