# 구현 계획서: 동영상 뒤로가기 시 채팅 연결 및 검정 화면 버그 원천 해결 (v0.5.8)

## 1. 개요 및 목적
사용자로부터 "영상을 클릭하고 잠시 보다가 뒤로가기를 하면, 채팅에 연결한다고 하면서 검정화면으로 바뀌고 메인 격자(홈 화면)로 나가지 못한다"는 피드백이 접수되었습니다.
런타임 로그와 아키텍처 분석을 통해 규명된 세 가지 원인을 수술하여, 영상 뒤로가기 시 100% 정상적으로 메인 격자로 빠져나오도록 수정합니다.

---

## 2. 근본 원인 분석 (Root Causes)

1. **[원인 1] 픽셀 센서(`analyzeScreenPixelSensor`)의 광고 상태 검증 부재 (가장 유력)**:
   - 기존 코드는 접근성 이벤트가 발생할 때 스킵 노드가 없으면, **광고 상황인지 아닌지 따지지 않고 무조건 스크린샷을 찍어 우하단 ROI를 검사**했습니다.
   - 라이브 스트리밍이나 일반 영상을 보다가 뒤로가기를 누르면 플레이어 컨트롤 UI가 뜨는데, 이때 **우하단 영역에 [실시간 채팅] 버튼(흰색 아이콘)**이 위치합니다.
   - 픽셀 센서가 이 채팅 버튼의 흰색 픽셀(`whitePixels > 60`)을 "스킵 버튼"으로 오인하여 콕 클릭(`clickAtCoordinates`)해버렸고, 이로 인해 유튜브 TV가 **"채팅에 연결하는 중..."** 화면으로 진입한 뒤 TV에서 채팅 조작이 불가능해 검은 화면으로 굳어버린 것입니다.
2. **[원인 2] `checkAndDismissShortsDialog`의 "확인" 버튼 오클릭 위험**:
   - `dismissButtonKeywords`에 `"확인"`, `"예"`, `"OK"`가 포함되어 있어, 화면에 "채팅에 연결" 팝업이 떴을 때 이를 쇼츠 해제 팝업으로 오탐하여 "확인"을 자동으로 눌러버릴 수 있었습니다.
3. **[원인 3] Lounge 프로토콜의 `que`(Queue, 대기열) capability 선언**:
   - 우리 앱이 구글 Lounge 서버에 세션을 연결할 때 `capabilities`에 `"que,dsdtr,atp,vsp"`를 전송했습니다.
   - `que`가 포함되어 있으면 유튜브 TV는 "스마트폰 원격 기기가 재생 대기열(Queue)을 제어하고 있다"고 인식합니다.
   - 따라서 리모컨으로 뒤로가기를 눌렀을 때 홈 메인 격자로 나가지 않고, 원격 기기의 다음 큐 대기 화면(검은 배경)이나 "모바일 기기에서 채팅 연결" 대기 화면으로 빠지게 됩니다 (iSponsorBlockTV 등 주요 오픈소스에서도 동일한 이유로 `que`를 제거함).

---

## 3. 정밀 수술 계획

### 가. `TvAdSkipService.kt` 수술
1. **픽셀 센서 & Fallback 클릭에 "광고 컨텍스트 가드(Ad Context Guard)" 적용**:
   - 픽셀 센서(`analyzeScreenPixelSensor`)와 `performCobaltFallbackClick`은 오직 **실제 광고가 재생 중일 때만** 실행되도록 제한:
     - `LoungeManager.getInstance(this).isAdPlaying`이 `true`이거나,
     - 화면 노드 트리에 `"광고"`, `"Ad"`, `"Sponsored"`, `"광고 정보"` 등의 광고 식별자가 명백히 존재할 때만 실행!
     - 일반 동영상 재생 중, 뒤로가기 시, 홈 화면에서는 픽셀 센서 및 좌표 클릭을 **100% 완전 차단**.
   - 또한 화면 노드에 `"채팅"`, `"Chat"`, `"실시간 채팅"` 텍스트가 감지되면 어떠한 클릭도 하지 않도록 보호.
2. **`checkAndDismissShortsDialog` 가드 보강**:
   - 화면에 `"채팅"`, `"Chat"` 단어가 보이면 즉시 `return false`로 종료하여 채팅 관련 팝업을 절대 건드리지 않음.

### 나. `LoungeManager.kt` 수술
1. **`capabilities`에서 `que` 제거**:
   - `connectSession()` 시 `"capabilities", "dsdtr,atp,vsp"`로 전송하여 유튜브 TV가 강제 Queue 모드 및 모바일 채팅 연동 모드로 빠지는 것을 방지.

### 다. 버전 및 산출물 갱신
- `app/build.gradle.kts`: `versionCode = 58`, `versionName = "0.5.8"`
- `version.json`: `versionCode: 58`, `versionName: "0.5.8"`
- 빌드 후 루트 `TV_YouTube_Ad_Skipper.apk` 갱신.

---

## 4. 검증 계획
1. 소스 코드 수정 후 빌드 성공 (`./gradlew assembleDebug`)
2. `check_encoding.py`로 UTF-8 인코딩 무결성 검증
3. 루트 APK 파일 크기 및 일시 확인
