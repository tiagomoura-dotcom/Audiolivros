# Especificação — Audiolivros (Android)

## 1. Visão geral

App pessoal para ouvir audiolivros no Android. O usuário adiciona livros importando um arquivo de áudio ou colando um link de vídeo. O app identifica os capítulos, toca em segundo plano e com a tela desligada, lembra a posição de cada livro e permite marcar pontos e trechos para ouvir novamente.

O download por link é para uso pessoal, com conteúdo que o usuário tem direito de baixar (domínio público ou com autorização do autor).

## 2. Telas

Existe um protótipo visual com três telas (biblioteca, player e tela bloqueada). Esta seção descreve o que cada tela contém.

### 2.1 Biblioteca (tela inicial)

- Título "Biblioteca" e resumo ("4 livros · 1 em processamento"); botão de configurações no canto
- Cartão **Continuar ouvindo** com o último livro tocado: capa, título, autor, capítulo atual e posição ("Cap. VII · D. Glória · 04:21"), botão de play, barra de progresso do livro, porcentagem e tempo restante. Tocar no cartão abre o player
- Dois botões lado a lado: **Importar arquivo** e **Baixar por link**
- "Baixar por link" expande um painel com: campo do link, três opções marcadas por padrão ("Salvar capítulos e legenda", "Converter para MP3", "Usar miniatura do vídeo como capa") e o botão **Baixar áudio**
- Lista **Todos os livros**: capa (ou capa provisória com iniciais), título, autor, número de capítulos e a origem deles ("148 capítulos · da descrição"), barra de progresso e status ("38%", "Concluído", "Novo"). Livro em processamento mostra "Detectando capítulos… 64%"
- Toque longo num livro abre: renomear, editar autor, alterar capa, reanalisar capítulos, excluir
- Cartão **Posições e marcações** no fim: "Salvas no aparelho · backup hoje, 07:40" e o botão **Exportar**

### 2.2 Player

- Barra superior: voltar, "Tocando agora" e menu (editar capítulos, reanalisar capítulos, exportar este livro, detalhes do arquivo)
- Capa em proporção 3:4 com um botão redondo de câmera no canto, que abre a folha **Foto de capa** (2.4)
- Título, autor e capítulo atual (na cor de destaque)
- Barra de progresso **do capítulo**, arrastável, com as marcações desenhadas: ponto = risco vertical, trecho = faixa clara
- Abaixo da barra: tempo decorrido, "Cap. 7 de 148" e tempo restante do capítulo
- Enquanto um trecho está sendo marcado, aparece o aviso "Marcando trecho desde 04:21 — toque em Concluir"
- Controles: capítulo anterior, voltar 30 s, play/pausa (botão grande), avançar 30 s, próximo capítulo
- Linha de quatro botões:
  - **Velocidade**: cada toque alterna 1,0× → 1,25× → 1,5× → 1,75× → 2,0× → 0,75×; toque longo abre ajuste fino de 0,5× a 3,0× em passos de 0,05
  - **Timer** de sono (seção 6.3)
  - **Marcar**: cria uma marcação de ponto na posição atual
  - **Trecho**: o primeiro toque marca o início e o rótulo muda para "Concluir"; o segundo toque marca o fim e cria a marcação de trecho
- Abas **Capítulos** e **Marcações (N)**:
  - Capítulos: cabeçalho com o total e a origem ("Detectados pela descrição do vídeo"); cada linha tem número, título, duração, ✓ se já ouvido e "tocando" no atual. Tocar pula para o capítulo
  - Marcações: cada linha tem botão de play, capítulo, horário ("06:50 – 07:48 · trecho" ou "02:05 · ponto") e nota. Toque longo edita a nota ou exclui

### 2.3 Notificação e tela bloqueada

Controle de mídia padrão do Android (via MediaSession) com capa, título, capítulo atual e progresso. Botões: voltar 30 s, play/pausa, avançar 30 s e **Marcar** (cria um ponto sem desbloquear o celular). Se o timer de sono estiver ativo, mostrar o tempo restante no subtítulo da notificação.

### 2.4 Folha "Foto de capa"

Folha inferior com: "Escolher da galeria", "Tirar foto", "Usar miniatura do vídeo" (só aparece se o livro veio de um link e a miniatura existe) e "Remover capa". Texto de apoio: "A imagem é recortada em 3:4 e fica salva no aparelho."

### 2.5 Configurações

- Reprodução: intervalo de pulo (10, 15, 30 ou 60 s; padrão 30) e retroceder ao retomar (0, 3, 5 ou 10 s; padrão 3)
- Detecção de capítulos: limiar de silêncio (padrão −40 dBFS), pausa mínima (padrão 2,0 s), duração mínima de capítulo (padrão 3 min) e lista de palavras-chave (padrão: capítulo, cap., parte, prólogo, epílogo, prefácio)
- Downloads: versão atual do yt-dlp e botão **Atualizar yt-dlp**
- Bateria: mostra se o app está liberado da otimização de bateria, com botão para liberar (seção 6.2)
- Backup: exportar, importar e "Salvar arquivo de posição automaticamente" com escolha de pasta (seção 9)

### 2.6 Editor de capítulos

Lista editável: renomear, ajustar o início (com botões para ouvir 5 s antes e depois do ponto), adicionar capítulo na posição atual, excluir e "Reanalisar" escolhendo a fonte (seção 5).

## 3. Design

Tema escuro único, pensado para ouvir à noite.

| Token | Valor |
|---|---|
| Fundo | `#121318` |
| Superfície | `#1C1E25` |
| Superfície 2 | `#272A33` |
| Borda | `#33363F` |
| Divisória | `#262931` |
| Texto | `#EDEBE6` |
| Texto secundário | `#A4A7B1` |
| Destaque | `#F2A65A` |
| Texto sobre destaque | `#121318` |

- Capas provisórias (sem foto): fundo escolhido pelo hash do título entre `#3B4A6B`, `#5B3F4E`, `#4A5A3E` e `#2F5B57`, com as iniciais do título no canto inferior esquerdo, em Literata
- Fontes: **Literata** (títulos, peso 600) e **Figtree** (texto, pesos 400 a 700), ambas do Google Fonts com licença OFL; empacotar em `res/font`
- Raios: cartões 20 dp, botões 14 dp, capas 8 a 12 dp; botão de play circular de 76 dp no player
- Rótulos de seção em caixa alta, 12 a 13 sp, espaçamento de letras 0,08 em, cor secundária
- Ícones em traço (Material Symbols Outlined ou equivalente)

## 4. Adicionar livros

### 4.1 Importar arquivo

- `ActivityResultContracts.OpenDocument` com MIME `audio/*`; aceitar MP3, M4A, M4B, AAC, Opus, OGG, WebM e FLAC
- Chamar `takePersistableUriPermission` e guardar o URI. Se a permissão falhar, copiar o arquivo para `filesDir/books/<id>/`
- Ler com `MediaMetadataRetriever`: duração, título, artista/autor e capa embutida. Sem título nos metadados, usar o nome do arquivo sem extensão
- Depois de importar, iniciar automaticamente a detecção de capítulos (seção 5)

### 4.2 Baixar por link

- Usar a biblioteca youtubedl-android, inicializada no `Application`
- Executar num `CoroutineWorker` em primeiro plano, com notificação de progresso (porcentagem e etapa: "Baixando", "Convertendo", "Detectando capítulos")
- Opções do yt-dlp conforme o painel:
  - Sempre: `-x` e `--write-info-json` e `-o "%(title)s.%(ext)s"` em `filesDir/books/<id>/`
  - "Converter para MP3": `--audio-format mp3`
  - "Salvar capítulos e legenda": `--embed-chapters --write-auto-subs --write-subs --sub-langs "pt.*" --convert-subs srt`
  - "Usar miniatura do vídeo como capa": `--write-thumbnail --convert-thumbnails jpg`
- Título e autor vêm do `info.json` (`title`, `uploader` ou `channel`); o usuário pode editar
- Tratar erros com mensagem clara e botão "Tentar de novo". Se o erro sugerir que o yt-dlp está desatualizado, sugerir **Atualizar yt-dlp**
- O botão **Atualizar yt-dlp** usa a função de atualização da própria biblioteca e mostra a versão nova

## 5. Capítulos

Fontes, em ordem de prioridade. A detecção usa a primeira que der resultado, e o usuário pode reanalisar com outra pelo editor (2.6). Cada capítulo guarda a sua fonte.

1. **Metadados embutidos.** MP3 com quadros ID3 CHAP: ler com o Media3 (`MetadataRetriever` e `ChapterFrame`). M4B/M4A com capítulos (atom Nero `chpl` ou faixa de texto QuickTime): verificar se o Media3 expõe; se não, usar um parser próprio dos atoms ou o ffprobe do ffmpeg embutido (`-show_chapters -print_format json`), o que se mostrar viável
2. **`info.json` do yt-dlp.** Campo `chapters`: lista de `{start_time, end_time, title}` em segundos
3. **Lista colada da descrição.** Um campo de texto onde o usuário cola a descrição do vídeo. Cada linha com um horário vira um capítulo. Aceitar horário no início ou no fim da linha, com separadores opcionais (`-`, `–`, `—`, `:`, `|`). Regex base para o horário: `(?:(\d{1,2}):)?(\d{1,2}):(\d{2})`. Ignorar linhas sem horário. Testes com:
   - `0:00 Capítulo 1 - Do título`
   - `1:02:33 — Cap. 12: A ópera`
   - `Prólogo 00:00`
   - `12:30 | Parte II`
4. **Legenda (.srt ou .vtt).** Normalizar o texto (minúsculas, sem acentos) e procurar as palavras-chave das configurações seguidas de número arábico, romano ou por extenso ("capitulo 3", "capitulo iii", "capitulo terceiro", "capitulo vinte e um"). O início do capítulo é o início da legenda; se houver um silêncio de pelo menos 1 s até 5 s antes, ajustar para o fim desse silêncio. Ignorar ocorrências a menos de 60 s de um capítulo já marcado, para evitar menções no meio da narração
5. **Pausas (silêncio).** Detalhes abaixo

### 5.1 Detecção por pausas

- Decodificar em blocos com `MediaExtractor` + `MediaCodec` para PCM, misturar para mono e calcular o nível RMS em dBFS a cada janela de 100 ms
- Salvar esse envelope (uma lista de floats; cerca de 1,4 MB para 10 h) em arquivo, para poder reajustar a sensibilidade sem decodificar de novo
- Silêncio = sequência de janelas abaixo do limiar (padrão −40 dBFS) com duração mínima (padrão 2,0 s)
- Candidato a capítulo = fim de cada silêncio. Descartar candidatos que criem capítulos menores que a duração mínima (padrão 3 min), mantendo os silêncios mais longos
- Nomear como "Capítulo 1", "Capítulo 2"…
- Alternativa a avaliar: o filtro `silencedetect` do ffmpeg embutido (`-af silencedetect=noise=-40dB:d=2 -f null -`), se for possível executá-lo pela biblioteca
- Rodar num worker com progresso; nunca carregar o arquivo inteiro na memória
- Na tela de reanálise, um controle de sensibilidade recalcula a lista a partir do envelope salvo, em tempo real

## 6. Reprodução

### 6.1 Serviço

- `MediaSessionService` com ExoPlayer, declarado no manifesto com `android:foregroundServiceType="mediaPlayback"`
- Permissões: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (pedir na primeira reprodução no Android 13+), `WAKE_LOCK`, `INTERNET`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- ExoPlayer configurado com:
  - `setWakeMode(C.WAKE_MODE_LOCAL)` — mantém a CPU ativa com a tela desligada
  - `setAudioAttributes` com `USAGE_MEDIA`, `AUDIO_CONTENT_TYPE_SPEECH` e foco de áudio gerenciado (pausar em ligações e retomar depois de interrupções curtas)
  - `setHandleAudioBecomingNoisy(true)` — pausar quando o fone desconectar
- Velocidade com `setPlaybackSpeed`, mantendo o tom da voz
- Botões personalizados na sessão (−30 s, +30 s, Marcar) via comandos personalizados do Media3 (`CommandButton` + `SessionCommand`, usando a API de layout/preferências de botões da versão em uso)
- Botões do fone e Bluetooth funcionam pela própria MediaSession
- Capítulo anterior: se passaram mais de 3 s do início do capítulo atual, voltar ao início dele; senão, ir ao anterior

### 6.2 Segundo plano e tela desligada

- O serviço em primeiro plano com a sessão de mídia mantém o áudio com a tela desligada
- Alguns fabricantes (Xiaomi, Samsung, Huawei e outros) encerram apps em segundo plano. Nas configurações e na primeira reprodução, verificar `PowerManager.isIgnoringBatteryOptimizations`; se não estiver liberado, mostrar um cartão explicando o motivo, com botão que abre `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, e um link para dontkillmyapp.com com as instruções do fabricante

### 6.3 Timer de sono

- Opções: 15, 30, 45 e 60 min e "Fim do capítulo"
- Nos últimos 10 s, abaixar o volume gradualmente e pausar
- Mostrar o tempo restante no botão do player e na notificação

### 6.4 Posição (não pode se perder)

- Salvar no Room a cada 5 s durante a reprodução e também ao pausar, ao buscar (seek), ao trocar de capítulo, em `onTaskRemoved` e em `onDestroy` do serviço
- Ao retomar, voltar alguns segundos conforme a configuração (padrão 3 s)
- Guardar também a velocidade por livro
- Marcar o livro como concluído ao chegar nos últimos 30 s

## 7. Marcações

- Tipos: **ponto** (`startMs`) e **trecho** (`startMs` e `endMs`)
- Criadas pelos botões Marcar e Trecho no player e pelo botão Marcar da notificação (nota padrão: "Marcado pela notificação")
- Ao criar, abrir um campo opcional para nota (pode ser dispensado)
- Tocar numa marcação de ponto: ir para a posição e tocar. Tocar num trecho: tocar do início e pausar no fim. Opção "Repetir trecho" toca em loop até o usuário parar
- As posições são absolutas no livro; a tela mostra o capítulo correspondente
- Desenhar as marcações na barra de progresso do capítulo (seção 2.2)

## 8. Capa

- Fontes: capa embutida no arquivo (`MediaMetadataRetriever.getEmbeddedPicture`), miniatura baixada pelo yt-dlp, galeria (`PickVisualMedia`, sem pedir permissão) ou câmera (`TakePicture` com FileProvider)
- Recortar no centro em 3:4, redimensionar para 900 × 1200 px no máximo e salvar como JPEG em `filesDir/covers/<bookId>.jpg`
- "Remover capa" volta à capa provisória com iniciais (seção 3)
- A capa também vai para os metadados da MediaSession (aparece na notificação e na tela bloqueada)

## 9. Backup e arquivo de posição

- **Exportar**: gera um JSON com todos os livros, posições, velocidades, capítulos e marcações (formato em `docs/exemplo-backup.json`) e salva pelo `CreateDocument`
- **Importar**: lê o JSON e associa cada livro a um livro existente pela "impressão digital" (nome do arquivo + tamanho em bytes + duração com tolerância de 1 s). Livros sem correspondência ficam listados como "arquivo ausente", para o usuário apontar o arquivo
- **Arquivo de posição automático** (opcional): o usuário escolhe uma pasta (`OpenDocumentTree`) e o app grava um arquivo por livro (`<título>.posicao.json`, mesmo formato com um único livro), atualizado ao pausar e no máximo uma vez por minuto durante a reprodução
- O cartão da biblioteca mostra a data do último backup

## 10. Modelo de dados (Room)

- `Book`: `id` (UUID), `title`, `author`, `audioUri`, `isCopied`, `fileName`, `fileSizeBytes`, `durationMs`, `coverPath`, `placeholderColor`, `chapterSource`, `status` (PROCESSING, READY, ERROR), `processingProgress`, `addedAt`, `lastPlayedAt`, `finished`
- `Chapter`: `id`, `bookId`, `index`, `title`, `startMs`, `source` (EMBEDDED, INFO_JSON, DESCRIPTION, SUBTITLE, SILENCE, MANUAL)
- `Bookmark`: `id`, `bookId`, `startMs`, `endMs` (nulo para ponto), `note`, `createdAt`
- `PlaybackState`: `bookId` (chave), `positionMs`, `speed`, `updatedAt`
- A duração de cada capítulo é calculada (início do próximo ou fim do livro), não armazenada

## 11. Marcos

1. **Base**: projeto, tema, Room, importar arquivo, biblioteca, player com Media3 em segundo plano e tela desligada, notificação, posição salva, velocidade, ±30 s, CI gerando o APK
2. **Capítulos de fontes prontas**: metadados embutidos, colar lista da descrição, legenda com palavras-chave, editor de capítulos
3. **Marcações**: ponto, trecho, notas, desenho na barra, repetir trecho, botão Marcar na notificação
4. **Detecção por pausas**: worker, envelope salvo, ajuste de sensibilidade
5. **Download por link**: youtubedl-android, opções do painel, `info.json`, progresso, atualizar yt-dlp
6. **Acabamento**: capas, timer de sono, backup e importação, arquivo de posição automático, configurações, cartão de bateria

## 12. Critérios de aceite

- Tocar 60 min com a tela desligada, sem interrupção
- Fechar o app pela tela de multitarefa durante a reprodução e reabrir: a posição restaurada difere no máximo 5 s
- Analisar por pausas um livro de 10 h sem erro de memória, com progresso visível
- Todos os exemplos da seção 5 cobertos por testes unitários
- Desconectar o fone pausa a reprodução; uma ligação pausa e a reprodução pode ser retomada
- Exportar o backup, reinstalar o app, importar e reapontar os arquivos: posições e marcações restauradas
