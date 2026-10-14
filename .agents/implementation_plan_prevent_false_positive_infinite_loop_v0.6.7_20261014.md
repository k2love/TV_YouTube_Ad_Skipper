# 구현 계획서: 500회 오감지 무한 루프 차단 및 QR 격자 패턴 검증 (v0.6.7)

- **기능 슬러그**: `prevent_false_positive_infinite_loop_v0.6.7`
- **버전/날짜**: `v0.6.7 / 20261014`
- **문서 위치**: `.agents/implementation_plan_prevent_false_positive_infinite_loop_v0.6.7_20261014.md`

## 1. 개요 및 배경
v0.6.6에서 `accessibility_service_config.xml`에 `canTakeScreenshot="true"` 속성이 부여되면서 픽셀 센서가 활성화되었습니다. 그러나 실제 광고가 재생 중이지 않은 일반 영상 시청 중에도 우하단 픽셀 센서가 동작하여, 자막/채널 로고/밝은 배경 등을 스킵 버튼으로 오판하고 자체 토스트("광고 건너뛰기 자동 실행 (n회)")를 띄운 뒤, 이 토스트 노드를 다시 스킵 버튼으로 착각하여 클릭하는 **자기 증식 오감지 무한 루프(100회~500회)** 현상이 발생했습니다.

본 작업은 이 오감지 무한 루프를 근본적으로 차단하고, 좌측 하단 QR 광고 검출에 격자 교차 패턴(Transition) 검증을 추가하여 오탐률 0%를 달성하며, 버전을 v0.6.7로 갱신하여 GitHub에 무인 자동 업데이트 APK를 배포하는 것을 목표로 합니다.

## 2. 작업 원칙 및 제약사항
1. **수술적 정밀 최소 수정**: 오감지 발생 핵심 경로인 `TvAdSkipService.kt`의 픽셀 센서 판정 조건 및 노드 필터링, 쿨다운 로직만 정밀하게 수정합니다.
2. **사이드 이펙트 방지**: 정상적인 광고 상황(Lounge 신호 또는 광고 텍스트 노출 시)에서의 스킵 기능과 좌하단 QR 닫기는 정상 작동을 보장합니다.
3. **인코딩 검증**: 한글 문자열 깨짐 방지를 위해 UTF-8 인코딩을 철저히 검증합니다.
4. **빌드 및 배포**: 수정 전후 `./gradlew assembleDebug` 성공을 확인하고, 루트의 `TV_YouTube_Ad_Skipper.apk` 갱신 및 GitHub 푸시를 완료합니다.

## 3. 세부 수정 계획

### 1) `TvAdSkipService.kt`
- **우하단 픽셀 센서의 비(非)광고 상황 동작 원천 차단**:
  - `inspectBitmapAndTrigger`에서 `if (!isAdContext) return`을 적용하여 실제 광고 상황이 아닐 때는 우하단 스킵 버튼 판정을 아예 실행하지 않음.
- **자가 토스트 및 앱 패키지 노드 감지 무한 루프 차단**:
  - `packageName == "com.example.tvadskipper"` 무조건 이벤트/노드 무시.
  - 노드 텍스트에 "자동 실행", "회)", "완료", "닫기 완료" 등이 포함되어 있거나 텍스트 길이가 20자 이상인 경우 스킵 대상에서 배제.
  - 광고 스킵 쿨다운을 `clickCooldownMs = 4000L` (기존 800ms)로 대폭 강화하여 연타/루프 차단.
- **좌측 하단 QR 쇼핑 카드 흑백 격자 교차(Transition) 검증**:
  - 단순 픽셀 수(White/Dark) 외에 가로 스캔 라인에서 도트 격자 교차 횟수(`qrTransitions >= 20`)를 검증하여 일반 흰색 자막이나 배경의 오탐 방지.
- **비정상 누적 카운트 자동 리셋**:
  - `onServiceConnected()`에서 기존에 50회 이상 비정상 누적된 카운트를 0회로 자동 초기화.

### 2) `version.json` 및 `app/build.gradle.kts`
- `versionCode = 67`
- `versionName = "0.6.7"`
- 변경 로그: 500회 오감지 무한 루프 차단, QR 격자 패턴 검증, 비정상 카운트 자동 리셋.

### 3) APK 빌드 및 GitHub 푸시
- `./gradlew assembleDebug` 빌드
- `TV_YouTube_Ad_Skipper.apk` 루트 갱신
- GitHub `origin/main` 푸시 완료

## 4. 검증 기준
- [ ] `TvAdSkipService.kt` 수정 후 빌드 성공 (`./gradlew assembleDebug`)
- [ ] 일반 영상 시청 중 우하단 픽셀 센서가 오감지하지 않는지 로직 검증
- [ ] 토스트 문구 및 자가 패키지가 접근성 트리에서 무시되는지 검증
- [ ] GitHub 저장소에 `v0.6.7` 커밋 및 `TV_YouTube_Ad_Skipper.apk` 푸시 완료
