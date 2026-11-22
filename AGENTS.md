# AGENTS.md — Apex Solar Work Engineering & Agent Guardrails

> **ATTENTION ALL AI CODING AGENTS (Gemini, Claude, Cursor, Copilot, etc.)**:
> Read this entire document before editing ANY file.
> The architectural decisions below were requested and approved by the project owner.
> **DO NOT REVERT OR OVERWRITE THESE CORE INVARIANTS.**

---

## 1. Project Overview & Identity

- **App Name**: **Apex Solar Work** (formerly DocFolio)
- **Current Version**: **v1.5** (versionCode `6`)
- **Repository**: [https://github.com/azizthekiller2/Apex-Solar-Work](https://github.com/azizthekiller2/Apex-Solar-Work)
- **Application ID**: `com.aistudio.docfolio.krmvqx` (registered in `app/google-services.json`)
- **Code Namespace**: `com.example` (MainActivity is `com.example.MainActivity`)
- **Primary Use Case**: Field engineers, solar site workers, and management use this app to take photos of site slips (inspection protocols, material requisition slips, delivery challans) and store them in a shared company vault where the boss can search by date (e.g. `12/5/2026`) and view high-resolution photos on remote devices.

---

## 2. CRITICAL INVARIANTS (DO NOT BREAK / DO NOT OVERWRITE)

### Rule 1: Shared Company Vault (NO Login Silos)
- **Path**: Cloud Firestore collection is `/company_documents/{docId}`.
- **NEVER** change this back to `/users/{userId}/documents`.
- **NEVER** add back mandatory Google Sign-In gates or block app entry behind authentication.
- **Why**: The boss and multiple field workers across the company install the APK on different phones. Everyone must instantly see and search all company slips without individual account silos.

### Rule 2: Fast 1-Second Save Flow (AI OCR is Optional)
- In `ScanReviewScreen.kt`, the AI button is labeled **"Auto-Fill with AI (Optional)"** (`OutlinedButton`).
- **NEVER** make AI OCR automatic or block the user from saving immediately.
- **Why**: Cloud AI scanning took too long on slow mobile connections. Field workers take a photo, confirm the date, and tap **Save** in 1 second. AI auto-fill is purely an optional helper.

### Rule 3: High-Resolution Photo Quality for Remote Zoom
- In `util/ImageStorageHelper.kt`:
  - `MAX_PREVIEW_EDGE = 800` (Do NOT downgrade to 480).
  - `MAX_PREVIEW_BASE64_LENGTH = 250_000` (Do NOT downgrade to 140_000).
- In `data/DocumentRepository.kt`:
  - Base64 truncation ceiling is `280_000` chars.
- In `ui/DocumentDetailScreen.kt`:
  - Tapping the photo opens a full-screen zoom dialog with pinch-to-zoom (`rememberTransformableState`).
- **Why**: When the boss opens slips on their phone, handwritten carbon-copy text and small numbers must remain sharp and readable when zoomed in.

### Rule 4: Boss Date Search Support
- `util/DateSearchParser.kt` parses `DD/MM/YYYY` (default Indian convention) and ISO `YYYY-MM-DD`.
- Ambiguous dates like `12/5/2026` are automatically resolved for search so the boss finds all slips filed on that date.
- Both the main Vault search bar and the **By Date** tab calendar support instant date lookup.

### Rule 5: In-App Auto-Updater & Relay Endpoint
- Managed by `util/AppUpdateManager.kt`.
- Checks for updates automatically on app startup.
- **Primary / Fallback URL**: Must use the Oracle VM relay manifest endpoint:
  ```text
  https://docfolio.130.210.30.151.sslip.io/api/documents/apex_update_manifest/photo?token=docfolio_secret_test_key_99812
  ```
- **DO NOT** rely on unauthenticated `raw.githubusercontent.com` URLs — the `@azizthekiller2` GitHub account currently has a spam flag that causes raw URLs and GitHub Actions to return 404.
- **DO NOT** restart, modify, or interfere with other systemd services on Oracle VM `130.210.30.151` (it hosts 17+ mission-critical Telegram bots).

### Rule 6: Firestore Security Rules
- Database ID: `ai-studio-android-docfolio-c591d8bf-0c72-4b94-b6ba-4700a1282700`.
- Active production rules:
  ```javascript
  rules_version = '2';
  service cloud.firestore {
    match /databases/{database}/documents {
      match /company_documents/{docId} {
        allow read, write: if true;
      }
      match /{document=**} {
        allow read, write: if false;
      }
    }
  }
  ```
- Deploy command: `firebase deploy --only firestore:rules --project gen-lang-client-0228559203`.

---

## 3. Architecture & File Reference

```text
app/src/main/java/
├── com/example/
│   ├── MainActivity.kt              # Launches DocFolioApp, triggers AppUpdateManager check
│   ├── data/
│   │   ├── DocumentRepository.kt    # Shared /company_documents collection real-time sync & CRUD
│   │   ├── GeminiOcrService.kt      # Optional Firebase AI Logic structured extraction
│   │   └── local/                   # Room SQLite database (offline cache)
│   ├── firebase/
│   │   ├── AppCheckSetup.kt         # Firebase App Check config
│   │   ├── AuthHelper.kt            # Silent/anonymous auth provider
│   │   └── FirestoreErrorHandler.kt # Error mapping & diagnostics
│   ├── model/
│   │   └── DocumentModels.kt        # ScannedDocument, DocumentCategory, OCR fields
│   ├── ui/
│   │   ├── DocFolioApp.kt           # Top-level scaffold, navigation, update notification dialog
│   │   ├── VaultScreen.kt           # Search bar, category filters, document cards
│   │   ├── DateLookupScreen.kt      # Calendar view, active day indicators, daily report PDF export
│   │   ├── ScanReviewScreen.kt      # Fast photo preview, optional AI button, 1-click Save
│   │   ├── DocumentDetailScreen.kt  # Full-screen pinch-to-zoom photo, PDF actions, OCR text
│   │   └── CameraView.kt            # CameraX viewfinder with flash & grid
│   └── util/
│       ├── AppUpdateManager.kt      # In-app APK updater & manifest parser
│       ├── DateSearchParser.kt      # DD/MM/YYYY date matching engine
│       ├── ImageStorageHelper.kt    # 800px preview base64 compressor & file manager
│       └── SearchablePdfGenerator.kt# On-device A4 PDF builder
```

---

## 4. Build & Environment Instructions (Termux / Linux)

### Local Build on Termux
```bash
# Set Android SDK and Termux arm64 aapt2 override
export ANDROID_HOME=$HOME/android-sdk
export GRADLE_OPTS="-Xmx3g -Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false"

# Build debug APK
gradle :app:assembleDebug
```
Output path: `app/build/outputs/apk/debug/app-debug.apk`.

### Run JVM Unit Tests
```bash
gradle :app:testDebugUnitTest
```

### Keystore
- `debug.keystore` is stored in the repository root.
- Password: `android`, Alias: `androiddebugkey`.
- SHA-1 matches `app/google-services.json` so all Google & Firebase services authenticate properly.
