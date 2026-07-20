package com.v2ir.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.v2ir.data.local.entity.ConfigEntity
import com.v2ir.data.local.entity.SubscriptionEntity

@Database(
    entities = [ConfigEntity::class, SubscriptionEntity::class],
    version = 7,
    // FIX (Bug #21): Changed exportSchema to true.
    // With exportSchema = false, Room generates no schema history files, which means:
    // - Migration correctness cannot be verified by Room's built-in MigrationTestHelper
    // - Silent schema drift goes undetected during development
    // - Combined with the now-removed fallbackToDestructiveMigration, this made data
    //   loss completely invisible.
    // Schema files are written to app/schemas/ and should be committed to version control.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        const val DATABASE_NAME = "smart_xray_db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE configs ADD COLUMN extraParams TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE configs ADD COLUMN lastSuccess INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE configs ADD COLUMN failCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN isExpanded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN healthyCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN lastScanTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN bestLatency INTEGER NOT NULL DEFAULT -1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE configs ADD COLUMN isFree INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE configs ADD COLUMN bandwidth REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE configs ADD COLUMN rawUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE configs ADD COLUMN countryLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscriptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        isPublic INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        serverCount INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE configs ADD COLUMN subscriptionId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE configs ADD COLUMN sni TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE configs ADD COLUMN fragmentIp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE configs ADD COLUMN tcpLatency INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE configs ADD COLUMN realLatency INTEGER NOT NULL DEFAULT -1")
            }
        }
    }
}




