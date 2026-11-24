package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        PdfDocumentEntity::class,
        BuildTaskEntity::class,
        PipelineRunEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun buildTaskDao(): BuildTaskDao
    abstract fun pipelineRunDao(): PipelineRunDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_dev_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
