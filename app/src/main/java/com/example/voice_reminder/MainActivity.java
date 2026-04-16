package com.example.voice_reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ReminderAdapter adapter;
    private List<VoiceReminder> reminderList = new ArrayList<>();
    private ApiService apiService;
    private TextToSpeech tts;
    private EditText currentReminderEditText;
    private ActivityResultLauncher<Intent> voiceInputLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    private String selectedTime = "10:00";
    private String selectedDays = "Daily";
    private String selectedAudioPath = null;
    private int selectedPriority = 1;

    // Use a constant for the Channel ID to avoid typos
    public static final String CHANNEL_ID = "REMINDER_CHANNEL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        checkPermissions();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        setupRetrofit();
        setupLaunchers();

        recyclerView = findViewById(R.id.reminderRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReminderAdapter(this, reminderList);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_add_reminder).setOnClickListener(v -> showReminderDialog(null));

        fetchRemindersFromApi();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Channel for Voice Reminder App Alarms");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> listPermissionsAssign = new ArrayList<>();
        for (String per : permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, per) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsAssign.add(per);
            }
        }
        if (!listPermissionsAssign.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsAssign.toArray(new String[0]), 100);
        }

        // Specifically for exact alarms on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
    }

    private void setupLaunchers() {
        voiceInputLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) currentReminderEditText.setText(matches.get(0));
            }
        });

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    selectedAudioPath = uri.toString();
                    Toast.makeText(this, "Custom Audio Selected", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void playReminderAudio(VoiceReminder reminder) {
        if (reminder.AudioPath != null && !reminder.AudioPath.isEmpty()) {
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(this, Uri.parse(reminder.AudioPath));
                mp.prepare();
                mp.start();
                mp.setOnCompletionListener(MediaPlayer::release);
            } catch (Exception e) {
                Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show();
            }
        } else if (tts != null) {
            tts.speak(reminder.ReminderDetails, TextToSpeech.QUEUE_FLUSH, null, "PreviewID");
        }
    }

    public void scheduleAlarm(VoiceReminder reminder) {
        if (!reminder.Status) return;

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("text", reminder.ReminderDetails);
        intent.putExtra("audioPath", reminder.AudioPath);
        intent.putExtra("priority", reminder.TaskPriority);

        // Using FLAG_IMMUTABLE or FLAG_MUTABLE as required by newer Android versions
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                (int) reminder.Id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        long triggerTime = convertTimeToMillis(reminder.ReminderTime);

        if (alarmManager != null) {
            // Use setExactAndAllowWhileIdle so it works even in Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
            Log.d("ALARM_AGENT", "Scheduled for: " + reminder.ReminderDetails + " at " + triggerTime);
        }
    }

    private long convertTimeToMillis(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Calendar now = Calendar.getInstance();
            Calendar alarmTime = Calendar.getInstance();
            Date date = sdf.parse(timeStr);
            if (date != null) {
                alarmTime.setTime(date);
                alarmTime.set(Calendar.YEAR, now.get(Calendar.YEAR));
                alarmTime.set(Calendar.MONTH, now.get(Calendar.MONTH));
                alarmTime.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));

                // If the time has already passed today, schedule for tomorrow
                if (alarmTime.before(now)) {
                    alarmTime.add(Calendar.DATE, 1);
                }
                return alarmTime.getTimeInMillis();
            }
        } catch (Exception e) {
            Log.e("TIME_ERROR", "Error parsing time", e);
        }
        return System.currentTimeMillis() + 5000; // Default 5 seconds later
    }

    public void showReminderDialog(VoiceReminder reminderToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(reminderToEdit == null ? "Adaptive AI: New Task" : "Edit Task");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        selectedTime = (reminderToEdit != null) ? reminderToEdit.ReminderTime : "10:00";
        selectedDays = (reminderToEdit != null) ? reminderToEdit.ReminderDays : "Daily";
        selectedPriority = (reminderToEdit != null) ? reminderToEdit.TaskPriority : 1;
        selectedAudioPath = (reminderToEdit != null) ? reminderToEdit.AudioPath : null;

        final EditText et = new EditText(this);
        et.setHint("What should I remind you?");
        if (reminderToEdit != null) et.setText(reminderToEdit.ReminderDetails);
        layout.addView(et);

        Button btnVoice = new Button(this);
        btnVoice.setText("🎤 Speak Details");
        btnVoice.setOnClickListener(v -> {
            currentReminderEditText = et;
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voiceInputLauncher.launch(i);
        });
        layout.addView(btnVoice);

        Button btnTime = new Button(this);
        btnTime.setText("⏰ Time: " + selectedTime);
        btnTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hr, min) -> {
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hr, min);
                btnTime.setText("⏰ Time: " + selectedTime);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });
        layout.addView(btnTime);

        Button btnDays = new Button(this);
        btnDays.setText("📅 Days: " + selectedDays);
        btnDays.setOnClickListener(v -> {
            String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            boolean[] checked = new boolean[7];
            if (reminderToEdit != null && reminderToEdit.ReminderDays != null) {
                for (int i = 0; i < days.length; i++) {
                    if (reminderToEdit.ReminderDays.contains(days[i])) checked[i] = true;
                }
            }
            new AlertDialog.Builder(this).setTitle("Select Days")
                    .setMultiChoiceItems(days, checked, (d, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton("OK", (d, w) -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < 7; i++) if (checked[i]) sb.append(days[i]).append(",");
                        selectedDays = sb.length() > 0 ? sb.toString().substring(0, sb.length() - 1) : "Daily";
                        btnDays.setText("📅 Days: " + selectedDays);
                    }).show();
        });
        layout.addView(btnDays);

        Button btnAudio = new Button(this);
        btnAudio.setText(selectedAudioPath == null ? "🎵 Select Custom Audio" : "🎵 Audio Selected");
        btnAudio.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            filePickerLauncher.launch(intent);
        });
        layout.addView(btnAudio);

        Button btnPriority = new Button(this);
        btnPriority.setText("⚡ Priority: " + (selectedPriority == 2 ? "CRITICAL" : "NORMAL"));
        btnPriority.setOnClickListener(v -> {
            selectedPriority = (selectedPriority == 1) ? 2 : 1;
            btnPriority.setText("⚡ Priority: " + (selectedPriority == 2 ? "CRITICAL" : "NORMAL"));
        });
        layout.addView(btnPriority);

        builder.setView(layout);
        builder.setPositiveButton("Save", (d, w) -> {
            VoiceReminder nr = (reminderToEdit == null) ? new VoiceReminder() : reminderToEdit;
            nr.ReminderDetails = et.getText().toString();
            nr.ReminderTime = selectedTime;
            nr.ReminderDays = selectedDays;
            nr.TaskPriority = selectedPriority;
            nr.AudioPath = selectedAudioPath;
            nr.Status = true;

            if (reminderToEdit == null) {
                apiService.addReminder(nr).enqueue(new Callback<VoiceReminder>() {
                    @Override public void onResponse(Call<VoiceReminder> c, Response<VoiceReminder> r) {
                        if (r.isSuccessful() && r.body() != null) {
                            scheduleAlarm(r.body());
                        }
                        fetchRemindersFromApi();
                    }
                    @Override public void onFailure(Call<VoiceReminder> c, Throwable t) {
                        Toast.makeText(MainActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                updateReminderOnServer(nr);
                scheduleAlarm(nr);
            }
        });
        builder.show();
    }

    private void setupRetrofit() {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.18.15:5159/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);
    }

    public void fetchRemindersFromApi() {
        apiService.getReminders().enqueue(new Callback<List<VoiceReminder>>() {
            @Override
            public void onResponse(Call<List<VoiceReminder>> call, Response<List<VoiceReminder>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    reminderList.clear();
                    reminderList.addAll(response.body());
                    adapter.updateList(reminderList);
                }
            }
            @Override public void onFailure(Call<List<VoiceReminder>> call, Throwable t) {}
        });
    }

    public void updateReminderStatus(VoiceReminder r) {
        apiService.updateStatus((int) r.Id, r.Status).enqueue(new Callback<VoiceReminder>() {
            @Override public void onResponse(Call<VoiceReminder> c, Response<VoiceReminder> rs) {
                if (r.Status) scheduleAlarm(r);
            }
            @Override public void onFailure(Call<VoiceReminder> c, Throwable t) { fetchRemindersFromApi(); }
        });
    }

    private void updateReminderOnServer(VoiceReminder r) {
        apiService.updateReminder((int) r.Id, r).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> rs) { fetchRemindersFromApi(); }
            @Override public void onFailure(Call<Void> c, Throwable t) {}
        });
    }

    public void deleteReminder(VoiceReminder r) {
        apiService.deleteReminder((int) r.Id).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> rs) { fetchRemindersFromApi(); }
            @Override public void onFailure(Call<Void> c, Throwable t) {}
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}