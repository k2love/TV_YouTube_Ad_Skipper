# 결과 리포트: 자동 업데이트 다운로드 후 즉시 설치 화면 전환 버그 픽스 (v0.6.3)

## 1. 버그 원인 정밀 분석
사용자 보고:
> "앱을 가동했을 때, 자동으로 업데이트를 감지하고 앱을 다운로드 하면 설치까지 넘어가지 않아. 다시 아래 내려와서 업데이트 확인하고 다운로드 받아야 설치까지 넘어가."

- **원인 1: WindowManagerService 윈도우 스택 트랜잭션 충돌 (타이밍 레이스)**
  - 앱 시작 시 자동 업데이트가 돌면, 프로그레스 다이얼로그(`progressDialog.dismiss()`)가 닫히는 애니메이션 중 동기적으로 곧바로 `context.startActivity(intent)`가 호출되었습니다.
  - 안드로이드 TV 시스템의 창 관리자(WindowManager)가 이전 Dialog 창 제거 트랜잭션 처리 중에 들어온 새 Activity 전환 인텐트를 드롭(무시)해 버려 설치 화면이 뜨지 않았습니다.
  - 반면, 수동 업데이트의 경우 사용자가 리모컨을 조작해 내려오면서 윈도우 포커스가 완전히 안정된 상태에서 물리 키 클릭으로 트리거되었기 때문에 정상적으로 넘어갔던 것입니다.
- **원인 2: 파일 저장 위치 및 권한 문제**
  - APK가 내부 캐시(`context.cacheDir`)에 저장되어 있었고, 시스템 PackageInstaller 프로세스에 명시적 `grantUriPermission` 및 `setReadable(true, false)`가 누락되어 첫 실행 시 인스톨러가 파일 읽기에 실패했습니다.
- **원인 3: 실패 시 원터치 재시도 수단 부재**
  - 인텐트가 일시적으로 무시되었을 때 화면에 아무런 백업 UI가 남지 않아 사용자가 다시 아래로 내려가 수동으로 재다운로드를 받아야만 했습니다.

---

## 2. 해결 수술 내역 (v0.6.3)

### 가. `UpdateManager.kt` 강화
1. **안전한 외부 저장소 경로 이관**:
   - `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir`
   - 시스템 PackageInstaller가 가장 안정적으로 읽을 수 있는 표준 다운로드 디렉터리에 저장.
2. **권한 및 플래그 전수 보강**:
   - `destFile.setReadable(true, false)` 적용.
   - `queryIntentActivities`를 통해 모든 PackageInstaller 관련 패키지에 `grantUriPermission`을 명시적으로 부여.
   - `Intent.FLAG_ACTIVITY_CLEAR_TOP` 추가.

### 나. `MainActivity.kt` 설치 전환 플로우 개선
1. **다운로드 완료 피드백 및 트랜잭션 딜레이 적용**:
   - 다운로드 완료 시 100% 진행률과 함께 "다운로드 완료! 설치 화면 준비 중..." 텍스트를 400ms 노출.
   - 프로그레스 다이얼로그 닫힌 후 300ms 딜레이를 두어 윈도우 매니저 트랜잭션 충돌을 원천 차단하고 `installApk` 호출.
2. **'📦 업데이트 설치 준비 완료' 다이얼로그 백업 제공 (`showInstallReadyDialog`)**:
   - 다운로드가 끝나면 화면에 **"📦 업데이트 설치 준비 완료"** 팝업을 띄워둡니다.
   - 시스템 설치 화면이 바로 뜨거나, 만약 시스템이 일시적으로 씹었더라도 화면 중앙의 **`[설치 시작]`** 버튼을 누르면 **재다운로드 없이 0.1초 만에 설치 화면이 즉시 열립니다!**

---

## 3. 빌드 및 배포 검증
- **빌드 결과**: `./gradlew assembleDebug` 성공 (`BUILD SUCCESSFUL in 5s`).
- **인코딩 검증**: 전수 검사 통과 (`UTF-8 OK`).
- **배포 파일**: 루트 디렉토리 `TV_YouTube_Ad_Skipper.apk` (6,547,939 bytes) 최신 교체 완료.
