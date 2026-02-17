# InstaGuard

<p align="center">
  <img src="Image%20for%20readme.png" alt="InstaGuard banner" width="100%" />
</p>

InstaGuard is an Android app that limits Instagram usage with a strict time budget and automatic carry-forward.

Current version: `v0.3.4`

## Why this exists

Short social media checks can easily turn into long sessions. InstaGuard enforces a measurable rule:

- You earn 5 minutes of Instagram time per hour.
- Unused time carries forward.
- No budget accrual happens between 2:00 AM and 8:00 AM.
- When budget reaches zero, Instagram is actively blocked until budget refills.

## Core behavior

- Tracks whether Instagram is currently in foreground.
- Deducts time while Instagram is in use.
- Adds budget over time outside quiet hours.
- Opens a blocker screen and pushes to Home when budget is exhausted.
- Does not provide an in-app "override" button.

## Android limitation

On standard consumer Android devices, no normal app can guarantee "only uninstall can bypass" with complete certainty unless running as a device owner (enterprise-managed mode). This project implements the strongest practical non-root strategy for open-source usage.

## Tech stack

- Kotlin
- Jetpack Compose
- DataStore Preferences
- AccessibilityService
- Gradle Kotlin DSL

## Project structure

- `app/src/main/java/com/instaguard/MainActivity.kt`: Dashboard and setup guidance
- `app/src/main/java/com/instaguard/BlockActivity.kt`: Lock screen shown on budget exhaustion
- `app/src/main/java/com/instaguard/service/InstaGuardAccessibilityService.kt`: Foreground monitoring and enforcement
- `app/src/main/java/com/instaguard/domain/BudgetEngine.kt`: Budget math and quiet-hour rules
- `app/src/main/java/com/instaguard/data/BudgetRepository.kt`: State coordination
- `app/src/main/java/com/instaguard/data/BudgetStore.kt`: Persistent storage

## Local development

Prerequisites:

- Android Studio (latest stable)
- JDK 17

Build and test:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

## Device setup

1. Install the app.
2. Open InstaGuard.
3. Tap "Open Accessibility Settings".
4. Enable "InstaGuard Watchdog".
5. Disable battery optimization for InstaGuard.

## CI/CD

This repo includes GitHub Actions workflows:

- `android-ci.yml`
  - Triggers on every push and pull request
  - Runs unit tests
  - Builds debug APK
  - Uploads debug APK as artifact

- `android-release.yml`
  - Triggers on tags matching `v*` (example: `v0.3.4`)
  - Builds signed release APK
  - Publishes a GitHub Release with attached installable APK (`app-release.apk`)

## Update delivery

- In-app update downloads the APK directly and opens Android installer.
- It does not redirect users to GitHub UI.
- You only need one signed APK per release tag.
- Separate patch files are not required for this setup.

## Version management

Version is centralized in `gradle.properties`:

- `VERSION_NAME`
- `VERSION_CODE`

Bump automatically with:

```bash
./scripts/bump_version.sh patch
# or: minor / major
```

## Publishing a release

```bash
git tag v0.3.4
git push origin v0.3.4
```

After push, GitHub Actions creates a release and attaches:

- `app-release-unsigned.apk`

## Roadmap

- Optional cap on maximum carry-forward budget
- Daily and weekly budgets
- Better tamper detection for accessibility disable events
- Signed release pipeline with encrypted keystore secrets

## License

Apache-2.0
