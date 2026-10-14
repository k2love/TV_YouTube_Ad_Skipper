# Lounge 프로토콜 bc/bind 정밀 복구 및 픽셀 센서 이원화 구현 계획서

## 1. 개요
1. **Lounge 프로토콜 엔진 복구**: `pyytlounge` 최신 3.4.0 공식 소스코드 분석을 바탕으로, `bc/bind`의 청크 크기(Chunk Size) 디코딩, `c`(SID) / `S`(gsessionid) 이벤트 단일 문자열 파싱, `connect` -> `subscribe (RID=rpc)` 정밀 2단계 핸드셰이크를 100% 일치하도록 전면 수정하여 실시간 광고 이벤트 수신을 완전 복구합니다.
2. **픽셀 색상 센서 이원화(하이브리드)**: 우하단 노란색 원형 카운터 감지 및 흰색 건너뛰기 버튼 감지 로직을 보조 엔진으로 탑재하여, 사용자가 원하는 모드를 언제든 선택하거나 병행할 수 있도록 이원화합니다.

## 2. 세부 작업
1. **`LoungeManager.kt` 전면 재작성**:
   - `connect()`: `RID=1&VER=8&CVER=1&auth_failure_option=send_error` 정확한 POST 파라미터 적용
   - 청크 바이트 길이(`chunk_remaining`) 기반 스트림 파서 구현
   - `c` (SID), `S` (gsessionid), `last_event_id` (AID) 정확한 인덱스 파싱
   - `subscribe()`: `RID=rpc&CI=0&TYPE=xmlhttp` 기반 지속적 Long-polling 스트림 수립
   - `_command("skipAd")`: `ofs`, `req0__sc=skipAd`, `count=1` 정확한 커맨드 디스패치
2. **픽셀 색상 센서(PixelSensor) 엔진 구현**:
   - `AccessibilityService.takeScreenshot()` (Android 11+) 연동
   - 우하단 ROI(Region of Interest) 영역의 노란색 카운터(#FFD600) 및 흰색 알약(#FFFFFF) 픽셀 비율 검출
   - 건너뛰기 버튼 검출 시 정밀 1회 터치 클릭
3. **UI 이원화 연동 (`activity_main.xml` / `MainActivity.kt`)**:
   - 모드 선택 탭/스위치:
     - [모드 1] Lounge 프로토콜 모드 (공식 API 네트워크 방식)
     - [모드 2] 픽셀 색상 센서 모드 (컴퓨터 비전 시각 방식)
   - 실시간 감지 상태 및 수신 로그 표시
4. **빌드 검증 및 최종 APK 갱신**
