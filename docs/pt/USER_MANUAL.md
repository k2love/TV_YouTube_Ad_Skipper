# 📖 TV YouTube Ad Skipper - Manual do Usuário

`TV YouTube Ad Skipper` é um utilitário em segundo plano completo para Android TV que pula anúncios instantaneamente e silencia o áudio do hardware no app oficial do YouTube.

---

## 🎯 1. Modos de Operação Duplos

### 🌟 [Modo 1] Protocolo Lounge Oficial (Recomendado: 100% sem erros)
- **Princípio**: Conexão direta pela sessão oficial do YouTube por código de TV. Recebe sinais diretos dos servidores do Google e pula anúncios em 0,001s sem escanear a tela.
- **Passos de Vinculação**:
  1. Abra o YouTube na TV, vá em [Configurações] ➔ **[Vincular com código da TV]**.
  2. Anote o **código de 12 dígitos**.
  3. Abra o TV Ad Skipper, insira os 12 dígitos e clique em **[Vincular TV]**.
  4. O status verde confirma a conexão automática contínua.

---

### 💤 [Novidade] Arquitetura Deep-Sleep para Google TV de Entrada
Elimina travamentos e perda de quadros na reprodução de 4K 60fps HDR em Smart TVs de entrada (TCL, Hisense, etc.).

- **100% inativo durante o vídeo principal**: Enquanto o vídeo normal é reproduzido (`isAdPlaying == false`), o loop de monitoramento para totalmente (`serviceInfo.eventTypes = 0`) para **0,0% de sobrecarga de CPU/GPU/IPC**.
- **Sem capturas periódicas de tela**: Evita perda de desempenho no hardware overlay (HWC).
- **Despertar instantâneo apenas em anúncios**: Mudo imediato em 0,001s, pulo assistido e retorno instantâneo ao sono profundo.

---

### 🛒 [Novidade] Detecção de Anúncios QR de Compras via YouTube Innertube API
- **Princípio**: Consulta única aos metadados em `/youtubei/v1/next` no início do vídeo, sem capturas contínuas.
- **Sobrecarga zero**: 0 capturas em 95%+ dos vídeos normais e fechamento preciso a `+4,0s` e `+8,0s` apenas em vídeos com produtos.

---

### 👁️ [Modo 2] Sensor de Cores de Pixels (Visão Computacional)
- **Princípio**: Monitora a contagem regressiva amarela e clica no botão branco de pulo com precisão milimétrica.
- **Anti-falsos positivos**: Fica completamente desativado durante vídeos normais.

---

## 🛡️ 2. Principais Configurações

- **Silêncio automático de anúncios**: Silencia anúncios e restaura o volume anterior com precisão.
- **Ignorar pop-ups de Shorts**: Fecha avisos de desconexão em 0,1s.
- **Fechar anúncios QR de compras**: Fecha banners de compras apenas em vídeos com produtos.
- **Atualização Sem Fio (OTA)**: Verificação automática e atualização em 1 clique pelo GitHub.
