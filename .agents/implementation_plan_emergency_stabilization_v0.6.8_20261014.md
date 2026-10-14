# 긴급 안정화 및 초미니 오버레이 배지 개편 계획서 (v0.6.8)

- **기능 슬러그**: `emergency_stabilization_and_mini_badge_v0.6.8`
- **버전/날짜**: `v0.6.8 / 20261014`
- **문서 위치**: `.agents/implementation_plan_emergency_stabilization_v0.6.8_20261014.md`

## 1. 개요 및 목적
1. **Google TV 시스템 불안정 및 유튜브 로그인 풀림 원인 완벽 제거**:
   - 1.2초마다 `startService`를 난사하던 `ensureLoungeServiceAlive()` 루프 제거.
   - 유튜브 TV 이외의 화면(런처, 시스템UI, 타 앱)에서의 백그라운드 스크린샷 및 노드 탐색 전면 차단.
   - 계정/로그아웃 팝업을 무단 승인할 위험이 있는 `checkAndDismissShortsDialog`의 광범위 클릭("확인", "예", "OK") 전면 차단.
   - 리모컨 포커스를 강탈하여 계정 메뉴로 튀게 만들던 `ACTION_FOCUS` / `ACTION_SELECT` 강제 주입 제거.
2. **알림 UI 전면 혁신 (사용자 요구사항 반영)**:
   - 화면 하단 중앙을 가리고 접근성 트리에 잡히던 거대한 시스템 기본 `Toast` 전면 폐지.
   - **우측 상단(Top-Right)**에 기존 토스트의 **1/4 이하 초미니 반투명 칩 배지(Mini Pill Badge)**로 교체.
   - `importantForAccessibility = NO`로 설정하여 자기 자신 감지 무한 루프 물리적 원천 차단.

## 2. 세부 수술 항목

### 1) 시스템 부하 및 포커스 교란 제거 (`TvAdSkipService.kt`)
- `triggerPeriodicVisualCheck()`에서 `ensureLoungeServiceAlive()` 매초 호출 제거.
- `if (activePkg != "com.google.android.youtube.tv" && activePkg != "com.google.android.youtube") return`으로 유튜브 TV 전용 격리.
- `tryPerformClick`에서 `ACTION_FOCUS`, `ACTION_SELECT` 제거, 노드 클릭 성공 시 불필요한 가상 터치 제스처 발사 금지.
- `checkAndDismissShortsDialog`의 포괄적 "확인" 클릭 제거 및 기본 비활성화.
- `takeScreenshot`은 오직 광고 컨텍스트(`isAdContext == true`)일 때만 실행.

### 2) 초미니 알림 배지 (Mini Pill Badge) 구현
- `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`를 활용한 미니 배지 뷰:
  - 위치: 화면 **우측 상단 (Top: 24dp, Right: 32dp)**
  - 크기: 기존 토스트의 1/4 수준 (글자 크기 11sp, 높이 26dp, 좌우 패딩 10dp)
  - 디자인: 반투명 다크 배경 (`#CC1A1A1A`), 둥근 모서리, 흰색 텍스트
  - 문구: `"⚡ 스킵 완료"`, `"🎯 QR 닫기"`
  - 접근성 무시: `importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO`
  - 1.2초 후 자동 소멸.

### 3) 버전 갱신 및 배포
- `app/build.gradle.kts` 및 `version.json`을 `v0.6.8` (versionCode: 68)로 갱신.
- 빌드 검증 및 `TV_YouTube_Ad_Skipper.apk` 갱신 후 GitHub 푸시.
