package com.green.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// IMPORTANT: every schema change (add/remove/rename column or table) MUST have a corresponding
// Migration entry here AND bump the version in @Database(version = N) in AppDatabase.kt.
// Without it, Room will crash on launch for existing installs. Never use fallbackToDestructiveMigration
// — it silently wipes all user data (VPN keys, configs, subscriptions) on update.

// v2: configs(id, name, vlessLink, configJson, createdAt)
// v3: never existed
// v4: + subscriptionId on configs, + subscriptions table
val MIGRATION_2_4 = object : Migration(2, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE configs ADD COLUMN subscriptionId INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subscriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                url TEXT NOT NULL,
                name TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

// v5: + sortOrder on configs
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE configs ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
    }
}
