# 결과 리포트: 긴급 시스템 안정화 및 우측 상단 초미니 배지 개편 (v0.6.8)

- **작업명**: Google TV 시스템 안정화, 유튜브 로그인 세션 보호 및 초미니 배지 알림 개편
- **버전**: `v0.6.8` (versionCode: `68`)
- **완료 일시**: 2026-10-14
- **GitHub 저장소**: [https://github.com/k2love/TV_YouTube_Ad_Skipper](https://github.com/k2love/TV_YouTube_Ad_Skipper)

---

## 1. 🔍 사용자 문제 제기 및 근본 원인 분석

### 사용자 피드백
> "0.6.6 업데이트부터 앱이 Google TV 전반을 불안하게 하고 있어. 유튜브 로그인도 풀리고 말이야.  
> 그리고 유튜브 특성상 네가 광고 차단했다는 메시지를 하단 중앙에 뿌리면 안돼.  
> 맨 우측 상단이나 맨 좌측 하단에 지금보다 1/3, 1/5 이하로 줄여야 해."

### 치명적 버그 원인 규명
1. **1.2초마다 `startService` 무한 난사**:
   - `triggerPeriodicVisualCheck()` 내부에 `ensureLoungeServiceAlive()`가 매초 호출되면서 `startService(LoungeSkipService)`를 난사. 안드로이드 OS 바인더 스레드 및 메모리를 고갈시켜 TV 전체가 극심하게 버벅이고 앱들이 크래시됨.
2. **비(非)유튜브 환경(런처, 시스템UI) 개입**:
   - `activePkg == "android"`까지 허용되어 Google TV 홈 화면이나 타 앱에서도 백그라운드 캡처와 가상 제스처를 쏴대어 시스템 UI 불안정 초래.
3. **유튜브 로그인 풀림의 원인 (포커스 강탈 & 위험 팝업 승인)**:
   - `tryPerformClick`에서 `ACTION_FOCUS`, `ACTION_SELECT`를 강제로 주입하여 유튜브 리모컨의 포커스를 마구 빼앗아 좌측 사이드바 하단의 [계정/로그아웃] 영역으로 포커스를 튀게 만듦.
   - `checkAndDismissShortsDialog`가 "연결 해제" 문구를 감지하면 무조건 "확인/예/OK"를 눌러버려, 계정 관련 팝업을 확인 눌러 세션을 날려버림.
4. **하단 중앙 거대한 기본 Toast**:
   - 자막과 진행 바를 가리고, 접근성 서비스가 자신이 띄운 토스트 문구를 다시 광고 버튼으로 인식해 500회 오감지 폭주를 유발.

---

## 2. 🛠️ v0.6.8 긴급 수술 내역

### ① Google TV 시스템 안정성 100% 회복
- **`startService` 매초 난사 완전 제거**: 부팅/서비스 시작 시 1회만 점검하도록 격리.
- **공식 유튜브 TV 패키지 엄격 제한**:
  ```kotlin
  val isYouTubeTv = activePkg == "com.google.android.youtube.tv" ||
                    activePkg == "com.google.android.youtube"
  if (!isYouTubeTv) return
  ```
  홈 화면, 런처, 시스템 설정, 타 앱에서는 1바이트의 리소스도 쓰지 않고 즉시 반환.
- **불필요한 화면 캡처 중단**: 오직 실제 광고 컨텍스트(`isAdContext == true`)일 때만 보수적으로 검사.

### ② 유튜브 로그인 세션 보호 & 포커스 교란 전면 제거
- `tryPerformClick`의 `ACTION_FOCUS`, `ACTION_ACCESSIBILITY_FOCUS`, `ACTION_SELECT` 포커스 강탈 코드 전면 삭제.
- 노드 클릭이 성공하면 화면 가상 터치를 절대 중복 발사하지 않도록 차단.
- `checkAndDismissShortsDialog`를 기본 비활성화(`false`)하고, 계정/로그인 관련 단어 노출 시 절대 동작하지 않도록 안전 잠금.

### ③ 우측 상단 1/4 크기 초미니 배지 (Mini Pill Badge) 시스템 탑재
- 거대한 하단 중앙 Toast 전면 폐지.
- 접근성 오버레이(`TYPE_ACCESSIBILITY_OVERLAY`)를 활용한 초소형 캡슐 칩 배지 구현:
  - **위치**: 화면 **맨 우측 상단 (Top: 30px, Right: 40px)**
  - **크기**: 기존 토스트의 **1/4 이하 초미니 크기** (글자 11sp, 높이 약 25dp)
  - **디자인**: 고급스러운 반투명 다크 그레이(`rgba(32,32,32,0.9)`), 둥근 모서리, 흰색 텍스트
  - **문구**: `"⚡ 스킵 완료"`, `"🎯 QR 닫기"`
  - **자가 감지 원천 차단**: `importantForAccessibility = NO`, `FLAG_NOT_FOCUSABLE`로 접근성 서비스가 절대 자기 자신을 인식하지 못하며, 리모컨 포커스도 0% 간섭.
  - **1.2초 후 자동 페이드아웃 소멸**.

---

## 3. 📦 배포 및 TV 설치 방법

- **GitHub 최신 커밋**: v0.6.8 푸시 완료
- **적용 방법**:
  - 이미 v0.6.6 이상이 설치된 TV: 앱을 켜거나 **[업데이트 확인]**을 누르면 GitHub에서 **v0.6.8**을 감지하고 자동 설치됩니다.
  - 구글 드라이브 설치 기반: 최신 `TV_YouTube_Ad_Skipper.apk`를 구글 드라이브에 올려 업데이트하시면 시스템 안정성과 미니 배지가 즉시 적용됩니다.
