# RepLock — Development Rules & Architecture Guidelines

These guidelines are mandatory for all AI assistants (Claude, Cursor, Copilot, Antigravity) working on this codebase.

---

## 1. CRITICAL: Test Files Location (`src/test` vs `src/main`)

⚠️ **NEVER place unit test files (`*Test.kt`) inside `src/main`!**

- **Correct Path**:
  ```
  android/app/src/test/java/com/fitness/squatcounter/.../*Test.kt
  ```
- **Forbidden Path**:
  ```
  android/app/src/main/java/com/fitness/squatcounter/.../*Test.kt
  ```

### Why:
`src/main` is compiled into the production APK. Android's production classpath **does not have access to JUnit** (`org.junit.Test`, `assertEquals`, etc.). Putting test files in `src/main` causes `compileDebugKotlin` and GitHub Actions CI to fail with `Unresolved reference: junit`.

All test files must reside strictly in:
`android/app/src/test/java/`

---

## 2. Package Names & Screen Imports

`BlockerScreen.kt` is packaged under `com.fitness.squatcounter.blocker`:
```kotlin
package com.fitness.squatcounter.blocker
```
When importing in `MainActivity.kt` or other UI components:
```kotlin
// CORRECT:
import com.fitness.squatcounter.blocker.BlockerScreen

// INCORRECT:
import com.fitness.squatcounter.ui.BlockerScreen
```

---

## 3. Releases & Versioning

Whenever new features are added:
1. Increment `versionCode` and `versionName` in `android/app/build.gradle.kts`.
2. Update `version.json` in the project root to match:
   ```json
   {
     "versionCode": <new_code>,
     "versionName": "<new_name>",
     "downloadUrl": "https://github.com/Madhav-Soni/Replock/releases/download/latest/app-debug.apk",
     "releaseNotes": "<summary>"
   }
   ```
3. Push to `main`. GitHub Actions will automatically build the APK and update the release for in-app updates.
