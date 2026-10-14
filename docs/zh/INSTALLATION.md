# 📥 TV YouTube Ad Skipper - 安装与配置指南

本文档提供在 **Android TV / Google TV** 上安装 `TV YouTube Ad Skipper` 并开启无障碍权限的详细步骤。

---

## 🚀 1. 下载最新安装包 (APK)
- **直接下载**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
- **GitHub 仓库**: [GitHub Releases](https://github.com/k2love/TV_YouTube_Ad_Skipper)

---

## 📺 2. 电视安装方法

### 方法 A: 手机无线传输 (最推荐 ⭐⭐⭐)
1. 在电视与手机的 Play 商店分别安装 **'Send Files to TV' (SFTTV)**。
2. 在电视端安装免费文件管理器（如 **'File Commander'**）。
3. 手机下载 APK 后，通过 SFTTV 发送至电视。
4. 打开电视端文件管理器的 `Download` 文件夹，点击 APK 即可安装。

### 方法 B: U盘安装
1. 将下载的 APK 复制到 U 盘并插入电视。
2. 打开电视文件管理器安装。

### 方法 C: 电脑无线 ADB 安装 (开发者/高级用户)
```powershell
adb connect <电视IP地址>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. 必备设置: 解除 Google TV 受限设置

在 Google TV 12/13/14+ 系统中，侧载应用的无障碍权限可能会被提示 **"受限设置"**:
1. 进入电视 [设置] ➔ **[应用]** ➔ **[查看所有应用]**。
2. 找到并点击 **[TV Ad Skipper]**。
3. 滑动至最底部，点击 **[允许受限设置]** 并确认。
4. 返回并进入 [设置] ➔ [系统] ➔ [无障碍] ➔ 开启 **[TV YouTube 广告自动跳过服务]** 即可！
