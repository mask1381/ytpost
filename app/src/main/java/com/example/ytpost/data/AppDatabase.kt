package com.example.ytpost.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Task::class, 
        ProcessedItem::class, 
        DownloadPreferenceProfile::class, 
        RssHistory::class,
        RssFeed::class,
        TelegramChat::class
    ], 
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun processedItemDao(): ProcessedItemDao
    abstract fun downloadPreferenceDao(): DownloadPreferenceDao
    abstract fun rssHistoryDao(): RssHistoryDao
    abstract fun rssFeedDao(): RssFeedDao
    abstract fun telegramChatDao(): TelegramChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rss_feeds` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `channelId` TEXT NOT NULL, 
                        `channelName` TEXT NOT NULL, 
                        `feedUrl` TEXT NOT NULL, 
                        `isActive` INTEGER NOT NULL DEFAULT 1, 
                        `lastCheckedItemId` TEXT, 
                        `captionScript` TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_chats` (
                        `chatId` INTEGER NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `username` TEXT, 
                        `participantsCount` INTEGER, 
                        `cachedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`chatId`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ytpost_database"
                )
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
