# 📥 TV YouTube Ad Skipper - インストール＆設定ガイド

本書は、**Android TV / Google TV** に `TV YouTube Ad Skipper` をインストールし、必要なユーザー補助権限を設定する手順を説明します。

---

## 🚀 1. 最新 APK のダウンロード

- **直接ダウンロード**: [TV_YouTube_Ad_Skipper.apk ダウンロード](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
- **GitHub リポジトリ**: [GitHub Releases](https://github.com/k2love/TV_YouTube_Ad_Skipper)

---

## 📺 2. インストール手順

### 方法 A: スマホからワイヤレス転送 (最もおすすめ ⭐⭐⭐)
1. **アプリ準備**: テレビとスマホの両方に Google Play から **'Send Files to TV' (SFTTV)** をインストール。
2. **ファイル管理アプリ**: テレビにファイル管理アプリ（**'File Commander'** 等）をインストール。
3. **ファイル送信**:
   - スマホで [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk) をダウンロード。
   - SFTTV で APK を選択し、テレビに送信。
4. **インストール**: テレビのファイル管理アプリで `Download` フォルダを開き、APK を実行してインストール。

---

### 方法 B: USB メモリを使用
1. PC で APK をダウンロードし、USB メモリにコピー。
2. テレビの USB ポートに挿入し、ファイル管理アプリから実行。

---

### 方法 C: PC からワイヤレス ADB インストール (上級者向け)
```powershell
# 1. テレビと接続
adb connect <テレビのIPアドレス>:5555

# 2. APK インストール
adb install -r TV_YouTube_Ad_Skipper.apk

# 3. ユーザー補助権限の一括付与
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. 必須設定: Google TV 制限設定の解除

Google TV 12/13/14+ では外部アプリのユーザー補助権限をオンにする際、**「制限付き設定です」** と表示されて無効化される場合があります:

1. テレビの [設定(歯車)] ➔ **[アプリ]** ➔ **[アプリをすべて表示]**。
2. **[TV Ad Skipper]** を選択。
3. 画面下部の **[制限付き設定を許可]** をクリックして承認。
4. [設定] ➔ [システム] ➔ [ユーザー補助] ➔ **[TV YouTube 広告スキップサービス]** をオンにします。
