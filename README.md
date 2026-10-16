# Kalenderwerker for Android

Native Android app for importing, editing, and exporting `.ics` calendars. Files are read locally, the current draft is saved in private app storage, and exported calendars can be opened in an installed calendar app.

## Features

- Import one or more iCalendar files with Android's document picker or open a shared `.ics` file.
- Edit event titles, start/end dates, all-day state, location, and description; add or remove entries.
- Preserve recurrence rules, alarms, time-zone definitions, and unknown event properties when exporting.
- Save as `.ics`, then choose a compatible calendar app and destination calendar.
- No network permission or account credentials are required.
- GitHub Actions builds a debug APK for each change.

## Build

Requirements: JDK 17, Gradle 8.11.1, Android SDK Platform 35, and Build Tools 35.0.0.

Run `gradle --no-daemon assembleDebug` from the repository root. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
