# 📥 TV YouTube Ad Skipper - Guide d\'installation

Instructions pour installer `TV YouTube Ad Skipper` sur **Android TV / Google TV** et activer les autorisations requises.

---

## 🚀 1. Téléchargement de l\'APK
- **Téléchargement direct** : [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. Méthodes d\'installation

### Méthode A : Envoi sans fil depuis le smartphone (Recommandé ⭐⭐⭐)
1. Installez **'Send Files to TV' (SFTTV)** sur la TV et le smartphone depuis le Play Store.
2. Installez un gestionnaire de fichiers sur la TV (**'File Commander'**).
3. Téléchargez l\'APK sur le smartphone et envoyez-le à la TV via SFTTV.
4. Ouvrez le dossier `Download` sur la TV et lancez l\'installation.

### Méthode B : Clé USB
Copiez l\'APK sur une clé USB, connectez-la à la TV et installez via le gestionnaire de fichiers.

### Méthode C : ADB sans fil depuis un PC
```powershell
adb connect <IP_DE_LA_TV>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Paramètre essentiel : Débloquer les paramètres restreints sur Google TV
1. Allez dans [Paramètres] ➔ **[Applications]** ➔ **[Afficher toutes les applications]**.
2. Sélectionnez **[TV Ad Skipper]**.
3. Faites défiler vers le bas et cliquez sur **[Autoriser les paramètres restreints]**.
4. Activez ensuite le service dans [Paramètres] ➔ [Système] ➔ [Accessibilité].
