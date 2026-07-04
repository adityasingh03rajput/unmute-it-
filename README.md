# Unmute It

**Unmute It** is a stealth-oriented Android Accessibility Service designed to prevent accidental silencing of your device and provide an automated, system-native volume scheduling system (Cron-job style). 

Unlike standard apps, *Unmute It* does not appear in your app drawer or launcher. It embeds itself directly into Android's native Accessibility Settings, dynamically camouflaging its UI to match your device's exact OEM framework (whether you use Samsung One UI, Vivo Funtouch, or Pixel Stock).

## Features

- 🛑 **Accidental Mute Prevention:** Intercepts volume down physical button presses. If the volume is dragged to zero, the app instantly snaps it back to a minimum level to ensure you never miss a call.
- ⏳ **Intentional 8-Second Override:** If you *actually* want to mute your phone, you must physically hold the Volume Down button for exactly 8 seconds. This confirms intent and allows the system to enter Vibrate/Silent mode.
- 📳 **Forced Vibration Bypass (USAGE_ALARM):** Bypasses OEM firmware-level haptic suppression on incoming calls (such as on Vivo devices) by packaging persistent vibration loops with Alarm audio attributes.
- ⏰ **Native UI Cron-Job Scheduler:** Set a specific time for the app to automatically unmute your phone. 
    - Translates 0–100% slider selections perfectly to your specific hardware's absolute volume limits (Ringtone, Alarm, Media, or Call).
    - Features a "Repeat Daily" toggle to act as a true cron job, automatically rescheduling itself for exactly 24 hours later.
    - Fully bypasses Android's Do Not Disturb (DND) security constraints by acquiring Notification Policy Access.

## Installation & Setup

1. **Build and Install** the APK via Android Studio or `./gradlew installDebug`.
2. **Hide in Plain Sight:** You will not find an icon for this app on your home screen.
3. Open your phone's native **Settings > Accessibility > Unmute it**.
4. **Enable the Service** toggle.
5. Tap the **Settings (Gear Icon)** to open the Native Scheduler Menu.
6. *Optional:* The app will ask for Do Not Disturb access. Grant it to ensure the automated scheduler can yank the phone out of Silent/Vibrate mode.

## Technical Details

- **Target SDK:** 34 (Android 14)
- **Architecture:** `PreferenceFragmentCompat` + `AccessibilityService` + `AlarmManager (Exact)`
- **Theme Matching:** Implements `Theme.DeviceDefault.DayNight` to strip standard AppCompat styles and inherit raw OS-level styles.
