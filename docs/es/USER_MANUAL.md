# 📖 TV YouTube Ad Skipper - Manual de usuario

`TV YouTube Ad Skipper` es una utilidad todo en uno para Android TV que salta anuncios automáticamente y silencia el audio del hardware en la app oficial de YouTube.

---

## 🎯 1. Modos de funcionamiento dual

### 🌟 [Modo 1] Protocolo Lounge oficial (Recomendado: 100% sin errores)
- **Principio**: Conexión directa mediante la sesión oficial de YouTube por código de TV. Recibe señales directas de los servidores de Google y salta anuncios en 0.001 segundos sin escanear la pantalla.
- **Pasos de vinculación**:
  1. Abre la app de YouTube en tu TV, ve a [Ajustes] ➔ **[Vincular con código de TV]**.
  2. Anota el **código de 12 dígitos**.
  3. Abre TV Ad Skipper, introduce los 12 dígitos y pulsa **[Vincular TV]**.
  4. Una vez en verde («Vinculado»), la configuración estará completa y se reconectará automáticamente tras reiniciar.

---

### 💤 [Novedad] Arquitectura Deep-Sleep para Google TV de gama de entrada
Soluciona definitivamente los tirones y pérdidas de fotogramas al reproducir 4K 60fps HDR en televisores económicos (TCL, Hisense, etc.).

- **100% inactivo durante el video principal**: Cuando el video regular está reproduciéndose (`isAdPlaying == false`), el bucle de supervisión se detiene por completo y se fija `serviceInfo.eventTypes = 0`, garantizando **0.0% de carga en CPU/GPU/IPC**.
- **Sin capturas de pantalla periódicas**: Evita la degradación del plano de video por hardware (HWC) suprimiendo capturas periódicas.
- **Despertar instantáneo solo con anuncios**: Se silencia en 0.001s y se activa momentáneamente para saltar el anuncio, volviendo a hibernar de inmediato.

---

### 🛒 [Novedad] Detección de anuncios QR de compras vía YouTube Innertube API
- **Principio**: En lugar de capturar la pantalla constantemente, consulta los metadatos de compras en `/youtubei/v1/next` solo 1 vez al iniciar el video.
- **Cero sobrecarga**: Mantiene 0 capturas en más del 95% de videos estándar y solo cierra los cuadros QR en el <5% de videos con productos etiquetados (a los `+4.0s` y `+8.0s`).

---

### 👁️ [Modo 2] Sensor de color de píxeles (Visión artificial)
- **Principio**: Supervisa el temporizador circular amarillo y hace clic con precisión milimétrica en el botón blanco de saltar al finalizar la cuenta atrás.
- **Protección contra falsos positivos**: Se desactiva completamente durante el video normal para no confundir subtítulos amarillos con anuncios.

---

## 🛡️ 2. Ajustes principales

- **Silencio automático de anuncios**: Silencia anuncios no saltables y restablece el volumen exacto.
- **Omisión automática de pop-ups de Shorts**: Cierra avisos de desconexión en 0.1s.
- **Cierre automático de QR de compras**: Cierra banners de compras solo en videos identificados con productos.
- **Actualización inalámbrica (OTA)**: Comprobación automática y actualización en 1 clic desde GitHub.
