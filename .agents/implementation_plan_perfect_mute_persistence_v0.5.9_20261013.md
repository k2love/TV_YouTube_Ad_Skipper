# 구현 계획서: 강제 광고 음소거 조기 복원 버그 완벽 수술 (v0.5.9)

## 1. 개요 및 목적
사용자로부터 **"5초, 15초, 20초, 30초짜리 강제 광고가 나올 때 볼륨을 0으로 줄인 뒤, 3~4초 만에 바로 볼륨이 100으로 다시 높아진다. 광고가 끝날 때까지 0을 유지하다가 본 영상이 시작되면 정확히 100으로 올려달라"**는 핵심 결함이 보고되었습니다.
런타임 패킷과 소스 코드를 심층 분석하여 밝혀진 3가지 원인을 완벽히 수술하여, 모든 길이의 강제 광고(5~30초)에서 소리를 끝까지 죽이고 본 영상 시작 시점에 칼복원하도록 만듭니다.

---

## 2. 근본 원인 분석 (Root Causes)

1. **`adState == "0"`을 광고 종료로 오인 (가장 치명적인 3초 복원의 주범)**:
   - 유튜브 TV는 광고 시작 시 `currentTime=0`, `adState="0"`, `isSkipEnabled="false"` 패킷을 먼저 전송함.
   - `adState="0"`은 종료가 아니라 광고 재생 초기화(INIT/UNSTARTED) 상태임.
   - 기존 코드는 `adState == "0"`을 광고 종료로 간주하여 `adPodBridgeJob`을 통해 **1.0초 뒤 볼륨을 100으로 복원**해 버림 (광고 시작 2초 후 패킷 수신 -> 3~4초 시점에 소리가 터지는 정확한 이유!).
2. **`nowPlaying` 수신 시 광고 여부 미검증**:
   - 광고 재생 중에도 구글 서버는 `nowPlaying` 패킷을 주기적으로 보냄.
   - 이 패킷에는 본 영상의 `videoId`가 항상 포함되어 있으며, 광고 중일 때는 `adState="1"`, `adVideoId="..."`가 함께 실려 옴.
   - 기존 코드는 `videoId.isNotEmpty()`만 보고 광고 중인지 검사하지 않은 채 본 영상이 시작되었다고 착각하여 볼륨을 100으로 즉시 복원함.
3. **`onStateChange: state=1` 오탐 (광고 비디오 자체의 재생 상태)**:
   - 광고 비디오 자체도 플레이어 관점에서는 `state=1` (PLAYING)으로 재생됨.
   - 광고가 재생을 시작하자마자 `state=1`이 오는데, 기존 코드는 `state == 1`을 보면 무조건 본 영상 시작으로 간주하고 볼륨을 100으로 올려버림.
4. **안전 타이머(Safety Timeout)의 20초 한계**:
   - 기존 타이머가 `delay(20000)`으로 설정되어 있어 30초 강제 광고의 경우 20초 시점에 소리가 복원됨. 45초로 연장 필요.

---

## 3. 정밀 수술 계획

### `LoungeManager.kt` 수술 내역
1. **`onAdStateChange` / `adPlaying`**:
   - `adState == "0"`을 광고 종료 조건에서 완전히 제거하고, 광고 진행 중(`adIsPlaying`) 조건에 편입.
   - 실제 종료는 `adState == "1082"`, `"STOPPED"`, `"ENDED"`만 인정.
2. **`onStateChange` (state=1)**:
   - `if (state == 1 && !isAdPlaying && isAutoMuted)`:
     반드시 `!isAdPlaying`일 때만 본 영상 재생으로 확정하여 볼륨 복원.
3. **`nowPlaying`**:
   - payload 내에 `adState` 또는 `adVideoId`가 있거나 `adState == "1"`이면 여전히 광고 진행 중으로 확정(`isAdPlaying = true`, `muteAudio()`).
   - 광고 필드가 전혀 없고 `!isAdPlaying`일 때만 순수 본 영상으로 판단하여 `restoreAudio()`.
4. **`muteAudio()`**:
   - 안전 타이머를 `delay(45000)` (45초)로 확장하여 30초 강제 광고 및 연속 2개 광고(15+15초) 완벽 커버.

---

## 4. 검증 계획
1. 소스 코드 수정 후 빌드 성공 (`./gradlew assembleDebug`)
2. `check_encoding.py`로 UTF-8 인코딩 무결성 검증
3. 루트 APK(`TV_YouTube_Ad_Skipper.apk`) 갱신 및 파일 크기 확인
