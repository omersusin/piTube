package com.omersusin.pitube.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Dead-code cleanup: the downloaded_songs table had no readers or writers
 * anywhere in the app (its DAO was registered but never called). Drop it.
 */
class Migration28To29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS downloaded_songs")
    }
}
