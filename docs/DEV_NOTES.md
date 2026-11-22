# Attendra UiTM - Developer notes

Plain-English notes for explaining the code in the FYP presentation. One part per feature.

---

## Prompt 0: Simple coding style (whole app)

The app does the same things as before. Only the way the code is written changed. The database, the
security rules, the layouts, the texts and the screens were not touched.

### What changed

| Before | Now | Why it is easier |
|---|---|---|
| Lambda: `v -> submit()` | `new View.OnClickListener() { public void onClick(View v) { submit(); } }` | You can see which method Android calls |
| Short if: `x ? a : b` | `if (x) { ... } else { ... }` | Reads from top to bottom |
| Method reference: `this::showMenu` | A normal listener that calls `showMenu(v)` | No hidden step |
| `if (x) a(); else b();` without brackets | Always `{ }` (only one-line `if (x) return;` stays) | No mistakes when adding a line |
| Assigning inside a condition, `--left[0] == 0`, `a \|= b` | One step per line | One idea per line |
| `DropdownAdapter<T>` with a label function | `DropdownAdapter` with a list of texts (`List<String>`) | No generics |
| Very long methods | Split into small named methods (`watchClubs`, `renderTotals`, ...) | Each part has a name |
| Methods without a comment | One short comment above each method | Explains the method |

### What was kept on purpose

- The database classes (now called `...Java`) and the ViewModel classes. Moving them would change how the app runs, for example when
  the phone is rotated. New features must not add new ones.
- `Db.root()` for every database call. It also chooses the local emulator in test builds, so do not
  call `FirebaseDatabase.getInstance()` directly.
- `Callback<T>`: Firebase answers later, so every read gives its answer through `onSuccess` or
  `onError`.
- Values kept in an array of one, like `final int[] left = {n}`: Java does not let an inner class
  change a normal local variable.

### How the code is organised

| Folder | What is inside |
|---|---|
| `data/` | Reads and writes of the Firebase database |
| `model/` | Plain classes: Session, Course, Group, ... |
| `ui/auth`, `ui/splash` | Log in, register, email code |
| `ui/lecturer` | Lecturer pages |
| `ui/session` | The Create Session steps |
| `ui/student` | Student pages and GPS check-in |
| `ui/settings` | Settings, profile, clubs, preferred name |
| `ui/common` | Small helpers shared by the pages |
| `map/` | The OpenStreetMap maps |
| `util/` | Text, dates, checks (Validators), saved settings |

### Sections to know: starting a session (`data/SessionJava.java`, method `write`)

- SECTION 1: make the session ID (`push()` makes a unique key).
- SECTION 2: put the session and its index nodes in one map.
- SECTION 3: add one notification for each student.
- SECTION 4: save the map with one write, so everything is saved or nothing is saved.

Database nodes used: `sessions`, `sessionsByLecturer`, `sessionsByGroup` or `sessionsByClub`,
`notifications`.

### If you change ...

| If you change ... | Then ... |
|---|---|
| `labels` in a dropdown | Change the list it comes from in the same order, because `list.get(position)` finds the tapped item |
| `RADIUS_DEFAULT` in `CreateSessionViewModel` | New map-pin sessions start with that radius |
| `LATE_CHOICES` in `WhenStepPage` | Other "Mark as late after" choices are shown |
| `MAX_ACCURACY_M` in `CheckInPage` | GPS must be more (smaller) or less (bigger) accurate to check in |

### How to test it on the emulator

1. Register page: switch Student / Lecturer, tap Create account with empty fields, open every dropdown.
2. Student: Home, My Attendance, Notifications (long-press, Mark all), Settings, Profile, check in.
3. Lecturer: Home, Classes, Add class, History, Report, Share CSV, Profile, Preferred name.
4. Create a session, then on the Live Monitor: Mark, +10 min, Radius, Close.

---

## File names

The files were renamed. The code inside did not change, so the app runs the same.

| Before | Now | Example |
|---|---|---|
| `...Repository.java` | `...Java.java` | `VerificationRepository` -> `VerificationJava` |
| `...Activity.java`, `...Fragment.java` | `...Page.java` | `VerifyEmailFragment` -> `VerificationPage` |
| `activity_...xml`, `fragment_...xml` | `..._page.xml` | `fragment_verify.xml` -> `verification_page.xml` |
| `...Binding` | Follows the XML name | `FragmentVerifyBinding` -> `VerificationPageBinding` |

Android only allows small letters and _ in XML file names. The full list is in `Dev_Manual.pdf`.

---

## Prompt A: One phone per student account

Idea: Google Forms "Limit to 1 response", but one student account per phone. Lecturers and the
tester accounts are not checked.

### Files changed

| File | What changed |
|---|---|
| `ui/student/PhoneCheckPage.java` + `phone_check_page.xml` | New page between login and the Student Dashboard |
| `util/HomeRouter.java` | Students go through the phone check first (login, auto-login, after the email code, after the preferred name) |
| `ui/auth/AuthPage`, `VerificationPage`, `VerificationViewModel`, `data/VerificationJava` | The 6-digit code page can also move the account to a new phone |
| `ui/student/CheckInPage`, `data/CheckInJava` | Check-in also saves `deviceHash` |
| `database.rules.json`, `tools/rules_test.sh` | New rules and tests |

### Sections (`PhoneCheckPage`)

1. Read the phone ID: `ANDROID_ID` changed to a SHA-256 text. The real ID is never saved.
2. Read `students/{uid}`.
3. Read `devices/{deviceHash}` once.
4. Decide: (a) free phone, link it; (b) my phone, open Home; (c) another student's phone, sign out
   and show "Phone already linked"; (d) I have another phone, ask to move with an email code.

### Database

- `devices/{deviceHash}` = `{ uid, linkedAt }`
- `students/{uid}/deviceHash` and `students/{uid}/deviceMovedAt` (only after a move)
- `attendance` and `attempts`: `deviceHash`

### If you change ...

| If you change ... | Then ... |
|---|---|
| Remove the `deviceHash` check in the `attendance` rule | A friend's phone can check in again |
| Delete `devices/{hash}` in the Firebase console | That phone becomes free again (use this if a student loses a phone) |
| `MOVE_WAIT_MS` in `PhoneCheckPage` (and 604800000 in the rules) | Students wait a different time before moving again |

Remember: `ANDROID_ID` is different for a debug build and a release build, and after a factory reset.
The phone then looks like a new phone. Publish the new rules and the new APK at the same time.

### How to test

1. Log in as student 1: Home opens. Log out.
2. Log in as student 2 on the same emulator: "Phone already linked".
3. In the console delete `devices/{hash}`, then log in as student 2: Home opens.
4. Log in as student 1 on another emulator: "Move it to this phone?", then the email code.

---

## Prompt B: Recurring weekly sessions

Idea: Moodle Attendance "Repeat the session above as follows", Google Calendar "Repeat weekly".

### Files changed

| File | What changed |
|---|---|
| `when_step_page.xml`, `WhenStepPage`, `CreateSessionViewModel` | "Repeat weekly", "Until" spinner, one tick per week |
| `ReviewStepPage` | "Repeats weekly" row; saves the series |
| `data/SessionJava` | `createSeries`, `writeSeries`, `cancelWeeks` |
| `data/AttendanceJava` | `closeOverdue` closes past sessions one after another |
| `LecturerHomePage`, `HistoryPage` | Auto-close with a Toast; History has "Upcoming" and the cancel menu |
| `LiveMonitorPage` | No Mark / Close buttons before a future week starts |
| `NotificationsPage`, `StudentMainPage` | A notification is shown and counted only after `showAt` |

### Sections (`SessionJava.writeSeries`)

1. Build the series (`sessionSeries/{seriesId}`).
2. One normal session per ticked week: start = first start + (week - 1) x 7 days.
3. One `updateChildren` saves everything, or nothing.

Only ticked weeks get a number. Untick the break and choose a later "Until" week to still have 14
classes: "Week 8 of 14" is the 8th class.

### Database

- `sessionSeries/{seriesId}`: lecturerUid, title, courseId or clubId, groups, where, startTime,
  endTime, lateAfter, weeks, skippedWeeks, createdAt
- `sessions/{id}`: `seriesId`, `weekNo`, `weekTotal`
- `notifications/{uid}/{sessionId}`: `showAt`

### If you change ...

| If you change ... | Then ... |
|---|---|
| `DEFAULT_WEEKS` (14) or `MAX_WEEKS` (16) in `CreateSessionViewModel` | Longer or shorter semesters (the rules also say 16) |
| `ONE_WEEK_MS` to 14 days | Sessions every two weeks |
| Remove `autoCloseOverdue` in Home and `closeOverdue` in History | Past sessions stay open until the lecturer closes them |

### How to test

Do not change the emulator date: check-in uses the server time. Create weekly sessions, then open
History > Upcoming and long-press a week. For past weeks, use a session that ends in a few minutes
and open Home after that.

---

## Prompt C: Semester summary and 80% alert

Idea: U-Future attendance report, Moodle Attendance "Report" and "warnings".

### Files changed

| File | What changed |
|---|---|
| `ui/lecturer/SemesterSummaryPage` + `semester_summary_page.xml` | New page |
| `ClassesPage`, `SessionReportPage` | "Semester summary" button |
| `data/AttendanceJava` | `countCourse` (counting) and `alertGroups` ... `writeAlerts` (alerts after closing) |
| `model/Attendance` | `ATTENDANCE_LIMIT`, `MIN_SESSIONS`, `belowLimit`, `percent` |
| `NotificationsPage`, `MyAttendancePage` | Warning icon; red "Below 80%" line |
| `util/SemesterUtil` | `semesterOf(time)` |

### Sections

1. Read the students of the group.
2. Read the closed sessions of this course, group and semester.
3. Count present, late and absent. Percent = (present + late) x 100 / all records.
4. Show the list. "Below 80%" filters it; "Share CSV" exports it.

Only saved records are counted, so a student who registered late is not marked absent for old
sessions, the same as My Attendance.

### Database

- `alerts/{uid}/{courseId}` = the percentage of the last alert
- `notifications/{uid}/{key}` with `type` = "alert"

### If you change ...

| If you change ... | Then ... |
|---|---|
| `ATTENDANCE_LIMIT` to 75 | The list, the red line and the alert all use 75% |
| `MIN_SESSIONS` to 1 | One absence in week 1 already sends an alert |
| `attended()` in `SemesterSummaryPage` to `present`, and `attended` in `writeAlerts` to `c[PRESENT]` | Late counts as absent |
| Remove the `alerts` node | Students get the same alert every time a session closes |

### How to test

Close 3 sessions where a demo student is absent: after the third one the student gets "Attendance
below 80%". Close one more absent session: a new alert. Close one where the student is present: no
new alert. Open Classes > Semester summary and tap "Below 80%".
