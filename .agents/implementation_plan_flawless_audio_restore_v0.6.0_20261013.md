# 구현 계획서: 본 영상 볼륨 100% 칼복원 및 상태 자가치유 정밀 수술 (v0.6.0)

## 1. 개요 및 목적
사용자로부터 **"30초짜리 강제 광고가 끝나고 영상이 플레이되는데 볼륨이 여전히 0인 상태가 발생했다. 만의 하나 그런 상황이 발생하더라도 뒤로가기 해서 광고가 안 붙은 영상에 들어가면 볼륨 100이 되어야 하고, 광고가 붙는 영상을 다시 만나면 0으로 줄였다가 광고가 끝나면 100으로 복귀되는 정확도를 극대화해 달라"**는 요구사항이 접수되었습니다.
런타임 패킷 분석을 통해 본 영상 시작 시 볼륨 0 고착(Deadlock)이 발생하는 구조적 원인을 해결하고, 완벽한 자가치유(Self-healing) 볼륨 라이프사이클을 구현합니다.

---

## 2. 근본 원인 분석 (Root Causes)

1. **`isAdPlaying` 플래그의 데드락(Deadlock) 고착 버그**:
   - 강제 광고는 스킵 버튼이 없으므로, 광고 슬롯이 끝날 때 구글 서버가 `adState="1082"` 종료 패킷을 보내지 않고 **곧바로 본 영상 `nowPlaying` 또는 `onStateChange: state=1` 패킷으로 직행**하는 경우가 빈번함.
   - 하지만 이전 코드(v0.5.9)는 `isAdPlaying`을 끄는 조건이 오직 `adState == "1082"`뿐이었고, `nowPlaying`과 `onStateChange`에서 복원할 때 `!isAdPlaying` 가드를 걸어두었음.
   - 결과: `adState == "1082"`가 생략되면 `isAdPlaying`이 영원히 `true`에 묶여, **본 영상이 아무리 재생되어도 `!isAdPlaying` 가드에 막혀 볼륨 복원이 원천 차단**되는 치명적 데드락이 발생함.
2. **뒤로가기 시 음소거 잔류 버그**:
   - 영상을 보다가 뒤로가기를 눌러 홈 그리드로 빠져나왔을 때(`state=5` CUED 또는 `videoId=""`), 음소거를 즉시 해제하지 않고 방치하여 다음 영상 진입 시에도 소리가 0으로 묶여 있는 경우가 발생함.
3. **광고 없는 영상 진입 시 판단 부재**:
   - 광고가 없는 영상에 진입했을 때 `nowPlaying`에 광고 필드가 전혀 없음에도 불구하고 이전 상태(`isAdPlaying`)를 강제 리셋하지 못했음.

---

## 3. 정밀 수술 설계

### 가. 본 영상 확정 판별 로직 (`nowPlaying`)
- `nowPlaying` 패킷 수신 시:
  - `adState`나 `adVideoId`가 존재하거나 `adState == "1"`인 경우: **광고 진행 중** (`isAdPlaying = true`, `muteAudio()`).
  - 반대로 **`adState`와 `adVideoId`가 모두 비어 있고 `videoId.isNotEmpty()`인 경우**:
    - 이것은 100% 순수 본 영상임! 이전 광고 종료 패킷 누락 여부와 무관하게 **즉시 `isAdPlaying = false`로 강제 해제**하고 **볼륨 100% 즉시 복원(`restoreAudio()`)**!

### 나. 본 영상 재생 상태 (`onStateChange: state=1`)
- `onStateChange`에서 `state == 1` (PLAYING) 수신 시:
  - `payload`에 `adState`가 없고 `currentTime > 0`인 본 영상 재생 상태라면:
  - `isAdPlaying = false`로 강제 리셋 후 **볼륨 100% 즉시 복원**!

### 다. 뒤로가기 및 영상 정지 시 완전 리셋 (`state=5`, `state=0`, `videoId=""`)
- 뒤로가기를 누르거나 영상이 끝나면:
  - `isAdPlaying = false` 리셋.
  - `isAutoMuted == true`라면 **즉시 볼륨 100% 원상 복구**하여 다음 영상 진입 시 정상 소리 보장.

### 라. 자가 치유 안전 타이머 (Safety Self-Healing)
- 30초 강제 광고 길이를 감안하여, 음소거 시작 후 **35초가 지나면 어떤 예외 상황이든 무조건 볼륨 100으로 자동 복원**하여 소리 먹통을 원천 방지.

---

## 4. 검증 계획
1. 소스 코드 수정 후 빌드 성공 (`./gradlew assembleDebug`)
2. `check_encoding.py`로 UTF-8 인코딩 무결성 검증
3. 루트 APK(`TV_YouTube_Ad_Skipper.apk`) 갱신 및 파일 크기 확인
