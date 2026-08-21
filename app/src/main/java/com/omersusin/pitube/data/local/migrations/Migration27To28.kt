package com.omersusin.pitube.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Per-profile data isolation:
 * 1. watch_history gains a profileId column and a composite (videoId, profileId)
 *    primary key so each account has its own history. Existing rows predate
 *    profile tracking — they are preserved with profileId = '' and adopted by
 *    the active profile at runtime (ViewHistory.ensureScopeMigration).
 * 2. playlists gain an owning profileId column; legacy playlists keep '' and
 *    are adopted the same way (PlaylistRepository.ensureScopeMigration).
 */
class Migration27To28 : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── watch_history: rebuild with composite primary key ──
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS watch_history_new (
                videoId TEXT NOT NULL,
                position INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                title TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL,
                channelName TEXT NOT NULL,
                channelId TEXT NOT NULL,
                isMusic INTEGER NOT NULL,
                isShort INTEGER NOT NULL DEFAULT 0,
                isLocal INTEGER NOT NULL DEFAULT 0,
                profileId TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(videoId, profileId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO watch_history_new
                (videoId, position, duration, timestamp, title, thumbnailUrl, channelName, channelId, isMusic, isShort, isLocal, profileId)
            SELECT videoId, position, duration, timestamp, title, thumbnailUrl, channelName, channelId, isMusic, isShort, isLocal, ''
            FROM watch_history
            """.trimIndent()
        )
        db.execSQL("DROP TABLE watch_history")
        db.execSQL("ALTER TABLE watch_history_new RENAME TO watch_history")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_videoId ON watch_history(videoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_timestamp ON watch_history(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isMusic ON watch_history(isMusic)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isShort ON watch_history(isShort)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_isLocal ON watch_history(isLocal)")

        // ── playlists: add owning profileId ──
        db.execSQL("ALTER TABLE playlists ADD COLUMN profileId TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_profileId ON playlists(profileId)")
    }
}
