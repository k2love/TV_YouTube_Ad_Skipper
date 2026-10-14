# 구현 계획서: 좌측 하단 QR 코드 쇼핑 광고 오버레이 자동 닫기 기능 구현 (v0.6.1)

## 1. 개요 및 목적
유튜브 TV에서 본 영상 시청 도중 화면 좌측 하단에 불쑥 튀어나와 화면을 가리는 **QR 코드 제휴 쇼핑 광고 오버레이**를 자동으로 감지하고, 카드 하단의 **'X' (닫기) 버튼을 0.1초 만에 자동 클릭하여 즉시 닫아주는 기능**을 구현합니다.

---

## 2. 사진 분석 및 좌표/텍스트 규격

사용자가 업로드한 실제 TV 사진 분석 결과:
1. **QR 코드 카드 영역**:
   - 화면 좌측 하단 (X: 약 8% ~ 43%, Y: 약 48% ~ 75%)
   - 순백색(#FFFFFF) 둥근 사각형 카드 형태
   - 법적 필수 고지 텍스트: **`수수료 지급`** (제휴 쇼핑 광고 공통)
2. **X 닫기 버튼 (타깃)**:
   - 카드 하단 중앙 바로 아래에 반투명 원형 배경 + 흰색 `X` 아이콘 위치
   - **정밀 화면 상대 좌표: `X = 0.25f` (25.0%), `Y = 0.795f` (79.5%)**

---

## 3. 세부 수술 설계

### 가. 접근성 노드 기반 자동 닫기 (`checkAndDismissQrAdOverlay`)
1. **키워드 탐색**:
   - `qrOverlayKeywords = listOf("수수료 지급", "수수료지급", "QR 코드", "QR", "스폰서")`
   - `qrCloseKeywords = listOf("닫기", "광고 닫기", "Close", "Dismiss", "X", "x")`
2. **판별 및 액션**:
   - 화면 좌측 하단(X < 50%, Y > 50%)에 닫기 버튼 또는 쇼핑 광고 텍스트가 발견되면:
     1) 해당 노드가 닫기 버튼인 경우 `tryPerformClick(node)`
     2) 또는 해당 카드 바로 아래의 정밀 타깃 좌표 **`(X = 0.25f, Y = 0.795f)`**로 `clickAtCoordinates` 터치 제스처 발사!
   - 닫기 성공 시 1.5초 쿨다운 및 `"🎯 [쇼핑 광고] QR 코드 오버레이 광고 자동 닫기 완료"` 토스트/로그 발송.

### 나. 컴퓨터 비전 픽셀 센서 보강 (`inspectBitmapAndTrigger`)
- 화면 캡처 분석 시, **좌측 하단 QR 카드 ROI (X: 15~35%, Y: 55~70%)**에 거대한 순백색(#FFFFFF) 픽셀 덩어리(120px 이상)가 출현했을 때:
  - "👁️ [Pixel Sensor] QR Ad Card detected in bottom-left!"
  - 즉시 **`(X = width * 0.25f, Y = height * 0.795f)`**로 터치 제스처 발사하여 닫기!

### 다. 설정 및 UI 연동
- SharedPreferences 키: `key_qr_ad_auto_dismiss` (기본값: true)
- `MainActivity`: 'QR 코드 광고 자동 닫기' 스위치 토글 제공

### 라. 버전 갱신
- `app/build.gradle.kts`: `versionCode = 61`, `versionName = "0.6.1"`
- `version.json`: `versionCode: 61`, `versionName: "0.6.1"`

---

## 4. 검증 계획
1. 소스 코드 수정 후 빌드 성공 (`./gradlew assembleDebug`)
2. `check_encoding.py`로 UTF-8 인코딩 무결성 검증
3. 루트 APK(`TV_YouTube_Ad_Skipper.apk`) 갱신 및 파일 크기 확인
