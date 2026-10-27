package com.tiagomoura.audiolivros.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.*, p.positionMs AS positionMs, p.speed AS speed,
            (SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id) AS chapterCount
        FROM books b LEFT JOIN playback_states p ON p.bookId = b.id
        ORDER BY b.lastPlayedAt IS NULL, b.lastPlayedAt DESC, b.addedAt DESC
        """,
    )
    fun observeAllWithProgress(): Flow<List<BookWithProgress>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): Book?

    @Query("SELECT * FROM books WHERE fileName = :fileName AND fileSizeBytes = :sizeBytes LIMIT 1")
    suspend fun findByFile(fileName: String, sizeBytes: Long): Book?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: Book)

    @Query("UPDATE books SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE books SET author = :author WHERE id = :id")
    suspend fun updateAuthor(id: String, author: String?)

    @Query("UPDATE books SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun updateLastPlayed(id: String, timestamp: Long)

    @Query("UPDATE books SET finished = :finished WHERE id = :id")
    suspend fun updateFinished(id: String, finished: Boolean)

    @Query("UPDATE books SET durationMs = :durationMs WHERE id = :id AND durationMs <= 0")
    suspend fun updateDurationIfUnknown(id: String, durationMs: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startMs")
    fun observeForBook(bookId: String): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startMs")
    suspend fun getForBook(bookId: String): List<Chapter>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY startMs")
    fun observeForBook(bookId: String): Flow<List<Bookmark>>
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_states WHERE bookId = :bookId")
    suspend fun get(bookId: String): PlaybackState?

    @Upsert
    suspend fun upsert(state: PlaybackState)
}
