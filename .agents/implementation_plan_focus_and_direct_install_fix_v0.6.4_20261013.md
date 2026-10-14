# 구현 계획서: 업데이트 포커스 튐 버그 및 원스텝 설치 직행 버그 픽스 (v0.6.4)

## 1. 사용자 피드백 및 정밀 원인 규명
1. **포커스 튐 현상 (파란색 활성화 테두리가 '접근성 설정 열기'로 이동)**:
   - `performCheckUpdate` 및 `startDownloadAndInstall`에서 중복 클릭 방지를 위해 `binding.btnCheckUpdate.isEnabled = false`를 실행함.
   - 안드로이드 TV 시스템은 현재 포커스 중인 뷰가 `disabled`되는 즉시 포커스를 잃고, 레이아웃의 첫 번째 포커스 가능 뷰인 `btnOpenSettings`로 포커스를 강제 이동시킴.
   - 나중에 `isEnabled = true`로 복구해도 포커스는 돌아오지 않고 상단에 머무름.
2. **첫 번째 업데이트 시 설치 화면 미진입 (2회차 강제 현상)**:
   - v0.6.3에서 `updateManager.installApk(apkFile)`를 실행한 바로 다음 줄에서 `showInstallReadyDialog(apkFile)`를 동시에 띄움.
   - 시스템 PackageInstaller가 화면을 열려는 찰나에 `MainActivity`가 새로운 다이얼로그를 띄워 포커스와 윈도우 스택을 가로채면서 시스템 설치 창이 백그라운드로 밀려나거나 취소됨.
   - 사용자가 한 번 더 조작해야만(2회차) 시스템 창이 떴던 원인이 됨.

---

## 2. 해결 수술 설계

### 가. 포커스 고정 (No-Disable 방침)
- `btnCheckUpdate.isEnabled = false` 전면 제거.
- 중복 클릭 방지는 내부 코틀린 플래그(`isCheckingUpdate`, `isDownloadingApk`)로 소프트웨어적 방어.
- 버튼을 누른 상태에서도 버튼의 `isEnabled`가 계속 `true`이므로, **파란색 포커스 테두리가 절대로 딴 곳(`btnOpenSettings`)으로 튀지 않고 해당 버튼에 고정됨**.
- 다이얼로그가 닫히거나 작업 완료 시 항상 `binding.btnCheckUpdate.requestFocus()`를 명시적으로 호출.

### 나. 원스텝 단독 설치 직행
- 다운로드 완료 시:
  1. `progressDialog.dismiss()` 호출.
  2. 동시 팝업(`showInstallReadyDialog`) 제거.
  3. 알 수 없는 앱 설치 권한(`canRequestPackageInstalls()`) 검사: 미허용 시 설정 화면으로 안내.
  4. 500ms 딜레이 후 `updateManager.installApk(apkFile)`를 **단독으로 깨끗하게 실행**.
- 취소 시 백업 지원:
  - `downloadedApkFile = apkFile` 변수에 보관.
  - 버튼 텍스트를 `지금 설치하기 (재다운로드 불필요)`로 변경하여, 언제든 버튼을 누르면 재다운로드 없이 즉시 `installApk`가 실행되도록 구현.

---

## 3. 검증 계획
1. 소스 코드 수정 후 UTF-8 인코딩 검사
2. `./gradlew assembleDebug` 빌드 확인
3. 루트 `TV_YouTube_Ad_Skipper.apk` 갱신
