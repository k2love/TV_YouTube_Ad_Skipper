# 구현 계획서: 구글 드라이브 연동 인앱 자동/수동 OTA 업데이트 시스템

## 1. 개요
새로 빌드된 APK를 구글 드라이브에 업로드하면, TV의 앱이 이를 자동으로 감지하여 사용자에게 업데이트 의사를 묻고, 수동 버튼으로도 언제든 업데이트할 수 있는 인앱 OTA(Over-The-Air) 시스템을 구축합니다.

---

## 2. 세부 구현 아키텍처

### 2.1 메타데이터 규격 (`version.json`)
구글 드라이브에 APK와 함께 업로드할 JSON 규격:
```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "downloadUrl": "https://drive.google.com/file/d/GOOGLE_DRIVE_APK_FILE_ID/view?usp=sharing",
  "changelog": "강제 광고 볼륨 안정화 및 홈화면 오작동 완벽 차단"
}
```
- 구글 드라이브의 보기 링크(`file/d/ID/view`)를 직다운로드 링크(`https://drive.google.com/uc?export=download&id=ID`)로 자동 변환하는 파서 내장.

### 2.2 UpdateManager.kt 구현
- **버전 체크**: 현재 앱의 `versionCode`와 원격 `version.json`의 `versionCode` 비교.
- **자동 감지**: 앱 실행 시 백그라운드에서 조용히 체크 $\rightarrow$ 새 버전 발견 시 업데이트 의사 확인 다이얼로그 출력.
- **수동 업데이트**: 메인 화면의 [업데이트 확인] 버튼 클릭 시 즉시 체크 및 피드백.
- **다운로드 & 프로그레스 UI**: 파일 다운로드 진행률(%) 다이얼로그 표시.
- **설치 인텐트 발송**: 안드로이드 `FileProvider`를 통해 시스템 패키지 인스톨러 호출 (`REQUEST_INSTALL_PACKAGES`).

### 2.3 매니페스트 및 리소스 구성
- `AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`
  - `androidx.core.content.FileProvider` 등록
- `res/xml/file_paths.xml`: 캐시 및 다운로드 경로 프로바이더 설정.
- `activity_main.xml` & `MainActivity.kt`:
  - 상단/하단에 [업데이트 확인] 버튼 및 구글 드라이브 `version.json` URL 설정창/다이얼로그 제공.

---

## 3. 검증 계획
1. `./gradlew assembleDebug` 빌드 성공 확인.
2. `apksigner` v1 & v2 서명 검증 통과.
3. UTF-8 한글 무결성 검증.
4. 루트에 `version.json` 템플릿과 함께 배포 패키지 준비.
