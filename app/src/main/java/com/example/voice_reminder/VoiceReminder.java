package com.example.voice_reminder;

import com.google.gson.annotations.SerializedName;

public class VoiceReminder {
    @SerializedName("id")
    public long Id;

    @SerializedName("reminderDetails")
    public String ReminderDetails;

    @SerializedName("reminderTime")
    public String ReminderTime;

    @SerializedName("reminderDays")
    public String ReminderDays;

    @SerializedName("status")
    public boolean Status;

    @SerializedName("audioPath")
    public String AudioPath;

    // --- ADAPTIVE AGENT FIELD ---
    // This connects your Java logic to the SQL 'TaskPriority' column
    @SerializedName("taskPriority")
    public int TaskPriority;
}