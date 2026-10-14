# 📥 TV YouTube Ad Skipper - Installation & Setup Guide

This guide provides step-by-step instructions for installing the `TV YouTube Ad Skipper` APK on **Android TV / Google TV** and enabling the required accessibility permissions.

---

## 🚀 1. Download Latest APK

Download the latest release APK using the following link:
- **Direct Download**: [Download TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
- **GitHub Repository**: [GitHub Releases](https://github.com/k2love/TV_YouTube_Ad_Skipper)

---

## 📺 2. Installation Methods

### Method A: Wireless Smartphone Transfer (Recommended ⭐⭐⭐)
No PC or USB drive required. Takes less than 1 minute using your Android phone.

1. **Install Transfer App**: Install **'Send Files to TV' (SFTTV)** from the Google Play Store on both your TV and smartphone.
2. **File Explorer**: Install a free file manager on your TV (e.g., **'File Commander'** or **'FX File Explorer'**).
3. **Transfer File**:
   - Download [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk) to your phone.
   - Open SFTTV on your phone, tap `Send`, select the APK, and choose your TV to transfer.
4. **Install**: On your TV, open your file manager, navigate to `Download`, and click the APK to install.

---

### Method B: USB Flash Drive
1. Download [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk) on your PC and copy it to a USB drive.
2. Plug the USB drive into your TV.
3. Open your TV file manager, browse the USB drive, and install the APK.

---

### Method C: Wireless ADB from PC (Advanced / Developers)
If your PC and TV are on the same Wi-Fi network, you can install and grant permissions with single terminal commands:

1. **Enable Developer Options & USB Debugging on TV**:
   - Go to TV [Settings] ➔ [System] ➔ [About] ➔ Click [Android TV OS Build] 7 times.
   - Go to [Settings] ➔ [System] ➔ [Developer options] ➔ Turn ON **[USB Debugging]**.
2. **Check TV IP Address**: [Settings] ➔ [Network & Internet] ➔ Click connected Wi-Fi (e.g., `192.168.0.50`).
3. **Execute in PC Terminal**:
   ```powershell
   # 1. Connect to TV wirelessly
   adb connect 192.168.0.50:5555

   # 2. Install APK (keeps app data)
   adb install -r TV_YouTube_Ad_Skipper.apk

   # 3. 1-Click Accessibility Permission Grant
   adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
   adb shell settings put secure accessibility_enabled 1
   ```

---

## ⚠️ 3. Crucial: Google TV Restricted Settings Bypass

On Google TV 12, 13, and 14+, when enabling accessibility for sideloaded apps, you may see **"Restricted setting. For your security, this setting is currently unavailable."** and the toggle will be grayed out. This is a default Android security restriction that can be resolved in 10 seconds:

### Solution:
1. Press [Settings (Gear icon)] on your remote.
2. Go to **[Apps]** ➔ **[See all apps]**.
3. Select **[TV Ad Skipper]**.
4. Scroll to the bottom and click **[Allow restricted settings]**.
5. Return to the app or go to [Settings] ➔ [System] ➔ [Accessibility] ➔ Turn ON **[TV YouTube Ad Skip Service]**!

---

## ✅ 4. Verification

1. Launch **TV Ad Skipper**.
2. Check that the Accessibility Service status shows **`Running (Enabled)`** in green.
3. Open the official YouTube TV app:
   - Non-skippable ads will be auto-muted with 0ms delay.
   - When the skip countdown ends, ads will be skipped automatically within 0.001 seconds!
