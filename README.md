# Personal Cashier

A personal finance and organizer app for Android, built with Kotlin and Jetpack Compose.
Track spending, income and savings, keep subscriptions and debts under control, plan purchases,
manage to-do lists and write notes — all stored locally on the device, no account and no internet
connection needed.

Available in **English** and **Russian**.

---

## Features

### Money
- **Balance & savings** — the main balance plus a separate savings balance; move money between them.
- **Spending & income** — add records with an amount, items / description and a necessity level
  (necessary, medium, not needed). The balance is updated automatically; if a spending is bigger
  than the balance, the app offers to cover the difference from savings.
- **History** — all spending and income in one list grouped by day, with filters (all / spending /
  income), search by text or amount, details, editing and deleting (the balance is corrected).
- **Overview** — statistics for 7 days, a month or a year: totals, a necessity breakdown chart and more.
- **Planned** — essentials and a wishlist with prices; items can be picked when adding a spending.
  Reusable items stay in the list after being bought. Each list has its own remembered sort
  (name A–Z / Z–A, newest / oldest first, most expensive / cheapest first, reusable first / last),
  which is also used when picking items while adding a spending.
- **Subscriptions** — recurring payments (daily / weekly / monthly / yearly) that are charged
  automatically on their date and appear in the history; can be paused.
- **Debts** — money you owe and money owed to you, with partial repayments, due dates and an
  optional balance update.

### Organizer
- **To-do** — tasks in categories shown as tiles. One-time tasks with an optional date and time,
  and recurring tasks that reset every day / week / month / year. Categories can be reordered by
  dragging, renamed and deleted.
  - In edit mode (long-press a task) tasks can be **reordered by dragging**. The order is saved;
    a recurring task that is done for now moves down the list and returns to its own place when it
    resets. New tasks are added at the end.
- **Notes** — a title and unlimited text with formatting (bold, italic, underline, strike-through,
  highlight, bulleted lists), clickable links, file and link attachments.
  - Sort by last edited, date added, title, "with files first", or a **custom order** changed by
    long-pressing and dragging notes.
  - **Secret notes** are hidden from the list and only appear when their exact title is typed into
    the search.

### App
- **Customizable home screen** — long-press a tile (or tap the pencil) to rearrange all tiles;
  the balance tile can be switched between three sizes.
- **Calendars** in all date pickers start the week on Monday, whatever the device region.
- **Side menu** with Home and Settings.
- **Settings** — language (system / English / Russian), backup and transfer, developer page; the
  app version is shown at the bottom.
- **Backup & transfer** — export *all* app data to a single file and import it on another device
  (see below).

---

## Backup & transfer

**Settings → Backup & transfer**

- **Export** → *Save to file* (system file picker) or *Send…* (share sheet: messengers, e-mail,
  Bluetooth, …).
- **Import** → pick a backup file. The app shows what the backup contains (date, app version,
  number of records, notes, tasks, files …) and asks for confirmation.
  **Import replaces all current data on the device.**
- On a fresh install the welcome screen offers **Restore from a backup**, so there is no need to
  set up the app first.

Everything is transferred: history, balances, all lists, notes with formatting and attached files,
tasks and categories, subscriptions, debts, the order of everything that can be reordered (home
tiles, to-do categories, tasks, notes), sort modes and layout settings. The language setting is per device and is not
included.

### File format

A backup is a ZIP archive with the extension `.pcbackup`:

```
manifest.json           header + every key of every preferences DataStore, with its type
files/<relative path>   note attachment files
```

```jsonc
{
  "format": "personal-cashier-backup",
  "version": 1,
  "createdAt": "1762500000000",
  "appVersion": "0.2.3",
  "stores": {
    "user_prefs": { "overall_balance": { "t": "float", "v": "1234.56" }, ... },
    ...
  },
  "files": [ "att-1/photo.jpg", ... ]
}
```

The backup is generic: it copies every key of every DataStore, so data added in future versions is
included automatically. Numbers are stored as strings, so they keep their exact value. Files are
checked before anything is changed: wrong or damaged files, backups from a newer app version and
archives with unsafe paths are rejected, and the current data stays untouched.

---

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose (BOM 2024.09), Material 3 |
| Navigation | Navigation Compose |
| Storage | AndroidX DataStore Preferences, JSON via Gson |
| Async | Kotlin Coroutines & Flow |
| Build | Gradle 8.13, Kotlin DSL |
| Min / target SDK | 34 (Android 14) / 36 |

---

## Architecture

- **Single activity, Compose only.** `MainActivity` hosts the `NavHost` with all routes.
- **No ViewModels / repositories.** Screens collect `Flow`s from small DataStore wrapper objects
  and recompose when data changes.
- **Storage.** Six preferences DataStores (registered in `datastores/AppStores.kt`):

  | DataStore | Contents |
  |---|---|
  | `app_data_v2` | notes, to-do categories and tasks, debts, subscriptions, planned items, home layout, sort modes of notes, essentials and wishlist |
  | `user_prefs` | balance, savings, first-run flag |
  | `spending_prefs` | spending history |
  | `income_prefs` | income history |
  | `essentials_prefs`, `wishlist_prefs` | data of older versions (migrated into planned items) |

  Lists are stored as JSON arrays. `JsonListStore` keeps an in-memory copy, so changes appear
  instantly while they are written to disk in the background. Note attachments are copied into the
  app's private `files/note_files/` folder.
- **Backup** (`backup/`) — `BackupEngine` works only with DataStores and a folder (no Android
  dependencies) and is covered by JVM unit tests; `AppBackup` and `BackupUi` connect it to the
  system file picker and share sheet.
- **Localization** — all UI text is in `res/values/strings.xml` and `res/values-ru/strings.xml`;
  the language can be changed in the app (per-app language, Android 13+).

> The `classes/` package still contains Room entities and DAOs from an early version. They are not
> used.

---

## Project structure

```
app/src/
├── main/
│   ├── java/com/example/financeapplication/
│   │   ├── MainActivity.kt          # activity + navigation
│   │   ├── backup/                  # export / import of all data
│   │   ├── datastores/              # data classes and storage of every section
│   │   ├── screens/                 # one file per screen (+ NoteRichText, MainDrawer)
│   │   ├── ui/components/           # shared Compose components, date/time pickers
│   │   ├── ui/theme/                # colors, typography, theme
│   │   └── utils/                   # Money.kt (money and date formatting), AppInfo.kt (app version)
│   └── res/
│       ├── values/strings.xml       # English
│       ├── values-ru/strings.xml    # Russian
│       └── xml/                     # file provider paths, locale config
├── debug/res/values/strings.xml     # app name of the debug build
└── test/                            # JVM unit tests
```

---

## Getting started

Requirements: Android Studio (Ladybug or newer), JDK 11+, a device or emulator with Android 14+.

```bash
git clone https://github.com/Zangormo/Finance-personal-application.git
```

Open the cloned folder in Android Studio, wait for the Gradle sync and run the `app` configuration.
`local.properties` is not tracked; Android Studio creates it with your SDK path.

### Debug and release builds

The debug build has its own application ID (`com.example.financeapplication.debug`), the name
**Personal Cashier Debug** and separate data, so it can be installed next to the release build.
This is handy for testing — e.g. export a backup from the release build and import it into the
debug build.

```bash
./gradlew installDebug        # install the debug build
./gradlew assembleRelease     # build the release APK
```

### Tests

```bash
./gradlew testDebugUnitTest
```

The unit tests cover money parsing, note text formatting, task ordering, essentials / wishlist sorting,
the Monday-first calendar locale and, in detail, the backup:
encoding of every value type, validation of broken / foreign / malicious files, the ZIP container,
complete device-to-device transfers with realistic data, and a check that every DataStore in the
code is part of backups.

### AI agents

`CLAUDE.md` describes the project structure and the rules for AI coding agents: keep the existing
UI style, make sure new data is included in backup export / import, and update `CLAUDE.md` and
this README after every task.

---

## Version

**0.2.3** (alpha)
