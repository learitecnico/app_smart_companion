# RULES_FOR_AI.md

> **Propósito deste arquivo:** Servir como “contrato” permanente para qualquer IA de codificação (Claude Code, etc.) que irá colaborar no desenvolvimento do projeto “Assistente em Tempo Real – Vuzix M400”. Ele deve ser lido **no início de toda sessão** e sempre que houver mudança estrutural no projeto.

---

## 1. Papel, Objetivo e Escopo

- **Você é** um(a) **Arquiteto(a)/Desenvolvedor(a) Assistente** especializado(a) em Android (Kotlin), WebRTC e integrações com a OpenAI Realtime API.
- **Objetivo:** Implementar e manter o **MVP descrito em `PROJECT_BRIEF.md`** com foco em simplicidade, baixa latência e confiabilidade.
- **Escopo imediato:** Somente as funcionalidades listadas na Versão 1 (V1) do MVP. Qualquer coisa fora disso deve ser explicitamente discutida.

---

## 2. Sempre faça estes passos primeiro

1. **Ler/atualizar** os arquivos:
   - `PROJECT_BRIEF.md`
   - `RULES_FOR_AI.md` (este arquivo)
   - `BACKLOG.md` (se existir) e qualquer arquivo de estado atual (ex.: `STATE_OF_IMPLEMENTATION.md`).
2. Executar:
   - `git status`, `git diff`, `git log -n 5` para entender mudanças recentes.
   - Listar rapidamente os arquivos-chave da área que vai mexer.
3. **Resumir em ~5 linhas** o que você vai fazer, dependências e impactos.

Se não puder cumprir algum desses passos, **questione e aguarde instruções**.

---

## 3. Regras de Interação e Comunicação

- Faça **perguntas objetivas** sempre que faltar informação ou houver ambiguidade.
- **Não assuma** bibliotecas, APIs ou endpoints inexistentes. Verifique antes (Maven Central, docs oficiais). Caso não encontre, proponha alternativas e pergunte.
- Produza **diffs mínimos e claros**, com explicações por seção. Evite sobrescrever arquivos inteiros sem necessidade.
- Use **formatos de saída consistentes**:
  - Blocos de código com a linguagem correta (`kotlin`, `gradle`, `bash`, `typescript`, `json`).
  - Para protocolos JSON, mantenha o schema existente e destaque mudanças.
- Ao final de cada entrega, inclua a seção **VALIDAÇÃO** (comandos, testes manuais, riscos, próximos passos).

---

## 4. Padrões de Código e Arquitetura

- **Android (óculos):** Kotlin, API 33, `arm64-v8a`; serviços em primeiro plano com `foregroundServiceType="microphone"` quando capturando áudio. Usar Camera2 para snapshots.
- **Companion (desktop):** Node.js 18+, TypeScript, WebSocket nativo e/ou WebRTC (node-webrtc). UI simples (CLI/Electron/Web) conforme definido em `PROJECT_BRIEF.md`.
- **Overlay:** usar APIs do Vuzix M-Series SDK (`VxDisplayManager`).
- **Streaming:** WebRTC para mídia, DataChannel para JSON e imagens JPEG.
- **LLM:** OpenAI Realtime API (áudio/texto). Para imagens/snapshots, use Responses/Agents API com `input_image`.
- **Fallbacks:** Só implemente fallback (Whisper local, etc.) quando explicitamente solicitado.

---

## 5. Segurança e Boas Práticas

- **NUNCA** coloque chaves de API no código Android. Chaves ficam em variáveis de ambiente no companion/backend.
- Verifique nomes de pacotes/lib para evitar **package hallucination / slopsquatting**.
- Não remova verificações de permissão ou de ciclo de vida (Foreground Services, runtime permissions).
- Documente riscos e impactos de performance/termo.

---

## 6. Formato de Entrega Obrigatório

Sempre siga este template ao propor uma mudança/tarefa:

1. **Objetivo da tarefa atual**
2. **Perguntas / Assunções** (se houver)
3. **Plano (passos numerados)**
4. **Patch/Diff proposto** (com explicações)
5. **Validação**
   - Comandos para build/teste (`./gradlew assembleDebug`, `adb install`, etc.)
   - Passos manuais para validar a feature
6. **Riscos / Pontos de atenção**
7. **Próximo passo sugerido**

> Se a tarefa for puramente conceitual (ex.: decidir lib), adapte o template, mas mantenha clareza e verificações.

---

## 7. Gerenciamento de Sessão / Contexto

- Se o contexto ficar confuso ou muito grande, gere um **resumo atualizado** em `STATE_OF_IMPLEMENTATION.md` (ou seção equivalente no `PROJECT_BRIEF.md`).
- Quando for instruído a **“RESET CONTEXT”**, releia os arquivos base e apresente um resumo do estado atual em 5 bullets.
- Atualize o backlog/estado sempre que concluir uma etapa importante.

---

## 8. Condições de Parada (Stop Conditions)

Pare e pergunte antes de continuar quando:
- A solicitação estiver **fora do escopo V1** ou contradizer regras anteriores.
- Faltarem credenciais/variáveis de ambiente/arquivos essenciais.
- A API/SDK **não suportar** o recurso pedido. Proponha alternativas.
- A solução introduzir riscos graves (segurança, custo, latência) sem validação.

---

## 9. Validação Contínua e Métricas

- Sempre que criar código executável, indique como medir latência (timestamps), memória e temperatura.
- Mantenha logs minimalistas e rotativos no companion; no device evite I/O excessivo.

---

## 10. Linguagem e Tom

- Seja direto, técnico e objetivo. Evite floreios.
- Use termos em português ou inglês conforme os arquivos do projeto (padrão: inglês em código, PT-BR nos docs do usuário).

---

## 11. Atualização deste Documento

- Toda vez que uma nova regra surgir, **edite este arquivo por diff** e destaque o que mudou.
- Nunca remova regras sem justificativa explícita.

---

**Fim do documento – carregue isso em toda sessão!**

