# 안드로이드 TV용 유튜브 광고 건너뛰기 앱 개발 계획서

## 1. 개요
PDF 문서("TV 유튜브 광고 차단 및 건너뛰기 방법 - Google Gemini.pdf")에 기술된 설계에 따라, 안드로이드 TV / Google TV 환경에서 공식 유튜브 앱(`com.google.android.youtube.tv`) 시청 중 광고 건너뛰기 버튼이 활성화되었을 때 자동으로 감지하여 클릭해 주는 백그라운드 접근성 서비스(Accessibility Service) 애플리케이션을 구현합니다.

## 2. 주요 요구사항 및 아키텍처
### 2.1 핵심 접근성 서비스 (`TvAdSkipService`)
- 대상 패키지 감시: `com.google.android.youtube.tv` (TV용), `com.google.android.youtube` (모바일/범용 대비)
- 스킵 키워드 탐색: `건너뛰기`, `Skip`, `Skip Ad`, `광고 건너뛰기`, `건너뛰기까지` 등
- 다단계 클릭 매커니즘:
  1. `AccessibilityNodeInfo.performAction(ACTION_CLICK)`: 노드 및 상위 부모 노드 순회 클릭
  2. 제스처 클릭 주입: 노드 영역의 중심 좌표(`Rect.centerX()`, `centerY()`)에 `dispatchGesture` 가상 클릭
  3. 반복 클릭 방지 쿨다운 타이머(약 1.5~2초) 적용
- 화면 해상도 감지 및 안전 장치

### 2.2 TV 친화적 UI (`MainActivity`)
- Android TV Leanback 대응 런처 배너 및 포커스 친화적 UI
- 접근성 권한 활성화 여부 실시간 상태 표시
- 원클릭 시스템 접근성 설정 화면 호출 버튼 (`Settings.ACTION_ACCESSIBILITY_SETTINGS`)
- Android TV(Google TV 12/13+)의 제한된 설정(Restricted Settings) 우회를 위한 ADB 커맨드 복사/안내 가이드 제공
- 최근 스킵 감지 테스트 및 설정 옵션 (쿨다운 시간, 타깃 키워드 등)

### 2.3 프로젝트 빌드 및 설정 구성
- Android Gradle Plugin 8.5+, Kotlin 1.9+, Gradle 8.13 (또는 8.5)
- Compile SDK 34, Min SDK 24 (Android 7.0 Nougat ~ Android 14 Google TV 호환)
- `AndroidManifest.xml`:
  - `android.software.leanback` (required=false)
  - `android.hardware.touchscreen` (required=false)
  - `BIND_ACCESSIBILITY_SERVICE` 서비스 등록
- `res/xml/accessibility_service_config.xml`:
  - `typeWindowStateChanged`, `typeWindowContentChanged`
  - `flagDefault`, `flagRetrieveInteractiveWindows`, `flagIncludeNotImportantViews`
  - `canRetrieveWindowContent="true"`, `canPerformGestures="true"`

## 3. 세부 구현 단계
1. **프로젝트 디렉토리 구조 및 Gradle 빌드 스크립트 작성**
   - `build.gradle.kts` (루트 및 app 모듈)
   - `settings.gradle.kts`
   - `gradle.properties`, `local.properties` (Android SDK 경로 지정)
   - Gradle wrapper 배치
2. **리소스 및 매니페스트 구성**
   - `accessibility_service_config.xml`
   - TV Leanback 배너 및 앱 아이콘, 색상/스타일/문자열 리소스 (한글 보존)
   - `AndroidManifest.xml`
3. **핵심 로직 구현**
   - `TvAdSkipService.kt`: 접근성 노드 분석, 부모 탐색 클릭, 제스처 클릭 주입
   - `MainActivity.kt`: 서비스 활성화 상태 모니터링, 접근성 설정 열기, ADB 명령어 안내 UI
4. **빌드 검증**
   - Gradle assembleDebug 빌드 수행 및 APK 생성 확인
   - 린트 및 인코딩 오류 검증
5. **완료 보고서 작성 (`walkthrough.md`)**
