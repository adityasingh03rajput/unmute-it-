package com.bunk.volumeapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import java.util.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(this, "Please grant DND access for Unmute it to work", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {}
        }
        
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private var selectedHour = 0
        private var selectedMinute = 0

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            // Set initial time
            val cal = Calendar.getInstance()
            selectedHour = cal.get(Calendar.HOUR_OF_DAY)
            selectedMinute = cal.get(Calendar.MINUTE)
            
            val timePref = findPreference<Preference>("pref_time")
            timePref?.summary = String.format("%02d:%02d", selectedHour, selectedMinute)
            
            timePref?.setOnPreferenceClickListener {
                TimePickerDialog(requireContext(), { _, h, m ->
                    selectedHour = h
                    selectedMinute = m
                    timePref.summary = String.format("%02d:%02d", h, m)
                }, selectedHour, selectedMinute, true).show()
                true
            }
            
            val typePref = findPreference<DropDownPreference>("pref_stream_type")
            
            typePref?.setOnPreferenceChangeListener { preference, newValue ->
                val index = typePref.findIndexOfValue(newValue.toString())
                if (index >= 0) {
                    preference.summary = typePref.entries[index]
                }
                true
            }
            // Init summary
            typePref?.summary = typePref?.entry
            
            val schedulePref = findPreference<Preference>("pref_schedule_now")
            schedulePref?.setOnPreferenceClickListener {
                val streamType = typePref?.value?.toIntOrNull() ?: 2
                val volPref = findPreference<SeekBarPreference>("pref_volume_level")
                val volValue = volPref?.value ?: 100
                
                val repeatPref = findPreference<androidx.preference.SwitchPreferenceCompat>("pref_repeat_daily")
                val isRepeating = repeatPref?.isChecked ?: false
                
                scheduleVolume(selectedHour, selectedMinute, streamType, volValue, isRepeating)
                true
            }
        }
        
        private fun scheduleVolume(hour: Int, minute: Int, streamType: Int, volValue: Int, isRepeating: Boolean) {
            val context = requireContext()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        return
                    } catch (e: Exception) {}
                }
            }
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, VolumeSchedulerReceiver::class.java).apply {
                putExtra("streamType", streamType)
                putExtra("volumeValue", volValue)
                putExtra("isRepeating", isRepeating)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val now = Calendar.getInstance()
            if (cal.timeInMillis <= now.timeInMillis) {
                if (cal.get(Calendar.HOUR_OF_DAY) == now.get(Calendar.HOUR_OF_DAY) && 
                    cal.get(Calendar.MINUTE) == now.get(Calendar.MINUTE)) {
                    cal.set(Calendar.SECOND, now.get(Calendar.SECOND) + 2)
                } else {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
            
            Toast.makeText(context, "Unmute scheduled for ${String.format("%02d:%02d", hour, minute)}", Toast.LENGTH_LONG).show()
        }
    }
}
