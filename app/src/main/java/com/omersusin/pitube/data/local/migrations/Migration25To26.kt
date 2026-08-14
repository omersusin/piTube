package com.omersusin.pitube.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Added the translation cache table.
class Migration25To26 : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_translations` (
                `id` TEXT NOT NULL,
                `engine` TEXT NOT NULL,
                `targetLanguage` TEXT NOT NULL,
                `sourceText` TEXT NOT NULL,
                `translatedText` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}