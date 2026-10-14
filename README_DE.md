# 📺 TV YouTube Ad Skipper (Automatisches Überspringen und Stummschalten von YouTube-Werbung auf Android TV)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/de/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Deutsch-orange.svg)](#-sprachpriorität)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **Android TV-Dienstprogramm, das Werbung in der offiziellen YouTube TV-App innerhalb von 0,001 s nach Ablauf des Timers automatisch überspringt und nicht überspringbare Werbung hardwareseitig stummschaltet.**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**Deutsch**]

---

## 🚀 Schnellstart (3 einfache Schritte)

1. **APK herunterladen**: [Direkter Download TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Auf den Fernseher übertragen**: Über die App **'Send Files to TV' (SFTTV)** vom Smartphone oder per USB-Stick installieren.
3. **Bedienungshilfen aktivieren**: Auf dem TV: [Einstellungen] ➔ [Bedienungshilfen] ➔ **[TV YouTube Werbung-Überspringen-Dienst]** aktivieren!
   - *(Bei Google TV 12+: Siehe [Anleitung zur Aufhebung eingeschränkter Einstellungen](docs/de/INSTALLATION.md#%EF%B8%8F-3-wichtig-eingeschränkte-einstellungen-in-google-tv-aufheben))*.

👉 **[Ausführliche Installationsanleitung (docs/de/INSTALLATION.md)](docs/de/INSTALLATION.md)**

---

## 🌟 Hauptfunktionen

1. **0,001s Sofort-Überspringen & Stummschaltung**: Klickt sofort auf «Werbung überspringen» und schaltet Zwangswerbung automatisch stumm.
2. **💤 Deep-Sleep-Architektur für Einsteiger-Google TVs (0% Ruckeln bei 4K)**: Stoppt die Hintergrund-Überwachungsschleife während des Hauptvideos (`serviceInfo.eventTypes = 0`) vollständig für **0,0% CPU/GPU/IPC-Last**. Die dauerhafte Abschaffung periodischer Screenshots behebt 4K 60fps HDR-Mikroruckler auf TV-Geräten von TCL, Hisense etc.
3. **🛒 Pixellose QR-Einkaufswerbungs-Erkennung via YouTube Innertube API**: Statt permanenter Bildschirmaufnahmen erfolgt eine einmalige Hintergrundabfrage der Metadaten über `/youtubei/v1/next` beim Videostart. 0 Screenshots bei 95%+ normalen Videos und punktgenaues Schließen nur bei Videos mit Produkten (+4s und +8s).
4. **🎯 Offizieller Lounge-Protokoll-Modus (100% fehlerfrei)**: Direkter Signalempfang von Google-Servern über TV-Code.
5. **🌐 Unterstützung für 13 globale Sprachen**: Zuverlässige Erkennung von Werbe-Badges und Skip-Buttons in 13 Hauptsprachen.
6. **🚀 Drahtlose Auto-Updates (OTA)**: Prüft beim Start automatisch auf neue Versionen auf GitHub und aktualisiert mit 1 Klick.

👉 **[Ausführliches Benutzerhandbuch (docs/de/USER_MANUAL.md)](docs/de/USER_MANUAL.md)**

---

## 📜 Versions-Neuheiten (v0.8.1)

- **Deep-Sleep-Architektur**: Vollständige Beseitigung von 4K 60fps Frame-Drops auf günstigen Smart-TVs.
- **Bereinigte Barrierefreiheits-Konfiguration**: Entfernung schwerer Flags (`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) und Entspannung des Timeouts auf 500ms.
- **Innertube QR-Prüfung ohne Screenshots**: Hintergrund-Metadatenprüfung ohne Grafikeinbußen.
- **Mehrsprachigkeit vollständig bewahrt**: Vollständige Beibehaltung der Erkennungsmuster für 13 Sprachen.
