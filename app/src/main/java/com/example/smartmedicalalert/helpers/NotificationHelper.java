package com.example.smartmedicalalert.helpers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class NotificationHelper {

    public static void createNotificationChannel(Context context, String notificationID, String notificationName) {
        NotificationChannel channel = new NotificationChannel(notificationID, notificationName, NotificationManager.IMPORTANCE_HIGH);
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

}
