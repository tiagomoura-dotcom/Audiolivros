# Audiolivros — app Android

App Android de uso pessoal para ouvir audiolivros. Ele importa arquivos de áudio do celular ou baixa o áudio de um link (via yt-dlp), detecta os capítulos, salva a posição de cada livro e permite marcar pontos e trechos para ouvir de novo. Precisa tocar em segundo plano e com a tela desligada.

A especificação completa está em `docs/ESPECIFICACAO.md`. Leia antes de implementar qualquer funcionalidade e siga a ordem dos marcos (seção 11). O formato do arquivo de backup/posição está em `docs/exemplo-backup.json`.

## Stack

- Kotlin, Jetpack Compose, Material 3 com tema próprio (seção 3 da especificação)
- Reprodução: AndroidX Media3 (ExoPlayer + MediaSessionService)
- Banco local: Room. Preferências: DataStore
- Tarefas longas (download, análise de capítulos): WorkManager com notificação de progresso
- Download por link: biblioteca youtubedl-android (yt-dlp + Python + ffmpeg empacotados; repositório yausername/youtubedl-android no GitHub). Confirme as coordenadas Maven e a versão atual no README antes de adicionar.
- Imagens: Coil
- minSdk 26; targetSdk e compileSdk na versão estável mais recente
- Use versões estáveis atuais de todas as dependências e centralize-as em `gradle/libs.versions.toml`

## Arquitetura

- Um módulo `app`, com pacotes por funcionalidade: `library`, `player`, `chapters`, `bookmarks`, `download`, `covers`, `backup`, `settings` e `core` (banco, tema, utilidades)
- MVVM: telas Compose + ViewModel + repositório. Hilt para injeção de dependência
- A lógica de detecção de capítulos (parsers e análise de silêncio sobre amostras) fica em classes Kotlin puras, sem dependência do Android, para ter testes unitários
- O serviço de reprodução é a única fonte da verdade sobre a posição atual; as telas observam o estado do MediaController

## Regras

- Interface inteiramente em português do Brasil, com textos em `strings.xml`
- Nunca decodificar um arquivo de áudio inteiro na memória: processar em blocos (livros podem ter mais de 10 horas)
- A posição de reprodução nunca pode se perder: salvar conforme a seção 6.4
- Alvos de toque com no mínimo 48 dp
- O app não vai para a Play Store; é instalado manualmente pelo APK
- Escreva testes unitários para todos os parsers de capítulos (seção 5)

## Build e CI

- `./gradlew assembleDebug` deve funcionar localmente
- Criar `.github/workflows/build.yml`: a cada push na `main`, compilar o APK de debug e publicá-lo como artefato do workflow (JDK exigido pelo Android Gradle Plugin, cache do Gradle)
- Gerar APK apenas para `arm64-v8a`, para reduzir o tamanho (as bibliotecas nativas do yt-dlp e do ffmpeg são grandes)

## Comandos

- Compilar: `./gradlew assembleDebug`
- Testes: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lint`
