# 📺 TV YouTube Ad Skipper (Android TV YouTube 広告自動スキップ＆ミュート)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/ja/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-日本語%20対応-orange.svg)](#-言語優先順位)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **公式 YouTube TV アプリで広告スキップタイマー終了後、0.001秒で自動スキップし、強制広告中は音声を自動で完全ミュートする Android TV 専用ユーティリティです。**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**日本語**]

---

## 🚀 クイックスタート (簡単3ステップ)

1. **APKダウンロード**: [TV_YouTube_Ad_Skipper.apk 直接ダウンロード](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **テレビにインストール**: スマホアプリ **'Send Files to TV' (SFTTV)** または **USBメモリ** 経由で転送してインストール。
3. **ユーザー補助を有効化**: テレビ [設定] ➔ [ユーザー補助] ➔ **[TV YouTube 広告スキップサービス]** をオン！
   - *(Google TV 12+ で権限が制限されている場合: [Google TV 制限設定解除ガイド](docs/ja/INSTALLATION.md#%EF%B8%8F-3-必須設定-google-tv-制限設定の解除) 参照)*

👉 **[詳細インストール＆テレビ設定ガイド (docs/ja/INSTALLATION.md)](docs/ja/INSTALLATION.md)**

---

## 🌟 主な機能

### 1. 0.001秒 超高速スキップ＆自動ミュート
- 「スキップ」ボタンが表示された瞬間に 0.001 秒で自動クリック。
- スキップ不可の強制広告中は音声を自動ミュートし、本編開始時に元の音量に完全復元。

### 2. 💤 低スペック Google TV 向け「完全休眠 (Deep-Sleep) アーキテクチャ」 (4K 60fps カクつき 0%)
- 本編再生中（`isAdPlaying == false`）はバックグラウンドの監視ループを 100% 停止し、`serviceInfo.eventTypes = 0` によりアクセシビリティイベントの生成を遮断（CPU/GPU/IPC 負荷 0.0%）。
- 本編再生中の定期スクリーンショット撮影（`takeScreenshot`）を完全撤廃し、低価格帯 TV（TCL、Hisense など）での 4K 60fps HDR 再生時の定期的なフレーム落ち（カクつき）を根本解決。

### 3. 🛒 非ピクセル YouTube Innertube API ベース QR ショッピング広告検知
- 常時画面キャプチャの代わりに、動画開始時に YouTube Innertube API（`/youtubei/v1/next`）でショッピングメタデータを 1 回事前照会。
- 95% 以上の通常動画ではキャプチャ 0 回で完全休眠を維持し、商品タグ付き動画（5% 未満）でのみ `+4.0秒`・`+8.0秒` にピンポイントで閉じます。

### 4. 🎯 公式 Lounge プロトコルモード (推奨: 100% エラーフリー)
- YouTube 公式「テレビコードでリンク」セッション経由で Google サーバーから広告完了信号を受信してスキップ。
- 画面字幕やUI変更の影響を一切受けない純正アルゴリズム。

### 5. 🌐 世界 13 言語対応＆多地域広告検知
- 日本語、韓国語、英語、中国語、スペイン語など世界 13 主要言語の広告バッジおよびスキップボタンに対応。

### 6. 🚀 ワイヤレス自動更新 (OTA)
- GitHub に新しい APK がリリースされると、アプリ起動時に自動検知して 1 クリック更新。

👉 **[詳細マニュアル＆モード別使い方 (docs/ja/USER_MANUAL.md)](docs/ja/USER_MANUAL.md)**

---

## 📜 最新バージョン更新情報 (v0.8.1)

- **完全休眠 (Deep-Sleep) アーキテクチャ導入**: 低スペック Google TV での 4K 60fps 再生時のカクつきを完全解消。
- **高負荷フラグの恒久削除**: `accessibility_service_config.xml` から不要なフラグを削除し、タイムアウトを 500ms に緩和。
- **非ピクセル Innertube QR ショッピング検知**: 常時キャプチャを廃止し、動画ごとの 1 回メタデータ照会方式へ移行。
- **世界 13 言語広告検知を完全保持**: 多言語正規表現および誤検知防止アルゴリズムを統合。

---

## 📄 ライセンス

This project is licensed under the MIT License.
