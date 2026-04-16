package com.example.voice_reminder;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {
    @GET("api/VoiceReminder")
    Call<List<VoiceReminder>> getReminders();

    @POST("api/VoiceReminder")
    Call<VoiceReminder> addReminder(@Body VoiceReminder reminder);

    @PUT("api/VoiceReminder/{id}")
    Call<Void> updateReminder(@Path("id") long id, @Body VoiceReminder reminder);

    @DELETE("api/VoiceReminder/{id}")
    Call<Void> deleteReminder(@Path("id") long id);

    @PUT("api/VoiceReminder/active/{id}")
    Call<VoiceReminder> updateStatus(@Path("id") long id, @Body boolean isActive);
}