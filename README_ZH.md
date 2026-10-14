# 📺 TV YouTube Ad Skipper (Android TV YouTube 广告自动跳过与静音)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/zh/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-中文-orange.svg)](#-语言支持)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **专为 Android TV 设计的实用工具，在官方 YouTube TV 应用中跳过倒计时结束时 0.001 秒瞬间自动跳过广告，并在不可跳过的广告期间对硬件音频进行完全自动静音。**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**简体中文**]

---

## 🚀 快速入门 (简单3步安装)

1. **下载 APK**: [直接下载 TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **传输至电视**: 通过手机端 **'Send Files to TV' (SFTTV)** 应用或 **U盘** 传输至电视进行安装。
3. **开启无障碍权限**: 电视 [设置] ➔ [系统] ➔ [无障碍] ➔ 开启 **[TV YouTube 广告自动跳过服务]**！
   - *(若在 Google TV 12+ 上权限受限，请参阅 [解除受限设置指南](docs/zh/INSTALLATION.md#%EF%B8%8F-3-必备设置解除-google-tv-受限设置))*

👉 **[详细安装与设备配置指南 (docs/zh/INSTALLATION.md)](docs/zh/INSTALLATION.md)**

---

## 🌟 核心功能

### 1. 0.001秒 超高速跳过与硬件级静音
- 广告跳过按钮一旦出现，0.001秒内瞬间自动点击。
- 在不可跳过的强制广告播放期间自动静音电视音频，并在正片开始时精准恢复原音量。

### 2. 🎯 官方 Lounge 协议模式 (首选推荐: 100% 零误差)
- 通过 YouTube 官方「通过电视代码关联」会话直接从 Google 服务器接收广告结束信号。
- 绝不受屏幕字幕、视频画面变化或界面改版影响。

### 3. 👁️ 计算机视觉像素颜色传感器
- 广告期间监测右下方黄色圆形倒计时，倒计时结束后对白色跳过按钮进行定点点击。
- 播放正常视频时彻底停用像素传感器，坚决杜绝因黄色字幕或背景光导致的误静音。

### 4. 🛡️ 自动跳过 Shorts 弹窗及关闭 QR 推广
- 0.1秒自动确认并跳过 Shorts 投屏断开连接提示。
- 0.1秒定点坐标自动关闭左下角推广购物二维码弹窗。

### 5. 🚀 无线自动更新 (OTA)
- 启动时自动检查 GitHub 最新构建版本，支持一键在应用内自动更新。

👉 **[详细用户手册与设置说明 (docs/zh/USER_MANUAL.md)](docs/zh/USER_MANUAL.md)**
