# LS Files

**Enterprise-grade file manager for Android** with on-device indexing, cloud storage support, tags, a safe folder, and smart search powered by Gemini AI.

LS Files is a modern, Jetpack Compose-based file manager that goes beyond simple browsing. It provides a complete toolkit for managing your device storage: category-based organization, a recoverable recycle bin with automatic 30-day purging, AI-assisted content search (Gemini OCR over your images), a PIN/biometric-protected safe folder, ZIP compression with live progress, and cloud account management — all on-device with a Room database.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Clone & Open](#clone--open)
  - [Environment Setup (`.env`)](#environment-setup-env)
  - [Firebase Configuration](#firebase-configuration)
  - [Build & Run](#build--run)
- [Configuration](#configuration)
  - [Signing](#signing)
  - [Gradle Properties](#gradle-properties)
  - [Permissions](#permissions)
- [Feature Deep Dive](#feature-deep-dive)
  - [Home Screen & Storage Overview](#home-screen--storage-overview)
  - [Browse & Sorting](#browse--sorting)
  - [Smart Search (Gemini OCR)](#smart-search-gemini-ocr)
  - [Tags & Starred Files](#tags--starred-files)
  - [Safe Folder](#safe-folder)
  - [Recycle Bin & Auto-Purge](#recycle-bin--auto-purge)
  - [ZIP / UnZIP](#zip--unzip)
  - [File Operations](#file-operations)
  - [Cloud Storage](#cloud-storage)
  - [Quick Share](#quick-share)
  - [Haptics & UX Details](#haptics--ux-details)
- [Data Layer](#data-layer)
  - [Room Database](#room-database)
  - [File Categorization](#file-categorization)
- [Testing](#testing)
- [Release Build](#release-build)
- [Known Notes & Limitations](#known-notes--limitations)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Features

| Area | Description |
|---|---|
| 📂 **Browse** | Full file-system navigation with breadcrumbs, list/grid view modes, hidden file toggle, and sort by name / date / size / type (ascending or descending). Directories are always sorted first. |
| 🏠 **Home overview** | Storage usage card (used / free / total with ratio), category cards with real size breakdowns, recent files, and quick access to cloud storage, starred files, and the safe folder. |
| 🔍 **Smart Search** | Search by filename across the tree, plus **Gemini AI OCR** over images so you can find files by their *content* (e.g., receipts, screenshots, scanned documents). Search history is saved (and can be cleared). |
| 🏷️ **Tags** | Create colored, icon-tagged labels (defaults: Important, Work, Study, Life), assign tags to single or multiple files, filter by tag. |
| ⭐ **Starred files** | One-tap starring; starred files persist in Room and survive renames. |
| 🔐 **Safe Folder** | Move files into a private app-internal storage area protected by a **4-digit PIN or biometric authentication** (BiometricPrompt). |
| 🗑️ **Recycle Bin** | Soft-delete files to `ls_bin`; restore to original (or custom) location; **automatic purge after 30 days** via a WorkManager periodic worker (24-hour cadence). |
| 🗜️ **Compress / Extract** | Multi-select ZIP compression and extraction with live byte progress, per-file status, and cancellation. Extraction enforces **zip-slip protection** (canonical path checks). |
| ✂️ **File operations** | Copy, move, single & batch rename, delete (to bin), with automatic name-collision handling (`file (1).ext`). |
| ☁️ **Cloud storage** | Plug-in provider architecture with Google Drive, OneDrive, and Dropbox adapters; connected accounts are stored in Room and can be disconnected (OAuth revocation). |
| 📤 **Quick Share** | Share single/multiple files via Android share sheet targeting the system Quick Share flow (falls back to a chooser). |
| ✨ **UX polish** | Haptic feedback on actions, animated nav icons, shimmer placeholders, pie-chart storage breakdown, edge-to-edge Material 3 theming (light/dark). |

---

## Tech Stack

- **Language:** Kotlin 2.2.10
- **UI:** Jetpack Compose (BOM `2024.09.00`) — Material 3, Material Icons Extended, view-model compose, lifecycle runtime compose
- **Architecture:** MVVM — single `Activity` + `MainViewModel` (`AndroidViewModel`) + repository, unidirectional state via `StateFlow` / `SharedFlow`
- **Async:** Kotlin Coroutines (core + android) — heavy IO on `Dispatchers.IO`
- **Persistence:** Room 2.7.0 (runtime, KTX, compiler via KSP)
- **Networking:** Retrofit 2.12.0 + Moshi 1.15.2 + OkHttp 4.10.0 (Logging & base)
- **AI:** Gemini `generateContent` API (`gemini-3.5-flash`) for image OCR
- **Background jobs:** WorkManager 2.9.1 (periodic bin purge)
- **Security:** AndroidX Biometric 1.2.0-alpha05, AppCheck reCAPTCHA
- **Build:** AGP 9.1.1, Gradle (with configuration cache), KSP 2.3.5, Secrets Gradle Plugin, google-services plugin
- **Testing:** JUnit 4, Robolectric, Roborazzi (JVM screenshot tests), Compose UI Test, AndroidX Test
- **Targets:** `minSdk 24` (Android 7.0) · `targetSdk 36` · `compileSdk 36`

---

## Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  MainActivity (FragmentActivity)                                  │
│  ├── StoragePermissionManager  ── MANAGE_EXTERNAL_STORAGE flow   │
│  └── LsFilesTheme + MainAppScreen (single-activity navigation)   │
│        │                                                          │
│        ▼                                                          │
│  ┌────────────────────────────┐          ┌─────────────────────┐ │
│  │  MainViewModel             │◄────────►│  Room (AppDatabase) │ │
│  │  · navigation state        │  flows   │  · indexed_files    │ │
│  │  · directory listing/sort  │          │  · starred_files    │ │
│  │  · selection & multi-select│          │  · tags / xref      │ │
│  │  · file ops (copy/move/…)  │          │  · bin_items        │ │
│  │  · ZIP jobs w/ progress    │          │  · search_history   │ │
│  │  · smart search + OCR      │          │  · cloud_accounts   │ │
│  │  · safe folder & bin       │          │  · app_settings     │ │
│  │  · cloud accounts          │          │  · files            │ │
│  └───────────┬────────────────┘          └─────────┬───────────┘ │
│              │                                     │             │
│              ▼                                     ▼             │
│  ┌────────────────────────┐             ┌──────────────────────┐ │
│  │  FileRepository        │             │  Services & Workers │ │
│  │  · FS scanning/scoring │             │  · GeminiOcrService │ │
│  │  · zip (stdlib)        │             │  · BinAutoPurge      │ │
│  │  · bin/safe ops        │             │  · BinPurgeWorker    │ │
│  └───────────┬────────────┘             │  · QuickShareManager │ │
│              │                          └──────────────────────┘ │
│              ▼                                                  │
│  ┌────────────────────────┐        ┌──────────────────────────┐ │
│  │  CloudProviderAdapter │        │  UI Screens (8) + comps │ │
│  │  · GoogleDrive  · OneDrive · Dropbox                    │ │
│  └────────────────────────┘        └──────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

Key design decisions:

- **Single-activity MVVM.** Navigation is *state-based* (`NavDestination` StateFlow) with `Crossfade` transitions and a `ModalNavigationDrawer`, rather than a navigation library — this keeps the whole app in one Compose tree with a shared ViewModel.
- **Repository as the single gateway.** All disk, database, and service access funnels through `FileRepository`, so the ViewModel stays thin and testable.
- **Coroutine-heavy IO.** Every disk operation (listing, scanning, zipping, OCR) runs on `Dispatchers.IO` with `ensureActive()` checks so ZIP jobs are cancellable.
- **Streaming UI events.** `MutableSharedFlow<UIEvent>` delivers snackbars and haptics without polluting state.
- **Index-first search.** The `indexed_files` table is the source of truth for smart search; the file-tree walk is the fallback path. The index auto-seeds from the 10 most recent files when empty.

---

## Project Structure

```
ls-files/
├── .env.example                  # Template for secrets (GEMINI_API_KEY)
├── .gitignore
├── metadata.json                 # App capabilities declaration (Gemini API)
├── firebase-applet-config.json   # Firebase project wiring (AppCheck)
├── build.gradle.kts              # Top-level build (plugins only)
├── settings.gradle.kts           # Repos + rootProject "LS Files"
├── gradle.properties             # JVM args, caching, config cache, workers
├── gradle/
│   └── libs.versions.toml        # Version catalog (single source of truth)
└── app/
    ├── build.gradle.kts          # App module: SDK, signing, secrets, deps
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/
        │   │   ├── MainActivity.kt
        │   │   ├── data/
        │   │   │   ├── cloud/          # CloudProvider iface + GoogleDrive/OneDrive/Dropbox adapters
        │   │   │   ├── db/             # Room: Entities, Daos, AppDatabase
        │   │   │   ├── model/          # FileItem, FileCategory, SortField, ...
        │   │   │   └── repository/     # FileRepository (the workhorse)
        │   │   ├── permission/         # StoragePermissionManager (Android 11+ mgmt)
        │   │   ├── service/            # GeminiOcrService, BinAutoPurgeService, QuickShareManager
        │   │   ├── ui/
        │   │   │   ├── MainViewModel.kt
        │   │   │   ├── components/     # 20+ Compose components (dialogs, sheets, charts…)
        │   │   │   ├── screens/        # Home, Browse, Search, Tags, Recent, Bin, Settings, SafeFolder, CategoryDetail
        │   │   │   ├── theme/          # LsFilesTheme (Color/Type/Theme)
        │   │   │   └── util/           # HapticFeedbackUtil
        │   │   └── worker/             # BinPurgeWorker (WorkManager)
        │   └── res/                    # icons, themes, XML provider paths
        ├── test/                       # JVM tests (JUnit, Robolectric, Roborazzi)
        └── androidTest/                # Instrumented tests
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| [Android Studio](https://developer.android.com/studio) | Ladybug or newer (AGP 9.x support) |
| JDK | 17+ (project targets Java 11 bytecode) |
| Android SDK | Platform 36 (compileSdk) |
| Gradle | Managed by the wrapper; AGP 9.1.1 requires Gradle 9.x |
| Device / Emulator | Android 7.0 (API 24) through API 36 |

### Clone & Open

```bash
git clone <your-repo-url> ls-files
cd ls-files
# Open in Android Studio: File > Open… and select the project root.
```

Or build from the command line:

```bash
./gradlew assembleDebug        # macOS / Linux
gradlew.bat assembleDebug      # Windows
```

### Environment Setup (`.env`)

The **Secrets Gradle Plugin** reads `GEMINI_API_KEY` from a `.env` file (matching web-project conventions). Without it, the key is *not* packaged into the APK and smart search falls back to built-in mock OCR text.

1. Copy the template:
   ```bash
   cp .env.example .env
   ```
2. Edit `.env` and set a real key:
   ```dotenv
   GEMINI_API_KEY=AIza...your-key
   ```
3. Keep `.env` out of version control (it is git-ignored). The value is injected into `BuildConfig.GEMINI_API_KEY` at build time.

> **Note:** If `GEMINI_API_KEY` is blank or still the placeholder `MY_GEMINI_API_KEY`, `GeminiOcrService` returns deterministic fallback text per filename (receipts, screenshots, IDs, etc.) so search still functions offline.

### Firebase Configuration

`firebase-applet-config.json` wires the app to the Firebase project `micro-spanner-468112-p0` for **App Check (reCAPTCHA)** against the Gemini API.

- App Check: `com.google.firebase:firebase-appcheck-recaptcha` (enabled dependency)
- The google-services plugin is configured with `missingGoogleServicesStrategy = WARN`; in the common build setup no `google-services.json` is required.
- `FIREBASE_APPCHECK_DEBUG_TOKEN` is added to the Secrets plugin `ignoreList` so local debug builds aren't blocked.

### Build & Run

```bash
# Debug APK
./gradlew :app:assembleDebug

# Install to a connected device/emulator
./gradlew :app:installDebug

# Run unit + Robolectric/Roborazzi tests
./gradlew :app:testDebugUnitTest

# Release (requires signing config — see below)
./gradlew :app:assembleRelease
```

---

## Configuration

### Signing

`app/build.gradle.kts` defines two signing configs:

| Config | Keystore | Notes |
|---|---|---|
| `debugConfig` | `debug.keystore` (project root) | `android` / `androiddebugkey` / `android` — used for debug builds |
| `release` | `$KEYSTORE_PATH` env, else `<rootDir>/my-upload-key.jks` | Alias `upload`; passwords from `STORE_PASSWORD` and `KEY_PASSWORD` env vars |

For an uploadable release build on CI or locally:

```bash
export KEYSTORE_PATH=/path/to/upload.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Release notes:

- `isMinifyEnabled = false` (R8 is *disabled*), `isCrunchPngs = false`
- Default proguard-optimize rules + `proguard-rules.pro` are still wired for future enablement

### Gradle Properties

| Property | Value | Purpose |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx4g -Dfile.encoding=UTF-8` | Daemon heap |
| `org.gradle.parallel` | `true` | Parallel builds |
| `org.gradle.caching` | `true` | Build cache |
| `org.gradle.configuration-cache` | `true` | Faster configuration phase |
| `org.gradle.workers.max` | `4` | Thread ceiling to avoid overload |
| `kotlin.compiler.execution.strategy` | `in-process` | Avoids "Could not connect to Kotlin compile daemon" |
| `kotlin.code.style` / `android.nonTransitiveRClass` | official / true | Kotlin style, lean R classes |

### Permissions

Declared in `AndroidManifest.xml` and managed by `StoragePermissionManager`:

| Permission | Reason | Tier |
|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Full file-system access (All Files Access) — **required** on Android 11+ | Runtime via system settings screen |
| `READ/WRITE_EXTERNAL_STORAGE` | Legacy fallback (maxSdk 29 / 32) | Runtime |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | Android 13+ scoped media read | Runtime |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Gemini API + network | Normal |
| `BLUETOOTH*`, `ACCESS_FINE_LOCATION`, `CAMERA` | Platform capabilities (Quick Share / future scanning) | Runtime |
| `USE_BIOMETRIC` | Safe Folder unlock | Runtime |

The app shows a **rationale dialog** before launching the “All files access” settings page and re-checks permission on every resume.

---

## Feature Deep Dive

### Home Screen & Storage Overview

`HomeScreen` renders:

- **Storage card** — total / used / free from `Environment.getExternalStorageDirectory()` (via `getStorageSpaceInfo()`), visualized with a `StorageBreakdownPieChart`.
- **Category grid** (`CategoriesGrid`) — nine `FileCategory` tiles with real disk sizes computed by a depth-limited recursive scan (max depth 4, `Android/` skipped, app sizes resolved through `PackageManager`).
- **Recent files** — last 20 by `lastModified`, plus a "recently indexed" list (top 5) surfaced from Room.
- **Quick actions** — cloud connect, starred (Tags screen), Safe Folder, document scan (creates a sample scanned PDF in `Documents/`).

### Browse & Sorting

- Breadcrumb-style navigation with directory up/down and deep back-handler chaining (viewer → cloud dialog → permission dialog → drawer → category → selection → subfolder → screen).
- **View modes:** LIST / GRID (persisted in `SharedPreferences("app_settings")`).
- **Sorting:** field (Name, Date, Size, Type) and order (asc/desc) — persisted; directories always bubble to the top. Sort selection is remembered across sessions.

### Smart Search (Gemini OCR)

`SearchScreen` + `GeminiOcrService`:

1. **Filename search** across the indexed `files`/`indexed_files` tables (SQL `LIKE` on name *and* OCR text).
2. **Content search** — images without OCR text are sent to **Gemini `gemini-3.5-flash`** (`generateContent`, base64 inline data, images downsampled to ≤1024 px / JPEG q80 to save bandwidth and memory). The extracted text is cached in `indexed_files.ocrText` and matched by later searches.
3. **Indexing build** — `triggerOcrSmartSearchIndexing()` walks the tree and backfills the index with progress status (`isIndexingOcr` gate prevents duplicate runs).
4. **Search history** — every query is persisted (deduped by query string, capped at 20 recent); users can clear history or delete individual queries.
5. **Offline fallback** — without an API key, or on network error, `generateFallbackOcrText()` fabricates sensible demo text based on filename keywords so the pipeline never blocks.

### Tags & Starred Files

- Default tags seeded on first run: **Important** (#F44336), **Work** (#2196F3), **Study** (#9C27B0), **Life** (#4CAF50).
- Multi-select → `TagSelectionDialog` → store `FileTagCrossRef(filePath, tagId)` pairs; `TagsScreen` lists tags from Room and filters files by tag.
- Starring writes into `starred_files`; the star state follows the file across **renames** (entity re-created on the new path) and is removed on permanent delete.

### Safe Folder

- Files physically move to `context.filesDir/safe_folder` (app-internal storage = hidden from other apps).
- **Unlock options:** 4-digit PIN (stored in `app_settings`, key `safe_folder_pin`) or **biometric** (`SafeFolderLockDialog` + `BiometricPrompt`).
- Operations: move in, restore to `Documents/`, or permanently delete. Locking clears the in-memory file list.

### Recycle Bin & Auto-Purge

- Deletion moves files to `context.filesDir/ls_bin` (renamed with a timestamp prefix) and records metadata in `bin_items` — original path, size, mime, timestamp.
- `BinItem.daysRemaining` counts down from **30 days**.
- **Restore** returns items to the original folder (or a chosen destination) with collision-safe naming.
- **Auto-purge dual mechanism:**
  - `BinAutoPurgeService.purgeExpiredDeletedFiles()` — immediate sweep on app init (threshold: now − 30 days), deleting expired physical files + DB rows.
  - `BinPurgeWorker` — a **periodic WorkManager job (every 24h, battery-not-low constraint)** using `enqueueUniquePeriodicWork(KEEP)` so it survives app restarts; retries up to 3 attempts on failure.

### ZIP / UnZIP

- `compressToZip()` — streams with a 64KB buffer, recursive folder entries, live `(bytes, total, fileName)` callbacks, cancellation via `ensureActive()`; a failed/partial zip is cleaned up automatically.
- `extractZip()` — same streaming model plus a **zip-slip guard**: every entry's canonical path must stay under the destination folder or a `SecurityException` aborts extraction.
- `ZipProgressDialog` shows title, current file, and progress bar with a Cancel button.

### File Operations

| Operation | Implementation detail |
|---|---|
| Copy | `copyRecursively` / `copyTo` with `overwrite=false` |
| Move | `renameTo` first; falls back to copy-then-delete (atomic-ish) |
| Rename | Illegal-character validation (`/ \ : * ? " < > |`), refuses empty names and existing targets; keeps star state |
| Batch rename | Iterates pairs, per-item collision handling, returns success count |
| Delete | Soft-delete → bin (see above) |
| Collisions | `getUniqueCollisionFile` generates `name (1).ext`, `name (2).ext`, … |

### Cloud Storage

- `CloudProvider` interface defines `connectAccount`, `listFiles`, `uploadFile`, `downloadFile`, `deleteFile`, `disconnect`.
- Adapters: `GoogleDriveCloudProvider` / `GoogleDriveAdapter`, `OneDriveAdapter`, `DropboxAdapter` (wired through `CloudProviderAdapter`).
- Connecting stores a `CloudAccountEntity` (id, provider, name, email, timestamp) in Room; the Home screen cloud section renders from `cloudAccountsFlow`.
- Disconnecting revokes OAuth and removes the local row.

### Quick Share

`QuickShareManager.launchQuickShareIntent()`:

- Single file → `ACTION_SEND`; multiple → `ACTION_SEND_MULTIPLE`, all via `FileProvider` (`${applicationId}.provider`, paths in `res/xml/file_paths.xml`).
- Targets `com.google.android.gms` for the system Quick Share surface; falls back to `Intent.createChooser` if unavailable, and finally to `ACTION_SEND` with `Uri.fromFile` for pre-API-24 scenarios.

### Haptics & UX Details

- `HapticFeedbackUtil` (compose-level) maps `HapticType` events to view/canvas haptic feedback: selection toggles, move success, delete.
- Animated navigation icons with selected/unselected variants (`AnimatedNavIcon`).
- `ShimmerPlaceholder` while directories load; `EmptyState` illustrations; `StorageBreakdownPieChart` for storage usage.

---

## Data Layer

### Room Database

`AppDatabase` (version 3, `fallbackToDestructiveMigration`, singleton via double-checked locking) — database `ls_files_database`:

| Table | Purpose |
|---|---|
| `files` | Flat file records (name, path, size, mime, lastModified, isDirectory, isDeleted) used for querying + purge jobs |
| `indexed_files` | Search index (fileName/parentPath/categoryName/lastModified indexes) incl. `ocrText` for AI content search |
| `starred_files` | Starred items keyed by path |
| `tags` + `file_tag_cross_ref` | Tag definitions and many-to-many file-tag links |
| `bin_items` | Recycle bin metadata + original paths |
| `search_history` | Recent queries (20 max) |
| `cloud_accounts` | Connected cloud providers |
| `app_settings` | Key/value store (`safe_folder_pin`, …) |

### File Categorization

`FileRepository.determineCategory()` classifies each file into: **Downloads, Images, Videos, Audio, Documents, Apps, Screenshots, Archives, Other.**

Priority order:

1. `Screenshots/` parent folder or `Screenshot*` prefix → `SCREENSHOTS`
2. Extension map (50+ extensions, e.g., `.jpg/.png/.heic`, `.mp4/.mkv`, `.mp3/.flac`, `.pdf/.docx`, `.apk`, `.zip/.rar/.7z`)
3. MIME-type prefixes (`image/`, `video/`, `audio/`, `text/`, office/PDF markers, package archives, compressed)
4. `Download(s)/` parent → `DOWNLOADS`
5. Fallback → `OTHER`

Directory sizes are computed shallowly (depth ≤ 1) skipping dot-files and the `Android/` folder for responsiveness.

---

## Testing

```
app/src/test        # JVM tests (run on host, incl. Robolectric + Roborazzi)
app/src/androidTest  # Instrumented tests (device / emulator)
```

| Test | Stack |
|---|---|
| `ExampleUnitTest` | JUnit 4 — sample host test |
| `ExampleRobolectricTest` | Robolectric — Android framework on the JVM |
| `GreetingScreenshotTest` | **Roborazzi** — captures Compose screenshots into `build/outputs/roborazzi` |
| `ExampleInstrumentedTest` | AndroidX Test runner + Espresso |

Run everything:

```bash
./gradlew testDebugUnitTest connectedAndroidTest
```

Roborazzi regenerate:

```bash
./gradlew :app:recordRoborazziDebug
```

Visual diff verification:

```bash
./gradlew :app:verifyRoborazziDebug
```

---

## Release Build

1. Prepare a keystore (or export `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
2. Ensure `.env` contains a production `GEMINI_API_KEY`.
3. Build:
   ```bash
   ./gradlew :app:assembleRelease
   # Output: app/build/outputs/apk/release/app-release.apk
   ```
4. (Optional future work) enable R8 by flipping `isMinifyEnabled = true` and auditing `proguard-rules.pro`.

---

## Known Notes & Limitations

- **Cloud adapters** currently exercise the provider scaffolding (connect/disconnect flow, Room persistence, UI); full file listing/upload/download over the provider APIs is scaffolded on the `CloudProvider` interface.
- **Quick Share peers** (`mockNearbyPeers`) are mock data for UI; the actual transfer uses the Android share sheet.
- **Document scan** generates a demo PDF placeholder (text-only PDF header) — it is not a real scanner capture yet (CameraX deps are present in the catalog but commented out).
- **OCR fallback text** is deterministic demo content when no API key is configured; production search quality requires a valid key.
- **Room migrations** are destructive (`fallbackToDestructiveMigration`) — schema changes wipe local state.
- Category/recency scans are **depth-limited (depth 4)** to stay fast; deeply nested folders may not appear in category or recent views.
- `settings.gradle.kts` names the root project **LS Files**; the app module id is `com.aistudio.lsfiles.app` with package/namespace `com.example`.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Could not connect to Kotlin compile daemon` | Already mitigated via `kotlin.compiler.execution.strategy=in-process` in `gradle.properties` |
| Build fails on missing `google-services.json` | Expected — the project uses `firebase-applet-config.json` + `missingGoogleServicesStrategy = WARN` |
| `GEMINI_API_KEY` is `MY_GEMINI_API_KEY` in the APK | Create `.env` from `.env.example` and set a real key; rebuild |
| Search returns fallback demo text | No/placeholder API key or network failure — intentional graceful degradation |
| Files don’t show up in categories | Depth limit (4) or permission not granted — grant **All files access** |
| Bin items disappear after 30 days | By design — `BinPurgeWorker` runs daily and purges expired items |
| Keystore error on release build | Export `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD` or create `my-upload-key.jks` at project root |
| `installDebug` fails on API 30+ | StorageManager requires the user to enable **“All files access”** in system settings (rationale dialog is shown first) |

---

## License

This project is provided for evaluation and development use. No license file is present in the repository — contact the project owner before redistributing.