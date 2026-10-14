# 구현 계획서: 자동 업데이트 다운로드 후 즉시 설치 화면 전환 버그 픽스 (v0.6.3)

## 1. 문제 분석 및 버그 원인
사용자 보고:
> "앱을 가동했을 때, 자동으로 업데이트를 감지하고 앱을 다운로드 하면 설치까지 넘어가지 않아. 다시 아래 내려와서 업데이트 확인하고 다운로드 받아야 설치까지 넘어가."

- **원인 1: 다이얼로그 Dismiss와 `startActivity`의 윈도우 매니저 트랜잭션 충돌**
  - 다운로드가 100% 끝나자마자 `progressDialog.dismiss()`와 `context.startActivity(intent)`가 동기적으로 연속 호출되면서, 안드로이드 TV 시스템의 WindowManagerService가 창 제거 애니메이션 처리 중에 액티비티 전환 인텐트를 드롭(Drop/무시)함.
- **원인 2: 파일 저장 위치 및 리눅스 파일 퍼미션/권한 문제**
  - APK가 `context.cacheDir`에 저장되어 있었고, `apkFile.setReadable(true, false)` 및 패키지 인스톨러에 대한 명시적 `grantUriPermission`이 누락되어 있어 첫 호출 시 권한 문제로 패키지 인스톨러가 백그라운드에서 차단됨.
- **원인 3: 설치 화면 호출 실패 시 사용자 피드백 부재**
  - 첫 시도 실패 시 화면에 아무런 액션 버튼이 남지 않아 사용자가 아래로 스크롤하여 수동으로 다시 다운로드받아야만 했음.

---

## 2. 해결 방안 및 수술 설계

### 가. `UpdateManager.kt` 강화
1. **저장 경로 변경**:
   - `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir`
   - PackageInstaller가 가장 안정적으로 읽을 수 있는 표준 다운로드 경로 사용.
2. **퍼미션 및 URI 권한 명시 부여**:
   - `destFile.setReadable(true, false)`
   - `queryIntentActivities`를 통해 시스템 PackageInstaller 패키지에 `grantUriPermission` 명시 부여.
   - `intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)` 추가.

### 나. `MainActivity.kt` 설치 플로우 개선
1. **완료 피드백 및 딜레이 트랜잭션 안정화**:
   - 다운로드 완료 시 "다운로드 완료! 설치 화면 준비 중..." (100%) 표시.
   - 300ms 후 `progressDialog.dismiss()` 호출.
   - 200ms 후 `updateManager.installApk(apkFile)` 호출.
2. **'설치 준비 완료' 다이얼로그 백업 제공 (`showInstallReadyDialog`)**:
   - 다운로드가 끝나면 화면에 "📦 업데이트 설치 준비 완료" 다이얼로그를 띄워둠.
   - 시스템 설치 화면이 바로 뜨거나, 만약 시스템이 일시적으로 씹었더라도 화면 중앙의 `[설치 시작]` 버튼을 누르면 다운로드 없이 0.1초 만에 설치 화면이 즉시 열림!

---

## 3. 검증 계획
1. 소스 코드 수정 후 UTF-8 인코딩 검사
2. `./gradlew assembleDebug` 빌드 확인
3. 루트 `TV_YouTube_Ad_Skipper.apk` 갱신
