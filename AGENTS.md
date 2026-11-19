# AGENTS.md — DocFolio Engineering Handoff Guide

This guide gives any developer or AI coding agent complete context to understand, modify, build, and test **DocFolio** without breaking Firebase, authentication, signing, or PDF generation.

---

## 1. Product Purpose & Domain Context

**DocFolio** is a Kotlin + Jetpack Compose (Material 3) Android app for scanning physical field/site documents into searchable PDFs with Gemini OCR and letting a manager/boss instantly pull up every document filed on a specific date (e.g. `12/1/2026` or `24/02/2026`).

Real-world target documents include:
- **Printed inspection tables** (e.g., *Kalpataru — Solar System Inspection Protocol* with Scheme Name, Block, Solar Capacity, Date, Make/Status/Remark columns, and signatures).
- **Handwritten carbon-copy requisition slips** (e.g., *Kalpataru Projects International Ltd. — Material Requisition & Issue Slip*, slip number, `DD/MM/YYYY` handwritten date, sub-contractor, department, quantities, remarks).

Two bundled sample images live under `app/src/main/assets/sample_forms/` (`kalpataru_material_slip.jpg`, `kalpataru_solar_inspection.jpg`) and are offered inside the Scan bottom sheet as optional test inputs. The Firestore vault itself starts empty.

---

## 2. Critical Invariants (Do Not Break)

1. **Identity & Package Split**:
   - `namespace = "com.example"` (keep Kotlin packages under `com.example.*` and generated `R` class unchanged).
   - `applicationId = "com.aistudio.docfolio.krmvqx"` (registered in `app/google-services.json` and `FileProvider` authority `${applicationId}.fileprovider`). Do not change it unless re-registering Firebase.
2. **Signing & Keystore**:
   - Do **not** alter `signingConfigs` in `app/build.gradle.kts` or `debug.keystore.base64`.
   - `debug.keystore.base64` decodes to `debug.keystore` whose SHA-1 is already registered in `app/google-services.json` for Google Sign-In.
3. **Firebase Initialization Order**:
   - `configureAppCheck(this, intent)` in `MainActivity.onCreate()` **must** run before any Firebase AI Logic or Firestore calls.
   - `GeminiOcrService` creates `Firebase.ai.generativeModel(...)` lazily so App Check is guaranteed initialized first.
4. **Named Cloud Firestore Database**:
   - **Never** use parameterless `Firebase.firestore` or `FirebaseFirestore.getInstance()`.
   - Always connect via `Firebase.firestore(Firebase.app, context.getString(R.string.firestore_database_id))` (database ID: `ai-studio-android-docfolio-c591d8bf-0c72-4b94-b6ba-4700a1282700`, configured in `app/src/main/res/values/firebase_applet_config.xml`).
5. **Authentication**:
   - Uses Google Sign-In via Jetpack `CredentialManager` (`AuthHelper.kt`: interactive `GetSignInWithGoogleOption`, silent `GetGoogleIdOption`, single option per `GetCredentialRequest`).
   - Do **not** switch to anonymous auth (`signInAnonymously()`).
6. **AI / OCR Backend**:
   - Uses **Firebase AI Logic** (`Firebase.ai`) backed by App Check. No `GEMINI_API_KEY` is used or needed.
7. **Storage & Play Policy Compliance**:
   - Never add `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES`. Gallery import uses `ActivityResultContracts.PickVisualMedia` and permanent PDF export uses `ActivityResultContracts.CreateDocument`.

---

## 3. Data Model & Firestore Rules Contract

Collection path: `/users/{userId}/documents/{docId}`

Defined in `firebase-blueprint.json`, enforced strictly in `firestore.rules`, and mirrored in `app/src/main/java/com/example/model/DocumentModels.kt` (`@Keep @IgnoreExtraProperties ScannedDocument`) and `DocumentRepository.kt`.

### Exact 18 Required Keys on `create`
`userId`, `title`, `category`, `documentDateIso`, `filedDateIso`, `referenceNumber`, `organization`, `contractorOrParty`, `summary`, `extractedFields`, `ocrFullText`, `localImageFileName`, `localPdfFileName`, `previewBase64`, `pageCount`, `starred`, `createdAt`, `updatedAt`.

Key rule constraints:
- Never include an `"id"` key in Firestore write maps (`@DocumentId` reads the doc ID automatically).
- `category` must be one of: `MATERIAL_SLIP`, `INSPECTION_PROTOCOL`, `DELIVERY_CHALLAN`, `INVOICE_RECEIPT`, `SITE_REPORT`, `OTHER`.
- `filedDateIso` must match `YYYY-MM-DD`; `documentDateIso` may be `""` or `YYYY-MM-DD`.
- `extractedFields`: list of up to 30 strings (each `<= 300` chars).
- `ocrFullText`: `<= 50_000` chars.
- `previewBase64`: `<= 160_000` chars (client compresses JPEG to `<= 140_000` chars so cross-device preview & PDF regeneration stay well under Firestore's 1 MiB document limit).
- `createdAt` & `updatedAt`: submitted via `FieldValue.serverTimestamp()`, immutable `createdAt` on updates.
- Updates are restricted to three explicit actions in `firestore.rules`:
  - `isEditDocumentAction()` (metadata + OCR + `localPdfFileName` + `updatedAt`)
  - `isToggleStarAction()` (`starred` + `updatedAt`)
  - `isUpdateLocalFilesAction()` (`localImageFileName`, `localPdfFileName`, `previewBase64`, `updatedAt`)

If you add a new field to `ScannedDocument`, you must update **all three**: `DocumentModels.kt`/`DocumentRepository.kt`, `firebase-blueprint.json`, and `firestore.rules` (then re-run `firestore.test.js` and deploy rules).

---

## 4. Core Module Reference

| File | Responsibility |
| :--- | :--- |
| `MainActivity.kt` | Calls `configureAppCheck(this, intent)`, `enableEdgeToEdge()`, auth-gates UI via `AuthHelper.authStateFlow()`, scopes `DocFolioViewModel` with `key(user.uid)`. |
| `firebase/AppCheckSetup.kt` | Registers debug token (synchronous `.commit()`) in debug builds and `recaptcha_site_key` in release builds. |
| `firebase/AuthHelper.kt` | Credential Manager Google Sign-In, silent auto-sign-in, `NonCancellable` sign-out, auth state flow. |
| `data/GeminiOcrService.kt` | Calls `Firebase.ai.generativeModel(...)` with strict `Schema.obj(...)` JSON response schema, 60s timeout, `CancellationException` preservation, and fallback normalization. |
| `data/DocumentRepository.kt` | Real-time Firestore `snapshots()` using `ServerTimestampBehavior.ESTIMATE`, 18-key create payload, action-scoped updates, delete. |
| `data/local/DocumentEntity.kt`, `DocumentDao.kt`, `DocFolioDatabase.kt`, `LocalDocumentCacheRepository.kt` | Local Room SQLite metadata cache (filename, dates, local image/PDF paths, OCR content) mirrored one-way from Cloud Firestore. |
| `util/DateSearchParser.kt` | Parses `DD/MM/YYYY` (default), `MM/DD/YYYY` alternate when ambiguous (e.g. `12/1/2026`), ISO `YYYY-MM-DD`, text dates (`12 Jan 2026`), and UTC millis round-trip for Material 3 `DatePicker`. |
| `util/ImageStorageHelper.kt` | EXIF-aware bitmap decode, local storage in `filesDir/docfolio/`, compact Base64 JPEG thumbnail encoder/decoder. |
| `util/SearchablePdfGenerator.kt` | Native Android `PdfDocument` A4 generator: Page 1 renders scanned photo + overlaid `alpha=1` selectable OCR text layer; Page 2+ renders structured metadata, extracted fields, and wrapped OCR transcript. Also generates multi-document Boss Daily Report PDFs and `FileProvider` intents. |
| `viewmodel/DocFolioViewModel.kt` | Combines Firestore stream + Room one-way local cache sync + search query + explicit date filter + `DateMatchMode` (`EITHER`, `FILED_ONLY`, `DOCUMENT_ONLY`) + category + starred filters; coordinates OCR scanning, saving, editing, deleting, and PDF export. |
| `ui/DocFolioApp.kt` | Adaptive navigation (`NavigationBar` / `NavigationRail`), CameraX `CameraView` launcher, `PickVisualMedia`, SAF `CreateDocument`, snackbar feedback. |
| `ui/CameraView.kt` | In-app CameraX `PreviewView` + `ImageCapture` document scanner with Accompanist Permissions (`CAMERA`), alignment reticle, flash modes, tap-to-focus, lens switch, and Gallery fallback (`BackHandler` wired). |
| `ui/VaultScreen.kt` | Search bar, smart date banner (`DD/MM` vs `MM/DD` toggle + clear + Day PDF export), category filter chips, metrics strip, document list. |
| `ui/DateLookupScreen.kt` | Boss Date Lookup: typed date jump, quick active-date pills, interactive monthly calendar with document indicators, Export Day PDF button. |
| `ui/ScanReviewScreen.kt` | Animated OCR scan overlay, retry/manual fallback on error, editable fields & dual-date inputs, Save & Generate Searchable PDF CTA (`BackHandler` wired). |
| `ui/DocumentDetailScreen.kt` | Pinch-to-zoom scan viewer, Open/Share/Save PDF actions, metadata & extracted fields, in-document highlighted OCR search + Copy, Edit Metadata sheet, Delete dialog (`BackHandler` wired). |

---

## 5. Build & Test Commands

> Note: This project uses Gradle `9.3.1` (`gradle/wrapper/gradle-wrapper.properties`) and AGP `9.1.1`. If running without a `gradlew` script, invoke `gradle` directly.

```bash
# 1. Restore debug keystore (required once before building)
base64 -d debug.keystore.base64 > debug.keystore

# 2. Build debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
gradle :app:assembleDebug

# 3. Run JVM unit & Robolectric tests
gradle :app:testDebugUnitTest

# 4. Run Firestore security rules tests against local emulator
firebase emulators:exec --only firestore "node --test firestore.test.js"
```

### Existing Automated Tests
- `app/src/test/java/com/example/DocFolioCoreUnitTest.kt` (Robolectric): date parsing (`DD/MM` & `MM/DD` ambiguity, UTC calendar round-trip), vault dual-date & keyword filtering, Gemini OCR JSON normalization, PDF `wrapText` bounds, and OCR highlight matching.
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`: verifies `app_name` resource (`DocFolio`).
- `firestore.test.js`: 4 end-to-end Firestore security rule tests (owner CRUD, cross-user denial, unauthenticated denial, invalid payload rejection).
