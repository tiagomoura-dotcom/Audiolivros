package com.tiagomoura.audiolivros.download

import java.io.File
import java.util.Properties

/** Opções do painel "Baixar por link" (seção 2.1), todas marcadas por padrão. */
data class DownloadOptions(
    val chaptersAndSubtitles: Boolean = true,
    val convertToMp3: Boolean = true,
    val thumbnailAsCover: Boolean = true,
) {
    /**
     * Argumentos do yt-dlp conforme a seção 4.2. [withSubtitles] permite repetir o
     * download sem legendas quando só elas falham (por exemplo, HTTP 429 do YouTube).
     */
    fun ytDlpOptions(outputDir: File, withSubtitles: Boolean = chaptersAndSubtitles): List<Pair<String, String?>> = buildList {
        add("-x" to null)
        add("--write-info-json" to null)
        add("--no-playlist" to null)
        add("-o" to File(outputDir, "%(title)s.%(ext)s").absolutePath)
        if (convertToMp3) add("--audio-format" to "mp3")
        if (chaptersAndSubtitles) add("--embed-chapters" to null)
        if (chaptersAndSubtitles && withSubtitles) {
            add("--write-auto-subs" to null)
            add("--write-subs" to null)
            add("--sub-langs" to "pt.*")
            add("--convert-subs" to "srt")
            // Uma pausa entre as legendas reduz o bloqueio por excesso de pedidos.
            add("--sleep-subtitles" to "1")
        }
        if (thumbnailAsCover) {
            add("--write-thumbnail" to null)
            add("--convert-thumbnails" to "jpg")
        }
    }

    /** Guarda as opções na pasta do livro, para o "Tentar de novo". */
    fun save(file: File) {
        file.parentFile?.mkdirs()
        val properties = Properties().apply {
            setProperty(KEY_CHAPTERS, chaptersAndSubtitles.toString())
            setProperty(KEY_MP3, convertToMp3.toString())
            setProperty(KEY_THUMBNAIL, thumbnailAsCover.toString())
        }
        file.outputStream().use { properties.store(it, null) }
    }

    companion object {
        private const val KEY_CHAPTERS = "capitulos_e_legenda"
        private const val KEY_MP3 = "converter_mp3"
        private const val KEY_THUMBNAIL = "miniatura_como_capa"

        fun load(file: File): DownloadOptions {
            if (!file.exists()) return DownloadOptions()
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            return DownloadOptions(
                chaptersAndSubtitles = properties.getProperty(KEY_CHAPTERS)?.toBoolean() ?: true,
                convertToMp3 = properties.getProperty(KEY_MP3)?.toBoolean() ?: true,
                thumbnailAsCover = properties.getProperty(KEY_THUMBNAIL)?.toBoolean() ?: true,
            )
        }
    }
}
