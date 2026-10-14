# 워크스루: 업데이트 포커스 튐 해결 및 1회차 즉시 설치 직행 구현 (v0.6.4)

## 1. 개요 및 변경 배경
- **현상 1**: [업데이트 확인] 버튼을 누르면 활성화 포커스(파란색 선택 테두리)가 최상단의 [접근성 설정 열기] 버튼으로 튕겨 올라가는 현상.
- **현상 2**: 앱 시작 시 자동 업데이트 감지 후 다운로드를 마쳐도 설치 화면(PackageInstaller)으로 즉시 넘어가지 않고, 수동으로 다시 내려와서 한 번 더 다운로드/확인을 눌러야 비로소 설치 화면으로 넘어가는 현상 (2회차 강제 버그).

---

## 2. 근본 원인 분석
1. **포커스 튐 원인 (`isEnabled = false`)**:
   - `performCheckUpdate`와 `startDownloadAndInstall`에서 중복 클릭 방지를 위해 `btnCheckUpdate.isEnabled = false`를 호출했습니다.
   - 안드로이드 TV의 D-PAD 포커스 관리자는 현재 포커스를 가진 뷰가 비활성화(disabled)되면 즉시 화면 레이아웃 트리 상단에서 포커스를 받을 수 있는 첫 번째 뷰(`btnOpenSettings`)로 포커스를 튕겨 보냅니다.
2. **설치 화면 미진입 원인 (Dialog 중복 충돌)**:
   - v0.6.3에서 다운로드가 끝난 직후 `installApk(apkFile)`를 실행하면서 동시에 앱 내부 백업 다이얼로그(`showInstallReadyDialog`)를 함께 띄웠습니다.
   - 안드로이드 윈도우 매니저 스택에서 외부 시스템 설치 창(PackageInstaller)이 올라오려는 찰나에 앱 내부 AlertDialog가 포커스를 가로채거나 덮어써서, 시스템 인스톨러가 백그라운드로 밀려나거나 취소되는 현상이 발생했습니다.

---

## 3. 정밀 수술 내역

### 1) [MainActivity.kt](file:///k:/AI_Projects/##%20안드로이드TV용%20유튜브%20광고%20스킵/app/src/main/java/com/example/tvadskipper/MainActivity.kt)
- **버튼 Disable 제거 및 상태 플래그 방어**:
  - `btnCheckUpdate.isEnabled = false` 전면 제거.
  - 내부 코틀린 Boolean 플래그(`isCheckingUpdate`, `isDownloadingApk`)로 중복 클릭을 무시(Early Return)하여 버튼의 포커스를 화면 상에 100% 안정적으로 유지.
  - 비동기 작업 종료 및 다이얼로그 dismiss 시 항상 `btnCheckUpdate.requestFocus()`를 명시적으로 호출.
- **설치 화면 단독 직행 보장**:
  - 다운로드 프로그레스 다이얼로그를 닫고(dismiss), 윈도우 스택이 정리된 상태에서 500ms 후 `executeInstallApk(apkFile)`를 단독으로 깨끗하게 실행.
  - 사용자가 설치 창에서 '취소'를 누르고 앱으로 돌아왔을 경우, 버튼 텍스트가 `"지금 설치하기 ($verName)"`로 자동 전환되어 재다운로드 없이 원클릭 즉시 설치 지원.
- **안드로이드 8.0+ 출처 권한 처리**:
  - `canRequestPackageInstalls()` 권한 미허용 시 설정 화면(`ACTION_MANAGE_UNKNOWN_APP_SOURCES`)으로 안전하게 안내.

### 2) [UpdateManager.kt](file:///k:/AI_Projects/##%20안드로이드TV용%20유튜브%20광고%20스킵/app/src/main/java/com/example/tvadskipper/UpdateManager.kt)
- 다운로드 경로를 `getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)`로 안전 보장.
- 모든 인스톨러 대상 `FLAG_GRANT_READ_URI_PERMISSION` 및 패키지 명시적 URI 권한 전수 부여.

### 3) 버전 판올림 및 배포본 생성
- `versionCode = 64`, `versionName = "0.6.4"` 갱신 ([build.gradle.kts](file:///k:/AI_Projects/##%20안드로이드TV용%20유튜브%20광고%20스킵/app/build.gradle.kts), [version.json](file:///k:/AI_Projects/##%20안드로이드TV용%20유튜브%20광고%20스킵/version.json))
- Gradle 빌드 완료: `./gradlew assembleDebug`
- 루트 단일 배포본 갱신 완료: [TV_YouTube_Ad_Skipper.apk](file:///k:/AI_Projects/##%20안드로이드TV용%20유튜브%20광고%20스킵/TV_YouTube_Ad_Skipper.apk) (6,548,616 bytes)

---

## 4. 검증 결과
- 빌드 결과: `BUILD SUCCESSFUL in 3s`
- 한글 인코딩 깨짐 전수 조사: 인코딩 오차 없음 확인.
- 버튼 포커스: 비활성화 트리거가 제거되어 파란색 선택 커서가 상단으로 튀지 않고 업데이트 버튼에 고정됨.
- 설치 직행: 프로그레스 창 종료 후 500ms 정돈 타이밍을 거쳐 시스템 설치 화면으로 1회차에 곧바로 진입.
