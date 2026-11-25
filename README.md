# Chores Anything

A household chores app for Android. Make a household, invite the people in it, and keep the chores of the home in one place: who does what, when, how, and what got done.

- **Households** hold any number of people and chores. One person can be in several: the flat and the parents' house.
- **A chore says how it is done.** Besides a title it carries the how ("the broom is in the hall closet, sweep the balcony too"), what you need, about how long it takes, who does it and when. The person doing it opens it and has everything, no questions asked across the house.
- **Anyone or someone.** A chore is for one person, or for anyone: the first one who does it ticks it off.
- **Rhythm.** A chore repeats daily, weekly, every two weeks or monthly. Ticking it off moves it to its next day. A repeating chore can also **take turns**: dishes go to the next person in the list each time.
- **Pass it on.** "Can you take the bins tonight?" passes a chore to someone, who takes it or says no. Until then it shows as passed to them, and nobody else needs to ask.
- **Adults and kids.** Everyone joins as an adult; the owner can make someone a kid. An adult's chore goes through at once and a kid cannot change it. A kid's chore ("Mom, drive me to the cinema") waits until an adult says yes, changes it or says no.
- **Today** shows what matters now: what needs your OK, what was passed to you, what is overdue, today, this week, later, and whenever. "Only mine" hides the rest of the family.
- **Calendar** shows the month with its chores, repeating ones on every day of their rhythm, and finished ones on the day they were done.
- **History** is the household's log: who did, added, passed, took or confirmed which chore and when, with a tally of this week's minutes per person. It keeps a year.
- **Reminders.** A chore can ring the phone of the person it is for: at its time, or 15 minutes, an hour or a day before. Once a day, at nine or a time you choose in Settings, the phone lists today's chores.
- **Notifications** tell the others: a kid asked for something, a chore was given to you, passed to you, taken, refused or done. They need the one-time step under "Notifications and invite links".
- **Invites** work four ways: a code anyone can type, a QR code, a link, or an invitation for one email address (a Google account, or an email account once its address is confirmed).
- **Delete account** in Settings leaves every household (a household you own goes to its next member, an empty one is deleted) and removes the sign-in. The Play Store requires this of apps with accounts.
- Sign-in is with Google, or with an email and password (with a new-password link and an email to confirm the address), so a new phone picks up where the old one left. The app is in English and Greek. It follows the phone's language until another one is chosen in **Settings**, which also has sign out.

It is a sibling of [List Anything](https://github.com/Kokouliss/list-anything) and shares its look, its stack and its Firebase set-up.

## Set up Firebase (once, about ten minutes)

The app stores everything in a Firebase project that you own. The free Spark plan is enough and no payment card is needed.

1. Open the [Firebase console](https://console.firebase.google.com) and create a project named `chores-anything`. If that id is taken, Firebase adds a suffix: then change the id in `firebase/.firebaserc`, and the host `chores-anything.web.app` in `core/domain/.../model/Invites.kt` and `app/src/main/AndroidManifest.xml` to the one Firebase gives you. Google Analytics can stay off.
2. Go to **Build > Authentication > Sign-in method** and enable **Google**. Pick a support email when asked. Then press **Add new provider** and enable **Email/Password** (leave "Email link" off). The emails Firebase sends sign with the project's public-facing name, which you set under **Project settings > General**.
3. Go to **Build > Firestore Database** and create a database in **production mode**. Choose a location close to you, for example `eur3 (Europe)`. The location cannot be changed later.
4. In Firestore, open the **Rules** tab, replace the text with the contents of [`firebase/firestore.rules`](firebase/firestore.rules), and press **Publish**. Without this step the app cannot read or write anything. Repeat this step whenever `firebase/firestore.rules` changes. The deploy command under "Notifications and invite links" publishes the rules too.
5. Go to **Project settings > Your apps**, add an **Android app**, and enter:
   - Package name: `com.kokouliss.choresanything`
   - SHA-1: `5E:BF:9C:15:BD:EF:A2:D2:88:F0:B2:39:01:8C:A5:5A:48:5E:2A:E8`. The console calls this field optional, but Google sign-in does not work without it. To add it later, open **Project settings > Your apps**, pick the Android app and press **Add fingerprint**.
6. Download `google-services.json` and put it in the `app` folder, so the file is `app/google-services.json`. Do this after step 2, because the file must contain the Google sign-in client id. The file is ignored by git on purpose.

The SHA-1 belongs to the debug key in `app/debug.keystore`, which is committed so that every machine builds an app Google sign-in accepts. A release build signed with another key needs that key's SHA-1 added in step 5 as well.

If you choose a Google account and land back on the sign-in screen, the SHA-1 is missing or belongs to another key. Add it as described in step 5 and try again after a few minutes. The app does not need to be rebuilt.

## Notifications and invite links (once)

Notifications to the other people in a household are sent by a small Cloud Function, and invite links open in the app because a page on Firebase Hosting vouches for it. Both are in `firebase/` and are published with one command.

1. The function needs the **Blaze** plan (pay as you go). Upgrade under the gear menu of the Firebase console. A household's notifications stay far inside the free quota, so the bill is zero, but a payment card is required. Without Blaze, skip the function: invite links and the rules still deploy, and the app works without push notifications (the reminders on your own phone need no server).
2. On a computer with Node.js 20 or newer:

   ```sh
   npm install -g firebase-tools
   firebase login
   cd firebase
   npm --prefix functions install
   firebase deploy
   ```

   Without the Blaze plan, use `firebase deploy --only hosting,firestore` as the last line.

Android checks the invite page when the app is installed, so install the app again after the first deploy. `firebase deploy` publishes the function, the invite page, the security rules and the Firestore index together, so it also replaces setup step 4 from now on. Run it again after each update that touches `firebase/`. The project it deploys to is named in `firebase/.firebaserc`.

The invite page lists the SHA-256 fingerprint of the shared debug key in `firebase/hosting/assetlinks.json`. An app signed with another key needs that key's SHA-256 added there.

## Download

Every merge to `main` builds the app and publishes it on the [releases page](https://github.com/Kokouliss/Chores-Anything/releases). The newest one is always at `https://github.com/Kokouliss/Chores-Anything/releases/latest/download/chores-anything.apk`. A build needs `app/google-services.json` in the repository or as a secret; see the workflow in `.github/workflows/release-apk.yml`.

## Signing key (once, before the Play Store)

Until the steps below are done, downloads are signed with the shared debug key from `app/debug.keystore`, which anyone can read. That is fine for trying the app out. For a store listing, and so that nobody else can build an "update" that installs over the app, make a key of your own:

1. On your computer, with a JDK installed:

   ```sh
   keytool -genkeypair -v -keystore chores-anything.keystore -alias chores-anything -keyalg RSA -keysize 2048 -validity 10000
   ```

2. In the GitHub repository go to **Settings → Secrets and variables → Actions** and add four repository secrets: `RELEASE_KEYSTORE_BASE64` (the file as text: `base64 -w0 chores-anything.keystore` on Linux, `base64 -i chores-anything.keystore` on a Mac), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`chores-anything`) and `RELEASE_KEY_PASSWORD`. From the next build on, the release page carries the optimized release build signed with this key, and `chores-anything.aab`, the file you upload to Google Play.
3. Print the key's fingerprints with `keytool -list -v -keystore chores-anything.keystore -alias chores-anything`. Add the **SHA-1** in the Firebase console under Project settings → Your apps → Add fingerprint, or Google sign-in refuses the new build. Add the **SHA-256** to `firebase/hosting/assetlinks.json` and deploy hosting again, or invite links stop opening the app.

Keep the keystore file and both passwords somewhere safe, such as a password manager. Without them the app can never be updated on the Play Store again.

## Build and run

Needs a JDK 21 and the Android SDK (Android Studio installs both).

```sh
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # onto a connected phone or emulator
```

A build without `app/google-services.json` compiles and starts, and shows a page that says the set-up is missing.

## Tests

```sh
./gradlew testDebugUnitTest           # domain model, view models, theme contrast
cd firebase && npm install && npm test   # security rules, on the local Firestore emulator
cd firebase/functions && npm install && npm test   # what the notification function sends to whom
```

## How it is built

Kotlin, Jetpack Compose with Material 3, Hilt, Navigation 3, WorkManager and Firebase (Auth, Firestore, Cloud Messaging). Firestore keeps a copy on the phone, so everything works offline and syncs later.

| Module | What is in it |
|---|---|
| `app` | The activity, the navigation, the notification channels, the reminders (`ReminderWorker`, `TodayWorker`, `ChoreReminderSync`) and the push service. |
| `core/domain` | The model (`Household`, `Member`, `Role`, `Chore`, `Repeat`, `Handoff`, `Activity`), the grouping for Today and the calendar, and the repository interfaces. Plain Kotlin, no Android. |
| `core/data` | The Firestore and Firebase Auth implementations. Every write is offline-first and goes to the household's history as well. |
| `core/ui` | The theme (paper and pine green), the icons, the components and the loader. |
| `feature/auth` | Sign-in, the first-name prompt and Settings. |
| `feature/chores` | Households, the Today, Calendar and History tabs, the chore page and the editor. |
| `firebase` | The security rules and their tests, the Cloud Function that sends notifications and cleans up after a deleted household, and the invite page. |

Each household is one Firestore document with its members and their roles; its chores and its history are subcollections. The rules say who may do what: a kid may add a chore that waits, do a chore, and answer a hand-over, but not change what a chore is. Reminders are planned on each phone from the chores it sees, so they need no server.

## Known limits

- Reminders use WorkManager and can be a few minutes late when the phone sleeps deeply.
- A chore that repeats monthly from the 31st falls on the last day of shorter months.
- There is no Play Store listing yet; see "Download" and "Signing key".
