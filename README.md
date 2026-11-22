# Apex Solar Work (v1.5)

**Apex Solar Work** is a high-speed Android document scanning, searchable archive, and site management system built with **Kotlin**, **Jetpack Compose (Material 3)**, **Firebase AI Logic**, and **Cloud Firestore**.

Designed specifically for solar field teams, site engineers, supervisors, and management to capture site slips, inspection protocols, and delivery challans, archive them in a shared company vault in real-time, and allow instant date and keyword searches across all remote devices.

---

## What's New in v1.5

- **Self-Seeding Offline Archive (Out-of-the-Box 35 Documents)**:
  - All 35 original site documents and full-resolution photos are pre-packaged into APK assets (`assets/seed_photos/` and `assets/seed_documents.json`).
  - On first launch on any device (e.g. boss's phone), the app automatically extracts photos to local storage and populates Room SQLite.
  - Zero initial internet connection or cloud login required to view documents and photos.
- **Robust Category & Keyword Filtering**:
  - Filter chips (`Material Slip`, `Challan`, `Inspection`, `Invoice`, `Site Report`) filter instantly from local cache.
  - Dual-engine fallback: if cloud Firestore is empty or unreachable, local in-memory filtering takes over so chips never show "No matching documents" for existing slips.
  - Room DAO queries company-wide project documents without being restricted by individual Google account UIDs.
- **Zero Lossy Image Compression**:
  - Original photos stored byte-for-byte in device storage and backed up to the private Telegram channel / Oracle VM relay.
- **Direct Shared Company Vault (Zero Login Silos)**:
  - Removed mandatory individual Google sign-in gates.
  - Anyone who downloads and opens the app immediately accesses the shared `company_documents` archive.
  - Real-time synchronization across all team members and management devices.
- **Fast 1-Second Save Flow**:
  - AI auto-fill is now **optional** (`Auto-Fill with AI (Optional)`).
  - Field workers can take a photo, confirm the prefilled date, and tap **Save** in ~1 second without waiting for cloud AI processing.
- **High-Resolution Remote Photo Quality**:
  - Base64 preview resolution increased to 800px (up to 280,000 characters) in Firestore.
  - Management can tap any slip in the vault to open the full-screen dialog and pinch-to-zoom into fine handwritten numbers and vendor signatures.
- **Boss Date Search & Lookup**:
  - Full support for Indian date format (`DD/MM/YYYY`) and ISO format (`YYYY-MM-DD`).
  - Searching dates like `12/5/2026` or `24/02/2026` immediately lists all slips filed for that date.
  - Dedicated **By Date** calendar tab highlighting days with active site activity.
- **In-App Auto-Updater**:
  - Background startup checks via Oracle VM relay manifest (`https://docfolio.130.210.30.151.sslip.io/...`).
  - Automatically notifies users when a new APK version is available and downloads/installs directly on-device without manual APK file transfers.

---

## 35 Master Seed Documents Breakdown

| Category | Filter Chip | Count | Details |
| :--- | :--- | :--- | :--- |
| `DELIVERY_CHALLAN` | **Challan** | 17 | Gate Passes (Kalpataru / Apex Solar) for modules, structures, DC cables |
| `MATERIAL_SLIP` | **Material Slip** | 12 | Site requisition slips (slips #13703, #13726, #13745, #6717, etc.) |
| `INVOICE_RECEIPT` | **Invoice** | 4 | Invoices & delivery receipts for solar hardware & civil supplies |
| `INSPECTION_PROTOCOL`| **Inspection** | 1 | Earthing resistance & PV inspection report (PV Power Tech) |
| `SITE_REPORT` | **Site Report** | 1 | Site work progress log |
| **Total** | **All (35)** | **35** | Complete field archive pre-loaded into APK |

---

## Architecture & Data Flow

```text
[ APK Assets: seed_photos/ & seed_documents.json ]
                      │ (First launch on any phone)
                      ▼
[ Local Cache: Room SQLite (DocumentDao) & files/docfolio/ ]
                      ▲
                      │ (Local-first reactive Flow)
[ DocFolioViewModel.kt: localDocsFlow + cloudDocsFlow ]
                      │
                      ▼
[ Vault Filter: Local in-memory fallback (Instant Category & Date Search) ]
                      │
                      ▼
[ VaultScreen UI: All (35), Material Slip (12), Challan (17), Inspection (1)... ]
```

---

## Key Features

- **Document Capture & Gallery Import**:
  - Fast CameraX viewfinder with framing guides and flash toggle.
  - System Photo Picker (`PickVisualMedia`) for importing existing site photos without broad storage permissions.
- **Searchable PDF Generation**:
  - Generates native A4 PDFs on-device using Android's `PdfDocument`:
    - **Page 1**: High-clarity scanned slip photo.
    - **Page 2+**: Clean metadata sheet with date, category, slip number, vendor name, and full OCR text.
- **Export Day Report**:
  - Combine all slips and protocols from any selected day into a single multi-page PDF report.
- **Offline Resiliency & Cache**:
  - Room SQLite local database caching document metadata for offline viewing.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **App Name** | **Apex Solar Work** |
| **Package** | `com.aistudio.docfolio.krmvqx` |
| **Version** | `v1.5` (versionCode `6`) |
| **Language** | Kotlin 2.1 |
| **UI** | Jetpack Compose, Material Design 3, Adaptive Navigation |
| **Architecture** | MVVM + `StateFlow` / `ViewModel` + Repository Pattern |
| **Backend & Sync** | Cloud Firestore (`company_documents` shared collection) |
| **Local Cache** | Room SQLite (KSP) |
| **Camera** | CameraX (`PreviewView`, `ImageCapture`) + Accompanist Permissions |
| **AI / OCR** | Firebase AI Logic (`Firebase.ai` Gemini multimodal structured output) |
| **PDF Engine** | Android Native `android.graphics.pdf.PdfDocument` + `FileProvider` |
| **In-App Updates** | `AppUpdateManager` via HTTP manifest relay |
| **Target SDK** | Android 7.0+ (`minSdk 24`, `targetSdk 36`, `compileSdk 36.1`) |
| **Build Tooling** | Gradle 9.3.1 · AGP 9.1.1 |

---

## Project Structure

```text
app/src/main/java/com/aistudio/docfolio/krmvqx/
├── MainActivity.kt                  # App entry point & update check trigger
├── data/
│   ├── DocumentRepository.kt        # Cloud Firestore shared company_documents CRUD & real-time sync
│   ├── GeminiOcrService.kt          # Optional Firebase AI Logic structured OCR extraction
│   └── local/                       # Room SQLite cache (DocumentEntity, DAO, Database)
├── firebase/
│   ├── AppCheckSetup.kt             # App Check initialization
│   ├── AuthHelper.kt                # Anonymous & Credential Manager authentication
│   └── FirestoreErrorHandler.kt     # Network and security error diagnostics
├── model/
│   └── DocumentModels.kt            # ScannedDocument, DocumentCategory, OCR & UI models
├── ui/
│   ├── CameraView.kt                # CameraX viewfinder, flash, framing reticle
│   ├── DocFolioApp.kt               # Main scaffold, navigation bar, update dialogs
│   ├── VaultScreen.kt               # Document vault, date & category search filters
│   ├── DateLookupScreen.kt          # Boss Date Lookup calendar & daily report export
│   ├── ScanReviewScreen.kt          # Fast photo preview, optional AI scan & instant save
│   ├── DocumentDetailScreen.kt      # High-res zoomable photo view, OCR viewer, PDF actions
│   ├── components/                  # Reusable cards, date picker, bottom sheets
│   └── theme/                       # Material 3 dark/light solar color scheme
├── util/
│   ├── AppUpdateManager.kt          # Version check, APK downloader & package installer
│   ├── DateSearchParser.kt          # DD/MM/YYYY and multi-format date parser
│   ├── ImageStorageHelper.kt        # 800px preview base64 compressor & file storage
│   └── SearchablePdfGenerator.kt    # A4 searchable PDF & daily bundle generator
└── viewmodel/
    └── DocFolioViewModel.kt         # UI state orchestration, search, sync & PDF generation
```

---

## Building the APK

### 1. Build via Gradle
```bash
# Build the installable debug APK
./gradlew :app:assembleDebug
```
The output APK is generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Run Pure JVM Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```

---

## Firestore Security Rules

Deploy rules to Google Cloud / Firebase:
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

Deploy command:
```bash
firebase deploy --only firestore:rules --project gen-lang-client-0228559203
```
