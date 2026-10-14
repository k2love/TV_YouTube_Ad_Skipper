# Project Harness Rules: TV YouTube Ad Skipper

## 🌐 Language & Localization Priority (언어 및 로컬라이제이션 절대 우선순위)
이 프로젝트의 모든 문서, 소스 코드 리소스, UI 및 의사결정의 언어 우선순위는 다음과 같으며, **반드시 한국어가 최우선이자 시스템 기본값(Default Fallback)**으로 설정되어야 합니다.

### [언어 우선순위 계층]
1. **한국어 (Korean, ko)**: **최우선 (Primary & System Default)**
   - 앱 기본 리소스(`app/src/main/res/values/strings.xml`)는 반드시 한국어로 유지.
   - 프로젝트 메인 문서(`README.md`), 작업 보고서, 계획서 등 모든 산출물은 한국어를 기본이자 표준으로 작성.
2. **영어 (English, en)**: 보조 글로벌 언어 (`res/values-en/strings.xml`, `README_EN.md`)
3. **일본어 (Japanese, ja)**: `res/values-ja/strings.xml`
4. **스페인어 (Spanish, es)**: `res/values-es/strings.xml`
5. **힌디어 (Hindi, hi)**: `res/values-hi/strings.xml`
6. **중국어 (Chinese, zh-rCN / zh-rTW)**: `res/values-zh-rCN/strings.xml`, `res/values-zh-rTW/strings.xml`
7. **러시아어 (Russian, ru)**: `res/values-ru/strings.xml`
8. **포르투갈어 (Portuguese, pt)**: `res/values-pt/strings.xml`
9. **프랑스어 (French, fr)**: `res/values-fr/strings.xml`
10. **독일어 (German, de)**: `res/values-de/strings.xml`
11. **인도네시아어 (Indonesian, in / id)**: `res/values-in/strings.xml`, `res/values-id/strings.xml`
12. **아랍어 (Arabic, ar)**: `res/values-ar/strings.xml`
13. **베트남어 (Vietnamese, vi)**: `res/values-vi/strings.xml`

---

## 🛡️ Core Development Principles (핵심 개발 원칙)
1. **한국어 기본 리소스 불변 원칙**:
   - `res/values/strings.xml`은 언제나 한국어로 유지하며, 영어는 반드시 `res/values-en/strings.xml`에 격리 배치한다.
2. **수술적 정밀 수정 (Surgical Changes)**:
   - 문제를 해결할 때 가장 관련이 높은 한두 줄의 코드만 정밀 타격 수정. 무관한 파일 구조나 코드는 건드리지 않는다.
3. **빌드 무결성 유지**:
   - 모든 수정 전후로 `./gradlew assembleDebug` 빌드 성공을 필수 확인한다.
4. **한글 및 UTF-8 인코딩 보존**:
   - 수정 후 파일 내 한글 리터럴 및 유니코드 깨짐(`?` 등)이 발생하지 않도록 전수 체크한다.
