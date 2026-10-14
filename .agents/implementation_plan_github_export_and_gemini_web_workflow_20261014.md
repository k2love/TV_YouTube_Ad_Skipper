# 구현 계획서: GitHub 저장소 배포 및 제미나이 웹 연계 환경 구성

- **기능 슬러그**: `github_export_and_gemini_web_workflow`
- **버전/날짜**: `20261014`
- **문서 위치**: `.agents/implementation_plan_github_export_and_gemini_web_workflow_20261014.md`

## 1. 개요 및 목적
사용자가 본 프로젝트(안드로이드TV용 유튜브 광고 스킵 앱)를 GitHub 저장소에 안전하게 업로드하고, 이후 웹 브라우저의 Gemini 웹(gemini.google.com 또는 AI Studio / GitHub Codespaces 등) 환경에서 연속해서 작업을 진행할 수 있도록 준비합니다.

## 2. 작업 원칙 및 제약사항
1. **기존 소스 코드 불변경 원칙**: 기존 Kotlin 소스 코드(`TvAdSkipService.kt` 등) 및 리소스 파일은 일체 수정하지 않고 온전히 보존합니다.
2. **빌드 안정성 유지**: Git 관련 설정 및 문서 추가 작업 전후로 빌드가 깨지지 않는지 검증합니다.
3. **불필요/민감 파일 제외**:
   - 로컬 SDK 경로가 담긴 `local.properties`
   - 빌드 디렉토리 (`build/`, `app/build/`, `.gradle/`)
   - 임시 실행 로그 (`scratch_logs*.csv`, `test_output.txt`)
   - Python 캐시 (`__pycache__/`)
   - 루트의 대용량 APK 파일(선택적 분리 또는 포함 여부 결정)
4. **한글 인코딩 보존**: 생성되는 모든 문서(`README.md`, `.gitignore`, 계획서 등)는 UTF-8 인코딩을 엄격히 준수합니다.

## 3. 세부 실행 계획

### 단계 1: `.gitignore` 작성
- Android 및 Gradle 표준 제외 항목 적용:
  - `*.iml`, `.gradle/`, `/local.properties`, `/.idea/`, `/build/`, `/*/build/`
  - `.DS_Store`, `Thumbs.db`
  - 대용량/임시 로깅 파일: `scratch_logs*.csv`, `test_output.txt`, `__pycache__/`
  - `TV_YouTube_Ad_Skipper.apk` (원격 저장소 경량화를 위해 제외하되, 릴리즈 파일용으로 필요시 관리)

### 단계 2: `README.md` 작성
- 제미나이 웹 및 GitHub 방문자가 프로젝트를 즉시 파악할 수 있도록 상세하고 구조화된 설명서 작성:
  - 프로젝트 개요 (Android TV YouTube 광고 자동 스킵 & 음소거 & QR 광고 닫기)
  - 주요 아키텍처 및 핵심 기능 (접근성 서비스, 백그라운드 센티넬 루프, 컴퓨터 비전 ROI 기반 QR 광고 탐지, OTA 자동 업데이트)
  - 프로젝트 디렉토리 구조 안내
  - 빌드 및 설치 방법 (`./gradlew assembleDebug`, ADB 설치)
  - 제미나이 웹(Gemini Web)에서 이어 작업하는 팁 및 프롬프트 가이드

### 단계 3: Git 초기화 및 로컬 커밋
- `git init -b main`
- 사용자 이름/이메일 확인 및 설정
- `git add .` 및 제외 파일 상태 검증 (`git status`)
- 첫 번째 커밋 생성 (`feat: Initial commit for Android TV YouTube Ad Skipper (v0.6.5)`)

### 단계 4: GitHub 원격 저장소 연결 및 푸시
- 사용자에게 GitHub 레포지토리 URL 또는 로그인 방식 확인
- 원격 저장소 등록 (`git remote add origin <URL>`)
- 푸시 실행 (`git push -u origin main`)

### 단계 5: 제미나이 웹 작업 연계 안내 및 워크스루 작성
- 제미나이 웹에서 GitHub 레포지토리를 활용하여 효과적으로 개발 및 프롬프트하는 방법 제공
- 작업 완료 리포트(`walkthrough.md`) 작성

## 4. 검증 기준
- [ ] `.gitignore` 설정으로 불필요한 빌드 파일/캐시가 스테이징되지 않는지 확인
- [ ] `git status`에서 깨끗한 소스 파일들만 포함되는지 확인
- [ ] 프로젝트 빌드 (`./gradlew assembleDebug`) 재확인
- [ ] 원격 저장소 푸시 완료
