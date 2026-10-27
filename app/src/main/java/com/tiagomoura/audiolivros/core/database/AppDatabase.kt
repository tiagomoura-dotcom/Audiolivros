package com.tiagomoura.audiolivros.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Chapter::class, Bookmark::class, PlaybackState::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun playbackStateDao(): PlaybackStateDao
}

/** v2: campos de livros baixados por link (marco 5) e mensagem de erro. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN sourceUrl TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN errorMessage TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN thumbnailPath TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN subtitlePath TEXT")
    }
}
