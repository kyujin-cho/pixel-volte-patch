# Bug reporting guide

Korean version available [here](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/bug-reporting.md).

Issues are for requesting new features or discussing critical bugs that make the app hard to use. Reports asking why a feature is not enabled on unsupported devices or carriers may be moved to Discussions without notice. Please check the [troubleshooting guide](https://github.com/kyujin-cho/pixel-volte-patch/blob/main/docs/troubleshooting.en.md) first.

## What to include

- Device model (e.g. Pixel 8 Pro)
- Android version and security patch level (Settings -> About phone)
- Pixel IMS app version
- Carrier and country
- SIM slot (SIM 1 / SIM 2 / eSIM)
- Toggles you changed (VoLTE, VoWiFi, VoNR, video calling, etc.)
- Steps to reproduce, expected behavior, and actual behavior

## Collecting logs

### Manual fallback (when the in-app export is unavailable)

Run one of the following commands on a PC right after reproducing the problem.

```bash
adb bugreport bugreport.zip
```

or dump the logcat buffers directly:

```bash
adb logcat -d -b main,system,radio > log.txt
```

`adb bugreport` creates a ZIP archive containing ANR traces (`FS/data/anr/`), native crash records (`FS/data/tombstones/`), and a logcat dump. In a logcat dump, app crash stack traces appear in the `main` buffer (`AndroidRuntime`, `FATAL EXCEPTION`), ANR information in the `system` buffer (`ActivityManager`, `ANR in ...`), and telephony/IMS messages in the `radio` buffer.

### Checking IMS registration

Open the dialer and go to `*#*#4636#*#*` -> `Phone information` -> `IMS Service Status` to check registration, and attach a screenshot if possible.

### In-app report export

On builds that provide report export and sharing, export a report from the app home screen and attach the resulting ZIP to the issue.

## Attaching files

Attach the `bugreport` ZIP or `log.txt` to the issue directly, or upload it to a [Gist](https://gist.github.com/) and leave a link. If you paste the text inline, wrap it in a code block.

## Privacy warning

Logs may contain subscriber identifiers such as IMSI, ICCID, IMEI, and phone numbers. Redact or remove them before posting to a public issue. (e.g. `01012345678` -> `010XXXXXXXX`)

## Filing the issue

Open a [new issue](https://github.com/kyujin-cho/pixel-volte-patch/issues/new/choose), choose the bug report form, and fill in the information above.
