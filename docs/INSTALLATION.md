# 📥 TV YouTube Ad Skipper 설치 및 설정 가이드

본 문서는 **Android TV / Google TV** 환경에서 `TV YouTube Ad Skipper` APK 파일을 설치하고, 정상 작동에 필요한 접근성 권한을 부여하는 단계별 안내서입니다.

---

## 🚀 1. 최신 설치 파일 (APK) 다운로드

아래 링크를 통해 항상 최신 버전의 빌드된 APK를 다운로드할 수 있습니다:
- **직접 다운로드**: [TV_YouTube_Ad_Skipper.apk 다운로드](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
- **GitHub 저장소**: [GitHub Releases 확인](https://github.com/k2love/TV_YouTube_Ad_Skipper)

---

## 📺 2. TV 설치 방법 (상황별 선택)

### 방법 A: 스마트폰 무선 전송 (가장 추천하는 쉬운 방법 ⭐⭐⭐)
PC나 USB 메모리 없이 스마트폰만으로 1분 만에 설치하는 방법입니다.

1. **앱 설치**: Android TV의 Google Play 스토어와 스마트폰(안드로이드)에 각각 **'Send Files to TV' (SFTTV)** 앱을 설치합니다.
2. **파일 탐색기 준비**: TV Play 스토어에서 무료 파일 관리자 앱(예: **'File Commander'** 또는 **'FX File Explorer'**)을 하나 설치합니다.
3. **파일 전송**:
   - 스마트폰으로 위의 [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)를 다운로드합니다.
   - 스마트폰에서 SFTTV 앱을 열고 `Send`를 눌러 다운로드한 APK를 선택한 뒤, TV 이름을 터치하여 전송합니다.
4. **설치 실행**: TV 파일 관리자 앱을 열어 `Download` 폴더로 이동한 뒤 전송받은 APK를 클릭하여 설치합니다.

---

### 방법 B: USB 메모리를 이용한 설치
1. PC에서 [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)를 다운로드하여 USB 메모리에 복사합니다.
2. TV 뒷면의 USB 포트에 메모리를 연결합니다.
3. TV 파일 관리자 앱으로 USB 메모리를 열어 APK 파일을 실행하고 설치합니다.

---

### 방법 C: PC에서 무선 ADB 원클릭 설치 (고급/개발자용)
PC와 TV가 같은 Wi-Fi에 연결되어 있다면 터미널 명령어만으로 즉시 설치하고 권한까지 일괄 부여할 수 있습니다.

1. **TV 개발자 옵션 활성화**:
   - TV [설정] ➔ [시스템] ➔ [정보] ➔ [Android TV OS 빌드]를 리모컨 확인 버튼으로 연속 7회 연타하여 개발자 모드를 켭니다.
   - [설정] ➔ [시스템] ➔ [개발자 옵션] ➔ **[USB 디버깅]**을 켭니다.
2. **TV IP 주소 확인**: [설정] ➔ [네트워크 및 인터넷] ➔ 연결된 Wi-Fi 클릭 후 IP 확인 (예: `192.168.0.50`).
3. **PC 터미널 실행 (PowerShell 또는 CMD)**:
   ```powershell
   # 1. TV와 무선 연결
   adb connect 192.168.0.50:5555

   # 2. APK 설치 (기존 앱 유지 업데이트)
   adb install -r TV_YouTube_Ad_Skipper.apk

   # 3. 접근성 권한 원클릭 활성화
   adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
   adb shell settings put secure accessibility_enabled 1
   ```

---

## ⚠️ 3. 필수 설정: Google TV 권한 잠금 해제

Google TV 12, 13, 14 이상 버전에서는 외부에서 설치한(사이드로딩) 앱의 접근성 권한을 켤 때 **"제한된 설정입니다. 보안을 위해 현재 이 설정을 사용할 수 없습니다"**라는 안내와 함께 스위치가 회색으로 비활성화될 수 있습니다. 이는 안드로이드 자체 보안 정책이며, 아래 방법으로 10초 만에 해제할 수 있습니다:

### 해결 절차:
1. TV 리모컨의 [설정(톱니바퀴)] 버튼을 누릅니다.
2. **[앱]** ➔ **[모든 앱 보기]**로 이동합니다.
3. 목록에서 **[TV Ad Skipper]**를 찾아 클릭합니다.
4. 화면 맨 아래로 내려가 **[제한된 설정 허용]**을 누르고 확인을 선택합니다.
5. 이제 TV Ad Skipper 앱을 실행하거나 [설정] ➔ [시스템] ➔ [접근성] ➔ **[TV 유튜브 광고 건너뛰기 서비스]**를 켜면 정상적으로 활성화됩니다!

---

## ✅ 4. 정상 작동 확인

1. **TV Ad Skipper** 앱을 실행합니다.
2. 화면 상단의 **접근성 서비스 상태**가 **`작동 중 (활성화됨)`** (초록색)으로 표시되는지 확인합니다.
3. 공식 YouTube TV 앱을 켜고 광고가 나올 때:
   - 0ms 무음 처리(Auto-Mute)와 함께 광고 스킵 버튼 노출 시 0.001초 만에 자동으로 스킵되는지 확인합니다!
