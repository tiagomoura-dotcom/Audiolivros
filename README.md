# Audiolivros

App Android pessoal para ouvir audiolivros: importa arquivos do celular ou baixa o
áudio de um link (yt-dlp), detecta capítulos, lembra a posição de cada livro e
permite marcar pontos e trechos. Toca em segundo plano e com a tela desligada.

**APK mais recente:** https://github.com/tiagomoura-dotcom/Audiolivros/releases/download/ultima-versao/audiolivros.apk

Cada push na `main` compila o APK de debug (só `arm64-v8a`) e substitui o arquivo da
release `ultima-versao`. Todas as versões têm a mesma assinatura (`app/debug.keystore`),
então a nova instala por cima da anterior sem perder a biblioteca.

- Especificação: `docs/ESPECIFICACAO.md`
- Formato do backup: `docs/exemplo-backup.json`
- Fontes Literata e Figtree (OFL): licenças em `docs/licencas/`

```
./gradlew assembleDebug        # compilar
./gradlew testDebugUnitTest    # testes
./gradlew lint                 # lint
```
