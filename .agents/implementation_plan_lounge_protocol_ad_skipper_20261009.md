# 유튜브 TV Lounge 프로토콜 기반 광고 건너뛰기 엔진 구현 계획서

## 1. 개요
공식 안드로이드 TV 유튜브 앱(`com.google.android.youtube.tv`)의 맞춤 추천 알고리즘을 100% 온전히 보존하면서, Cobalt 웹 캔버스 제약을 극복하기 위해 구글 공식 유튜브의 **'TV 코드로 연결(Lounge Protocol)'**을 안드로이드 TV 앱 내부 백그라운드 서비스(`LoungeSkipService`)로 직접 구현합니다.

## 2. 주요 아키텍처 및 통신 설계

### 2.1 Lounge 프로토콜 엔진 (`LoungeManager.kt`)
1. **페어링 (`pairing/get_screen`)**:
   - 사용자가 TV 유튜브 설정 > 'TV 코드로 연결'에 표시된 12자리 페어링 코드를 입력하면 구글 Lounge 서버와 핸드셰이크하여 고유 `screenId`와 `loungeToken`을 발급받아 SharedPreferences에 영구 저장.
2. **세션 바인딩 및 이벤트 수신 (`bc/bind`)**:
   - 구글 서버와 Long-polling 세션을 수립하여 실시간 재생 상태 이벤트 수신.
   - `onAdStateChange` 이벤트 감시:
     - `isSkipEnabled == "true"` 수신 즉시 반응.
3. **스킵 명령 디스패치**:
   - `skipAd` 커맨드 (`req0__sc=skipAd`) 및 보조 리모컨 명령(`dpadCommand: ENTER`) 전송.
4. **자동 재연결 및 와치독**:
   - TV가 켜지거나 네트워크가 변경되었을 때 자동 복구.

### 2.2 TV UI 구성 (`MainActivity`)
- TV 코드로 연결 입력창 (12자리 코드 입력 및 D-Pad 지원 포커스 UI)
- 페어링 상태 카드:
  - 연결됨 (초록색) / 미연결 (주황색)
  - 페어링된 기기 이름(예: "YouTube on TV") 표시
- 스킵 모드 하이브리드 지원:
  - Lounge 프로토콜 모드 (권장: 100% 무오류 네트워크 방식)
  - 접근성 시각 제스처 모드 (보조 로컬 모드)
- 최근 수신된 Lounge 이벤트 및 스킵 성공 로그 표시

## 3. 구현 단계
1. `app/build.gradle.kts`: OkHttp 4.12 및 Coroutines 추가
2. `LoungeManager.kt`: 페어링, 세션 바인딩, 이벤트 파싱, `skipAd` 전송 로직 구현
3. `LoungeSkipService.kt`: 백그라운드 상시 유지 서비스 구현
4. UI 갱신: 페어링 코드 입력 다이얼로그/카드, 상태 표시
5. 빌드 및 APK 생성 검증
