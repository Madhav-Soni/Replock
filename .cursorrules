# RepLock — Development Rules & Architecture Guidelines

These instructions are mandatory for all AI assistants (Claude, Cursor, Copilot) working on this repository.

---

## 🚨 RULE 1: NEVER Remove the In-App Updater & Internet Permission

RepLock uses a self-hosted in-app update mechanism via GitHub Releases. **DO NOT REMOVE OR STRIP THESE OUT:**

1. **`android/app/src/main/AndroidManifest.xml` MUST retain:**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
   ```
   *Do not delete `INTERNET` permission under the assumption of "offline only" — it is strictly required for the app to detect and download app updates.*

2. **`android/app/src/main/java/com/fitness/squatcounter/MainActivity.kt` MUST retain:**
   - The `updateManager` instance and `updateManager.checkForUpdates()` call in `onCreate()`.
   - The `UpdateDialog(...)` composable inside `setContent { ... }`.

3. **CI & Version Files MUST NEVER be deleted:**
   - `.github/workflows/release.yml`
   - `version.json`

---

## 🚨 RULE 2: NEVER Put Test Files in `src/main`

⚠️ **All test files (`*Test.kt`) MUST strictly be placed in:**
```
android/app/src/test/java/com/fitness/squatcounter/.../*Test.kt
```
**NEVER put `*Test.kt` inside `android/app/src/main/java/`!**

### Why:
`src/main` is compiled into the production Android APK and **does not have access to JUnit** (`org.junit.Test`, `assertEquals`, etc.). Putting test files in `src/main` breaks Gradle compilation with `Unresolved reference: junit`.

---

## 🚨 RULE 3: Keep Enum Values & Cross-File Dependencies Synchronized

When adding rejection handling or new exercises to detectors (e.g. `WallSitDetector`, `PlankDetector`):
- Ensure any `RejectionReason.XYZ` referenced is first added to `domain/RejectionReason.kt`.
- Ensure any `EXERCISE_XYZ` referenced is added to `data/CreditConfig.kt`.

---

## 🚨 RULE 4: How to Release an Update

Whenever you add a new feature or fix:
1. Bump `versionCode` and `versionName` in `android/app/build.gradle.kts`.
2. Update `version.json` in the root directory:
   ```json
   {
     "versionCode": <incremented_number>,
     "versionName": "<incremented_version>",
     "downloadUrl": "https://github.com/Madhav-Soni/Replock/releases/download/latest/app-debug.apk",
     "releaseNotes": "<brief summary of changes>"
   }
   ```
3. Commit and push to `main`. GitHub Actions will automatically build the APK and publish the release.
