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

### 2. 💤 低性能 Google TV 专用「完全休眠 (Deep-Sleep) 架构」 (4K 60fps 零卡顿)
- 正片播放期间（`isAdPlaying == false`）彻底停止后台巡视循环，动态将无障碍事件设置为 `serviceInfo.eventTypes = 0`，杜绝系统和 YouTube 生成事件，实现 **0.0% CPU/GPU/IPC 占用**。
- 彻底废除正片播放期间的周期性屏幕截图（`takeScreenshot`），保护硬件视频图层（HWC），彻底解决入门级电视（TCL、海信等）播放 4K 60fps HDR 时的微卡顿问题。

### 3. 🛒 基于 YouTube Innertube API 的无像素 QR 购物广告检测
- 不再持续截屏，而是在进入视频时调用 YouTube 内部 API（`/youtubei/v1/next`）预先检测 1 次商品元数据。
- 在超过 95% 的普通无商品视频中保持 **0 次截屏**并完全休眠；仅在包含商品标签的 <5% 视频中，于 `+4.0秒` 和 `+8.0秒` 时精准关闭二维码卡片。

### 4. 🎯 官方 Lounge 协议模式 (首选推荐: 100% 零误差)
- 通过 YouTube 官方「通过电视代码关联」会话直接从 Google 服务器接收广告结束信号。
- 绝不受屏幕字幕、视频画面变化或界面改版影响。

### 5. 🌐 全球 13 种语言多地区广告识别
- 支持中文（简体/繁体）、韩语、英语、日语、西班牙语等全球 13 种主流语言的广告徽章与跳过按钮识别。

### 6. 🚀 无线自动更新 (OTA)
- 启动时自动检查 GitHub 最新构建版本，支持一键在应用内自动更新。

👉 **[详细用户手册与设置说明 (docs/zh/USER_MANUAL.md)](docs/zh/USER_MANUAL.md)**

---

## 📜 最新版本更新说明 (v0.8.1)

- **完全休眠 (Deep-Sleep) 架构**: 彻底消除低性能电视上播放 4K 60fps 视频时的卡顿掉帧。
- **永久移除高负载无障碍标志**: 移除 `flagRetrieveInteractiveWindows` 及 `typeWindowContentChanged`，超时放宽至 500ms。
- **非像素 Innertube 购物广告检测**: 视频开始时单次元数据查询，杜绝日常观影截屏开销。
- **全球 13 语言广告正则全量保留**: 完整保留多语言跳过关键词及误判拦截机制。
