package com.bunk.volumeapp

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    private var isVolumeDownPressed = false
    private var volumeDownHoldRunnable: Runnable? = null
    private var isMuteAllowed = false
    
    private lateinit var volumeReceiver: BroadcastReceiver
    private lateinit var phoneStateReceiver: BroadcastReceiver
    
    private var vibrationLoopRunnable: Runnable? = null
    private var isForcedVibrating = false
    
    // Config: 8 seconds to allow mute
    private val HOLD_DURATION_MS = 8000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("VolumeOverrideApp", "Service Connected")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 1. Volume Change Receiver (Targeting RINGER volume now)
        volumeReceiver = object : BroadcastReceiver() {
            private var lastToastTime = 0L

            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION" || intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    val ringVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    val ringerMode = audioManager.ringerMode
                    Log.i("VolumeOverrideApp", "Volume/Mode changed. Vol: $ringVol, Mode: $ringerMode")
                    
                    if ((ringVol == 0 || ringerMode != AudioManager.RINGER_MODE_NORMAL) && !isMuteAllowed) {
                        Log.i("VolumeOverrideApp", "Mute attempted without 8s hold. Snapping back.")
                        // Debounce the snapback to prevent rapid 0-1-0-1 loops with the touchscreen
                        handler.removeCallbacksAndMessages("snapback")
                        handler.postDelayed({
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                            val currentMode = audioManager.ringerMode
                            
                            if ((currentVol == 0 || currentMode != AudioManager.RINGER_MODE_NORMAL) && !isMuteAllowed) {
                                try {
                                    if (currentMode != AudioManager.RINGER_MODE_NORMAL) {
                                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                        if (notificationManager.isNotificationPolicyAccessGranted) {
                                            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("VolumeOverrideApp", "Error restoring ringer mode: ${e.message}")
                                }
                                
                                try {
                                    if (audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0)
                                    }
                                    
                                    val now = System.currentTimeMillis()
                                    if (now - lastToastTime > 2000) {
                                        Toast.makeText(context, "Hold Volume Down 8s to mute", Toast.LENGTH_SHORT).show()
                                        lastToastTime = now
                                    }
                                } catch (e: Exception) {
                                    Log.e("VolumeOverrideApp", "Error restoring volume: ${e.message}")
                                }
                            }
                        }, "snapback", 300) // 300ms delay to let the slider settle
                    } else if (ringVol > 0 && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        isMuteAllowed = false 
                    }
                }
            }
        }
        val filter = IntentFilter()
        filter.addAction("android.media.VOLUME_CHANGED_ACTION")
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        registerReceiver(volumeReceiver, filter)
        
        // 2. Phone State Receiver (Force vibration on incoming calls)
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    Log.i("VolumeOverrideApp", "Phone state changed: $state")
                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        // Incoming call! Check if phone is in vibrate mode
                        Log.i("VolumeOverrideApp", "Incoming call detected. RingerMode: ${audioManager.ringerMode}")
                        if (audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE || audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                            startForcedVibration()
                        }
                    } else if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                        // Call answered or ended
                        Log.i("VolumeOverrideApp", "Call ended/answered. Stopping vibration.")
                        stopForcedVibration()
                    }
                }
            }
        }
        registerReceiver(phoneStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    private fun startForcedVibration() {
        if (isForcedVibrating) return
        Log.i("VolumeOverrideApp", "Starting forced vibration loop with ALARM attributes")
        isForcedVibrating = true
        
        val alarmAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        vibrationLoopRunnable = object : Runnable {
            override fun run() {
                if (isForcedVibrating) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val effect = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
                            vibrator.vibrate(effect, alarmAttributes)
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(1000)
                        }
                    } catch (e: Exception) {
                        Log.e("VolumeOverrideApp", "Failed to vibrate: ${e.message}")
                    }
                    handler.postDelayed(this, 1500) // vibrate for 1s, pause for 0.5s
                }
            }
        }
        handler.post(vibrationLoopRunnable!!)
    }
    
    private fun stopForcedVibration() {
        if (isForcedVibrating) {
            Log.i("VolumeOverrideApp", "Stopping forced vibration loop")
        }
        isForcedVibrating = false
        vibrationLoopRunnable?.let { handler.removeCallbacks(it) }
        vibrator.cancel()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (!isVolumeDownPressed) {
                    isVolumeDownPressed = true
                    Log.i("VolumeOverrideApp", "Volume Down pressed. Starting 8s timer.")
                    startVolumeDownHoldTimer()
                }
            } else if (action == KeyEvent.ACTION_UP) {
                isVolumeDownPressed = false
                Log.i("VolumeOverrideApp", "Volume Down released. Canceling timer.")
                cancelVolumeDownHoldTimer()
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isVolumeDownPressed = false
            cancelVolumeDownHoldTimer()
        }

        return super.onKeyEvent(event)
    }

    private fun startVolumeDownHoldTimer() {
        volumeDownHoldRunnable = Runnable {
            if (isVolumeDownPressed) {
                Log.i("VolumeOverrideApp", "8s timer complete. Muting ringtone.")
                isMuteAllowed = true
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, AudioManager.FLAG_SHOW_UI)
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
                
                Toast.makeText(this, "Ringtone Muted", Toast.LENGTH_SHORT).show()
            }
        }
        handler.postDelayed(volumeDownHoldRunnable!!, HOLD_DURATION_MS)
    }

    private fun cancelVolumeDownHoldTimer() {
        volumeDownHoldRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("VolumeOverrideApp", "Service Destroyed")
        unregisterReceiver(volumeReceiver)
        unregisterReceiver(phoneStateReceiver)
        stopForcedVibration()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
