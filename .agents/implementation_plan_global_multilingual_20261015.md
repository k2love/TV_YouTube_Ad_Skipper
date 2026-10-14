# 구현 계획서 - 글로벌 다국어 언어팩 지원 및 구글 드라이브 로그 전송 제거 (v0.8.0)

## 1. 개요 및 배경
- **배경**: TV 유튜브 광고 스커퍼 앱을 글로벌 오픈소스로 배포하고 각국 사용자들이 자국어로 앱을 직관적으로 이용할 수 있도록 다국어 지원을 확대합니다.
- **주요 목표**:
  1. **구글 드라이브 로그 전송 기능 제거**: 글로벌 배포 정책 및 개인정보/종속성 문제 해결을 위해 구글 앱스 스크립트 기반 드라이브 로그 업로드 기능 및 UI 버튼을 완전 삭제 (단, 문제 분석을 위한 로컬 로그 뷰어는 유지).
  2. **다국어 언어팩 전면 적용**:
     - 기본 언어(`res/values/strings.xml`)를 영어(English)로 변경.
     - 한국어(`values-ko`), 일본어(`values-ja`), 중국어 간체(`values-zh-rCN`), 번체(`values-zh-rTW`), 스페인어(`values-es`), 힌디어(`values-hi`), 포르투갈어(`values-pt`), 러시아어(`values-ru`), 프랑스어(`values-fr`), 독일어(`values-de`), 인도네시아어(`values-in`, `values-id`), 아랍어(`values-ar`), 베트남어(`values-vi`) 등 인구/사용량 상위 13개국 언어 지원.
  3. **`activity_main.xml` 하드코딩 텍스트 전수 리소스화**:
     - 기존 레이아웃에 직접 기재되어 있던 모든 한글 문자열을 `@string/...` 식별자로 교체하여 기기 언어 변경 시 자연스럽게 전환되도록 보장.
  4. **`TvAdSkipService.kt` 글로벌 광고 탐지 패턴 강화**:
     - UI뿐만 아니라 실제 유튜브 TV 앱의 각국 광고 배지(`광고`, `Ad`, `広告`, `广告`, `Anuncio`, `Werbung`, `Реклама`, `Iklan`, `विज्ञापन` 등), 카운트다운 텍스트, 스킵 버튼 키워드를 정규식에 반영하여 0ms 칼음소거 및 자동 스킵이 해외 언어 환경에서도 완벽 작동하도록 구현.

---

## 2. 변경 대상 파일 및 상세 내용

| 대상 파일 | 수정 내용 |
| :--- | :--- |
| `app/src/main/java/com/example/tvadskipper/AppLogger.kt` | `GOOGLE_APPS_SCRIPT_URL` 상수 및 `uploadLogsToGoogleDrive()` 메서드, 미사용 okhttp 임포트 제거 |
| `app/src/main/java/com/example/tvadskipper/MainActivity.kt` | `setupLogListeners()` 내 `btnShareLogs` 클릭 리스너 및 `uploadLogsToDrive()` 메서드 제거 |
| `app/src/main/res/layout/activity_main.xml` | `btnShareLogs` 버튼 뷰 제거, 하드코딩된 한글 문자열을 전부 `@string/...` 리소스 참조로 교체 |
| `app/src/main/res/values/strings.xml` | 기본 영문(English) 리소스 파일로 재구성 및 신규 스트링 키 전수 추가 |
| `app/src/main/res/values-ko/strings.xml` | 한국어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-ja/strings.xml` | 일본어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-zh-rCN/strings.xml` | 중국어 간체 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-zh-rTW/strings.xml` | 중국어 번체 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-es/strings.xml` | 스페인어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-hi/strings.xml` | 힌디어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-pt/strings.xml` | 포르투갈어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-ru/strings.xml` | 러시아어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-fr/strings.xml` | 프랑스어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-de/strings.xml` | 독일어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-in/strings.xml` & `values-id` | 인도네시아어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-ar/strings.xml` | 아랍어 번역 리소스 파일 신규 생성 |
| `app/src/main/res/values-vi/strings.xml` | 베트남어 번역 리소스 파일 신규 생성 |
| `app/src/main/java/com/example/tvadskipper/TvAdSkipService.kt` | 다국어 `adBadgePattern`, `countdownRegex`, `isSkipText`, `qrCloseKeywords` 매칭 패턴 확장 |
| `app/build.gradle.kts` | versionName "0.8.0", versionCode 80 상향 |
| `version.json` | 버전 0.8.0 및 글로벌 다국어 지원 릴리스 노트 갱신 |

---

## 3. 실행 단계 및 검증 계획
1. **1단계: 구글 드라이브 로그 전송 코드 제거**
   - `AppLogger.kt`, `MainActivity.kt`, `activity_main.xml`에서 구글 드라이브 관련 로직 완전 삭제.
2. **2단계: `activity_main.xml` 하드코딩 텍스트 추출 및 리소스화**
   - 레이아웃 파일 내 모든 한글 텍스트를 `@string/...`으로 치환.
3. **3단계: 기본 영문 `strings.xml` 및 각국 언어팩 작성**
   - 기본 `values/strings.xml`을 영문으로 작성하고 13개국 언어팩 디렉토리 및 파일 생성.
4. **4단계: `TvAdSkipService.kt`의 글로벌 광고 감지 패턴 확장**
   - 해외 유튜브 TV 앱의 실제 광고 및 스킵 문구 등록.
5. **5단계: 버전 갱신 및 Gradle 빌드 검증**
   - `./gradlew assembleDebug` 빌드 성공 확인.
6. **6단계: 인코딩 및 한글/다국어 리터럴 전수 체크**
   - 깨진 유니코드 문자가 없는지 전수 검증.
7. **7단계: 루트 APK 갱신, 결과 리포트 작성 및 Git 커밋/푸시, E: 드라이브 동기화**.
