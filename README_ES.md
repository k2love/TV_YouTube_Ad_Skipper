# 📺 TV YouTube Ad Skipper (Salto automático y silencio de anuncios para Android TV)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/es/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Español-orange.svg)](#-prioridad-de-idiomas)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **Utilidad para Android TV que salta anuncios automáticamente en la app oficial de YouTube TV en 0.001 segundos cuando termina el contador, y silencia por hardware durante los anuncios obligatorios.**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**Español**]

---

## 🚀 Inicio rápido (Instalación en 3 pasos)

1. **Descargar APK**: [Descarga directa TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Transferir a la TV**: Envía el archivo desde tu móvil con la app **'Send Files to TV' (SFTTV)** o usa una memoria USB.
3. **Activar accesibilidad**: En la TV: [Ajustes] ➔ [Accesibilidad] ➔ Activar **[Servicio para saltar anuncios de TV YouTube]**.
   - *(Si tienes Google TV 12+: Consulta la [Guía para desbloquear ajustes restringidos](docs/es/INSTALLATION.md#%EF%B8%8F-3-configuración-esencial-desbloquear-ajustes-restringidos-en-google-tv))*.

👉 **[Guía detallada de instalación (docs/es/INSTALLATION.md)](docs/es/INSTALLATION.md)**

---

## 🌟 Características principales

### 1. Salto en 0.001s y silencio automático
- Clic automático instantáneo al aparecer el botón "Saltar anuncio".
- Silencia los anuncios no saltables y restablece el volumen exacto al volver al video.

### 2. 💤 Arquitectura de hibernación profunda (Deep-Sleep) para Google TV de baja potencia (0% cortes en 4K)
- Detiene por completo el bucle centinela en segundo plano durante la reproducción del video principal (`isAdPlaying == false`), desactivando la recepción de eventos de accesibilidad (`serviceInfo.eventTypes = 0`) para un consumo del **0.0% de CPU/GPU/IPC**.
- Elimina permanentemente las capturas de pantalla periódicas durante el video principal, evitando caídas de fotogramas en televisores económicos (TCL, Hisense, etc.) al reproducir 4K 60fps HDR.

### 3. 🛒 Detección de anuncios QR de compras vía YouTube Innertube API (Sin píxeles)
- En lugar de capturar la pantalla constantemente, consulta los metadatos de compras en `/youtubei/v1/next` solo 1 vez al iniciar el video.
- Mantiene 0 capturas en más del 95% de videos habituales y solo cierra los cuadros QR en el <5% de videos con productos etiquetados (a los `+4.0s` y `+8.0s`).

### 4. 🎯 Protocolo Lounge oficial (Recomendado: 100% sin errores)
- Vinculación directa con la sesión oficial de YouTube mediante código de TV.
- Recibe señales directas de los servidores de Google sin escanear la pantalla.

### 5. 🌐 Soporte global en 13 idiomas
- Detección precisa de insignias de publicidad y botones de salto en 13 idiomas principales.

### 6. 🚀 Actualizaciones inalámbricas (OTA)
- Detecta nuevas versiones de GitHub al iniciar la app y actualiza en 1 clic.

👉 **[Manual de usuario y opciones (docs/es/USER_MANUAL.md)](docs/es/USER_MANUAL.md)**

---

## 📜 Novedades de la versión (v0.8.1)

- **Arquitectura Deep-Sleep**: Eliminación total de micro-tirones en 4K 60fps en televisores Google TV de gama de entrada.
- **Optimización de accesibilidad**: Eliminación de banderas de alta carga (`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) y reducción del timeout a 500ms.
- **Detección de QR de compras sin capturas**: Verificación única de metadatos en segundo plano.
- **Soporte multilingüe integral**: Preservación de 13 idiomas para insignias y botones de anuncios.
