# 📖 TV YouTube Ad Skipper - Manuel d\'utilisation

`TV YouTube Ad Skipper` est un utilitaire en arrière-plan pour Android TV qui ignore automatiquement les publicités YouTube et coupe le son au niveau matériel.

---

## 🎯 1. Modes de fonctionnement

### 🌟 [Mode 1] Protocole officiel Lounge (Recommandé : 100 % sans erreur)
- **Principe** : Connexion directe via la session officielle de YouTube par code TV. Reçoit les signaux des serveurs Google et ignore les annonces en 0,001 s sans analyse d\'écran.
- **Étapes d\'association** :
  1. Ouvrez YouTube sur votre TV, accédez à [Paramètres] ➔ **[Associer avec un code TV]**.
  2. Notez le **code à 12 chiffres**.
  3. Ouvrez TV Ad Skipper, saisissez les 12 chiffres et appuyez sur **[Associer la TV]**.
  4. Le statut passe au vert (« Associé ») et la reconnexion est automatique après redémarrage.

---

### 💤 [Nouveau] Architecture Deep-Sleep pour Google TV d\'entrée de gamme
Élimine définitivement les saccades et pertes d\'images lors de la lecture 4K 60fps HDR sur TV économiques (TCL, Hisense, etc.).

- **100 % en veille pendant la vidéo principale** : La boucle de surveillance est arrêtée (`serviceInfo.eventTypes = 0`) assurant une charge **0,0 % CPU/GPU/IPC**.
- **Suppression des captures périodiques** : Protège le plan de superposition vidéo matériel (HWC).
- **Réveil instantané uniquement sur les annonces** : Coupure du son en 0,001 s, saut rapide et retour immédiat au sommeil profond.

---

### 🛒 [Nouveau] Détection sans pixels des annonces d\'achat QR via YouTube Innertube API
- **Principe** : Requête unique en arrière-plan sur `/youtubei/v1/next` au démarrage de la vidéo pour inspecter les métadonnées de produits.
- **Zéro charge** : 0 capture sur plus de 95 % des vidéos standard et fermeture ciblée à `+4,0s` et `+8,0s` uniquement sur les vidéos comportant des produits.

---

### 👁️ [Mode 2] Capteur de couleur de pixels (Vision par ordinateur)
- **Principe** : Surveille le compte à rebours jaune et clique sur le bouton blanc avec une précision chirurgicale.
- **Anti-faux positifs** : Totalement désactivé pendant la vidéo normale pour ne pas réagir aux sous-titres.

---

## 🛡️ 2. Réglages principaux

- **Coupure automatique du son** : Coupe les annonces forcées et rétablit le volume exact.
- **Ignorer les pop-ups Shorts** : Fermeture automatique en 0,1 s.
- **Fermer les annonces d\'achat QR** : Fermeture automatique uniquement sur les vidéos de shopping.
- **Mise à jour sans fil (OTA)** : Détection et mise à jour automatique depuis GitHub en 1 clic.
