# 📺 TV YouTube Ad Skipper (안드로이드TV용 유튜브 광고 스키퍼)

Android TV 환경에서 실행되는 공식 YouTube 앱(`com.google.android.youtube.tv`)을 감지하여 광고 자동 스킵, 광고 중 무음(Mute) 처리, 쇼핑/QR 광고 오버레이 자동 닫기, OTA 자동 업데이트를 지원하는 백그라운드 접근성 유틸리티입니다.

---

## 🌟 주요 기능 (Key Features)

1. **초고속 광고 건너뛰기 (Ultra-fast Ad Skip)**
   - 접근성 노드 트리 실시간 감시(`AccessibilityEvent`)를 통해 "건너뛰기", "Skip Ad" 버튼 노출 즉시 자동 클릭.
   - 접근성 노드 미노출 상황에 대비한 가상 제스처 클릭 및 좌표 타격 보조 파이프라인 탑재.

2. **광고 구간 자동 음소거 (Smart Audio Mute & Restore)**
   - 건너뛸 수 없는 광고 또는 건너뛰기 대기 시간 동안 오디오 스트림(`AudioManager.STREAM_MUSIC`)을 자동 음소거.
   - 광고 종료 시 이전 볼륨 레벨을 오차 없이 완벽 복원.

3. **좌측 하단 쇼핑/QR 오버레이 자동 닫기 (QR Card Dismissal)**
   - 영상 시청을 방해하는 좌측 하단 제휴/QR 상품 오버레이 실시간 감지.
   - 접근성 노드 텍스트 분석("스캔", "쇼핑", "구매하기 등")과 화면 ROI(X: 3%~22%, Y: 45%~85%) 픽셀 센티넬 루프 이중화.
   - 실측 정밀 좌표(X: 12.4%, Y: 89.3%) 가상 클릭을 통한 오버레이 즉시 닫기.

4. **1.2초 시각 센티넬 백그라운드 루프 (Visual Sentinel Loop)**
   - 영상 연속 재생 중 안드로이드 접근성 이벤트가 발생하지 않는 정체 현상을 극복하기 위한 주기적 폴링 루프.

5. **OTA (Over-The-Air) 자동 인앱 업데이트**
   - `version.json` 메타데이터 기반 최신 버전 감지.
   - 백그라운드 APK 다운로드 및 패키지 인스톨러 연동 지원.

6. **YouTube Lounge 프로토콜 지원 (듀얼 모드)**
   - 네트워크 Lounge 페어링을 통한 스마트TV 제어 프로토콜 탑재.

---

## 🏗️ 아키텍처 및 핵심 파일 구조

```
app/src/main/java/com/example/tvadskipper/
├── MainActivity.kt        # 권한 안내, 서비스 토글, OTA 업데이트 체크 UI
├── TvAdSkipService.kt     # 핵심 접근성 서비스 (광고 감지, 음소거, QR 오버레이 닫기, 제스처)
├── UpdateManager.kt       # version.json 파싱, APK 다운로드 및 무인/직접 설치 제어
├── AppLogger.kt           # 실시간 디버그 로그 기록 및 관리
├── LoungeManager.kt       # YouTube Lounge 프로토콜 네트워크 통신 관리
└── LoungeSkipService.kt   # Lounge 프로토콜 기반 보조 스킵 백그라운드 서비스
```

### 핵심 모듈 상세
- **`TvAdSkipService.kt`**:
  - `onAccessibilityEvent(event)`: 이벤트 발생 시 타깃 윈도우(`com.google.android.youtube.tv`) 검증 후 광고 요소 분석.
  - `checkAndSkipAd(rootNode)`: "광고 건너뛰기", "Skip", "닫기" 노드 검색 및 `ACTION_CLICK`.
  - `dismissQrShoppingOverlay(rootNode)`: 정밀 실측 좌표(0.124f, 0.893f) 제스처 타격을 통한 QR 오버레이 제거.
  - `triggerPeriodicVisualCheck()`: 1.2초 주기의 센티넬 코루틴 루프.

---

## 🛠️ 개발 및 빌드 환경

- **언어**: Kotlin (JDK 17)
- **프레임워크**: Android SDK (minSdk 24, targetSdk 34)
- **빌드 시스템**: Gradle (Kotlin DSL, `build.gradle.kts`)

### 빌드 명령어

```powershell
# 디버그 APK 빌드
./gradlew assembleDebug

# 빌드 결과물 위치
app/build/outputs/apk/debug/app-debug.apk
```

### TV 디바이스에 무선(ADB) 설치

```powershell
# 1. Android TV와 ADB 연결
adb connect <TV_IP_주소>:5555

# 2. APK 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 접근성 권한 활성화 (필요 시 ADB 명령으로 바로 부여)
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## 🤖 Gemini 웹(Google Gemini)에서 작업하는 팁

이 프로젝트를 GitHub에 올린 후 [Gemini 웹(gemini.google.com)](https://gemini.google.com) 또는 Google AI Studio에서 프로젝트를 이어 작업하실 때 유용한 가이드입니다:

1. **GitHub 저장소 연결 또는 URL 프롬프트 제공**:
   - 프롬프트에 이 레포지토리의 주소를 전달하고, 수정하고자 하는 목표를 설명합니다.
   - 예: `"https://github.com/<사용자명>/<레포명> 저장소의 app/src/main/java/com/example/tvadskipper/TvAdSkipService.kt 파일을 확인하고, 새로운 광고 버튼 레이아웃에 대응하는 로직을 제안해줘."`

2. **단일 파일 코드 업로드/붙여넣기**:
   - `TvAdSkipService.kt` 등 관련성이 높은 핵심 파일 1~2개만 프롬프트에 직접 첨부하거나 붙여넣어 구체적인 버그 해결/기능 확장을 요청하면 가장 정확한 답변을 얻을 수 있습니다.

3. **작업 히스토리 활용**:
   - `.agents/` 디렉토리에 지금까지 해결한 구현 계획서(`implementation_plan_*.md`)와 검증 보고서(`walkthrough_*.md`)가 모두 누적 관리되어 있습니다.
   - 제미나이 웹에서 과거 히스토리나 설계 의도를 파악할 때 `.agents/` 내 문서를 참고 자료로 제공할 수 있습니다.
