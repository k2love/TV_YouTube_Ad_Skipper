# 📥 TV YouTube Ad Skipper - Guia de Instalação

Instruções para instalar o `TV YouTube Ad Skipper` no **Android TV / Google TV** e ativar as permissões necessárias.

---

## 🚀 1. Download do APK
- **Download direto**: [TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)

---

## 📺 2. Métodos de Instalação

### Método A: Envio sem fio do celular (Recomendado ⭐⭐⭐)
1. Instale o **'Send Files to TV' (SFTTV)** na TV e no celular pela Play Store.
2. Instale um gerenciador de arquivos na TV (**'File Commander'**).
3. Baixe o APK no celular e envie para a TV pelo SFTTV.
4. Na TV, abra a pasta `Download` e instale o APK.

### Método B: Pendrive USB
Copie o APK para o pendrive, conecte à TV e instale pelo gerenciador de arquivos.

### Método C: ADB sem fio do PC
```powershell
adb connect <IP_DA_TV>:5555
adb install -r TV_YouTube_Ad_Skipper.apk
adb shell settings put secure enabled_accessibility_services com.example.tvadskipper/.TvAdSkipService
adb shell settings put secure accessibility_enabled 1
```

---

## ⚠️ 3. Configuração essencial: Desbloquear configurações restritas no Google TV
1. Vá em [Configurações] ➔ **[Apps]** ➔ **[Ver todos os apps]**.
2. Selecione **[TV Ad Skipper]**.
3. Role até o final e clique em **[Permitir configurações restritas]**.
4. Em seguida, ative o serviço em [Configurações] ➔ [Sistema] ➔ [Acessibilidade].
