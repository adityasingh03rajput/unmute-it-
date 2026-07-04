package com.bunk.volumeapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.Toast
import android.util.Log

class VolumeSchedulerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val streamType = intent.getIntExtra("streamType", AudioManager.STREAM_RING)
        val volumeValue = intent.getIntExtra("volumeValue", 5)
        
        Log.i("VolumeOverrideApp", "Scheduler fired! Setting stream $streamType to $volumeValue")
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // If ringtone, ensure it's not in silent mode
        if (streamType == AudioManager.STREAM_RING) {
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                try {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                } catch(e: Exception) {
                    Log.e("VolumeOverrideApp", "Failed to change ringer mode: ${e.message}")
                }
            }
        }
        
        try {
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            val targetVolume = Math.round((volumeValue / 100f) * maxVolume).toInt()
            
            audioManager.setStreamVolume(streamType, targetVolume, 0)
            
            val isRepeating = intent.getBooleanExtra("isRepeating", false)
            if (isRepeating) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                val nextTime = System.currentTimeMillis() + android.text.format.DateUtils.DAY_IN_MILLIS
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                } else {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                }
                Toast.makeText(context, "Unmute it: Volume restored to $volumeValue%! Scheduled for tomorrow.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Unmute it: Volume restored to $volumeValue%!", Toast.LENGTH_LONG).show()
            }
        } catch(e: Exception) {
            Log.e("VolumeOverrideApp", "Failed to set volume in scheduler: ${e.message}")
        }
    }
}
