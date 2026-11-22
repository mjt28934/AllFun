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
        SourceEntity::class,
        DownloadEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun progressDao(): ProgressDao
    abstract fun sourceDao(): SourceDao
    abstract fun downloadDao(): DownloadDao

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

        /** v5 adds the offline download queue; existing library and progress rows stay. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `key` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `unitId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `mediaTitle` TEXT NOT NULL,
                        `unitTitle` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL,
                        `downloadedBytes` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `path` TEXT,
                        `error` TEXT,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_mediaId` ON `downloads` (`mediaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
            }
        }

        /** v6 makes the download queue explicit: persisted order plus chapter numbers. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN unitNumber REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN queueOrder INTEGER NOT NULL DEFAULT 0")
                // Existing rows keep the order they were created in, oldest first.
                db.execSQL("UPDATE downloads SET queueOrder = createdAt")
            }
        }

        /** v7 remembers which chapters were opened, so the detail list can mark them read. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN read INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "allfun.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                )
                .fallbackToDestructiveMigration()
                .build()
    }
}
