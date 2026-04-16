package com.example.voice_reminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class ReminderDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "reminder_app.db";
    private static final int DATABASE_VERSION = 1;

    public ReminderDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Exact schema you provided
        String CREATE_TABLE = "CREATE TABLE reminders (" +
                "reminder_id INTEGER PRIMARY KEY AUTOINCREMENT," + // bigint
                "created_at TEXT NOT NULL," +                       // datetime
                "updated_at TEXT NOT NULL," +                       // datetime
                "status INTEGER NOT NULL," +                        // bit (0 or 1)
                "reminder_time TEXT NOT NULL," +                    // nchar(5)
                "reminder_day TEXT," +                              // nvarchar(10)
                "reminder_details TEXT" +                           // nvarchar(10)
                ")";
        db.execSQL(CREATE_TABLE);
    }

    public void addReminder(String time, String day, String details) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // Getting current timestamp for created_at and updated_at
        String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());

        values.put("created_at", currentTime);
        values.put("updated_at", currentTime);
        values.put("status", 1); // 1 = Active (for the 'bit' type)
        values.put("reminder_time", time);
        values.put("reminder_day", day);
        values.put("reminder_details", details);

        db.insert("reminders", null, values);
        db.close();
    }

    // --- ADD THIS METHOD SO YOU CAN DISPLAY THE DATA ---
    public List<String> getAllReminders() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM reminders", null);

        if (cursor.moveToFirst()) {
            do {
                // Column 6 is reminder_details, Column 4 is reminder_time
                String info = cursor.getString(6) + " at " + cursor.getString(4);
                list.add(info);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS reminders");
        onCreate(db);
    }
}