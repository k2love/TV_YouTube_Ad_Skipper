# 📖 TV YouTube Ad Skipper - Benutzerhandbuch

`TV YouTube Ad Skipper` ist ein Hintergrunddienst für Android TV, der YouTube-Werbung automatisch überspringt und den Ton auf Hardwareebene stummschaltet.

---

## 🎯 1. Dual-Betriebsmodi

### 🌟 [Modus 1] Offizieller Lounge-Protokoll-Modus (Empfohlen: 100% fehlerfrei)
- **Funktionsweise**: Direkte Verbindung über die offizielle YouTube-Sitzung per TV-Code. Empfängt Signale von Google-Servern und überspringt Werbung in 0,001s ohne Bildschirmanalyse.
- **Verbindungsschritte**:
  1. Öffnen Sie YouTube auf Ihrem TV, navigieren Sie zu [Einstellungen] ➔ **[Mit TV-Code verknüpfen]**.
  2. Notieren Sie den **12-stelligen Code**.
  3. Öffnen Sie TV Ad Skipper, geben Sie den Code ein und klicken Sie auf **[TV verknüpfen]**.
  4. Der grüne Status bestätigt die dauerhafte Verbindung mit automatischem Reconnect.

---

### 💤 [Neu] Deep-Sleep-Architektur für Einsteiger-Google TVs
Beseitigt Mikroruckler und Bildaussetzer bei der 4K 60fps HDR-Wiedergabe auf günstigen TV-Geräten (TCL, Hisense etc.).

- **100% Ruhemodus während des Hauptvideos**: Die Überwachungsschleife pausiert vollständig (`serviceInfo.eventTypes = 0`) für **0,0% CPU/GPU/IPC-Last**.
- **Abschaffung periodischer Screenshots**: Schützt die Hardware-Overlay-Ebene (HWC) des Fernsehers.
- **Sofortiges Aufwachen nur bei Werbung**: Stummschaltung in 0,001s, assistiertes Überspringen und unmittelbare Rückkehr in den Tiefschlaf.

---

### 🛒 [Neu] Pixellose QR-Einkaufswerbungs-Erkennung via YouTube Innertube API
- **Funktionsweise**: Einmalige Hintergrundabfrage der Metadaten über `/youtubei/v1/next` beim Videostart statt permanenter Screenshots.
- **Null Belastung**: 0 Bildschirmaufnahmen bei über 95% der normalen Videos und gezieltes Schließen nur bei Videos mit Produkten (+4s und +8s).

---

### 👁️ [Modus 2] Computer-Vision-Farbsensor
- **Funktionsweise**: Überwacht den gelben Countdown und klickt punktgenau auf die weiße Skip-Schaltfläche.
- **Fehlalarmschutz**: Während regulärer Videos komplett inaktiv.

---

## 🛡️ 2. Haupteinstellungen

- **Automatische Stummschaltung**: Schaltet Werbung stumm und stellt die Originallautstärke wieder her.
- **Shorts-Popups überspringen**: Schließt Bestätigungsfenster in 0,1s.
- **QR-Einkaufswerbung schließen**: Schließt Werbebanner gezielt bei Shopping-Videos.
- **Drahtlose Auto-Updates (OTA)**: Automatische Update-Prüfung von GitHub beim Start.
