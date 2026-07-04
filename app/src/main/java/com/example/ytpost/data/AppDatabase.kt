package com.example.ytpost.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * چک‌لیست تغییرات دیتابیس:
 * ۱. اگر فیلدی به Entity اضافه شد یا تغییری کرد -> شماره version را یکی بالا ببر.
 * ۲. اگر Entity جدید اضافه شد -> آن را به لیست entities در پایین اضافه کن و version را بالا ببر.
 * ۳. در فاز توسعه: fallbackToDestructiveMigration دیتابیس قبلی را پاک می‌کند.
 * ۴. در فاز انتشار: باید Migration دستی نوشته شود تا داده‌های کاربر پاک نشود.
 */
@Database(
    entities = [Task::class, ProcessedItem::class, DownloadPreferenceProfile::class], 
    version = 4, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun processedItemDao(): ProcessedItemDao
    abstract fun downloadPreferenceDao(): DownloadPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ytpost_database"
                )
                // توجه: این متد باعث پاک شدن تمام داده‌های قبلی در صورت تغییر Schema می‌شود.
                // فقط برای فاز توسعه (Development) مناسب است.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
