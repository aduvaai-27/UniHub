# UniHub - Project Manual

UniHub is a class companion app for one university section (KUET, 2nd Year 2nd Term, roll 2307001 to 2307123).
Students and the Class Representative (CR) share one app for the class routine, assignments and exams, notices,
attendance, a live group chat, teacher and student contacts, a holiday calendar and profile pictures.

This manual describes what the app does, how it is set up, and which Android and Firebase techniques I have used to build it.

---

## Contents

1. [Quick facts](#1-quick-facts)
2. [Roles](#2-roles)
3. [Setting up the project](#3-setting-up-the-project)
4. [Services (screens) of the app](#4-services-screens-of-the-app)
5. [Attendance rules](#5-attendance-rules)
6. [Profile pictures](#6-profile-pictures)
7. [Notifications and background service](#7-notifications-and-background-service)
8. [Trusted time](#8-trusted-time)
9. [Offline mode](#9-offline-mode)
10. [Data model](#10-data-model)
11. [Firebase rules](#11-firebase-rules)
12. [Android concepts used](#12-android-concepts-used)
13. [Project structure](#13-project-structure)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Quick facts

| Item | Value |
|---|---|
| App name | UniHub |
| Package / application id | `com.unihub.app` |
| Language | Java 17, XML layouts, View Binding |
| Min SDK / target SDK | 24 (Android 7.0) / 34 (Android 14) |
| Build tools | Android Gradle Plugin 8.5.2 |
| Firebase project | `unihub-3a25a` |
| Firebase services | Authentication (email and password), Cloud Firestore, Realtime Database |
| Other libraries | Material Components, RecyclerView, ConstraintLayout, CardView, Volley |
| Class days | Sunday to Thursday |
| Time zone | Fixed to `Asia/Dhaka` (UTC+6) |

---

## 2. Roles

Everyone signs in with a roll number and a password. Two roles exist.

| | Student | CR (Class Representative) |
|---|---|---|
| Sign in | Roll `2307001` to `2307123` + password | Same |
| Register | Name, roll, password, lab group (A1, A2, B1, B2) | Same, plus the tick box "I am the Class Representative" and the invitation code |
| Routine | Views the routine of any group, opens on the own group | Views everything, adds and removes classes, imports the printed routine |
| Class changes | - | Adds an extra class or cancels a class for one date |
| Assignments, exams, notices | View | Post and delete |
| Attendance Tracker | Gives attendance while the class is in progress and tracks own attendance | Same (the CR sees only own attendance too) |
| Teachers and Students | View and search, save a contact to the phone | Same, plus add, edit and delete cards |
| Holiday and Vacation | View in the Calendar | Sets holidays and vacations |
| Profile pictures | Changes the own picture | Changes the picture of every student and every teacher |

The sign-in is a Firebase email account built from the roll number: `2307112@student.unihub.app`. The role is stored in the
`users` collection, and the Firestore rules check it on the server, so a student cannot act as the CR by editing the app.

---

## 3. Setting up the project

### 3.1 Requirements
* Android Studio (Koala or newer) with JDK 17
* An Android phone or emulator with Android 7.0 or newer
* The Firebase project `unihub-3a25a`, or a new one (see 3.3)

### 3.2 Open and run
1. Android Studio -> **File -> Open** -> choose the `UniHub` folder.
2. Wait for **Sync Project with Gradle Files** to finish.
3. Run the `app` configuration on a phone or emulator.

The Firebase plugin is applied only when `app/google-services.json` exists. Without that file, the app still starts in a local
mode (accounts stored on the phone only), and the screens that depend on Firebase say so.

### 3.3 Firebase set-up (once)
1. **Authentication** -> Sign-in method -> enable **Email/Password**.
2. **Firestore Database** -> Create database. Open the **Rules** tab, paste the whole of `firestore.rules`, then **Publish**.
3. **Realtime Database** -> Create database -> **Start in locked mode**. Open its **Rules** tab, paste the rules from
   [section 11.2](#112-realtime-database-rules), then **Publish**. Realtime Database is used only for the group chat.
4. **Project settings -> General -> the Android app (`com.unihub.app`)** -> download `google-services.json` and place it at
   `app/google-services.json`.
5. If the Realtime Database URL differs from the default, set it in `util/ChatPrefs.java` (`DB_URL`). The URL is shown at the
   top of the Data tab, for example `https://unihub-3a25a-default-rtdb.firebaseio.com`.
6. Cloud Storage is **not** needed. Profile pictures are kept in Firestore (see [section 6](#6-profile-pictures)).

### 3.4 CR invitation code
The code that unlocks the CR role is a constant in `ui/LoginActivity.java`:

```java
private static final String CR_CODE = "UNI-CR-2026";
```

I have kept it in one place so that it can be changed before the app is shared. Only the CR should know it.

### 3.5 First run
* The first CR who opens **Students** or **Teachers** while the server collection is empty stores the built-in student list and the
  built-in course cards (taken from the printed class sheets) in Firestore.
* The CR can load the printed Section A and Section B routine in one step: on **Class Routine**, **long-press the + button**.
  A section that already has a running routine is skipped, so nothing is doubled.

---

## 4. Services (screens) of the app

The Home screen shows a welcome header with the profile picture, the **Next Class** card, three counters
(Classes Today, Due This Week, Classes Attended), today's date, and a grid of service tiles.

### 4.1 Sign in and registration
One screen for both. Registration asks for name, roll, password and lab group. The roll must match `2307001` to `2307123`
(`RollValidator`). The password needs at least 6 characters.

### 4.2 Group Chat
A live class chat for students and the CR on Firebase Realtime Database. The latest 100 messages load, and new ones arrive
through a `ChildEventListener`. A message can be created once and never edited or deleted from the app. The **Mute** button
switches chat notifications off on that phone.

### 4.3 Class Routine
A weekly schedule from Sunday to Thursday. The routine of any group (A1, A2, B1, B2) can be viewed, and it opens on the own group.
Lab classes (even course numbers such as CSE 2202) are shown only to the matching lab group. The CR adds a class with the **+**
button (day from a list, times from scroll wheels) and removes classes. A removed weekly class is kept with an `endDate`, so old
attendance stays correct.

### 4.4 Class Changes (CR only)
Works on one date at a time:
* **Extra Class** adds a class held on that date only. It appears in Today and the Attendance Tracker like any other class.
* **Class Cancellation** marks a class of that date as not held.

Steps: choose the option, choose the date, choose the section or group, then pick the class from the routine of that day.
Both actions post a notice, and both can be undone (`ClassUndo`).

### 4.5 Assignments and Exams
Shared deadlines. The CR posts an assignment or an exam with a course, details, date (lists) and time (scroll wheels). Saving
writes the task and a notice, and the notice reaches every phone as a notification. Two reminders are scheduled per item:
one day before and one hour before.

### 4.6 Notices
A live notice board. Everyone reads, only the CR posts or deletes.

### 4.6b Dark mode
Every user (student and CR) can switch with the moon / sun icon in the home screen header: one tap = dark, next tap = light.
The choice is saved on the phone (`ThemePrefs`) and kept after logout. Colors live in `values/colors.xml` and `values-night/colors.xml`.

### 4.7 Attendance Tracker
Two tabs: **Today** and **Summary**. Students and the CR use it only for their own benefit: each person sees only their own
attendance. The rules are in [section 5](#5-attendance-rules).

### 4.8 Teachers
One card per course (code and title) with the teachers taking it: name, short name, department, email and phone.
* A round profile picture is shown for each teacher. Tapping it opens the picture full screen.
* **Search** by teacher name (or short name). While searching, each card keeps only the matching teachers.
* **Save to Contacts** checks the phone Contacts first, and opens the phone's "Add contact" screen with everything filled in if
  the number is not yet saved.
* The CR adds, edits and deletes course cards. While editing, **Pick number from Contacts** reads the phone Contacts.

### 4.9 Students
One card per student, ordered by roll: picture, roll, group, name, phone and email.
* **Search** by name or roll. Part of either works (`rohan`, `2307054`, `054`), several words must all match, and capital letters
  are ignored. A counter shows "Found 3 of 123" while searching.
* Tapping the picture opens it full screen.
* **Save to Contacts** works the same way as in Teachers.
* The CR adds, edits and deletes students.

### 4.10 Calendar
A month calendar that marks public holidays in red and holidays set by the CR in purple. Public holidays of Bangladesh come
from the Nager.Date REST API through Volley.

### 4.11 Holiday and Vacation (CR only)
The CR enters a purpose and one date or a range of dates (tomorrow or later, never today). Classes on those dates are
cancelled automatically: no absent mark is given, and the day shows as "Holiday - purpose". One entry covers a whole Eid break,
and deleting the entry brings the classes back. Public holidays cancel classes in the same way. A notice is posted so that
every student is notified.

### 4.12 Next 7 Days
Opened by tapping the **Next Class** card. It lists today and the six days after it for the own section and lab group, and marks
holidays, extra classes and cancelled classes.

### 4.13 KUET Website
An outlined button at the bottom of Home that opens the university website in the browser (implicit intent).

---

## 5. Attendance rules

* Sessions are generated automatically from the weekly routine and are never stored.
* Attendance can be given **only while that class is in progress**. The phone checks this, and the server rules check it again.
* Past and future classes cannot be changed, and attendance cannot be edited after it is given.
* A class without attendance counts as **Absent**.
* Absent is never stored. It is worked out from the clock: a class that has ended, was held, and has no attendance record is
  absent. The app does not need to be running, signed in or online when the class ends.
* The CR can mark a class as **Not Held**. It is excluded from every percentage, and students receive a notice.
* Percentages count from the day a class was added to the app.

### Summary tab
One card per course with its own ring, percentage, present / absent / held counts and a level. There is no overall percentage here.
The overall ring is shown on the Today tab only.

| Attendance | Level |
|---|---|
| 90% or more | Excellent |
| 80% to 89% | Good |
| 60% to 79% | Okay |
| Below 60% | Critical |

The whole-number percentage that is shown is the one that is judged, so the label always matches the number
(`AttendanceLevel`).

---

## 6. Profile pictures

Every person has a round profile picture: the logged-in user in the Home header, every card in **Students** and every teacher in
**Teachers**. Until a picture is saved, the default picture (`res/drawable-nodpi/default_avatar.png`) is shown.

### Using it
* **Tap a picture** and it opens full screen in `PhotoViewActivity`. It is a separate screen, so the screen it was opened from
  goes to `onPause()`. The lifecycle log is visible in Logcat under the tag `UniHubLifecycle`.
* **Add photo / Change photo** opens the phone gallery through the Android photo picker. No storage permission is needed.
* **Remove photo** brings the default picture back.
* A picture saved by roll 2307112 appears everywhere that roll is shown: Home header, Students card and the full-screen view,
  for every other user as well.

### Who can change which picture

| Picture | Student | CR |
|---|---|---|
| Own picture | Yes | Yes |
| Another student | No | Yes |
| A teacher | No | Yes |

The same rule is enforced by the Firestore rules, not only by the app.

### How it is stored
I have chosen Firestore instead of Cloud Storage, because Cloud Storage now requires a billing account while Firestore works on
the free Spark plan. The picture is cropped to a square and shrunk on the phone (`ImageUtil`), then saved as Base64 text in two
collections, written together in one batch (`ProfilePhotos`):

| Collection | Content | Used by |
|---|---|---|
| `photoThumbs/{key}` | 160 px JPEG, up to 40,000 characters | Lists and the Home header (listened to live) |
| `photoFull/{key}` | 800 px JPEG, up to 600,000 characters | The full-screen view only |

Key: `s_<roll>` for a student or the CR, `t_<teacher name>` for a teacher. The same teacher on several courses shares one picture.
If the CR renames a teacher, the picture no longer matches the new name. A changed picture needs internet, and a red bar says so.
Saved pictures still show offline from the local copy that Firestore keeps.

---

## 7. Notifications and background service

`NoticeService` is a foreground service with live Firestore listeners. New notices, assignments, exams, class changes and holidays
reach other phones within seconds.

| Channel | Used for |
|---|---|
| Deadline and exam reminders | One day and one hour before a deadline (AlarmManager -> `ReminderReceiver`) |
| New notices, assignments and exams | Every new notice, including class changes and holidays |
| Group chat messages | Messages from other people, never the own, and not while the chat screen is open |
| Background updates | The silent permanent notification of the service |

The permanent notification stays live, for example "Next class: CSE 2201 at 10:00 AM", with the nearest assignment and exam
deadline in the expanded view. It refreshes every minute and whenever the routine or a cancellation changes. `BootReceiver`
restarts the service after the phone restarts.

On Xiaomi, Redmi, Oppo and Realme phones: Settings -> Apps -> UniHub -> enable **Autostart** and set battery to **No restrictions**.
On Android 13 and newer, the notification permission is requested with `requestPermissions()`.

---

## 8. Trusted time

The phone clock cannot be trusted, because a user can change it to fake attendance or deadlines. `util/TrustedTime` is the only
clock used for attendance, next class, deadlines, calendar and notifications.

1. When the phone is online, the real time is read with **Volley (JSON)**: `worldtimeapi.org`, then `timeapi.io`, then the HTTP
   `Date` header of a Google server.
2. The answer is stored together with `SystemClock.elapsedRealtime()`, which counts since boot and ignores the clock setting:
   `now = API time + time elapsed since the sync`. It keeps working without internet.
3. It stays valid until the phone reboots (checked with `Settings.Global.BOOT_COUNT`). After a reboot, one new sync is needed.
4. It syncs at app start, when a screen opens, when the internet returns, and every 10 minutes or more when needed.
5. If it has never synced since boot, the phone clock is used and the Attendance screen says so.

The app time zone is fixed to `Asia/Dhaka` in `UniHubApp`, the same UTC+6 that the Firestore rules use. Giving attendance is
still checked by the server clock, so a wrong phone clock can never give attendance.

---

## 9. Offline mode

`OfflineWatcher` listens to the **airplane mode broadcast** (dynamic Broadcast Receiver) and to a **network callback** (Wi-Fi or
mobile data off, signal lost). `BaseActivity` starts it in `onStart()` and stops it in `onStop()`. A screen only overrides
`offlineBar()` and `onConnectivityChanged()`.

While offline, a red bar is shown and these features are blocked: Group Chat (typing and Send), Calendar holidays, the live date
and website button on Home, sign in and registration, the Present button, marking a class as not held, changing a profile picture,
and every CR action that writes to Firebase. Reading saved data still works, because Firestore keeps an offline copy.

---

## 10. Data model

### 10.1 Cloud Firestore

| Collection | Document id | Content |
|---|---|---|
| `users` | Firebase uid | name, id (roll), role (`student` or `cr`), group |
| `routine` | auto id | course, title, teacher, room, day, startMin, endMin, group, section, `date` (extra class), `endDate` (removed class) |
| `cancellations` | session key | sessionKey, date, routineId, by |
| `holidays` | auto id | purpose, startDate, endDate, by, createdAt |
| `tasks` | auto id | type (`ASSIGNMENT` or `EXAM`), title, course, details, due, createdAt, author |
| `notices` | auto id | title, body, author, timestamp |
| `attendance` | `date_routineId_roll` | uid, roll, name, group, date, routineId, sessionKey, course, markedAt |
| `students` | roll | roll, name, phone, email, group |
| `teacherCards` | course id | code, title, teachers (list of name, shortName, dept, email, phone) |
| `photoThumbs` | `s_<roll>` or `t_<name>` | thumb, updatedAt |
| `photoFull` | `s_<roll>` or `t_<name>` | full, updatedAt |

### 10.2 Realtime Database
`chat/messages/{pushId}` holds uid, name, roll, role, text and a server timestamp.

### 10.3 Local SQLite
`unihub.db`, table `marks`: the own present / absent answers per class session. It is exposed through the app's Content Provider
(`content://com.unihub.app.provider/marks`) and is emptied whenever the account changes, so the next person on the phone never
sees the previous person's "Classes Attended".

---

## 11. Firebase rules

### 11.1 Firestore rules
The complete rules are in **`firestore.rules`**. I have kept that file as the only copy, so it cannot get out of date. It is pasted into
Firebase Console -> Firestore Database -> Rules and published. It covers:
* role checks (`isStaff()` reads the role from `users`),
* attendance and user profiles can be read only by their owner (no CR access to other people's attendance),
* attendance creation only for the own roll, only on the same day, only while the class is running, with a fixed document id
  and a server timestamp (`inClass()` also rejects removed classes),
* new holidays only from tomorrow onwards,
* profile pictures (`photoWriter()`): the CR for everyone, a student only for `s_<own roll>`, with size limits and a server timestamp.

### 11.2 Realtime Database rules

```json
{
  "rules": {
    "chat": {
      "messages": {
        ".read": "auth != null",
        "$id": {
          ".write": "auth != null && !data.exists()",
          ".validate": "newData.hasChildren(['uid','name','roll','role','text','timestamp'])",
          "uid":       { ".validate": "newData.val() === auth.uid" },
          "name":      { ".validate": "newData.isString() && newData.val().length <= 60" },
          "roll":      { ".validate": "newData.isString() && newData.val().length <= 10" },
          "role":      { ".validate": "newData.val() === 'student' || newData.val() === 'cr'" },
          "text":      { ".validate": "newData.isString() && newData.val().length > 0 && newData.val().length <= 500" },
          "timestamp": { ".validate": "newData.val() === now" },
          "$other":    { ".validate": false }
        }
      }
    }
  }
}
```

Only signed-in users can read or send. A message is created once and never edited or deleted, and `uid` must match the sender's account.

---

## 12. Android concepts used

| Concept | Where it is used |
|---|---|
| Activity lifecycle | `BaseActivity` logs every callback under the tag `UniHubLifecycle`. Opening a picture, the Next Class card or any service tile sends the previous screen through `onPause()` |
| Intents | Explicit intents for every screen; implicit intents for the website, the Add contact screen and the photo picker |
| View Binding | Every layout (`ActivityMainBinding`, `ItemStudentBinding`, `ItemTeacherBinding`, `ActivityPhotoViewBinding`, ...) |
| RecyclerView + ViewHolder | Home tiles (2-column `GridLayoutManager`), students, routine, tasks, notices, chat, attendance sessions and summary |
| ListView + ArrayAdapter | Notices (`NoticeAdapter`, rows reused through `convertView`) |
| Custom Views | `AttendanceRingView` (progress ring), `TimeWheel` (time picker), `AvatarView` (round profile picture) |
| Firebase Authentication | Roll number as an email account |
| Cloud Firestore | Main data with live snapshot listeners, batches for multi-document writes, security rules |
| Realtime Database | Live group chat with `ChildEventListener` |
| Volley | Time API, weekday API (Aladhan), public holidays (Nager.Date) with `RequestQueue` and `JsonObjectRequest` |
| SQLite + Content Provider | Local copy of own attendance through `UniHubProvider`; reading the phone Contacts through `ContentResolver` |
| Runtime permissions | `READ_CONTACTS` and `POST_NOTIFICATIONS`. The photo picker needs none |
| Services | Foreground service `NoticeService` with a permanent notification |
| Broadcast Receivers | Manifest receivers `BootReceiver`, `ReminderReceiver`; dynamic receivers for the clock tick, date change, time zone change and airplane mode |
| AlarmManager | Deadline reminders (one day and one hour before) |
| Notifications | Four channels, reminders, chat, notices, live next-class notification |
| SharedPreferences | Session, chat mute switch, saved trusted time, saved public holidays |
| Activity Result API | Photo picker (`PickVisualMedia`) and permission requests |
| Material Components | Outlined text fields (with clear icon for the search bars), buttons, dropdowns, snackbars |

---

## 13. Project structure

```
UniHub/
  firestore.rules                  Firestore security rules (single source of truth)
  README.md                        this manual
  app/
    google-services.json           Firebase configuration
    src/main/
      AndroidManifest.xml
      java/com/unihub/app/
        UniHubApp.java             application class: time zone, channels, time sync, public holidays
        ui/                        screens
          LoginActivity, MainActivity, ChatActivity, RoutineActivity, AddRoutineActivity,
          ClassChangeActivity, TasksActivity, AddTaskActivity, NoticesActivity,
          AttendanceActivity, TeachersActivity, StudentsActivity,
          CalendarActivity, HolidayActivity, UpcomingActivity, PhotoViewActivity, BaseActivity
        adapter/                   RecyclerView / ListView adapters
        data/                      models, seed data, ProfilePhotos, DbHelper, UniHubProvider, MarksCache
        view/                      AttendanceRingView, TimeWheel, AvatarView
        service/                   NoticeService
        receiver/                  BootReceiver, ReminderReceiver
        util/                      Session, TrustedTime, TimeUtil, OfflineWatcher, Notifier, Reminders,
                                   Holidays, PublicHolidays, DateInfo, NextClass, NextDeadline, ThemePrefs,
                                   RollValidator, ContactsAccess, ContactsHelper, ImageUtil, ChatPrefs,
                                   ClassUndo, Ui
      res/
        layout/                    screens and list items
        drawable/                  cards, bubbles, unihub_logo, launcher icon
        drawable-nodpi/            default_avatar.png (default profile picture)
        values/                    strings, colors, themes
```

---

## 14. Troubleshooting

| Problem | Cause and fix |
|---|---|
| "Permission denied" when saving anything | The Firestore rules are not published, or only part of them was pasted. Paste the whole `firestore.rules` and publish again |
| Profile picture does not save | The `photoThumbs` and `photoFull` rules are missing, the phone is offline, or a student is trying to change someone else's picture |
| Group Chat says Realtime Database is not set up | Create the Realtime Database, publish its rules, and check `DB_URL` in `ChatPrefs.java` |
| The app starts in "Offline mode: accounts are saved on this phone only" | `app/google-services.json` is missing or the Firebase plugin did not apply. Add the file and sync Gradle |
| Sign in fails for a new account | Email/Password sign-in is not enabled in Firebase Authentication |
| Notifications stop after some time | Battery optimisation. Enable Autostart and set battery to No restrictions for UniHub |
| Attendance says the time is not verified | The phone has not reached a time API since boot. Connect to the internet and reopen the screen |
| "Present" is refused although the class is running | The class has a different group, the class was cancelled or is on a holiday, or the phone is offline |
| Build error after replacing files | File -> Invalidate Caches and Restart, then Sync Project with Gradle Files |
