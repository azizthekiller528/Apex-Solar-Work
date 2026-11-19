# DocFolio

**DocFolio** is an Android document scanning, OCR indexing, and searchable PDF vault built with **Kotlin**, **Jetpack Compose (Material 3)**, **Firebase AI Logic**, and **Cloud Firestore**.

It is designed for field teams, site engineers, and managers to capture printed inspection checklists and handwritten requisition slips, extract structured fields and full OCR text, generate native searchable PDFs, and instantly pull up every document filed on any specific date (e.g., `12/1/2026` or `24/02/2026`).

---

## Key Features

- **Document Capture & Import**
  - Scan directly with the device camera or pick photos using the zero-permission Android Photo Picker (`PickVisualMedia`).
  - Includes bundled sample forms inside the Scan sheet for quick testing.
- **AI-Powered OCR & Structured Field Extraction**
  - Uses **Firebase AI Logic** (Gemini multimodal structured JSON output) to read printed inspection tables, carbon-copy material slips, slip numbers, vendor/contractor names, departments, dates, and full text transcripts.
  - Supports manual editing and retry fallback if a scan is blurry or offline.
- **Boss Date Lookup & Smart Date Search**
  - Type any date in the Vault search bar or Date Lookup tab (`12/1/2026`, `24/02/2026`, `2026-01-12`, `12 Jan 2026`).
  - Defaults to `DD/MM/YYYY` with a one-tap toggle to `MM/DD/YYYY` whenever a slash date is ambiguous.
  - **Dual-Date Indexing**: Tracks both **Filed Date** (when scanned) and **Document Date** (written on the form), with filters for `Either`, `Filed Only`, or `Document Only`.
  - Interactive monthly calendar highlighting every active day with filed documents.
- **Searchable PDF Generation & Daily Reports**
  - Generates native A4 **Searchable PDFs** on-device using Android's `PdfDocument`:
    - **Page 1**: High-clarity scanned document photo with an overlaid selectable OCR text layer.
    - **Page 2+**: Clean, searchable OCR index sheet containing metadata, extracted key-value fields, and the full transcript.
  - **Export Day PDF**: Combine all documents from a chosen date into a single multi-page Boss Daily Report PDF.
  - Open, share via `FileProvider`, or save permanently via Android's Storage Access Framework (`CreateDocument`).
- **Cloud Sync & Per-User Security**
  - Authenticated via **Google Sign-In** (Jetpack Credential Manager) and synced in real time with **Cloud Firestore**.
  - Protected by **Firebase App Check** and strict owner-only Firestore security rules (`firestore.rules`).

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| Language | Kotlin |
| UI | Jetpack Compose, Material Design 3, Adaptive Navigation (`NavigationBar` / `NavigationRail`) |
| Architecture | MVVM + `StateFlow` / `ViewModel` + Repository pattern |
| AI / OCR | Firebase AI Logic (`Firebase.ai`) |
| Database & Auth | Cloud Firestore + Firebase Auth (Google Sign-In via Credential Manager) |
| Security | Firebase App Check + Zero-Trust Firestore Security Rules |
| PDF Engine | Native Android `android.graphics.pdf.PdfDocument` + `FileProvider` |
| Image Loading | Coil Compose |
| Android SDK | `minSdk 24` (Android 7.0+) · `targetSdk 36` · `compileSdk 36.1` |
| Build System | Gradle `9.3.1` · Android Gradle Plugin `9.1.1` |

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # App entry point, App Check init, edge-to-edge & auth gate
├── data/
│   ├── DocumentRepository.kt        # Cloud Firestore real-time sync & CRUD operations
│   └── GeminiOcrService.kt          # Firebase AI Logic structured OCR extraction
├── firebase/
│   ├── AppCheckSetup.kt             # Debug & Play/reCAPTCHA App Check initialization
│   ├── AuthHelper.kt                # Credential Manager Google Sign-In & auth state flow
│   └── FirestoreErrorHandler.kt     # Structured Firestore error diagnostics
├── model/
│   └── DocumentModels.kt            # ScannedDocument, DocumentCategory, OCR & UI state models
├── ui/
│   ├── AuthScreen.kt                # Google Sign-In screen
│   ├── DocFolioApp.kt               # Adaptive scaffold, camera/picker/SAF launchers
│   ├── VaultScreen.kt               # Main document vault, smart search & category filters
│   ├── DateLookupScreen.kt          # Boss Date Lookup calendar & daily report export
│   ├── ScanReviewScreen.kt          # OCR scan animation, field verification & PDF creation
│   ├── DocumentDetailScreen.kt      # Zoomable preview, highlighted OCR search, PDF actions
│   ├── components/                  # Reusable cards, date picker dialog & scan bottom sheet
│   └── theme/                       # Material 3 color schemes, typography & shapes
├── util/
│   ├── DateSearchParser.kt          # Multi-format DD/MM & MM/DD parser and calendar helpers
│   ├── ImageStorageHelper.kt        # EXIF-aware local image & compact Base64 preview helper
│   └── SearchablePdfGenerator.kt    # Native A4 searchable PDF & daily bundle generator
└── viewmodel/
    └── DocFolioViewModel.kt         # State management, filtering, OCR & PDF orchestration
```

---

## Build & Install the APK on Your Phone

### Option 1: Automatic APK Build via GitHub Actions (No PC Needed)

You can let GitHub build the signed installable APK and publish it straight to your repository's **Releases** tab so you can download it directly on your phone:

1. In this GitHub repository, tap **Add file → Create new file**.
2. Name the file: `.github/workflows/build-apk.yml`
3. Paste the workflow below and commit to `main`:

```yaml
name: Build Android APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 9.3.1

      - name: Accept Android SDK licenses
        run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses || true

      - name: Restore debug keystore
        run: |
          if [ -f debug.keystore.base64 ]; then
            base64 -d debug.keystore.base64 > debug.keystore
          fi

      - name: Build Debug APK
        run: gradle :app:assembleDebug

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: DocFolio-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk

      - name: Publish APK to Releases
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          cp app/build/outputs/apk/debug/app-debug.apk DocFolio-debug.apk
          if gh release view latest >/dev/null 2>&1; then
            gh release upload latest DocFolio-debug.apk --clobber
          else
            gh release create latest DocFolio-debug.apk \
              --title "DocFolio Android APK (Latest)" \
              --notes "Installable Android debug APK for Android 7.0+ (signed with registered debug keystore)."
          fi
```

4. Wait ~3–5 minutes for the **Actions** tab run to turn green.
5. Open **Releases → DocFolio Android APK (Latest)** on your phone and tap **`DocFolio-debug.apk`** to download.

### Option 2: Build Locally (Computer / Android Studio)

1. Restore the debug keystore (so Google Sign-In SHA-1 matches `app/google-services.json`):
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   ```
2. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
3. The installable APK is generated at:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Installing the APK on Android

1. Tap the downloaded `DocFolio-debug.apk` in your phone's **Files → Downloads** folder.
2. If prompted, enable **Allow from this source** for your browser or Files app.
3. Tap **Install** (if Play Protect prompts for a self-built app, tap **More details → Install anyway**).
4. Open **DocFolio** and sign in with your Google account.

---

## Testing

- **Unit & Robolectric Tests (JVM)**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Firestore Security Rules Tests**:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```

---

## Privacy & Permissions

- `INTERNET`: Required for Firebase Auth, Cloud Firestore sync, and Firebase AI Logic OCR.
- `CAMERA`: Optional runtime permission requested only when scanning a new document with the camera.
- **No broad storage permissions** (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` are not requested); gallery imports use the Android Photo Picker and PDF exports use the system Storage Access Framework.
