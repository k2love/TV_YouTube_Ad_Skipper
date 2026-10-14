# 📥 TV YouTube Ad Skipper - دليل التثبيت

دليل تفصيلي لتثبيت `TV YouTube Ad Skipper` على **Android TV / Google TV**.

---

## 🚀 1. تحميل APK
- **رابط مباشر**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. طرق التثبيت

### الطريقة أ: إرسال لاسلكي من الهاتف (موصى به ⭐⭐⭐)
1. ثبت **'Send Files to TV' (SFTTV)** على التلفزيون والهاتف من متجر Play.
2. ثبت مدير ملفات على التلفزيون (**'File Commander'**).
3. حمل ملف APK على هاتفك وأرسله للتلفزيون عبر SFTTV.
4. افتح مجلد `Download` في التلفزيون وثبت الملف.

### الطريقة ب: عبر فلاشة USB
انسخ ملف APK إلى فلاشة USB، وصلها بالتلفزيون وثبته عبر مدير الملفات.

### الطريقة ج: عبر ADB لاسلكي من الكمبيوتر
```powershell
adb connect <IP_التلفزيون>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. إعداد هام: فك الإعدادات المقيدة في Google TV
في Google TV 12/13/14+، قد تظهر رسالة **"إعداد مقيد"**:
1. اذهب إلى [الإعدادات] ➔ **[التطبيقات]** ➔ **[عرض جميع التطبيقات]**.
2. اختر **[TV Ad Skipper]**.
3. انزل للأسفل واضغط **[السماح بالإعدادات المقيدة]**.
4. ثم قم بتفعيل الخدمة من [الإعدادات] ➔ [النظام] ➔ [إمكانية الوصول].
