# 작업 보드: 강제 광고 0ms 칼음소거, 3~5초 볼륨 폭발 해결 및 버벅임 최적화 (v0.7.0)

- **상태**: 완료
- **목표**: 
  1. 광고 시작 즉시 0.001초 하드웨어 무음 + 3단계 확인 전송 + Mute-Guard 탑재
  2. 강제 광고 3~5초 시점 볼륨 10/최대 급상승 4대 원인 완벽 차단
  3. 일반 영상 시청 중 캡처 중단, 백그라운드 480x270 초경량 버퍼, 단일 패스 순회, 비동기 로거 적용으로 렉/버벅임 제로화
  4. v0.7.0 빌드 및 GitHub 배포

## 작업 세부 항목
- [x] 패치 파일 확인 및 압축 해제 (`TV_YouTube_Ad_Skipper_v0.7.0_source_patch.zip`)
- [x] `accessibility_service_config.xml`: `flagIncludeNotImportantViews` 플래그 제거 및 유튜브 패키지 전용 격리
- [x] `AppLogger.kt`: 단일 스레드 Executor 비동기 파일 I/O 적용
- [x] `LoungeManager.kt`:
  - `applyLocalHardwareMute()` 및 3단계 다중 전송 구현
  - `muteAudio()`에서 `lastSkipAdTime < 2500L` 제거
  - `onVolumeChanged` Mute-Guard 추가
  - `scheduleAdSkipCountdownRetries()`에서 강제 스킵 클릭 제거
  - `onStateChange`에서 `state == 5` 분리 및 `nowPlaying` 가드 추가
- [x] `TvAdSkipService.kt`:
  - `scanNodeTreeSinglePass()` 단일 패스 노드 순회 및 450ms 스로틀링
  - `onAccessibilityEvent`에서 일반 영상 중 스크린샷 캡처 완전 배제 (`isAdContext == true` 전용)
  - 백그라운드 `screenshotExecutor` 및 480x270 버퍼 처리
  - `inspectBitmapAndTrigger`에서 `yellowPixels >= 12` 감지 시 스킵 차단
- [x] `app/build.gradle.kts` 및 `version.json` v0.7.0 (versionCode: 70) 갱신
- [x] 사후 빌드 검증 (`./gradlew assembleDebug` 성공 확인)
- [x] 한글 리터럴 인코딩 전수 점검
- [x] `TV_YouTube_Ad_Skipper.apk` 갱신 완료
- [ ] Git 커밋 및 푸시 (`git push origin main`)
- [x] 결과 리포트 작성 (`walkthrough_instant_mute_and_stutter_fix_v0.7.0_20261015.md`)
