# 패키지 파싱 오류(Parse Error) 해결 계획서

## 1. 문제 분석
TV 기기에서 APK 설치 시 "패키지를 파싱하는 중 문제가 발생했습니다(There was a problem parsing the package)" 오류 발생.

### 원인 규명
1. **APK V1 (JAR) 서명 누락 (가장 유력)**:
   - `apksigner` 검사 결과 `Verified using v1 scheme (JAR signing): false`, `v2 scheme: true` 상태.
   - 많은 안드로이드 TV 기기(특히 Android 7~10 기반 또는 서드파티 런처/파일 관리자)의 내장 패키지 인스톨러는 v1(JAR) 서명이 포함되지 않은 APK 설치 시 파싱 오류를 뱉음.
2. **최소 SDK 버전(`minSdkVersion = 24`) 불일치 가능성**:
   - TV OS가 Android 6.0(API 23) 이하인 경우 설치가 거부되며 동일한 "파싱 오류"가 발생함.
   - `minSdkVersion`을 21 (Android 5.0 Lollipop)로 완화하여 호환 기기 범위를 극대화.
3. **배너 및 아이콘이 순수 벡터 XML 리소스인 경우의 TV 파서 버그**:
   - 일부 구형 TV OS 패키지 파서가 매니페스트 파싱 중 벡터 XML 배너 디코딩에 실패하여 파싱 오류 발생.
   - 표준 PNG 비트맵 아이콘 및 배너 리소스를 동봉하여 안전성 확보.

## 2. 해결 방안
1. **`app/build.gradle.kts` 수정**:
   - `minSdk = 21`로 변경
   - `signingConfigs`에 `v1SigningEnabled = true`, `v2SigningEnabled = true` 명시
   - debug 및 release 빌드 타입 모두에 서명 설정 적용
2. **`TvAdSkipService.kt` API 호환성 보강**:
   - `dispatchGesture`는 API 24 이상에서만 호출 가능하므로 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.N` 가드 추가
3. **PNG 아이콘 및 배너 리소스 추가**:
   - `res/drawable-xhdpi/banner.png` 및 `res/mipmap-xhdpi/ic_launcher.png` 생성하여 매니페스트에 지정
4. **빌드 및 검증**:
   - `assembleRelease` 또는 `assembleDebug` 재빌드
   - `apksigner`를 통해 `v1 scheme: true`, `v2 scheme: true` 전수 확인
   - `aapt dump badging`으로 `sdkVersion:'21'` 확인
