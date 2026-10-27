package com.tiagomoura.audiolivros.chapters

/** Capítulo encontrado por um parser, antes de ser salvo. Posição absoluta no livro. */
data class ParsedChapter(val title: String, val startMs: Long)

/** Ordena, remove inícios repetidos e numera títulos vazios como "Capítulo N". */
internal fun List<ParsedChapter>.normalized(): List<ParsedChapter> =
    filter { it.startMs >= 0 }
        .sortedBy { it.startMs }
        .distinctBy { it.startMs }
        .mapIndexed { index, chapter ->
            if (chapter.title.isBlank()) chapter.copy(title = defaultChapterTitle(index + 1)) else chapter
        }

internal fun defaultChapterTitle(number: Int) = "Capítulo $number"
