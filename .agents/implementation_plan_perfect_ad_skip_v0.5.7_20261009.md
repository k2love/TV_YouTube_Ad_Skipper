# 구현 계획서: 스마트TV 일반 광고 100% 칼스킵 보장 정밀 수술 (v0.5.7)

## 1. 개요 및 목적
사용자로부터 "강제 광고가 아닌 일반 '건너뛰기' 광고에서 건너뛰기 버튼을 누르지 못하고 지나치는 경우가 자주 발생한다"는 중대한 피드백이 보고되었습니다.
본 계획서는 안드로이드 TV 12 (Hisense SmartTV 4K) 및 YouTube on TV (TVHTML5/Cobalt) 환경에서 접근성 필터, 컴퓨터 비전 픽셀 센서, Lounge 클라우드 프로토콜의 병목과 결함을 수술적으로 완벽히 제거하여 0.1초 만에 100% 스킵되도록 하는 하이브리드 스킵 체계를 구현합니다.

---

## 2. 근본 원인 분석 (Root Causes)

1. **`TvAdSkipService.kt`의 `isCountdownText()` 오탐 버그**:
   - `countdownKeywords.any { ... } && text.any { it.isDigit() }` 구조로 인해, 유튜브가 흔히 표시하는 "광고 1/2 건너뛰기", "광고 1 of 2 건너뛰기" 등 숫자가 포함된 정상 스킵 버튼이 카운트다운 텍스트로 오인되어 `continue`되어 영구 누락됨.
   - 또한 `skipKeywords`에 등록된 `"광고 건너뛰기까지"` 문구가 카운트다운 노드와 불필요하게 매칭되어 노이즈 발생.
2. **픽셀 센서의 `yellowPixels > 10` 잠금 버그**:
   - 4K 해상도(3840x2160)의 우하단 ROI에서 11픽셀의 노란색(광고 영상 속 자막, 배경, 조명, 인물 등)만 감지되어도 `yellowPixels > 10`에 걸려 흰색 스킵 버튼이 화면에 떴음에도 클릭을 거부하고 무한 대기함.
   - 실제로는 흰색 버튼(`whitePixels > 60`)이 화면에 나타나면 노란색 유무와 무관하게 즉시 클릭해야 함.
3. **Cobalt 웹 컨테이너의 접근성 `ACTION_CLICK` 무시 결함**:
   - 스마트TV 유튜브 웹앱은 네이티브 안드로이드 `ACTION_CLICK`에 `true`를 반환하더라도 실제 DOM 클릭을 트리거하지 않는 경우가 많음.
   - 접근성 노드의 화면 좌표를 파악하여 화면 터치 제스처(`clickAtCoordinates`)와 접근성 포커스(`ACTION_ACCESSIBILITY_FOCUS`)를 동시에 병행 집행해야 함.
4. **Lounge 프로토콜 단일 전송 유실**:
   - `sendSkipAdCommand()`를 1회만 발송할 경우 TV의 네트워크 일시 지연 또는 포커스 상태에 따라 무시될 수 있음. 400ms 간격의 더블탭(2회 연속) 전송 필수.
5. **하이브리드(Lounge + 접근성) 양방향 협공 부재**:
   - Lounge에서 `isSkipEnabled == "true"`가 감지되었을 때 네트워크 명령만 보낼 것이 아니라 접근성 서비스의 화면 제스처 터치도 함께 발사해야 함.

---

## 3. 세부 수술 설계

### 가. `TvAdSkipService.kt` 수술
1. **정적 인스턴스 제공**:
   - `@Volatile var instance: TvAdSkipService? = null` 제공으로 서비스 간 상호 호출 지원.
2. **스킵 키워드 및 카운트다운 필터 정밀 재설계**:
   - `skipKeywords`에서 `"광고 건너뛰기까지"` 제거.
   - `isCountdownText()` 함수 재정의:
     - "초 후", "초 뒤", "초후", "초뒤", "남음", "seconds", "sec" 등의 진행형 카운트다운 단어가 명백히 존재하고 숫자가 함께 있는 경우만 `true`.
     - "건너뛰기"나 "Skip" 단독 텍스트 또는 "광고 건너뛰기"는 카운트다운 진행형 단어가 없으면 숫자가 있든 없든("광고 1/2 건너뛰기") 무조건 정상 스킵 버튼으로 판별.
3. **클릭 메커니즘 보강 (`tryPerformClick`)**:
   - 노드를 찾으면 포커스 부여(`ACTION_ACCESSIBILITY_FOCUS`) 및 `ACTION_CLICK` 시도.
   - 동시에 노드의 화면 좌표(`boundsInScreen`)가 유효하면 `clickAtCoordinates(centerX, centerY)` 터치 제스처를 반드시 병행 발사하여 Cobalt 웹뷰의 클릭 거부를 원천 차단.
4. **픽셀 센서 가드 역전 (`inspectBitmapAndTrigger`)**:
   - 흰색 버튼(`whitePixels > 60 && blackPixels > 6`)이 발견되면 노란색 픽셀 수와 무관하게 **최우선 즉시 클릭**.
   - `yellowPixels > 15`는 흰색 버튼이 감지되지 않았을 때만 카운트다운 진행 중으로 간주하는 보조 가드로 격리.
5. **외부 직접 스킵 트리거 (`triggerDirectScreenSkip`) 탑재**:
   - Lounge 등 외부에서 스킵 가능 신호를 수신했을 때, 화면 우하단 고정 좌표(스킵 버튼 위치: 89.5%, 89.5%)로 0.1초 만에 물리적 제스처 클릭을 쏴주는 함수 제공.
6. **쿨다운 최적화**:
   - `clickCooldownMs`를 1800ms에서 800ms로 단축하여 연속 광고나 빠른 스킵에 즉각 대응.

### 나. `LoungeManager.kt` 수술
1. **`sendSkipAdCommand()` 더블탭 발송**:
   - 1차 `sendCommand("skipAd")` 발송 후 400ms 뒤 2차 `sendCommand("skipAd")` 발송으로 수신율 100% 보장.
2. **하이브리드 동시 타격**:
   - `isSkipEnabled.equals("true")` 감지 즉시 `sendSkipAdCommand()` 발송과 동시에 `TvAdSkipService.instance?.triggerDirectScreenSkip()` 호출.

### 다. 버전 및 산출물
- `app/build.gradle.kts`: `versionCode = 57`, `versionName = "0.5.7"`
- `version.json`: `versionCode: 57`, `versionName: "0.5.7"`, 변경 내역 한국어 작성.
- 빌드 후 루트 `TV_YouTube_Ad_Skipper.apk` 갱신.

---

## 4. 검증 계획
1. 소스 코드 수정 후 빌드 성공 (`./gradlew assembleDebug`)
2. 수정 파일 내 한글 리터럴 깨짐(`\ufffd` 등) 전수 점검
3. 루트 APK 파일 크기 및 생성 일시 확인
