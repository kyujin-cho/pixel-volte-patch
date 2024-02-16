# Pixel IMS: Enable VoLTE on Tensor Pixel devices

## Troubleshooting

Refer [here](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/troubleshooting.en.md).

## Introduction

This document describes enabling VoLTE support on select Google Pixel devices by using Android's internal `telephony.ICarrierConfigLoader.overrideConfig()`. This patch can be considered as a rootless method of [voenabler](https://github.com/cigarzh/voenabler).

## Supported Carriers

### First-grade support

Carriers which can test if patch works or not by developer immediately

- LG U+ (Republic of Korea)

### 2차 지원

Carriers which aren't possible for testing by developer but reported as supported by community. Please refer the [Link](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/compatibility-chart.en.md) for complete list of carriers.

## Applying Patch

### Requirement

- Pixel device with Google Tensor Chipset
  - Google Pixel 6
  - Google Pixel 6a
  - Google Pixel 6 Pro
  - Google Pixel 7
  - Google Pixel 7a
  - Google Pixel 7 Pro
  - Google Pixel 8
  - Google Pixel 8 Pro
  - Google Pixel Fold

#### Optional

only if installing Shizuku using ADB

- Windows, macOS or Linux PC with [Android Platform Tools](https://developer.android.com/studio/command-line/adb) installed
- USB-A to USB-C or USB-C to USB-C cable to connect Pixel to the PC

### Installing Shizuku

[Shizuku](https://shizuku.rikka.app/) makes possible to call internal Android API without root permission by creating a proxy service with ADB user.

1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) at the Pixel device you're trying to patch.
   ![image-1](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035249.png)
2. Open installed applciation.
   ![image-2](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035312.png)

### Starting Shizuku without PC (Wi-Fi connection required)

1. Follow the [official guide to start Shizuku using Wifi debugging](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging) without needing any external PC, after that you should see something like "Shizuku is running" at your Pixel phone.
   ![image-4](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035351.png)
2. Now continue to next section.

### Starting Shizuku with PC

1. Connect your Pixel phone with PC by following [this description](https://shizuku.rikka.app/guide/setup/#start-by-connecting-to-a-computer).
2. Start shizuku service by executing `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`. You should see something like "Shizuku is running" at your Pixel phone.
   ![image-3](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot%202023-02-06%20at%203.54.00%20AM.png)
   ![image-4](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035351.png)
3. Now continue to next section.

### Install Pixel IMS application
1. As for now, there are two ways to obtain the application. Choose you favourite way and install the application.
   - Via [Play Store](https://play.google.com/store/apps/details?id=dev.bluehouse.enablevolte)
   - From [Github Releases](https://github.com/kyujin-cho/pixel-volte-patch/releases/download/1.2.8/dev.bluehouse.enablevolte.apk), by downloading APK file
2. Start installed application.
3. Tap "Allow all the time" when seeing prompt asking for Shizuku permission.
   ![image-5](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-235239.png)
4. Toggle "Enable VoLTE" to enable VoLTE.
   ![image-6](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-234343.png)
5. Restart your Pixel phone a couple of times until you can see VoLTE is working.

### Build application from source
Download [patched android.jar](https://github.com/Reginer/aosp-android-jar/raw/main/android-33/android.jar), put it under `$ANDROID_PATH/sdk/platforms/android-33` and start hacking as usual.

## FAQ

### I am looking for somewhere to post feedback.

- Bug report and feature request: [Issues](https://github.com/kyujin-cho/pixel-volte-patch)
- Anything else (including general questions): [Discussions](https://github.com/kyujin-cho/pixel-volte-patch/discussions)

### Does it also work on any carriers other than LG U+?

AYOR. Tested and checked working only with LG U+.

### How do I know if VoLTE is enabled or not?

`Registered` IMS Status at Home page means VoLTE is activated.
![image-7](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-234340.png)

For more information, you can make use of Pixel's internal application. To open it:

1. Open vanilla Dialer app from your Pixel phone.
   ![image-8](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035705.png)
2. Dial `*#*#4636#*#*`.
   ![image-9](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035701.png)
3. Tap "Phone information" menu.
   ![image-10](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035650.png)
4. Tap triple-dot icon at the upper right screen then select "IMS Service Status" menu.
   ![image-11](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-030524.png)
5. You should see `IMS Registration: Registered` if everything's done well.
   ![image-12](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035645.png)

### Do I have to do this every time I reboot the phone?

No.

### Do I have to do this after updating my Pixel?

Yes.

### How does it work?

There is a checker method, `ImsManager.isVolteEnabledByPlatform(Context)`, which determines if VoLTE is possible for your device-carrier combination(ref: [googlesource.com](https://android.googlesource.com/platform/frameworks/opt/net/ims/+/002b204/src/java/com/android/ims/ImsManager.java)). The abstract logic of that method is:

1. Check if `persist.dbg.volte_avail_ovr` System Property is true
   - If yes, return true
     - This is how voenabler works
   - Else continue
2. Check if device supports VoLTE
   - If not, return false
   - Else continue
3. **Check if your carrier supports VoLTE**
   - If not, return false
   - Else continue
4. Check if your carrier requires BGA-capable SIM for VoLTE
   - If not, return true
   - Else continue
5. Check if GBA bit is active at EF IST
   - If yes, return true
   - If not, return false

This patch alters the bolded logic, by force injecting config values as true regardless of carrier configuration.
