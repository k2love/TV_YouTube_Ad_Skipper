# 📺 TV YouTube Ad Skipper (Pular e Silenciar Anúncios do YouTube no Android TV)

[![Version](https://img.shields.io/badge/version-v0.8.1-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-green.svg)](docs/pt/INSTALLATION.md)
[![Language](https://img.shields.io/badge/language-Português-orange.svg)](#-prioridade-de-idiomas)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

> **Utilitário para Android TV que pula anúncios no app oficial do YouTube TV em 0,001s quando o cronômetro termina e silencia o hardware durante anúncios obrigatórios.**

[**한국어 (기본)**](README.md) | [**English**](README_EN.md) | [**Português**]

---

## 🚀 Início Rápido (3 Passos Simples)

1. **Baixar APK**: [Download direto TV_YouTube_Ad_Skipper.apk](https://raw.githubusercontent.com/k2love/TV_YouTube_Ad_Skipper/main/TV_YouTube_Ad_Skipper.apk)
2. **Transferir para a TV**: Envie do smartphone com o app **'Send Files to TV' (SFTTV)** ou use um pendrive USB.
3. **Ativar Acessibilidade**: Na TV: [Configurações] ➔ [Acessibilidade] ➔ Ativar **[Serviço para pular anúncios do YouTube na TV]**!
   - *(No Google TV 12+: Consulte o [Guia para liberar configurações restritas](docs/pt/INSTALLATION.md#%EF%B8%8F-3-configuração-essencial-desbloquear-configurações-restritas-no-google-tv))*.

👉 **[Guia detalhado de instalação (docs/pt/INSTALLATION.md)](docs/pt/INSTALLATION.md)**

---

## 🌟 Principais Recursos

1. **Pulo em 0,001s e Silêncio Automático**: Clique instantâneo assim que o botão "Pular anúncio" aparece e mudo automático durante anúncios obrigatórios.
2. **💤 Arquitetura de Hibernação Profunda (Deep-Sleep) para Google TV de Entrada (0% travamentos em 4K)**: Interrompe 100% dos loops de monitoramento em segundo plano durante o vídeo principal (`serviceInfo.eventTypes = 0`) para **0,0% de sobrecarga de CPU/GPU/IPC**. A remoção definitiva de capturas de tela periódicas elimina microtravamentos em 4K 60fps HDR em TVs TCL, Hisense, etc.
3. **🛒 Detecção de Anúncios QR de Compras via YouTube Innertube API (Sem pixels)**: Em vez de capturas constantes, realiza 1 consulta única aos metadados em `/youtubei/v1/next` no início do vídeo. Mantém 0 capturas em 95%+ dos vídeos normais e só fecha os cartões QR nos vídeos com produtos etiquetados (+4s e +8s).
4. **🎯 Protocolo Lounge Oficial (100% sem erros)**: Vinculação direta com a sessão do YouTube por código de TV.
5. **🌐 Suporte a 13 Idiomas Globais**: Detecção nativa de emblemas e botões de pulo em 13 idiomas principais.
6. **🚀 Atualização Sem Fio (OTA)**: Verifica novas versões no GitHub ao iniciar e atualiza em 1 clique.

👉 **[Manual completo do usuário (docs/pt/USER_MANUAL.md)](docs/pt/USER_MANUAL.md)**

---

## 📜 Novidades da Versão (v0.8.1)

- **Arquitetura Deep-Sleep**: Fim dos travamentos de quadros em 4K 60fps em Smart TVs básicas.
- **Configuração de Acessibilidade Leve**: Remoção de flags pesadas (`flagRetrieveInteractiveWindows`, `typeWindowContentChanged`) e timeout ajustado para 500ms.
- **Detecção Innertube QR**: Verificação de metadados sem impacto visual ou no hardware.
- **Suporte Multilíngue Total**: Algoritmo mantido com suporte a 13 idiomas.
