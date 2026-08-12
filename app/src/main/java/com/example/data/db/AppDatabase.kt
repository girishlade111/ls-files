package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FileEntity::class,
        IndexedFileEntity::class,
        StarredFileEntity::class,
        TagEntity::class,
        FileTagCrossRef::class,
        BinItemEntity::class,
        SearchQueryEntity::class,
        CloudAccountEntity::class,
        AppSettingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun starredDao(): StarredDao
    abstract fun tagDao(): TagDao
    abstract fun binDao(): BinDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun cloudDao(): CloudDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ls_files_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
