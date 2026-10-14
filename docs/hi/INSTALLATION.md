# 📥 TV YouTube Ad Skipper - इंस्टॉलेशन गाइड

Android TV / Google TV पर `TV YouTube Ad Skipper` इंस्टॉल करने की चरणबद्ध मार्गदर्शिका।

---

## 🚀 1. APK डाउनलोड
- **सीधा लिंक**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. इंस्टॉलेशन विधियां

### विधि A: फोन से वायरलेस ट्रांसफर (अनुशंसित ⭐⭐⭐)
1. टीवी और फोन पर Play Store से **'Send Files to TV' (SFTTV)** इंस्टॉल करें।
2. टीवी पर फाइल मैनेजर ऐप (**'File Commander'**) इंस्टॉल करें।
3. फोन में APK डाउनलोड करके SFTTV से टीवी पर भेजें।
4. टीवी के फाइल मैनेजर से `Download` फोल्डर में जाकर APK इंस्टॉल करें।

### विधि B: USB ड्राइव द्वारा
APK को USB में कॉपी करें और टीवी में लगाकर फाइल मैनेजर से इंस्टॉल करें।

### विधि C: वायरलेस ADB कमांड
```powershell
adb connect <टीवी_का_IP>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Google TV में प्रतिबंधित सेटिंग्स अनलॉक करें
Google TV 12+ पर एक्सेसिबिलिटी अक्षम होने पर:
1. टीवी [सेटिंग्स] ➔ **[ऐप्स]** ➔ **[सभी ऐप्स देखें]**।
2. **[TV Ad Skipper]** चुनें।
3. नीचे जाकर **[प्रतिबंधित सेटिंग्स की अनुमति दें]** पर क्लिक करें।
4. इसके बाद एक्सेसिबिलिटी सेटिंग्स में जाकर सेवा चालू करें।
