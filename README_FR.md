# 📺 TV YouTube Ad Skipper (Saut et sourdine automatiques des publicités YouTube sur Android TV)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/fr/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Français-orange.svg)](#-priorité-des-langues)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **Utilitaire pour Android TV qui ignore automatiquement les publicités dans l\'application officielle YouTube TV en 0,001 s dès la fin du minuteur et coupe le son pendant les publicités forcées.**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**Français**]

---

## 🚀 Démarrage rapide (3 étapes simples)

1. **Télécharger l\'APK** : [Téléchargement direct TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Transférer sur le téléviseur** : Utilisez l\'application **'Send Files to TV' (SFTTV)** ou une clé USB.
3. **Activer l\'accessibilité** : Sur la TV : [Paramètres] ➔ [Accessibilité] ➔ Activer **[Service pour ignorer les pubs YouTube TV]** !
   - *(Sur Google TV 12+ : Voir le [Guide pour débloquer les paramètres restreints](docs/fr/INSTALLATION.md#%EF%B8%8F-3-paramètre-essentiel-débloquer-les-paramètres-restreints-sur-google-tv))*.

👉 **[Guide détaillé d\'installation (docs/fr/INSTALLATION.md)](docs/fr/INSTALLATION.md)**

---

## 🌟 Fonctionnalités principales

1. **Saut en 0,001 s et coupure du son** : Clic automatique immédiat dès l\'apparition du bouton « Passer l\'annonce » et coupure automatique du son pendant les annonces forcées.
2. **💤 Architecture d\'hibernation profonde (Deep-Sleep) pour Google TV d\'entrée de gamme (0 % de saccades en 4K)** : Arrêt complet de la boucle de surveillance pendant la vidéo principale (`serviceInfo.eventTypes = 0`) assurant une charge **0,0 % CPU/GPU/IPC**. La suppression définitive des captures d\'écran périodiques élimine les micro-ralentissements en 4K 60fps HDR sur les TV d\'entrée de gamme (TCL, Hisense, etc.).
3. **🛒 Détection sans pixels des QR codes d\'achat via YouTube Innertube API** : Au lieu de capturer l\'écran en continu, une vérification unique des métadonnées sur `/youtubei/v1/next` est effectuée au début de la vidéo. 0 capture sur plus de 95 % des vidéos standard et fermeture ciblée uniquement sur les vidéos avec produits (+4s et +8s).
4. **🎯 Protocole officiel Lounge (100 % sans erreur)** : Réception directe des signaux des serveurs Google par code TV.
5. **🌐 Prise en charge de 13 langues mondiales** : Détection multilingue des badges publicitaires et des boutons de saut dans 13 langues principales.
6. **🚀 Mises à jour sans fil (OTA)** : Détection et mise à jour automatique depuis GitHub au démarrage.

👉 **[Manuel d\'utilisation détaillé (docs/fr/USER_MANUAL.md)](docs/fr/USER_MANUAL.md)**

---

## 📜 Nouveautés de la version (v0.8.1)

- **Architecture Deep-Sleep** : Résolution définitive des pertes d\'images 4K 60fps sur Smart TV économiques.
- **Configuration d\'accessibilité allégée** : Suppression des indicateurs lourds (`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) et délai étendu à 500 ms.
- **Détection Innertube QR sans capture** : Vérification en arrière-plan sans impact graphique.
- **Préservation multilingue** : Maintien complet des expressions régulières pour 13 langues.
