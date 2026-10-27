package com.tiagomoura.audiolivros.player

/** Capítulo como visto pelo player: só o necessário para navegar. */
data class TimelineChapter(val title: String, val startMs: Long)

/** Capítulo atual com início, fim e posição relativa. */
data class ChapterPosition(
    val index: Int,
    val count: Int,
    val title: String?,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

/**
 * Navegação por capítulos sobre posições absolutas no livro. A duração de cada
 * capítulo é calculada: vai até o início do próximo ou até o fim do livro.
 * Sem capítulos, o livro inteiro é tratado como um único capítulo.
 */
class ChapterTimeline(chapters: List<TimelineChapter>, private val durationMs: Long) {

    private val chapters: List<TimelineChapter> = chapters.sortedBy { it.startMs }

    val count: Int get() = chapters.size.coerceAtLeast(1)

    fun indexAt(positionMs: Long): Int {
        if (chapters.isEmpty()) return 0
        val index = chapters.indexOfLast { it.startMs <= positionMs }
        return index.coerceAtLeast(0)
    }

    fun positionAt(positionMs: Long): ChapterPosition {
        if (chapters.isEmpty()) {
            return ChapterPosition(0, 1, null, 0, durationMs.coerceAtLeast(0))
        }
        val index = indexAt(positionMs)
        val start = if (index == 0) 0 else chapters[index].startMs
        val end = chapters.getOrNull(index + 1)?.startMs ?: durationMs
        return ChapterPosition(index, chapters.size, chapters[index].title, start, end.coerceAtLeast(start))
    }

    /** Todos os capítulos com início e fim, para a lista do player. */
    fun all(): List<ChapterPosition> {
        if (chapters.isEmpty()) return listOf(positionAt(0))
        return chapters.mapIndexed { index, chapter ->
            val start = if (index == 0) 0 else chapter.startMs
            val end = chapters.getOrNull(index + 1)?.startMs ?: durationMs
            ChapterPosition(index, chapters.size, chapter.title, start, end.coerceAtLeast(start))
        }
    }

    /**
     * Destino do botão "capítulo anterior": se passaram mais de 3 s do início do
     * capítulo atual, volta ao início dele; senão, vai ao início do anterior.
     */
    fun previousTarget(positionMs: Long): Long {
        val current = positionAt(positionMs)
        if (positionMs - current.startMs > RESTART_THRESHOLD_MS || current.index == 0) {
            return current.startMs
        }
        return positionAt(chapters[current.index - 1].startMs).startMs
    }

    /** Destino do botão "próximo capítulo", ou nulo se já estiver no último. */
    fun nextTarget(positionMs: Long): Long? {
        if (chapters.isEmpty()) return null
        return chapters.getOrNull(indexAt(positionMs) + 1)?.startMs
    }

    companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
