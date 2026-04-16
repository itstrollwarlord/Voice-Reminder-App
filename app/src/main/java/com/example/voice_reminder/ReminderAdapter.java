package com.example.voice_reminder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import java.util.List;

public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder> {

    private Context context;
    private List<VoiceReminder> reminderList;

    public ReminderAdapter(Context context, List<VoiceReminder> reminderList){
        this.context = context;
        this.reminderList = reminderList;
    }

    @NonNull
    @Override
    public ReminderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.reminder_item, parent, false);
        return new ReminderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReminderViewHolder holder, int position) {
        VoiceReminder reminder = reminderList.get(position);

        // --- UPDATED: PRIORITY VISIBILITY LOGIC ---
        // Using TaskPriority to match your SQL column
        if (reminder.TaskPriority == 2) {
            // Critical Styling
            holder.txtPriority.setText("⚡ Priority: CRITICAL");
            holder.txtPriority.setTextColor(Color.RED);
            holder.txtReminder.setTextColor(Color.RED);
            holder.txtReminder.setTypeface(null, Typeface.BOLD);
        } else {
            // Normal Styling
            holder.txtPriority.setText("✓ Priority: Normal");
            holder.txtPriority.setTextColor(Color.parseColor("#4CAF50")); // Green
            holder.txtReminder.setTextColor(Color.BLACK);
            holder.txtReminder.setTypeface(null, Typeface.NORMAL);
        }

        holder.txtReminder.setText(reminder.ReminderDetails);
        holder.txtTime.setText(reminder.ReminderTime);

        // Cleaned up Day Display logic
        String daysDisplay = reminder.ReminderDays;
        if (daysDisplay == null || daysDisplay.isEmpty() || daysDisplay.equalsIgnoreCase("Daily")) {
            holder.txtDays.setText("Everyday");
        } else {
            holder.txtDays.setText(daysDisplay);
        }

        // IMPORTANT: Unset listener before setting checked state to avoid scroll-trigger bugs
        holder.switchActive.setOnCheckedChangeListener(null);
        holder.switchActive.setChecked(reminder.Status);

        holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(context instanceof MainActivity){
                reminder.Status = isChecked;
                ((MainActivity)context).updateReminderStatus(reminder);
            }
        });

        holder.btnPlay.setOnClickListener(v -> {
            if(context instanceof MainActivity) ((MainActivity)context).playReminderAudio(reminder);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if(context instanceof MainActivity) ((MainActivity)context).showReminderDialog(reminder);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if(context instanceof MainActivity) ((MainActivity)context).deleteReminder(reminder);
        });
    }

    @Override
    public int getItemCount() { return reminderList.size(); }

    public void updateList(List<VoiceReminder> list){
        this.reminderList = list;
        notifyDataSetChanged();
    }

    static class ReminderViewHolder extends RecyclerView.ViewHolder{
        TextView txtReminder, txtTime, txtDays, txtPriority; // Added txtPriority
        SwitchCompat switchActive;
        ImageView btnPlay, btnEdit, btnDelete;

        public ReminderViewHolder(@NonNull View itemView){
            super(itemView);
            txtReminder = itemView.findViewById(R.id.tvReminderDetails);
            txtTime = itemView.findViewById(R.id.tvReminderTime);
            txtDays = itemView.findViewById(R.id.tvReminderDays);
            txtPriority = itemView.findViewById(R.id.tvPriorityStatus); // Added this
            switchActive = itemView.findViewById(R.id.switchActive);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}