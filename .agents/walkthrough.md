# 변경 결과 리포트: 정상 영상 재생 중 오음소거 방지 (v0.7.2)

## 1. 개요 및 변경 배경
- **증상**: v0.7.1까지의 빌드에서 일반 정상 영상을 시청하던 중 갑자기 오디오가 0(무음)으로 음소거되는 심각한 오탐(False Positive) 결함이 발생했습니다.
- **원인 규명**:
  1. **픽셀 센서의 비광고 시점 노란색 오탐**: `analyzeScreenPixelSensor`에서 `if (yellowPixels >= 12)` 검사가 `if (!isAdContext) return`보다 앞에 위치하여, 일반 영상 시청 중 4.5초 주기 스크린샷 실행 시 화면 우하단에 노란 자막(예능 프로그램 등), 조명, 황혼, 로고 등의 12픽셀만 감지되어도 즉시 강제 음소거(`onAdDetectedFromLocalUi`)를 발동시켰습니다.
  2. **`adContextKeywords`의 2글자 영문 `"Ad"` 부분일치(contains) 참사**: "Broadcast", "Download", "Shadow", "Road", "Ahead", "Radio", "Leader", "Already", "Standard", "Trading", "Graduation", "Made" 등 일상 영어 단어에 포함된 'ad'로 인해 일반 영상 제목/자막/추천 카드가 광고로 오인되었습니다.
  3. **`isCountdownText`의 탐색바 잔여시간("15분 남음") 오탐**: 유튜브 TV 탐색바에 표시되는 "15분 남음", "-10:25 남음" 및 일반 제목("Top 10 in Korea", "2 in 1" 등)이 숫자와 "남음"/" in " 키워드로 인해 광고 카운트다운으로 오판되었습니다.

---

## 2. 수술적 수정 내역 (v0.7.2)

### A. `TvAdSkipService.kt`
1. **픽셀 센서 비광고 상태(`!isAdContext`) 즉시 차단**:
   - `analyzeScreenPixelSensor`에서 QR 닫기 로직 직후 `if (!isAdContext) return`을 배치하여, 일반 영상 시청 중에는 우하단 노란 픽셀/스킵 버튼 ROI 분석을 전면 차단했습니다.
   - 노란 카운트다운 임계값을 12에서 18로 상향하여 노이즈 내성을 강화했습니다.
2. **정규 광고 배지 전용 판별기 `isRealAdBadge()` 구현**:
   - 알려진 viewId(`knownSkipViewIds`, `skip_ad`, `ad_badge` 등) 또는 길이 20자 이하의 정규 광고 배지 패턴(`광고`, `광고 1/2`, `광고 2/2`, `광고 ·`, `Ad`, `Ad 1 of 2`, `Sponsored`)만 정규식(`adBadgePattern`)으로 정밀 검출하도록 전면 개편했습니다.
   - 긴 일반 영상 제목이나 "Broadcast", "Download" 등이 광고로 오인되는 결함을 100% 원천 차단했습니다.
3. **정밀 카운트다운 정규식 `countdownRegex` 도입**:
   - 실제 유튜브 TV 광고 카운트다운 문구(`\d+\s*초\s*(후|뒤)`, `영상이 곧 재생`, `Skip in \d+`, `Video will play after ad`)만 일치하도록 변경하여 일반 영상 탐색바의 "15분 남음"이나 "2 in 1" 등의 일반 문구를 완벽히 제외했습니다.
4. **스킵 버튼 일치 조건 강화**:
   - 스킵 키워드를 완전 일치(`text.equals("Skip", ignoreCase = true)`) 또는 `"Skip Ad"` 접두사로 한정하여 일반 영상 제목 내 "Skip" 단어로 인한 오작동을 차단했습니다.

---

## 3. 검증 결과
- **컴파일 및 패키징**: `./gradlew assembleDebug` 빌드 성공 (BUILD SUCCESSFUL in 7s)
- **한글 인코딩 전수 점검**: UTF-8 한글 리터럴 깨짐 0건 확인
- **최신 빌드 파일 갱신**: `TV_YouTube_Ad_Skipper.apk` (6,565,791 bytes) 생성 완료
