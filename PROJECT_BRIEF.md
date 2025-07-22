
> **Projeto:** Assistente em Tempo Real para Vuzix M400 (Android 13) + Companion Desktop  
> **Versão:** 1 (MVP) – última atualização: <!-- preencha a data quando editar -->

---

## 1. Visão & Objetivo

Criar um sistema mínimo, porém funcional, que permita:
1. **Capturar áudio** (e, sob demanda, snapshots de vídeo) no Vuzix M400.  
2. **Transmitir em tempo real** esses dados para um app Companion no computador via WebRTC.  
3. **Encaminhar o áudio (e snapshots) para a OpenAI Realtime API** a partir do Companion, recebendo respostas em texto e/ou áudio.  
4. **Exibir o texto imediatamente no visor do M400** (HUD) e tocar áudio de resposta opcionalmente.  
5. Permitir **edição dinâmica do prompt do agente** e adição de contexto (arquivos, instruções) no Companion, usando o **OpenAI Agents SDK (TS)**.

**Princípio**: “O mais simples possível que funcione”, com latência baixa e fácil manutenção.

---

## 2. Escopo da V1 (MVP)

### Incluído
- Canal WebRTC entre óculos e PC (áudio contínuo + DataChannel para JSON/imagens).  
- Encaminhamento de áudio para Realtime API e retorno de texto/áudio.  
- Snapshots de câmera sob demanda (não há streaming de vídeo contínuo).  
- HUD simples para texto no M400.  
- UI mínima no Companion para editar o system prompt e anexar arquivos de contexto.  
- Métricas básicas de latência e logs.

### Excluído (pós‑MVP)
- Streaming de vídeo contínuo (H.264) para o modelo.  
- Multi-dispositivos simultâneos.  
- Tool calling complexo, memória vetorial, bancos de dados.  
- Fallback de STT local (Whisper) — só se solicitado.  
- Distribuição local/manual via ADB apenas. (Nenhum deploy em loja ou MDM nesta versão).

---

## 3. Usuários & Casos de Uso

### Personas
- **Operador/Usuário no campo**: usa o M400 para falar e ver respostas.  
- **Operador técnico (no PC)**: monitora, ajusta prompt e contexto, envia comandos (ex.: solicitar snapshot).  
- **Desenvolvedor/Você**: evolui o MVP, mede latência, ajusta libs.

### Histórias-chave
1. *“Como usuário com o óculos, eu falo e quero ver a resposta rapidamente na tela.”*  
2. *“Como operador no PC, quero editar o prompt do agente durante a sessão e ver logs.”*  
3. *“Como usuário, quero capturar uma imagem do que estou vendo e obter contexto/resposta do modelo.”*  
4. *“Como dev, quero medir quanto tempo demora entre falar e ver a resposta no HUD.”*

---

## 4. Requisitos Funcionais

1. **Streaming de Áudio Upstream**: 16 kHz mono, via WebRTC até o PC.  
2. **Bridge Realtime**: Companion envia áudio para OpenAI Realtime, recebe `response.delta` (texto) e `response.audio.delta` (áudio).  
3. **HUD Overlay**: serviço foreground no Android que mostra texto com baixa latência, com opção de ocultar.  
4. **Snapshots**: captura via Camera2 e envio JPEG pelo DataChannel; Companion envia à API de imagens.  
5. **Prompt Editor**: UI para alterar `system prompt` e anexar arquivos ao agente.  
6. **Logs & Métricas**: timestamps, latência média, exportação simples de logs.

---

## 5. Requisitos Não Funcionais

- **Latência alvo**: < 600 ms microfone→HUD.  
- **Simplicidade de código**: mínimo de dependências e módulos.  
- **Segurança**: tokens só no Companion; comunicação local segura; nada de secrets no APK.  
- **Confiabilidade**: reconexão automática de WebRTC; buffer de jitter curto.  
- **Performance térmica**: evitar uso prolongado de câmera/vídeo.

---

## 6. Arquitetura

### Diagrama Alto Nível
```
[Vuzix M400 / Android 13]
  - AudioRecord (mic)
  - Camera2 (snapshot)
  - WebRTC Peer (audio + DataChannel)
  - HUD Overlay (VxDisplayManager)
        │
        ▼
[Companion Desktop / Node+TS]
  - WebRTC Peer (receive/send)
  - Bridge Realtime (WebSocket)
  - Agents SDK (prompt/context)
  - UI (Electron/Web)
        │
        ▼
[OpenAI]
  - Realtime API (audio/text)
  - Responses/Agents API (images, files)
```

### Principais componentes
- **Device App (Android)**: módulos `stream-core`, `hud-overlay`, `camera-snapshot`.  
- **Companion App (Node/TS)**: `webrtc-bridge`, `realtime-bridge`, `prompt-ui`.  
- **Docs/Config**: `RULES_FOR_AI.md`, `PROJECT_BRIEF.md`, `BACKLOG.md`.

---

## 7. Protocolos & Formatos

### 7.1. DataChannel JSON (exemplos)
```json
{
  "type": "model_text",
  "conversation_id": "abc123",
  "seq": 42,
  "ts": 1721665012345,
  "text": "Resposta parcial do modelo..."
}
```
```json
{ "type": "capture_snapshot", "quality": 80 }
```
```json
{ "type": "snapshot", "id": "snap-2025-07-22-123455", "mime": "image/jpeg", "data_base64": "..." }
```
- Chunking para arquivos grandes: `{ "type":"chunk", "id":"snap-...", "index":0, "total":10, "data_base64":"..." }`.

### 7.2. Áudio
- Upstream: Opus 16 kHz via WebRTC.  
- Downstream opcional: track de áudio WebRTC do Companion para o device.

---

## 8. Dependências & Recursos

### Android (óculos)
- Kotlin, Coroutines/Flow.  
- WebRTC Android lib (Maven).  
- Vuzix M‑Series SDK (HUD, input).  
- Camera2 API.

### Companion (desktop)
- Node.js 18+, TypeScript.  
- `node-webrtc` (wrtc) ou wrapper similar.  
- OpenAI Agents SDK TS.  
- WebSocket nativo.  
- (Opcional) Electron ou frontend web simples.

### OpenAI
- Realtime API (WebSocket/WebRTC).  
- Responses/Agents API (imagens, arquivos, system prompt).

---

## 9. Checklist de Preparação (resumo)

- Android Studio + SDK 33 + NDK 26.  
- Conta OpenAI com acesso Realtime + key.  
- Node.js 18+ instalado.  
- Vuzix M‑Series SDK baixado.  
- Rede Wi‑Fi 5 GHz estável (ou cabo USB‑C/Ethernet).  
- Forks dos repositórios: SmartGlassesManager, openai-realtime-examples, agents SDK.  
- Manifest Android com permissões corretas (`RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, etc.).

> **Checklist completo** está no Plano V1 (seção 9). Use-o como referência final.

---

## 10. Roadmap V1 (alto nível)

1. **Fase 0 – Ambiente & Forks**  
2. **Fase 1 – WebRTC Básico Óculos ⇄ PC (áudio + DataChannel)**  
3. **Fase 2 – Bridge com Realtime (texto/áudio)**  
4. **Fase 3 – Snapshots (Camera2 → JPEG → API de visão)**  
5. **Fase 4 – HUD Overlay**  
6. **Fase 5 – UI Prompt/Contexto**  
7. **Fase 6 – Telemetria & QA**

---

## 11. Métricas & Validação

- **Latência média** (ms) microfone→texto HUD.  
- % de pacotes perdidos/jitter no WebRTC.  
- Temperatura média do device durante sessão (opcional).  
- Logs de erros do Companion/Device.

Procedimentos de validação para cada entrega estarão na seção **VALIDAÇÃO** das tasks.

---

## 12. Riscos & Mitigações

| Risco | Impacto | Mitigação |
|-------|----------|-----------|
| Modalidades Realtime limitadas (sem vídeo) | Perda de contexto visual rico | Usar snapshots + input_image; considerar pipeline vision separado |
| Permissões A13/FGS incorretas | App morto pelo sistema | Manifest correto; pedir permissão antes de gravar áudio |
| Aquecimento/bateria | Queda de performance, travamento | Limitar FPS/bps; desligar câmera quando não usada |
| Rede instável | Latência alta/falhas | Priorizar 5 GHz; fallback cabo USB‑C/Ethernet |
| Token exposto | Comprometimento de segurança | Guardar no Companion; nunca em APK |
| “Alucinações” do agente | Código errado/danos | RULES_FOR_AI.md; validação humana; diffs mínimos |

---

## 13. Itens em Aberto / Perguntas

- Vai haver **áudio de retorno obrigatório** ou só texto?  
- UI do Companion será Electron ou web?  
- Precisamos de fallback offline (Whisper tiny) já nesta versão ou só depois?  
- Precisamos de algo além de **distribuição local/manual via ADB** para deployment?

Preencher/atualizar esta seção ao avançar.

---

## 14. Estado da Implementação (atualize sempre!)

- **Fase atual:** _preencher_  
- **Principais commits:** _preencher_  
- **Latência média medida:** _preencher_  
- **Issues abertas relevantes:** _preencher_

---

## 15. Glossário rápido

- **HUD**: Head-Up Display – overlay de texto no visor do M400.  
- **Realtime API**: endpoint da OpenAI para transcrição/LLM/TTS em streaming.  
- **Agents SDK**: SDK TS para criar agentes configuráveis (prompt, tools, contextos).  
- **Companion**: aplicativo desktop que faz ponte entre device e nuvem.  
- **Snapshot**: imagem única capturada da câmera (não vídeo contínuo).  
- **FGS**: Foreground Service, tipo de serviço Android com prioridade e permissão especial.  
- **XR1**: chipset do M400 (Qualcomm Snapdragon XR1).

---

**Fim do documento.**  
Ler isso (e o `RULES_FOR_AI.md`) ao iniciar qualquer sessão de IA de código.

