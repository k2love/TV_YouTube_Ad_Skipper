# 구현 계획서: 정상 영상 재생 중 오음소거 방지 (v0.7.2)

정상 영상이 재생되는 도중에 갑자기 소리가 꺼지고 음소거(0)되는 치명적인 오탐(False-Positive) 결함을 수술적으로 해결합니다.

## 1. 문제 원인 분석

1. **픽셀 센서의 비광고 상태(`!isAdContext`) 노란색 오탐 및 음소거 트리거**:
   - `analyzeScreenPixelSensor`에서 `if (yellowPixels >= 12)` 검사가 `if (!isAdContext) return`보다 **앞에 위치**해 있었습니다.
   - 일반 영상 시청 중에도 4.5초 주기로 스크린샷이 실행되는데, 영상 우하단에 노란색 자막(예능 프로그램 등), 조명, 황혼 배경, 노란 로고 등이 12픽셀(3x4px 수준의 극소 영역)만 감지되어도 `onAdDetectedFromLocalUi(isCountdownActive = true)`가 호출되어 정상 영상을 즉시 음소거시켰습니다.
2. **`adContextKeywords`의 2글자 영문 `"Ad"` 부분일치(contains) 참사**:
   - `adContextKeywords = listOf("광고", "Ad", ...)`에서 `combined.contains("Ad", ignoreCase = true)`를 사용하여 노드를 순회했습니다.
   - 결과적으로 "Downlo**ad**", "Bro**ad**cast", "Sh**ad**ow", "Ro**ad**", "Ahe**ad**", "R**ad**io", "Le**ad**er", "Alre**ad**y", "St**ad**ium", "Tr**ad**e", "Gr**ad**uation", "Lo**ad**ing", "M**ad**e" 등 'ad'가 들어간 수많은 일반 영어 단어가 포함된 영상 제목/자막/추천 카드 전체를 광고 UI로 오인하여 `onAdDetectedFromLocalUi`를 발동시켰습니다.
3. **`isCountdownText`의 `"남음"`, `" in "`, `"sec"` 오탐**:
   - 유튜브 TV의 일반 영상 재생 중 탐색바/컨트롤러에 표시되는 재생 잔여 시간(예: "15분 남음", "-10:25 남음")이 `"남음"` 키워드와 숫자 조건을 만족하여 카운트다운 진행 중인 광고로 오판되었습니다.
   - "Top 10 in Korea", "2 in 1" 등 일반 영상 제목도 카운트다운으로 오탐되었습니다.

---

## 2. 수술적 수정 계획

### A. `TvAdSkipService.kt`
1. **픽셀 센서 비광고 상태 조기 종료**:
   - `analyzeScreenPixelSensor`에서 QR 닫기 검사 직후, `if (!isAdContext) return`을 즉시 실행하여 일반 영상 시청 중에는 우하단 픽셀(노란 카운터/스킵 버튼)을 일체 검사하지 않도록 격리.
   - 노란 카운트다운 픽셀 임계값을 12에서 18로 상향하여 노이즈 내성 강화.
2. **정규 광고 배지 전용 판별기 `isRealAdBadge` 도입**:
   - 알려진 viewId(`knownSkipViewIds`, `skip_ad`, `ad_badge` 등) 또는 길이 20자 이하의 정규 광고 배지 패턴(`광고`, `광고 1/2`, `광고 2/2`, `광고 ·`, `Ad`, `Ad 1 of 2`, `Sponsored`)만 정밀 검출.
   - 긴 영상 제목이나 "Broadcast", "Download" 등의 일반 단어 포함 문장은 100% 배제.
3. **정밀 카운트다운 정규식 `countdownRegex` 도입**:
   - `(\d+\s*초\s*(후|뒤)|영상이\s*곧\s*재생|동영상이\s*곧\s*재생|원본\s*영상이\s*곧|[Ss]kip\s+in\s+\d+|[Vv]ideo\s+will\s+play\s+after\s+ad)` 정규식을 통해 "5초 후 광고 건너뛰기" 등 실제 광고 카운트다운만 검출하고 일반 영상의 "15분 남음" 등은 완벽히 제외.
4. **스킵 키워드 일치 조건 강화**:
   - "Skip" 단어 포함 대신 완전 일치(`text.equals("Skip", ignoreCase = true)`) 또는 `"Skip Ad"`로 시작하는 경우만 스킵 버튼으로 인정.

### B. 버전 갱신 및 배포
- `app/build.gradle.kts`: `versionCode = 72`, `versionName = "0.7.2"`
- `version.json`: `version 0.7.2` 메타데이터 및 변경 내역 갱신
- Gradle 빌드 (`./gradlew assembleDebug`), 한글 인코딩 무결성 검증
- APK 갱신, GitHub 푸시 및 E: 드라이브 동기화

---

## 3. 검증 기준
1. 정상 영상 시청 중(노란색 자막, 영상 내 황혼/조명, "15분 남음" 탐색바 표시 시) 음소거가 절대 발동하지 않는지 확인.
2. 영어 단어(Broadcast, Download, Shadow 등)가 포함된 영상 재생 시 오음소거가 발생하지 않는지 확인.
3. 실제 광고("광고 1/2", "5초 후 건너뛰기", 스킵 버튼 노드) 발생 시에는 기존과 같이 즉시 0ms 음소거가 정상 작동하는지 확인.
4. Gradle 빌드 성공 (BUILD SUCCESSFUL).
