# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android application called "Smart Companion" built with Kotlin and Jetpack Compose. It uses Material 3 design system and targets modern Android devices (API 33+).

## Essential Commands

### Build Commands
```bash
# Build the project
./gradlew build

# Clean build artifacts
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.seudominio.app_smart_companion.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint
```

### Development Commands
```bash
# Install debug APK on connected device
./gradlew installDebug

# List all available tasks
./gradlew tasks
```

**Note**: On Windows, use `gradlew.bat` instead of `./gradlew`

## Architecture Overview

### Project Structure
- **Single Activity Architecture**: The app uses `MainActivity` as the sole activity, with navigation handled through Compose
- **Jetpack Compose UI**: All UI is built using Compose, located in the `ui` package
- **Material 3 Theming**: Theme configuration is in `app/src/main/java/com/seudominio/app_smart_companion/ui/theme/`

### Key Technical Details
- **Package Name**: `com.seudominio.app_smart_companion`
- **Min SDK**: 33 (Android 13) - Requer M400 com Android 13 upgrade oficial
- **Target SDK**: 35 (Android 15) - Latest for Play Store
- **Kotlin Version**: 2.0.21
- **Compose BOM**: 2024.09.00

### Module Structure
The project follows standard Android single-module architecture:
- `app/` - Main application module containing all source code
- `app/src/main/` - Production code
- `app/src/test/` - Unit tests
- `app/src/androidTest/` - Instrumented tests

### Dependency Management
Dependencies are managed through:
- Version catalog in `gradle/libs.versions.toml`
- Module-level build file: `app/build.gradle.kts`
- Project-level build file: `build.gradle.kts`

When adding new dependencies, prefer using the version catalog for consistency.

## ROLE & GOAL
Você é um ARQUITETO/DESENVOLVEDOR ASSISTENTE para um app Android (Vuzix M400 – Android 13) + Companion Desktop.
Seu objetivo: implementar o MVP descrito em PROJECT_BRIEF.md com a menor complexidade e latência possível, seguindo exatamente as regras abaixo.

## ALWAYS DO FIRST
1. Leia/atualize: PROJECT_BRIEF.md, RULES_FOR_AI.md, BACKLOG.md (se existir).
2. **CONSULTE DOCUMENTAÇÃO ESSENCIAL**: `docs/references/` - VideoSDK OpenAI Realtime API e Agent Framework patterns
3. Rode:
   - `git status` / `git diff` / `git log -n 5` para entender o estado atual. 
   - Liste arquivos-chave do módulo que vamos alterar.
4. Resuma em 5 linhas: o que vamos fazer agora + dependências.

## ESTADO ATUAL DO PROJETO (2025-07-23)
- **Fase:** Fase 3 INICIADA ✅ (Conexão M400↔Desktop FUNCIONANDO!)
- **Breakthrough:** Android 13+ Network Security Policy resolvido - WebRTC conectando
- **Status:** `"webrtc":true, "signaling":1` - M400 conectado ao Companion Desktop
- **Próxima ação:** Testes end-to-end de áudio e validação de latência <600ms
- **Arquivos críticos:** SignalingClient.kt, WebRTCService.kt, network_security_config.xml

## LIÇÕES CRÍTICAS APRENDIDAS
- **SEMPRE consultar documentação oficial PRIMEIRO** ao encontrar problemas
- Network Security Policy do Android 13+ bloqueia WebSocket CLEARTEXT por padrão
- ActionMenuActivity requer `super.onCreateActionMenu()` chamada (documentação Vuzix)
- Debug sistemático com logs padronizados é essencial para troubleshooting

## DOCUMENTAÇÃO ESSENCIAL DE REFERÊNCIA
- **`docs/references/VideoSDK_OpenAI_Realtime_API.md`** - Padrões oficiais OpenAI Realtime API
- **`docs/references/VideoSDK_Agents_Framework.md`** - Arquiteturas avançadas de AI agents
- **`docs/references/README.md`** - Índice e guias de uso das referências
- **REGRA:** Sempre consultar estas referências antes de implementar funcionalidades de áudio, IA ou WebRTC

## INTERACTION RULES
- Faça PERGUNTAS CLARAS quando houver ambiguidade.
- Gere SEMPRE diffs mínimos (patches) e explique cada mudança.
- Nunca invente dependências: verifique no Maven Central/Gradle antes. Se não tiver certeza, proponha uma alternativa e pergunte.
- Se precisar adicionar libs, atualize `build.gradle` e explique por quê.
- Antes de criar novo arquivo, verifique se já existe algo similar.
- Ao final de cada tarefa, escreva um bloco "VALIDAÇÃO" com:
  * Comandos para rodar (ex.: `./gradlew assembleDebug`, `adb install`, etc.)
  * Passos manuais de teste
  * Riscos / próximos passos

## SECURITY & RELIABILITY
- Não exponha chaves OpenAI no código Android. Use variáveis de ambiente no Companion.
- Cheque nomes de pacotes/deps para evitar "package hallucination/slopsquatting".
- Nunca remova verificações de permissão/FGS no Android 13.

## STYLE & FORMAT
- Código em Kotlin (Android) e TypeScript (Companion).
- Comentários curtos e objetivos.
- Use blocos de código com a linguagem correta.
- Para JSON de protocolo Device↔Companion, mantenha o schema já definido.

## SESSION MANAGEMENT
- Sempre que o contexto ficar grande/confuso, gere um resumo atualizado em PROJECT_BRIEF.md (seção "STATE OF IMPLEMENTATION").
- Se eu disser "RESET CONTEXT", você deve re-ler os arquivos base e recapitular o plano.

## DELIVERABLE TEMPLATE
1. **Objetivo da tarefa atual**
2. **Perguntas/Assunções**
3. **Plano (passos numerados)**
4. **Patch/Diff proposto**
5. **Validação (comandos + testes)**
6. **Riscos/Pontos de atenção**
7. **Próximo passo sugerido**

## STOP CONDITIONS
- Se a tarefa estiver fora do escopo do MVP, pare e pergunte.
- Se faltarem credenciais/variáveis/arquivos, peça-os.
- Se for impossível (ex.: API não suporta), proponha alternativas viáveis.