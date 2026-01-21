package com.example.sitacardmaster

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "sitacardmaster.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "issued_cards"
        private const val COLUMN_ID = "id"
        private const val COLUMN_MEMBER_ID = "member_id"
        private const val COLUMN_COMPANY = "company"
        private const val COLUMN_VALID_UPTO = "valid_upto"
        private const val COLUMN_TOTAL_BUY = "total_buy"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = ("CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$COLUMN_MEMBER_ID TEXT," +
                "$COLUMN_COMPANY TEXT," +
                "$COLUMN_VALID_UPTO TEXT," +
                "$COLUMN_TOTAL_BUY TEXT," +
                "$COLUMN_TIMESTAMP TEXT)")
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveIssuedCard(memberId: String, company: String, validUpto: String, totalBuy: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        values.put(COLUMN_MEMBER_ID, memberId)
        values.put(COLUMN_COMPANY, company)
        values.put(COLUMN_VALID_UPTO, validUpto)
        values.put(COLUMN_TOTAL_BUY, totalBuy)
        values.put(COLUMN_TIMESTAMP, timestamp)

        val success = db.insert(TABLE_NAME, null, values)
        if (success != -1L) {
            platformLog("SITACardMaster", "Local Storage: Saved Member $memberId successfully.")
        } else {
            platformLog("SITACardMaster", "Local Storage Error: Failed to save record.")
        }
        db.close()
    }
}
