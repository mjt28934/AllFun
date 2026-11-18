package app.allfun.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaEntity::class,
        EpisodeEntity::class,
        ProgressEntity::class,
        SourceEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun progressDao(): ProgressDao
    abstract fun sourceDao(): SourceDao

    companion object {
        /** v2 adds extension-private memo columns; existing rows keep working with NULL. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN memo TEXT")
                db.execSQL("ALTER TABLE episodes ADD COLUMN memo TEXT")
            }
        }

        /** v3 adds favourites and repository metadata for installed sources. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sources ADD COLUMN repoUrl TEXT")
                db.execSQL("ALTER TABLE sources ADD COLUMN version TEXT")
            }
        }

        /** v4 stores creator/backdrop/cast only when a provider really supplied them. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN artist TEXT")
                db.execSQL("ALTER TABLE media ADD COLUMN backdropUrl TEXT")
                db.execSQL("ALTER TABLE media ADD COLUMN cast TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "allfun.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
