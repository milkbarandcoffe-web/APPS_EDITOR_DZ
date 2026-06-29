package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppSettingEntity::class, UserEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v5→v6: rimozione WebappEntity (tabella webapps droppata),
        // aggiunta bridgeDev e isOwner a settings, rimozione campi obsoleti.
        // Usiamo fallbackToDestructiveMigration come safety net; la migration
        // esplicita prova prima a fare l'upgrade pulito.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop tabella webapp non più usata
                db.execSQL("DROP TABLE IF EXISTS webapps")
                // Aggiungi nuovi campi a settings
                db.execSQL("ALTER TABLE settings ADD COLUMN bridgeDev TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN isOwner INTEGER NOT NULL DEFAULT 0")
                // I vecchi campi (bubbleUrl, bubbleToken, bubbleShape) restano
                // nel DB ma non nell'entity — Room li ignora silenziosamente
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webviewer_bolla_database"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
