# Bug Reporting Guide

한국어 버전은 [이곳](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/bug-reporting.md)에서 볼 수 있습니다.

This guide explains how to report a crash, an ANR ("app not responding") or another bug in Pixel IMS so that it can be investigated. Reports are filed with the [bug report form](https://github.com/kyujin-cho/pixel-volte-patch/issues/new?template=bug_report.yml).

> [!WARNING]
> **Logs can contain your IMSI, ICCID, IMEI and phone numbers.** GitHub issues are public. Read the [privacy section](#privacy-remove-identifiers-before-posting) before attaching anything.

## Before reporting

- Check the [troubleshooting guide](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/troubleshooting.en.md) and the FAQ in the [README](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/README.en.md).
- Feature requests for devices or carriers that are not listed as supported belong in [Discussions](https://github.com/kyujin-cho/pixel-volte-patch/discussions), not in issues.
- Search the existing issues for the same problem first.

## What to include

The bug report form asks for these; have them ready:

- **Device model** (for example Pixel 8 Pro)
- **Android version** and **security patch level**: Settings > About phone > Android version
- **Pixel IMS version**: shown at the top of the app's home page, and whether you installed it from **Google Play or a GitHub Releases APK**
- **Carrier and country** for each SIM involved, and the **SIM slot** (SIM 1 / SIM 2, physical SIM or eSIM)
- **Which toggles you changed** in Pixel IMS (for example Enable VoLTE, Enable VoNR, values changed in Expert mode)
- **Steps to reproduce**, the **expected** behavior and what **actually** happened
- **Logs**: the report zip exported from the app, or logs collected manually (see below)

## Exporting a report from the app (crash or ANR)

When Pixel IMS crashed or stopped responding, the next launch shows a card on the home page: "The app crashed last time. Send a report?" or "The app stopped responding last time. Send a report?".

1. Tap **Send report**. (Stored reports are also available any time under **Home > Diagnostics > Crash reports**.)
2. Optional: turn on **Include system logs** to add logcat (main, system and radio buffers) and the system's ANR and crash entries for Pixel IMS. This needs Shizuku to be running with permission granted; otherwise the switch is disabled. It is off by default because radio logs are the most likely place for subscriber identifiers.
3. Tap **Prepare preview**. The page shows every file that goes into the zip, after masking. Read it: masking is best effort.
4. Tap **Save** and store the zip (for example in Downloads). **Share** sends the zip to another app instead.
5. Tap **GitHub issue**. The bug report form opens with the title, device model, Android version, security patch, app version and carrier already filled in. Complete the remaining fields and drag the saved zip into **Logs and attachments**.

The zip contains:

- `environment.txt`: exit details (kind, time, app version), device model, Android version, security patch, build, baseband, app version, install source, and carrier name / MCC-MNC / country per SIM. It never includes subscription ids, ICCIDs or phone numbers.
- The exit trace: `anr-trace.txt` (ANR) or `java-crash.txt` (crash). For a native crash, `tombstone.pb` is added only if you turn on **Include native crash dump**.
- `system/*.txt` when **Include system logs** was on.

IMSI, ICCID, IMEI and phone numbers are masked as `[IMSI]`, `[ICCID]`, `[IMEI]`, `[PHONE]`, `[ID]` or `[NUMBER]`. `tombstone.pb` is a binary file that cannot be masked or previewed, which is why it is off by default.

Reports can be deleted one by one or all at once from **Crash reports**.

## Collecting logs manually with adb

Use this if the problem is not a crash or ANR (for example VoLTE does not register), if you run an older version without the in-app export, or if the app cannot be opened.

1. Enable **Developer options** (tap Settings > About phone > Build number seven times) and turn on **USB debugging** (or **Wireless debugging**). Install [platform-tools](https://developer.android.com/tools/releases/platform-tools) on your computer and check that `adb devices` lists the phone.
2. Optional: clear the old log so the result only covers your reproduction: `adb logcat -c`
3. Reproduce the problem.
4. Right after reproducing, save the log:

   ```sh
   adb logcat -d -b main,system,radio > log.txt
   ```

   For a complete system snapshot (large, and full of identifiers), use `adb bugreport bugreport.zip` instead.

Where to look:

- **Java crash**: `FATAL EXCEPTION` lines with the `AndroidRuntime` tag in `log.txt`, followed by the stack trace.
- **ANR**: `ANR in dev.bluehouse.enablevolte` lines from `ActivityManager` in `log.txt`. In a bugreport, the thread dump is in the **VM TRACES AT LAST ANR** section of the main `bugreport-*.txt` file and under `FS/data/anr/`.
- **Native crash**: lines starting with `*** *** ***` and `Fatal signal` with the `DEBUG` or `libc` tags in `log.txt`; tombstones are under `FS/data/tombstones/` in a bugreport.
- The system also keeps recent crash and ANR entries: `adb shell dumpsys dropbox --print data_app_crash` and `adb shell dumpsys dropbox --print data_app_anr` (these include other apps; keep only the Pixel IMS entries).

Attach the file to the issue, or paste only the relevant part into **Logs and attachments**.

## Checking the IMS status

Include the IMS status when reporting VoLTE, VoWiFi or VoNR problems:

1. Open the Phone app and dial `*#*#4636#*#*`.
2. Open **Phone information** and select the SIM.
3. Open the menu (top right) and choose **IMS Service Status**. Note whether IMS registration, Voice over LTE, Voice over WiFi and Video Telephony are available.

The home page of Pixel IMS also shows the IMS registration status per SIM.

## Privacy: remove identifiers before posting

Logs, bugreports and screenshots can contain:

- **IMSI** (subscriber identity, usually 15 digits starting with your carrier's MCC/MNC)
- **ICCID** (SIM card serial number, usually starting with `89`)
- **IMEI** (device identity, 15 digits)
- **Phone numbers**, including in SIP addresses such as `sip:+8210...@...`

Everything posted in an issue is public and stays in the history. Before attaching:

- The in-app export masks these identifiers on a best-effort basis. Still read the preview.
- Logs collected with `adb` are **not masked**. Look up your own values (Settings > About phone shows the IMEI and phone number; Settings > About phone > SIM status shows more) and search the file for them, then replace them with `[REDACTED]`. Also search for long runs of digits.
- Do not post a full `adb bugreport` publicly. Attach only the relevant excerpt.
- If you notice that you posted an identifier, edit the issue or comment to remove it and delete the attachment.
