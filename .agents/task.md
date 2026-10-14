# 작업 보드: 정상 영상 재생 중 오음소거 방지 (v0.7.2)

- **상태**: 완료
- **목표**: 
  1. 일반 영상 시청 중 노란 자막/배경, "15분 남음" 탐색바, 일반 영단어("Download", "Broadcast" 등)로 인한 오음소거(False Positive) 원천 차단
  2. 실제 광고에 대한 0ms 칼음소거 및 종료 후 볼륨(10) 정상 복원 기능 완전 보존
  3. v0.7.2 빌드 및 GitHub 배포

## 작업 세부 항목
- [x] 오음소거 원인 분석 및 구현 계획서 작성 (`implementation_plan_prevent_normal_playback_false_mute_v0.7.2_20261015.md`)
- [x] `TvAdSkipService.kt`:
  - [x] `analyzeScreenPixelSensor`: `if (!isAdContext) return`을 노란 카운터 검사 앞으로 전진 배치하여 일반 영상 시 픽셀 센서 오음소거 차단
  - [x] `isRealAdBadge()` 구현: 알려진 ID 및 실제 정규 광고 배지(길이 20자 이하, "광고 1/2", "Ad 1 of 2" 등)만 정밀 검출
  - [x] `countdownRegex` 및 `isCountdownText()` 정밀화: "15분 남음", "Top 10 in Korea" 등 일반 영상 문구 오탐 방지
  - [x] `scanNodeTreeSinglePass`: `adContextKeywords` 광범위 부분일치 제거하고 `isRealAdBadge()` 및 `out.skipNode`로 정밀 대체
- [x] `app/build.gradle.kts` 및 `version.json` v0.7.2 (versionCode: 72) 상향
- [x] 사후 빌드 검증 (`./gradlew assembleDebug` 성공 확인)
- [x] 한글 리터럴 인코딩 전수 점검
- [x] `TV_YouTube_Ad_Skipper.apk` 갱신 완료
- [ ] Git 커밋 및 푸시 (`git push origin main`)
- [ ] E: 드라이브 프로젝트 미러 동기화
- [x] 결과 리포트 작성 (`walkthrough_prevent_normal_playback_false_mute_v0.7.2_20261015.md` 및 `walkthrough.md`)
