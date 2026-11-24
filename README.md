# CalAi

CalAi is a Kotlin and Jetpack Compose voice calendar assistant. Speak or type a command and tap **Add event** to schedule it. Connect Google Calendar to sync your events, or try the local demo without cloud setup.

- **Add voice note** lets you dictate or type a note that saves in the event description.
- **Choose event image** adds a photo to the event card on this device.
- Rescheduling, deleting, and changing reminders show a review screen before applying the change.

## Run the app

Open this folder in Android Studio, sync Gradle, and run the `app` configuration. Install Android SDK 36 with minor API level 1 if Android Studio requests it. Use a phone or emulator running Android 7.0 (API 24) or newer. Google Calendar authorization requires Google Play services. The Speak button uses the device's speech recognition activity.

Without cloud setup, the app stores demo events locally and interprets a small set of commands. Demo reminders are saved with events and do not trigger notifications.

| Command | Expected result |
| --- | --- |
| Schedule Dentist tomorrow at 4 PM and remind me 30 minutes before | Create a 30-minute event with a popup reminder |
| Show this week | Display events over the next seven days |
| Move Dentist to tomorrow at 5 PM | Pick a match if needed, then review the new time |
| Remind me about Dentist 15 minutes before | Review a popup reminder for the existing event |
| Delete Dentist | Review the event before deleting it |

## Enable Gemini

1. Register Android app `com.ganeshaher.calai` in Firebase and add the downloaded `google-services.json` to `app/`. Sync and rebuild. The Google Services plugin applies when this file is present.
2. In the Firebase console, open Firebase AI Logic and complete setup for the Gemini Developer API.
3. Run a debug build and submit a command in demo mode. Find the App Check debug token in Logcat and register it in Firebase App Check for the Android app. Keep the token private. Release builds use Play Integrity, which must be configured in Firebase before distribution.
4. Submit a command again. The model name is defined in `app/src/main/res/values/strings.xml`. CalAi sends the command text and current time context to Gemini.

[Firebase AI Logic setup](https://firebase.google.com/docs/ai-logic/get-started) · [App Check setup](https://firebase.google.com/docs/ai-logic/app-check)

## Enable Google Calendar

1. Enable Google Calendar API in the Google Cloud project. Configure the OAuth consent screen. For an external app in testing, add your Google account as a test user.
2. Run the app module's `signingReport` Gradle task from Android Studio and copy its debug SHA-1. Create an **Android** OAuth client with that SHA-1 and package name `com.ganeshaher.calai`.
3. Open CalAi, select **Connect Google Calendar**, and grant Calendar access. The app reads and writes the selected account's primary calendar using the `calendar.events.owned` scope. Reconnect if authorization expires.
4. Create a test event with a reminder, then verify it in Google Calendar. The Calendar app and device notification settings determine whether the popup is displayed.

[Android OAuth setup](https://developer.android.com/identity/authorization) · [Calendar scopes](https://developers.google.com/workspace/calendar/api/auth)

The app supports single timed event creation and rescheduling. For recurring events, the agenda shows individual occurrences. For standalone tasks or recurring series changes, use Google Calendar or Google Tasks.

## Code layout

- `ui` holds Compose screens and the MVVM state and confirmation flow.
- `domain` holds commands, event models, time handling, and interfaces for parsing and storage.
- `data` holds the local demo, Firebase AI Logic parser, and Google Calendar API client.
