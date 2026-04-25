package com.example.personal_studio.data.local.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seeds the 7 default KB categories on first DB creation. Triggered by
 * destructive-migration too because Room calls onCreate after a wipe.
 */
class KbSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (name in AppDatabase.DEFAULT_KB_CATEGORIES) {
            db.execSQL(
                "INSERT OR IGNORE INTO kb_categories (parentId, name, seeded, createdAt) VALUES (NULL, ?, 1, ?)",
                arrayOf(name, now),
            )
        }
    }
}
