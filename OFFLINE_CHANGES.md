# Offline behaviour: what changed

Rule: anything that needs internet is blocked while the phone is offline.
When the user taps such a button, the app shows:
  "Failed: you are offline. This needs an internet connection."

Changes
1. BaseActivity: new helper setNeedsInternet(view, on). Offline the button is dimmed
   (alpha 0.5) but NOT disabled, so a tap reaches the action and shows the Failed message.
   The "Failed" message text is set here (requireOnline) and in OfflineWatcher.requireOnline(Context).
2. Buttons that were silently disabled offline now dim and show the message on tap:
   - CR: post notice (Notices), add routine (Routine), add task (Tasks), save routine (Add Routine),
     save task (Add Task), save holiday / vacation (Holiday), save extra class (Class Changes),
     add student (Students), add teacher card (Teachers), send chat message (Chat), KUET portal (Home).
   - Students: "Present" button (Attendance) stays visible, dimmed, and says "Failed" when tapped.
3. Attendance summary: while offline it keeps the last loaded data and shows
   "Offline: showing the last saved attendance." The text refreshes when the network changes.
4. Blocked while offline (unchanged, already guarded before): cancel / extra / undo class,
   holiday set / cancel, attendance give, notice delete, student/teacher delete, photo upload.

Not changed
- Reading data already in the Firestore offline cache (notices, routine, attendance history) still works.
- Chat input box stays disabled offline (hint text says so); the Send button shows the Failed message.
