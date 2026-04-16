package com.example.voice_reminder;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    private static TextToSpeech tts;

    @Override
    public void onReceive(Context context, Intent intent) {
        String text = intent.getStringExtra("text");
        int priority = intent.getIntExtra("priority", 1);

        // 1. SHOW THE VISUAL NOTIFICATION (The "Real" Part)
        showNotification(context, text, priority);

        // 2. ADAPTIVE AUDIO LOGIC
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();

        if (priority == 2) {
            // CRITICAL TASK: Force volume up and speak
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            speak(context, "Critical Reminder: " + text);
        } else if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            // NORMAL TASK: Only speak if phone isn't silent
            speak(context, text);
        } else {
            Log.d("AGENT", "User is busy. Staying silent visually.");
        }
    }

    private void showNotification(Context context, String text, int priority) {
        // This Intent opens MainActivity when the user taps the notification
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(priority == 2 ? "🚨 CRITICAL TASK" : "Voice Reminder")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Use current time as ID so multiple reminders don't replace each other
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void speak(Context context, String text) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AdaptiveID");
            }
        });
    }
}