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

### 2. 🎯 公式 Lounge プロトコルモード (推奨: 100% エラーフリー)
- YouTube 公式「テレビコードでリンク」セッション経由で Google サーバーから広告完了信号を受信してスキップ。
- 画面字幕やUI変更の影響を一切受けない純正アルゴリズム。

### 3. 👁️ コンピュータビジョン ピクセルカラーセンサー
- 右下の黄色い円形カウント終了後、白いスキップボタンのみをピンポイントで1回クリック。
- 動画内の黄色い字幕やロゴによる誤ミュートを完全遮断。

### 4. 🛡️ Shorts ポップアップ＆QR ショッピング広告自動通過
- Shorts キャスト時の切断確認ポップアップを 0.1 秒で自動通過。
- 画面左下の提携ショッピング QR オーバーレイを 0.1 秒で自動的に閉じる。

### 5. 🚀 ワイヤレス自動更新 (OTA)
- GitHub に新しい APK がリリースされると、アプリ起動時に自動検知して 1 クリック更新。

👉 **[詳細マニュアル＆モード別使い方 (docs/ja/USER_MANUAL.md)](docs/ja/USER_MANUAL.md)**

---

## 📄 ライセンス

This project is licensed under the MIT License.
