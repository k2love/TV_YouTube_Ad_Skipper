# 📥 TV YouTube Ad Skipper - Installationsanleitung

Schritt-für-Schritt-Anleitung zur Installation von `TV YouTube Ad Skipper` auf **Android TV / Google TV**.

---

## 🚀 1. APK herunterladen
- **Direktlink**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. Installationsmethoden

### Methode A: Kabellose Übertragung vom Smartphone (Empfohlen ⭐⭐⭐)
1. Installieren Sie **'Send Files to TV' (SFTTV)** auf TV und Smartphone über den Play Store.
2. Installieren Sie einen Dateimanager auf dem TV (**'File Commander'**).
3. APK auf das Smartphone laden und per SFTTV an den TV senden.
4. Öffnen Sie den Ordner `Download` auf dem TV und starten Sie die Installation.

### Methode B: Über USB-Stick
APK auf einen USB-Stick kopieren, an den TV anschließen und über den Dateimanager installieren.

### Methode C: Drahtloses ADB vom PC
```powershell
adb connect <TV_IP_ADRESSE>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Wichtig: Eingeschränkte Einstellungen in Google TV aufheben
Auf Google TV 12/13/14+ kann die Meldung **„Eingeschränkte Einstellung“** erscheinen:
1. Gehen Sie zu [Einstellungen] ➔ **[Apps]** ➔ **[Alle Apps ansehen]**.
2. Wählen Sie **[TV Ad Skipper]**.
3. Scrollen Sie nach unten und klicken Sie auf **[Eingeschränkte Einstellungen zulassen]**.
4. Aktivieren Sie den Dienst danach unter [Einstellungen] ➔ [System] ➔ [Bedienungshilfen].
