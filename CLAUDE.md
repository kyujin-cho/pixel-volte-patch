# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Pixel IMS** is an Android application that enables VoLTE (Voice over LTE) functionality on Google Tensor-powered Pixel devices without requiring root access. The app leverages Android's internal `telephony.ICarrierConfigLoader.overrideConfig()` API through Shizuku to override carrier configurations and enable IMS features.

**Primary carriers supported:**
- LG U+ (Republic of Korea) - 1st tier support
- Various international carriers - 2nd tier (community-reported)

**Target devices:** Google Pixel 6/6a/6 Pro, 7/7a/7 Pro, 8/8 Pro, Pixel Fold (Tensor chipset)

## Build & Development Commands

### Prerequisites
This project requires a **patched android.jar** to compile successfully:
1. Download the [patched android.jar](https://github.com/Reginer/aosp-android-jar/raw/main/android-34/android.jar)
2. Place it under `$ANDROID_SDK/platforms/android-34/`
3. This enables access to hidden Android APIs at compile-time

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug build to connected device
./gradlew installDebug

# Run lint checks
ktlint --reporter=checkstyle,output=build/ktlint-report.xml
```

### Testing
JVM unit tests for pure logic live under `app/src/test` and run with `./gradlew testDebugUnitTest` (CI runs them). Device behavior is tested manually by:
- Installing the APK on a Tensor Pixel device
- Verifying Shizuku permission grant
- Toggling VoLTE settings
- Checking IMS registration status

## Architecture

### Core Components

**1. Moder Classes (`Moder.kt`)**
- `Moder`: Base class providing access to hidden Android telephony services via Shizuku
  - `carrierConfigLoader`: ICarrierConfigLoader interface for carrier config overrides
  - `telephony`: ITelephony interface for telephony operations
  - `phoneSubInfo`: IPhoneSubInfo for subscription info
  - `sub`: ISub for subscription management

- `CarrierModer`: Device-level operations
  - Get active subscriptions
  - Check device IMS support

- `SubscriptionModer`: Per-SIM operations
  - Override carrier configs (VoLTE, VoWiFi, VoNR, etc.)
  - Query current config values
  - Restart IMS registration

**2. Shizuku Integration (`Utils.kt`)**
- `checkShizukuPermission()`: Verifies Shizuku service is running and permission is granted
- Shizuku provides ADB-level privileges without root to call hidden system APIs

**2a. Carrier config override paths (`Moder.kt`, `BrokerInstrumentation.kt`, `PrivilegedService.kt`)**
- Security patch before 2025-10 (or API < 31): direct `ICarrierConfigLoader.overrideConfig` through Shizuku
- Otherwise the broker path: `BrokerInstrumentation` runs inside the app process (`INSTR_FLAG_NO_RESTART`, API 31+) and calls the public `CarrierConfigManager.overrideConfig` with the shell identity
  - Preferred: the Shizuku UserService `PrivilegedService` (shell uid, bound by `PrivilegedServiceClient`) starts the instrumentation with a shell-owned `UiAutomationConnection`, and the broker uses the public `UiAutomation.adoptShellPermissionIdentity()` / `dropShellPermissionIdentity()`
  - Fallback (UserService not connected yet, or adoption fails): the app starts the instrumentation through Shizuku and the broker uses the hidden `IActivityManager.startDelegateShellPermissionIdentity`
  - `BrokerRunner` runs one broker instrumentation at a time (consecutive changes of the same SIM are merged) and passes an `IInstrumentationWatcher`, so AMS reports each run's result back to the app
- `ApplyResultTracker` keeps the per-SIM outcome (applying / applied / failed with reason) and, after each change, polls IMS registration for up to 30 s on a background thread; `restartIMSRegistration()` defers its IMS reset until a pending broker apply finishes. The Config page shows the result in `ApplyResultView`, a constant-height bar pinned below the scrolling toggle list (never inside it, so status updates do not shift the toggles); failures also show a Toast

**2b. System diagnostics (`diagnostics/`, `PrivilegedService.kt`)**
- `IPrivilegedService.collectDiagnostics(source, fd)` runs one fixed command as shell (`ShellDiagnostics`: `logcat -d -b main,system,radio`, `dumpsys dropbox --print data_app_anr|data_app_crash`), each bounded by a 15 s timeout and a tail size cap (logcat 2 MiB, dropbox 1 MiB after keeping only this app's entries). Output streams through a pipe the app passes in, never as one Binder parcel (#488)
- `SystemDiagnostics` is the app-side API: `availability()` (main-thread safe) and suspend `collect(context)`, which returns `Unavailable(reason)` without Shizuku/permission/UserService, else masked per-source files under `cacheDir/system-diagnostics/`. Call it only after explicit user opt-in
- `IdentifierMasker` (pure, unit tested) masks IMSI/ICCID/IMEI/phone numbers by pattern plus the device's own identifiers looked up best effort by `SubscriberIdentifiers`; masking is best effort, so users must preview before sharing
- Changing the AIDL requires bumping `PrivilegedServiceClient.INTERFACE_VERSION` so Shizuku replaces a stale UserService

**3. UI Layer**
- Built with **Jetpack Compose** and Material 3
- `HomeActivity.kt`: Main entry point with bottom navigation
- Navigation structure:
  - `/home`: Overview page showing app status
  - `/config{subscriptionId}`: Per-SIM configuration page
  - `/config{subscriptionId}/dump`: Raw config dump viewer
  - `/config{subscriptionId}/edit`: Expert mode editor
  - `/reports`: Stored crash/ANR reports (`CrashReportList`)
  - `/reports/{reportId}`: One report: preview, save, share, GitHub issue (`CrashReportDetail`)

**4. Hidden API Access**
- Uses `HiddenApiBypass` library to bypass Android's hidden API restrictions
- AIDL interface (`IPrivilegedService.aidl`) for the Shizuku UserService (`PrivilegedService`)

**5. Crash/ANR diagnostics (`diagnostics/`, `PixelIMSApplication.kt`)**
- `PixelIMSApplication` installs `JavaCrashRecorder` in `attachBaseContext` and starts `CrashReports.collectPreviousExits` in `onCreate` (both API 30+ only)
- `JavaCrashRecorder` is the default uncaught exception handler: it writes the stack trace synchronously to `filesDir/crash-pending/java-crash-<timestamp>-<pid>.txt` (no Binder calls) and chains to the previous handler
- On the next launch, a background thread reads `ActivityManager.getHistoricalProcessExitReasons` (`ExitReportCollector`) and `ExitReportProcessor` turns `REASON_ANR` (system ANR trace), `REASON_CRASH` (matched with the crash file by pid, else nearest timestamp within 60 s) and `REASON_CRASH_NATIVE` (raw tombstone protobuf, API 31+) exits into reports. A `SharedPreferences` cursor (`crash_reports.xml`) plus deterministic report ids (`<kind>-<timestamp>-<pid>`) make each exit produce at most one report; exits older than 7 days are skipped
- `CrashReportStore` keeps reports in `filesDir/reports/<id>/` (`report.properties` + gzip `payload.gz`), at most 5 reports and 4 MiB on disk, oldest evicted first; payloads are cut at 4 MiB (text payloads get a marker). Reports and the cursor are excluded from backup
- API for the UI (`CrashReports`): `store(context)` → `list()`, `get(id)`, `openPayload(id)`, `acknowledge(id)`, `unacknowledged()`, `hasUnacknowledged()`, `delete(id)`, `deleteAll()` (file I/O: call off the main thread); `revision` / `collected` are Compose states for refreshing
- Not covered: the `PrivilegedService` process (shell uid) and crashes before `attachBaseContext`
- Reporting UI: the home page shows `CrashReportCard` for unacknowledged reports once `CrashReports.collected` is true ("Send report" / "Dismiss" both acknowledge them) and a "Crash Reports" entry under Diagnostics. `CrashReportDetail` acknowledges the report on open, offers an opt-in "Include system logs" switch (disabled with an explanation without Shizuku/permission), and "Prepare preview" builds the export; changing the switch discards it
- Export (`ReportSharing` → `ReportExporter`, off the main thread): stages `environment.txt` (exit details + `ReportEnvironment`: device, Android version, security patch, build, baseband, app version, install source, carrier name/MCC-MNC/country per SIM; never subscription ids, ICCIDs, numbers or SIM display names), the exit payload, and `system/*.txt`, masks every text file with `IdentifierMasker` (plus the device's own identifiers from `SubscriberIdentifiers` when Shizuku is granted), and zips them to `cacheDir/report-export/pixel-ims-report-<id>.zip`. The preview reads the staged files, so it is byte-identical to the zip; the binary tombstone cannot be masked or previewed, so it is added (unchanged) only when the user turns on "Include native crash dump". Only one export exists at a time; it is cleared on the next prepare, on report deletion, and at app start (`PixelIMSApplication`)
- Sharing: "Save" (`ACTION_CREATE_DOCUMENT`), "Share" (`ACTION_SEND` chooser through the `${applicationId}.reports` `FileProvider`, paths limited to `cache/report-export/` in `res/xml/report_export_paths.xml`, read grant only via the intent's ClipData), and "GitHub issue" (`GitHubIssueLink`: `issues/new?template=bug_report.yml` with the title and form fields `device-model`, `android-version`, `security-patch`, `app-version`, `carrier`, `logs` prefilled; no traces or identifiers in the URL). These field ids are a contract with `.github/ISSUE_TEMPLATE/bug_report.yml`: do not rename them
- Debug builds only: `DebugCrashTriggerActivity` (`app/src/debug`) forces an ANR, Java crash or native crash: `adb shell am start -n dev.bluehouse.enablevolte/.diagnostics.DebugCrashTriggerActivity`

### Key Configuration Keys
The app primarily modifies these `CarrierConfigManager` keys:
- `KEY_CARRIER_VOLTE_AVAILABLE_BOOL`: Enable VoLTE
- `KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL`: Enable VoWiFi
- `KEY_CARRIER_VT_AVAILABLE_BOOL`: Enable Video Telephony
- `KEY_VONR_ENABLED_BOOL`: Enable VoNR (5G voice)
- `KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL`: Enable cross-SIM calling

## Important Development Notes

### Hidden API Bypass
On app startup (`HomeActivity.onCreate`), the app adds hidden API exemptions:
```kotlin
HiddenApiBypass.addHiddenApiExemptions("L") // Classes starting with L
HiddenApiBypass.addHiddenApiExemptions("I") // Interfaces starting with I
```

### Interface Caching
The `Moder` class implements `loadCachedInterface()` to cache Binder interfaces and avoid repeated service lookups, improving performance.

### Subscription Handling
- The app dynamically generates navigation routes based on active SIM subscriptions
- Each SIM gets its own config page with unique subscription ID
- Uses `SubscriptionInfo.uniqueName` extension: `"${displayName} (SIM ${simSlotIndex + 1})"`

### IMS Registration Check
VoLTE status is verified via:
1. `SubscriptionModer.isIMSRegistered`: Programmatic check returning `Boolean?`. `ImsRegistrationCheck` tries `ITelephony.isImsRegisteredForSubscriber(subId)`, then `isImsRegistered(subId)`, skipping variants that are missing or fail with hidden-API drift errors. `null` means "unknown" and is shown as such on the home page and IMS status QS tile instead of crashing.
2. After a config change, `ApplyResultTracker` polls `isIMSRegistered` (bounded, 30 s; `null` is shown as unknown) and the Config page shows the result
3. User can also check via dialer codes: `*#*#4636#*#*` → Phone information → IMS Service Status

### Version Updates
The app checks GitHub Releases API for updates:
```kotlin
getLatestAppVersion { latestVersion ->
    if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) { /* show update */ }
}
```
- `getLatestAppVersion` returns the newest release that is neither a draft nor a pre-release (`latestStableTag` in `AppVersion.kt`), so beta releases never prompt stable users.
- `parseAppVersion` accepts SemVer plus short beta tags such as `1.5.0b1` (read as `1.5.0-b1`, which sorts below `1.5.0`) and returns null instead of throwing; unparseable versions never count as newer.
- Beta releases: tag `X.Y.ZbN` with the same `versionName`, then mark the GitHub release as a pre-release.

## CI/CD

### GitHub Actions Workflows
- **default.yml**: Runs ktlint on PRs, builds debug APK, posts download link as PR comment
- **build-apk.yml**: Reusable workflow for building APKs with signing
- **build-aab.yml**: Builds Android App Bundle for Play Store releases
- **build-debug-apk.yml**: Standalone debug build workflow

### Linting
The project uses **ktlint 1.7.1** (the version CI downloads in `default.yml`). Lint violations fail the `lint` job, which gates the unit tests. Run `ktlint -F "app/src/**/*.kt"` to auto-format.

## Distribution

The app is distributed via:
1. **Google Play Store**: `dev.bluehouse.enablevolte`
2. **GitHub Releases**: Direct APK download

## Troubleshooting Development Issues

### Build Failures
- **Missing hidden API classes**: Ensure patched `android.jar` is installed at `$ANDROID_SDK/platforms/android-34/`
- **Shizuku not working**: Shizuku must be running via ADB or wireless debugging before launching the app

### Common Runtime Issues
- **Shizuku permission denied**: App will prompt for permission; grant "Allow all the time"
- **IMS not registering**: After toggling VoLTE, restart device 2-3 times with 5-minute intervals
- **Post-system update**: VoLTE configuration must be re-applied after Android system updates

## Code Style

Follow standard Kotlin conventions and ktlint rules. Use Jetpack Compose best practices for UI components.
