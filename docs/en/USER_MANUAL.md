# 📖 TV YouTube Ad Skipper - User Manual

`TV YouTube Ad Skipper` is an all-in-one background utility for Android TV that provides instant ad skipping and hardware auto-muting for the official YouTube TV app.

---

## 🎯 1. Dual-Engine Operating Modes

The app features two powerful engines that operate in synergy:

### 🌟 [Mode 1] Official Lounge Protocol Mode (Recommended: 100% Error-Free)
Bypasses on-screen visual scanning and leverages YouTube\'s official **\'Link with TV code\'** protocol.

- **How it works**: Connects directly to Google\'s WebSocket channel used for smart TV casting. It receives real-time ad completion signals (`onAdStateChange`, `isSkipEnabled=true`) directly from Google servers and triggers skipping within 0.001 seconds.
- **Advantages**: 100% immune to screen resolution changes, complex subtitles, or YouTube UI redesigns.
- **Setup Steps (One-time only)**:
  1. Open the **official YouTube app** on your TV.
  2. Navigate to the left menu: **[Settings]** ➔ **[Link with TV code]**.
  3. Note down the **12-digit code** (e.g., `123 456 789 012`).
  4. Open **TV Ad Skipper**, enter the 12 digits in the Lounge Card, and click **[Link TV]**.
  5. Once the status turns green (**`Linked (YouTube on TV) - Auto-monitoring active`**), setup is complete! The app automatically reconnects on TV reboot.

---

### 👁️ [Mode 2] Computer Vision Pixel Color Sensor (Shorts-Safe Visual Mode)
A lightweight visual detection engine that operates locally via Android Accessibility without needing a Lounge code.

- **How it works**:
  1. Monitors the bottom-right circular yellow countdown timer during ads to maintain hardware audio mute.
  2. Detects the white pill-shaped "Skip" button the moment the countdown ends and performs an exact 1-time pinpoint click.
- **False-Positive Prevention**:
  - The pixel sensor is completely deactivated during normal video playback, preventing accidental muting from yellow subtitles, dawn/dusk lighting, or yellow logos.

---

## 🛡️ 2. Core Features & Settings

| Setting | Default | Description |
| :--- | :---: | :--- |
| **Hardware Auto-Mute** | **ON** | Automatically mutes TV audio stream during ads and restores the exact previous volume when main content starts. |
| **Pixel Color Sensor (Android 11+)** | **ON** | Detects the skip button via low-overhead background pixel scanning without causing video stutter or frame drops. |
| **Auto-Dismiss Shorts Disconnect Prompt** | **ON** | Automatically confirms and bypasses the casting disconnect dialog within 0.1s when switching to Shorts. |
| **Auto-Dismiss QR Shopping Ads** | **ON** | Automatically clicks and closes affiliate QR shopping banners in the bottom-left corner at exact coordinates (12.4%, 89.3%). |

---

## 📊 3. Monitoring & Utilities

### Ad Skip Counter & Reset
- Displays the count of skipped ads since the last TV boot.
- Click **[Reset Count]** at any time to reset the counter to 0.

### 🚀 Wireless Auto-Update (OTA)
- Automatically checks GitHub for new APK releases upon app startup.
- Prompts with a changelog dialog and supports 1-click in-app download and installation.
- Can be manually triggered via **[🔄 Check Update Now]**.

### 📋 Live Diagnostic Log Viewer
- Real-time events, volume levels, and sensor detections are stored locally on the TV.
- Click **[📄 View Live Logs]** to inspect the last 150 lines.
- *(External Google Drive uploads have been removed in v0.8.0 for complete privacy)*.
