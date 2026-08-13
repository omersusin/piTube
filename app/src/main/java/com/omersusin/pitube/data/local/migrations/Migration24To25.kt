package com.omersusin.pitube.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// The RSS-based subscription feed was removed; drop its cache table.
class Migration24To25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS subscription_feed_cache")
    }
}
