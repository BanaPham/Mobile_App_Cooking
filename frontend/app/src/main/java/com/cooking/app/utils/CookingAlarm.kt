package com.cooking.app.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cooking.app.MainActivity
import com.cooking.app.R

class CookingAlarm(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val state = context.getSharedPreferences("cooking_alarm", Context.MODE_PRIVATE)
    private val pending = PendingIntent.getBroadcast(context, 90, Intent(context, CookingAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    val deadline get() = state.getLong("deadline", 0L)
    val label get() = state.getString("label", "").orEmpty()
    val canScheduleExactly get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun start(seconds: Int, label: String) {
        cancel()
        val target = System.currentTimeMillis() + seconds * 1000L
        state.edit().putLong("deadline", target).putString("label", label).apply()
        try {
            if (canScheduleExactly) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
        }
    }

    fun cancel() {
        state.edit().clear().apply()
        alarms.cancel(pending)
    }

    fun finishIfDue(): Boolean {
        val target = deadline
        if (target == 0L || System.currentTimeMillis() < target) return false
        val name = label
        cancel()
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("cooking_timer", "Hẹn giờ nấu ăn", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            channel.enableVibration(true)
            notifications.createNotificationChannel(channel)
        }
        val permitted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permitted && notifications.areNotificationsEnabled()) {
            val open = PendingIntent.getActivity(context, 91, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            notifications.notify(91, NotificationCompat.Builder(context, "cooking_timer")
                .setSmallIcon(R.drawable.ic_pot).setContentTitle("Hết giờ nấu")
                .setContentText(name).setContentIntent(open).setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .setPriority(NotificationCompat.PRIORITY_HIGH).build())
        } else {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 85)
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 1700)
            }
        }
        return true
    }
}

class CookingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CookingAlarm(context).finishIfDue()
    }
}
