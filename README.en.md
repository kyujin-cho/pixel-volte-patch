# Enable VoLTE on Pixel 6 & 7 with LG U+

## Introduction

This document describes enabling VoLTE support on select Google Pixel devices by using Android's internal `telephony.ICarrierConfigLoader.overrideConfig()`. This patch can be considered as a rootless method of [voenabler](https://github.com/cigarzh/voenabler).

## Applying Patch

### Requirement

- Pixel device with Google Tensor Chipset
  - Google Pixel 6
  - Google Pixel 6a
  - Google Pixel 6 Pro
  - Google Pixel 7
  - Google Pixel 7 Pro
- Windows, macOS or Linux PC with [Android Platform Tools](https://developer.android.com/studio/command-line/adb) installed
- USB-A to USB-C or USB-C to USB-C cable to connect Pixel to the PC

### Installing Shizuku

[Shizuku](https://shizuku.rikka.app/) makes possible to call internal Android API without root permission by creating a proxy service with ADB user.

1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) at the Pixel device you're trying to patch.
   ![image-1](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035249.png)
2. Open installed applciation.
   ![image-2](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035312.png)
3. Connect your Pixel phone with PC by following [this description](https://shizuku.rikka.app/guide/setup/#start-by-connecting-to-a-computer).
4. Start shizuku service by executing `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`. You should see somewhat like "Shizuku is running" at your Pixel phone.
   ![image-3](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot%202023-02-06%20at%203.54.00%20AM.png)
   ![image-4](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230206-035351.png)
5. Now continue to next section.

### Install Patch Application

1. Click the [following link](https://github.com/kyujin-cho/pixel-volte-patch/releases/download/1.2.0/dev.bluehouse.enablevolte.apk) or check out Releases tab of this Github repository to install latest version of `PixelIMS` application's APK file.
2. Install downloaded APK file.
3. Start installed application.
4. Tap "Allow all the time" when seeing prompt asking for Shizuku permission.
   ![image-5](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-235239.png)
5. Toggle "Enable VoLTE" to enable VoLTE.
   ![image-6](https://github.com/kyujin-cho/pixel-volte-patch/raw/main/assets/Screenshot_20230208-234343.png)
6. Restart your Pixel phone a couple of times until you can see VoLTE is working.

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

Not sure.

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
