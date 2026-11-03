package com.flow.timetable

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ScheduleDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE schedule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day TEXT NOT NULL,
                time TEXT NOT NULL,
                subject TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) onCreate(db)
    }

    fun getEntries(): List<TimetableEntry> {
        val entries = mutableListOf<TimetableEntry>()
        readableDatabase.query(
            "schedule", arrayOf("day", "time", "subject"), null, null, null, null, "id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += TimetableEntry(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
                )
            }
        }
        return entries
    }

    fun replaceEntries(entries: List<TimetableEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("schedule", null, null)
            entries.forEach { entry ->
                db.insertOrThrow(
                    "schedule",
                    null,
                    ContentValues().apply {
                        put("day", entry.day)
                        put("time", entry.time)
                        put("subject", entry.subject)
                    }
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "flow_timetable.db"
        const val DATABASE_VERSION = 1
    }
}
