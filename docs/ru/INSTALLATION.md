# 📥 TV YouTube Ad Skipper - Руководство по установке

Инструкция по установке `TV YouTube Ad Skipper` на **Android TV / Google TV** и предоставлению прав доступа.

---

## 🚀 1. Скачивание APK
- **Прямая ссылка**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. Способы установки

### Способ А: Передача со смартфона (Рекомендуется ⭐⭐⭐)
1. Установите **'Send Files to TV' (SFTTV)** на ТВ и телефон из Google Play.
2. Установите файловый менеджер на ТВ (**'File Commander'**).
3. Скачайте APK на смартфон и отправьте его на ТВ через SFTTV.
4. Откройте папку `Download` на ТВ и запустите установку.

### Способ Б: Через USB-флешку
Скопируйте скачанный APK на USB-накопитель, подключите к ТВ и установите через файловый менеджер.

### Способ В: Беспроводной ADB с ПК
```powershell
adb connect <IP_АДРЕС_ТВ>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Обязательно: Снятие ограничений в Google TV

В Google TV 12/13/14+ система может показать **«Ограниченная настройка»**:
1. Перейдите в [Настройки] ➔ **[Приложения]** ➔ **[Все приложения]**.
2. Выберите **[TV Ad Skipper]**.
3. Прокрутите вниз и нажмите **[Разрешить ограниченные настройки]**.
4. После этого включите службу в [Настройки] ➔ [Система] ➔ [Спец. возможности].
