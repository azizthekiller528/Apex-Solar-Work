# Apex Solar Work — Engineering Handoff (v1.5)

Last updated: 2026-11-22
Repository: https://github.com/azizthekiller2/Apex-Solar-Work
Current Release: v1.5 (versionCode 6)

---

## 1. What This App Is
**Apex Solar Work** (package `com.aistudio.docfolio.krmvqx`, namespace `com.example`) is a Kotlin + Jetpack Compose Android app built for solar site engineers, field workers, and managers to capture slips and inspect documents with an instant shared company archive.

---

## 2. Chronological Log of All Changes & Updates

### v1.0 – Initial Prototype (AI Studio Export)
- Created base project structure, CameraX capture, and Material 3 design.
- Built with Google Sign-In gate and `/users/{userId}/documents` Firestore schema.

### v1.1 – Search & Filter Upgrades
- Added server-side date range filter bar and category chips.

### v1.2 – App Check Debug Fix
- Resolved App Check token validation by baking a fixed debug token into `BuildConfig.APPCHECK_DEBUG_TOKEN`.

### v1.3 – Removed Automatic Blocking OCR
- Removed automatic Gemini scanning blocking the review screen. Field workers could enter metadata manually.

### v1.4 – Shared Vault & Boss Date Search
- **Removed Google Sign-In Gate**: Switched to zero-auth direct entry into the vault.
- **Shared Firestore Path**: Migrated from user silos `/users/{userId}/documents` to `/company_documents/{docId}` so all employees share one archive.
- **Boss Date Search**: Enhanced `DateSearchParser.kt` to handle `DD/MM/YYYY` (e.g. `12/5/2026`) and ISO dates across Vault search and By Date calendar tab.
- **Duplicate Detection**: Alert field workers if a slip with the same reference number or date already exists.

### v1.5 – High-Resolution Zoom, Fast Save, In-App Auto-Updater
- **Renamed App**: App label updated to **Apex Solar Work** across all manifests, strings, and UI titles.
- **Fast 1-Second Save**: Replaced slow AI scanning with an optional `Auto-Fill with AI (Optional)` button. Field workers can take a photo and tap **Save** in 1 second.
- **High-Resolution Photo Preview**:
  - `ImageStorageHelper.kt`: `MAX_PREVIEW_EDGE` raised to `800px`, `MAX_PREVIEW_BASE64_LENGTH` raised to `250_000` chars.
  - `DocumentRepository.kt`: previewBase64 ceiling raised to `280_000` chars.
  - `DocumentDetailScreen.kt`: Added full-screen dialog with pinch-to-zoom for reading small handwritten carbon-copy text and numbers.
- **In-App Auto-Updater**:
  - Built `AppUpdateManager.kt` which checks update manifest on startup.
  - Primary relay endpoint: `https://docfolio.130.210.30.151.sslip.io/api/documents/apex_update_manifest/photo?token=docfolio_secret_test_key_99812`.
  - Automatically notifies user, downloads APK, and triggers package installer on-device.
- **Repository Rename**: Renamed GitHub repository from `New-apk-` to `Apex-Solar-Work`.

---

## 3. Important Notes for Incoming AI Agents

1. **DO NOT add back authentication gates**: The app must open directly to the vault.
2. **DO NOT change `/company_documents` back to `/users/{userId}`**.
3. **DO NOT change the update manifest fallback URL**: Raw GitHub URLs return 404 due to account spam flag.
4. **DO NOT touch the Oracle VM (`130.210.30.151`)**: Running 17+ active Telegram bots.
5. **Always test builds on Termux**: Use Termux arm64 aapt2 override: `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`.