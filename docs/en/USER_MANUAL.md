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

### 💤 [New in v0.8.1] Zero-Overhead Deep-Sleep Architecture for Low-End Google TVs
Solves the recurring frame-drop and micro-stutter issue when playing high-bitrate 4K 60fps HDR videos on budget Google TVs (TCL, Hisense, etc.).

- **100% Dormant During Main Content**: When regular content is playing (`isAdPlaying == false`), the background sentinel polling loop is completely stopped (`removeCallbacks`), and runtime accessibility event reception is set to `eventTypes = 0`, guaranteeing **0.0% CPU/GPU/IPC overhead**.
- **No Periodic Screenshots**: Permanently abolishes periodic screenshot calls during main video playback, ensuring the TV's Hardware Video Overlay (HWC) plane never falls back to GPU composition.
- **Instant Wake-Up Only on Ads**: As soon as `LoungeManager` receives an ad start signal, hardware mute is engaged within 0.001s, and the sentinel is temporarily awakened to assist skipping before returning to deep sleep immediately when main playback resumes.

---

### 🛒 [New in v0.8.1] Non-Pixel YouTube Innertube API QR Shopping Ad Detection
- **How it works**: Instead of constantly capturing and analyzing the screen, the app makes a single-shot background query to YouTube\'s internal Innertube API (`https://www.youtube.com/youtubei/v1/next`) on video entry to inspect shopping metadata (`shoppingOverlay`, `merchandiseShelf`, etc.).
- **Zero Overhead**: Maintains **0 screen captures** on 95%+ of regular videos, and only performs pinpoint closes at `+4.0s` and `+8.0s` on the <5% of videos that actually feature tagged merchandise.

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
| **Auto-Dismiss QR Shopping Ads** | **ON** | Uses non-pixel Innertube pre-checking to automatically close affiliate QR shopping overlays at exact coordinates (12.4%, 89.3%) on shopping videos. |

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
