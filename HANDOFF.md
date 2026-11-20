# DocFolio — Engineering Handoff (v1.3)

Last updated: 2026-11-20 (OpenCode session → migrating to AntiGem/Antigravity)
Repo: https://github.com/azizthekiller2/New-apk-  (private)
Current commit on `main`: `df9b9c9` — "feat(scan): remove automatic AI OCR, go straight to manual entry"

## 1. What the app is

Kotlin + Jetpack Compose (Material 3) Android app, package `com.aistudio.docfolio.krmvqx`, namespace `com.example`. Scans physical field documents into searchable PDFs with manual date/details entry and a vault searchable by date. MinSdk 24, targetSdk 36.

## 2. Build toolchain (Termux, arm64)

- Gradle 9.3.1 at `~/tools/gradle-9.3.1/`
- AGP 9.1.1, Kotlin, KSP, Compose
- Android SDK 36.1 at `~/android-sdk/` (platforms/android-36.1, build-tools/36.1.0)
- **Critical**: SDK's aapt2 is x86-64; use the Termux arm64 aapt2 via `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` in `gradle.properties` (local build only, not committed).
- Build command: `ANDROID_HOME=~/android-sdk ~/tools/gradle-9.3.1/bin/gradle --no-daemon --no-parallel --max-workers=2 --no-configuration-cache assembleDebug`
- JVM args tuned to `-Xmx3g`, `org.gradle.workers.max=2`, `org.gradle.parallel=false`.
- `local.properties` (sdk.dir) is NOT committed; create locally.
- `debug.keystore` lives at repo root (committed), password `android`, alias `androiddebugkey`.

## 3. Signing

- Debug keystore SHA-1: `E5:09:51:E4:59:5C:1D:4F:9A:9F:05:40:3D:46:56:29:1E:25:63:C7`
- This SHA-1 is registered in Firebase App Check / Android app record, so Google Sign-In works for builds made from this repo.
- Do not change `signingConfigs` or `applicationId`.

## 4. Firebase (project gen-lang-client-0228559203)

- `app/google-services.json` is committed (was missing from AI Studio exports — restore it on every sync).
- Named Firestore database: `ai-studio-android-docfolio-c591d8bf-0c72-4b94-b6ba-4700a1282700` (configured in `app/src/main/res/values/firebase_applet_config.xml`). Never use `Firebase.firestore` without the database id.
- App Check debug token (registered): `d0cf01e0-4c5a-4d1e-9a10-7b3f2c8e5a61` — baked into `BuildConfig.APPCHECK_DEBUG_TOKEN` via gradle property `docfolio.appcheck.debugToken`.
- Firebase AI Logic is no longer called (OCR removed); App Check machinery is still present but effectively unused.
- Firestore rules in `firestore.rules`, blueprint in `firebase-blueprint.json`. If you add a field, update models + blueprint + rules + tests.

## 5. Current APK

- Path on phone: `/sdcard/Download/DocFolio-1.3-debug.apk` (30 MB)
- Repo copy: `~/projects/DocFolio-apk/DocFolio-1.3-debug.apk`
- GitHub Release: https://github.com/azizthekiller2/New-apk-/releases/tag/v1.3-debug  (asset uploaded)
- versionCode 4, versionName 1.3
- Package `com.aistudio.docfolio.krmvqx`, signed v2 with the debug keystore above.

## 6. What changed in this session (chronology)

1. Built 1.0 debug APK from an AI Studio export (added google-services.json, debug.keystore, gradle wrapper, CI workflow). Pushed commit `171e99c`.
2. Rebuilt 1.1 with a new AI Studio update (server-side search + date-range filter bar). Pushed `20ddbd3`.
3. Fixed "App Check token is invalid" by baking a fixed debug token and registering it in Firebase. Pushed `692533f`, released v1.2.
4. Removed automatic AI OCR (Gemini) — capture now opens the review screen directly with an empty draft; manual entry only. Pushed `df9b9c9`, released v1.3.
   - Removed `ProcessingOcr` state and its UI view, `retryOcrOnLastBitmap`, `proceedManualFromError`, `runOcrOnBitmap` from `DocFolioViewModel`.
   - `GeminiOcrService.kt` kept intact (still used by unit tests; can be re-wired).

## 7. Known issues / gotchas

- AI Studio exports **delete `app/google-services.json`** on every sync. Restore it after each update (`cp` from a saved copy).
- AI Studio exports reset `versionCode`/`versionName` to 1/"1.0" and delete `gradlew` exec bit — re-bump and `chmod +x gradlew` when rebuilding.
- GitHub Actions is **disabled account-wide** (spam flag) — CI workflow exists but won't run. Use GitHub Releases for distribution.
- `firebaseappcheck.googleapis.com` needs the `x-goog-user-project` header when called with ADC from Cloud Shell.
- Robolectric tests require Java 21 for SDK 36; this environment has Java 17, so `testDebugUnitTest` fails with `UnsupportedOperationException: Android SDK 36 requires Java 21`. This is an environment limitation, not a code failure. ExampleUnitTest (plain JVM) passes.
- The searchable PDF now has an empty OCR text layer (no extraction). Consider putting the manual entries into the transcript if desired.

## 8. Verification done

- `assembleDebug` → BUILD SUCCESSFUL for 1.0, 1.1, 1.2, 1.3.
- APK verified with `aapt2 dump badging` and `apksigner verify` (v2 signature) each build.
- No dangling references to removed symbols in main source (`grep` clean).
- Git working tree clean after each push.
- Releases v1.0, v1.1, v1.2, v1.3 all uploaded with assets.

## 9. VM (Oracle Cloud) — media relay planned, not built yet

- Host: `ubuntu@130.210.30.151`, key copy at `~/.ssh/javed_vm` (chmod 600). **Rotate this key** — it was uploaded to chat and should be treated as compromised.
- OS: Ubuntu 22.04 (aarch64), 1 vCPU, 5.9 GB RAM, 62 GB free, port 8100 free.
- nginx on 80/443, Cloudflare Tunnel running, 11 Telegram bot services already running — do not disturb.
- Plan (not implemented): FastAPI relay on :8100, Telethon/Bot storage into a private Telegram channel, auth via Firebase ID token verification, exposed via sslip.io or Cloudflare Tunnel. Photos/PDFs → Telegram; metadata/search stays on Firestore.
- Needs from user: a Telegram bot token (or session string) and (if sslip.io) nothing else.

## 10. Useful commands

```bash
# build
ANDROID_HOME=~/android-sdk ~/tools/gradle-9.3.1/bin/gradle --no-daemon --no-parallel --max-workers=2 --no-configuration-cache assembleDebug

# push
TOKEN=$(sed -n 's/.*\(ghp_[A-Za-z0-9]*\).*/\1/p' "/data/user/0/com.termux/files/home/.antigem/uploads/46fcba574193482b/azizthekiller2 GitHub private Token.txt")
git push "https://x-access-token:$TOKEN@github.com/azizthekiller2/New-apk-.git" main

# install on phone
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/DocFolio-<ver>-debug.apk
```

## 11. Files the next agent should read first

- `AGENTS.md` — full engineering handoff guide (invariants, data model, Firestore rules, module reference).
- `app/build.gradle.kts`, `app/src/main/java/com/example/viewmodel/DocFolioViewModel.kt`
- `app/src/main/java/com/example/firebase/AppCheckSetup.kt`
- `app/src/main/java/com/example/ui/ScanReviewScreen.kt`
- `firestore.rules`, `firebase-blueprint.json`

## 12. Open next steps

1. Decide whether to add the Telegram relay (Option C from earlier: keep manual entry, optionally allow AI on demand).
2. Decide whether to strip App Check now that no AI calls are made.
3. Rotate the SSH key that was uploaded to chat.
4. Possibly re-enable GitHub Actions by contacting GitHub Support (spam flag).
path>
/data/user/0/com.termux/files/home/projects/New-apk/HANDOFF.md