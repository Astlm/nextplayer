package dev.anilbeesetti.nextplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.anilbeesetti.nextplayer.core.database.dao.HiddenVideoDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.dao.NetworkConnectionDao
import dev.anilbeesetti.nextplayer.core.database.entities.HiddenVideoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.database.entities.NetworkConnectionEntity

@Database(
    entities = [
        MediumStateEntity::class,
        HiddenVideoEntity::class,
        NetworkConnectionEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediumStateDao(): MediumStateDao

    abstract fun hiddenVideoDao(): HiddenVideoDao

    abstract fun networkConnectionDao(): NetworkConnectionDao

    companion object {
        const val DATABASE_NAME = "media_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_state` (
                        `uri` TEXT NOT NULL,
                        `playback_position` INTEGER NOT NULL DEFAULT 0,
                        `audio_track_index` INTEGER,
                        `subtitle_track_index` INTEGER,
                        `playback_speed` REAL,
                        `last_played_time` INTEGER,
                        `external_subs` TEXT NOT NULL DEFAULT '',
                        `video_scale` REAL NOT NULL DEFAULT 1,
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_state_uri` ON `media_state` (`uri`)
                    """,
                )

                db.execSQL(
                    """
                    INSERT INTO `media_state` (
                        `uri`,
                        `playback_position`,
                        `audio_track_index`,
                        `subtitle_track_index`,
                        `playback_speed`,
                        `last_played_time`,
                        `external_subs`,
                        `video_scale`
                    )
                    SELECT
                        `uri`,
                        `playback_position`,
                        `audio_track_index`,
                        `subtitle_track_index`,
                        `playback_speed`,
                        `last_played_time`,
                        `external_subs`,
                        `video_scale`
                    FROM `media`
                    """,
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_new` (
                        `uri` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `filename` TEXT NOT NULL,
                        `parent_path` TEXT NOT NULL,
                        `last_modified` INTEGER NOT NULL,
                        `size` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `media_store_id` INTEGER NOT NULL,
                        `format` TEXT,
                        `thumbnail_path` TEXT,
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                db.execSQL(
                    """
                    INSERT INTO `media_new` (
                        `uri`,
                        `path`,
                        `filename`,
                        `parent_path`,
                        `last_modified`,
                        `size`,
                        `width`,
                        `height`,
                        `duration`,
                        `media_store_id`,
                        `format`,
                        `thumbnail_path`
                    )
                    SELECT
                        `uri`,
                        `path`,
                        `filename`,
                        `parent_path`,
                        `last_modified`,
                        `size`,
                        `width`,
                        `height`,
                        `duration`,
                        `media_store_id`,
                        `format`,
                        `thumbnail_path`
                    FROM `media`
                    """,
                )

                db.execSQL("DROP TABLE `media`")
                db.execSQL("ALTER TABLE `media_new` RENAME TO `media`")

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_uri` ON `media` (`uri`)
                    """,
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_media_path`")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_group_index` INTEGER")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_track_index` INTEGER")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `subtitle_delay` INTEGER NOT NULL DEFAULT 0")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `subtitle_speed` REAL NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_group_index` INTEGER")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_track_index` INTEGER")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `subtitle_delay` INTEGER NOT NULL DEFAULT 0")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `subtitle_speed` REAL NOT NULL DEFAULT 1")
                db.execSQL("DROP TABLE IF EXISTS `directories`")
                db.execSQL("DROP TABLE IF EXISTS `media`")
                db.execSQL("DROP TABLE IF EXISTS `audio_stream_info`")
                db.execSQL("DROP TABLE IF EXISTS `video_stream_info`")
                db.execSQL("DROP TABLE IF EXISTS `subtitle_stream_info`")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 本地 fork 与上游曾各自发布过不同结构的 v5，先收敛为统一结构。
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_group_index` INTEGER")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_track_index` INTEGER")
                db.execSQL("DROP TABLE IF EXISTS `directories`")
                db.execSQL("DROP TABLE IF EXISTS `media`")
                db.execSQL("DROP TABLE IF EXISTS `audio_stream_info`")
                db.execSQL("DROP TABLE IF EXISTS `video_stream_info`")
                db.execSQL("DROP TABLE IF EXISTS `subtitle_stream_info`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hidden_video` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `vault_path` TEXT NOT NULL,
                        `original_path` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `duration` INTEGER NOT NULL DEFAULT 0,
                        `size` INTEGER NOT NULL DEFAULT 0,
                        `width` INTEGER NOT NULL DEFAULT 0,
                        `height` INTEGER NOT NULL DEFAULT 0,
                        `hidden_at` INTEGER NOT NULL
                    )
                    """,
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_hidden_video_vault_path` ON `hidden_video` (`vault_path`)
                    """,
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Column definitions (order, types, nullability) must match the schema Room
                // generates from NetworkConnectionEntity exactly, or migration validation fails.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `network_connection` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `host` TEXT NOT NULL,
                        `port` INTEGER,
                        `path` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `password` TEXT NOT NULL,
                        `use_https` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """,
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_group_index` INTEGER")
                db.addColumnIfMissing("ALTER TABLE `media_state` ADD COLUMN `video_track_index` INTEGER")
            }
        }
    }
}

private fun SupportSQLiteDatabase.addColumnIfMissing(sql: String) {
    runCatching { execSQL(sql) }
}
