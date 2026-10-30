# Daily Money Tracker (Android)

A simple Sinhala/English personal income and expense tracker for Sri Lankan Rupees (LKR).

## Features
- Local SQLite storage on the phone (works offline)
- Income and expense entries with date, category and note
- Monthly income, expense and balance summary
- Edit and delete transactions
- JSON backup export and restore through Android's file picker
- Sinhala/English UI toggle

## Build an APK
1. Install Android Studio (current stable) on a computer.
2. Extract this ZIP.
3. In Android Studio choose **Open** and select the `DailyMoneyTracker` folder.
4. Allow Gradle sync and install the Android SDK platform requested by the project (compile SDK 35).
5. To create an APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. Android Studio will show the generated APK location, usually `app/build/outputs/apk/debug/app-debug.apk`.
7. Transfer that APK to your Android phone and install it. You may need to allow installation from that file manager.

## Data & backup
- Transactions are stored in the app's private SQLite database and remain after closing/reopening the app.
- Uninstalling the app or clearing its storage deletes local data. Export a JSON backup regularly.
- Restore replaces the current transaction list with the selected backup file. The app asks for confirmation first.
- Backup files contain financial records. Keep them somewhere private.

## Notes
- This ZIP contains Android Studio source files, not a precompiled APK.
- Android Studio needs an internet connection the first time it downloads Gradle and Android build dependencies.
