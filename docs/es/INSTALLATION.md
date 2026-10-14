# 📥 TV YouTube Ad Skipper - Guía de instalación

Esta guía explica paso a paso cómo instalar `TV YouTube Ad Skipper` en **Android TV / Google TV** y habilitar los permisos de accesibilidad necesarios.

---

## 🚀 1. Descarga del APK

- **Descarga directa**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
- **Repositorio**: [GitHub Releases](https://github.com/k2love/TV_YouTube_Ad_Skipper)

---

## 📺 2. Métodos de instalación

### Método A: Envío inalámbrico desde el móvil (Recomendado ⭐⭐⭐)
1. Instala **'Send Files to TV' (SFTTV)** en tu TV y móvil desde Google Play.
2. Instala un administrador de archivos en la TV (**'File Commander'**).
3. Descarga el APK en el móvil y envíalo a la TV con SFTTV.
4. En la TV, entra en la carpeta `Download` y ejecuta el APK.

### Método B: Memoria USB
1. Copia el APK descargado en un USB e insértalo en la TV.
2. Abre el explorador de archivos en la TV e instala el archivo.

### Método C: ADB inalámbrico desde PC
```powershell
adb connect <IP_DE_LA_TV>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Configuración esencial: Ajustes restringidos en Google TV

En Google TV 12/13/14+, el sistema puede mostrar **"Ajuste restringido"**:
1. Ve a [Ajustes] ➔ **[Aplicaciones]** ➔ **[Ver todas las aplicaciones]**.
2. Selecciona **[TV Ad Skipper]**.
3. Baja al final y pulsa **[Permitir ajustes restringidos]**.
4. Ahora entra en [Ajustes] ➔ [Sistema] ➔ [Accesibilidad] y activa **[Servicio para saltar anuncios de TV YouTube]**.
