package com.tiagomoura.audiolivros.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Book::class, Chapter::class, Bookmark::class, PlaybackState::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun playbackStateDao(): PlaybackStateDao
}
