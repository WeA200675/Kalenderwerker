# Kalenderwerker for Android

Native Android app for importing and editing `.ics` calendars, then writing events to a selected calendar on the device or exporting a portable `.ics` file.

## Features

- Import one or more iCalendar files with Android's document picker or open a shared `.ics` file.
- Select a writable calendar account available through Android's Calendar Provider (Google, Microsoft, Samsung, device calendars, and other installed providers).
- Edit event titles, start/end dates, all-day state, location, and description; add or remove entries.
- Preserve recurrence rules, time-zone definitions, alarms, and unknown event properties in the `.ics` export.
- Save as `.ics` and open it in a compatible calendar app as a fallback.
- Keep the draft in private app storage; request calendar access only when the user selects a device calendar. No network permission or account credentials are used.
- GitHub Actions builds an installable debug APK for each change.

## Build

Requirements: JDK 17, Gradle 8.11.1, Android SDK Platform 35, and Build Tools 35.0.0.

Run `gradle --no-daemon assembleDebug` from the repository root. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
