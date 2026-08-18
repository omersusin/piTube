package com.omersusin.pitube.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Scoped the home feed cache to the profile it belongs to. Existing rows
// predate profile tracking, so the cache table is emptied instead of guessing.
class Migration26To27 : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE home_feed_cache ADD COLUMN profileId TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL("DELETE FROM home_feed_cache")
    }
}
