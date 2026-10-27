package com.tiagomoura.audiolivros.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookStatus { PROCESSING, READY, ERROR }

enum class ChapterSource { EMBEDDED, INFO_JSON, DESCRIPTION, SUBTITLE, SILENCE, MANUAL }

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val audioUri: String,
    val isCopied: Boolean,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val coverPath: String?,
    val placeholderColor: Int,
    val chapterSource: ChapterSource?,
    val status: BookStatus,
    val processingProgress: Int,
    val addedAt: Long,
    val lastPlayedAt: Long?,
    val finished: Boolean,
    /** Link de origem, para livros baixados. */
    val sourceUrl: String? = null,
    val errorMessage: String? = null,
    /** Miniatura do vídeo, para "Usar miniatura do vídeo" na folha de capa. */
    val thumbnailPath: String? = null,
    val subtitlePath: String? = null,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val index: Int,
    val title: String,
    val startMs: Long,
    val source: ChapterSource,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startMs: Long,
    /** Nulo para marcação de ponto. */
    val endMs: Long?,
    val note: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "playback_states",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaybackState(
    @PrimaryKey val bookId: String,
    val positionMs: Long,
    val speed: Float,
    val updatedAt: Long,
)

/** Livro com a posição salva, para a biblioteca. */
data class BookWithProgress(
    @Embedded val book: Book,
    val positionMs: Long?,
    val speed: Float?,
    val chapterCount: Int,
)
