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
Currently, this project does not have automated unit tests. Testing is performed manually by:
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

**3. UI Layer**
- Built with **Jetpack Compose** and Material 3
- `HomeActivity.kt`: Main entry point with bottom navigation
- Navigation structure:
  - `/home`: Overview page showing app status
  - `/config{subscriptionId}`: Per-SIM configuration page
  - `/config{subscriptionId}/dump`: Raw config dump viewer
  - `/config{subscriptionId}/edit`: Expert mode editor

**4. Hidden API Access**
- Uses `HiddenApiBypass` library to bypass Android's hidden API restrictions
- AIDL interfaces (`IPrivilegedService.aidl`) for Shizuku service binding

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
1. `SubscriptionModer.isIMSRegistered`: Programmatic check using `ITelephony.isImsRegistered()`
2. User can also check via dialer codes: `*#*#4636#*#*` → Phone information → IMS Service Status

### Version Updates
The app checks GitHub Releases API for updates:
```kotlin
getLatestAppVersion { latestVersion ->
    // Compare with BuildConfig.VERSION_NAME
}
```

## CI/CD

### GitHub Actions Workflows
- **default.yml**: Runs ktlint on PRs, builds debug APK, posts download link as PR comment
- **build-apk.yml**: Reusable workflow for building APKs with signing
- **build-aab.yml**: Builds Android App Bundle for Play Store releases
- **build-debug-apk.yml**: Standalone debug build workflow

### Linting
The project uses **ktlint 0.48.2**. Lint violations will fail CI checks on PRs affecting `app/src/**`.

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
