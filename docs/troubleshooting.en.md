# Troubleshooting

## VoLTE does not work after (various situations)

Resetting network related configurations might help resoving the issue.

1. Reset Mobile Network Settings
   1. Open Settings app. Navigate to System -> Reset options.
   2. Click "Reset Mobile Network Settings" to reset Mobile Network Settings. **Do not check Erase eSIMs** if you have active eSIM subscription on your cellphone, otherwise you will lose whole access to your cell subscription.
2. Reset Carrier Services
   1. Open Settings app. Navigate to Apps page.
   2. Click `See all <> apps` section.
   3. Find `Carrier Services` app and click it.
   4. Click `Force Stop` to stop app first.
   5. Click `Storage & cache` page, and then clear all files by clicking `Clear storage` button.
3. Reboot your phone.
4. Start Pixel IMS app. Navigate to desired SIM card page and click "Reset all settings".
5. Try enabling VoLTE again as usual.

## Setting APN as `ims`

Some carriers (e.g. Polish Play) require IMS APN to be set as `ims`. Check [here](https://github.com/kyujin-cho/pixel-volte-patch/issues/136#issuecomment-1565598716) for more informations.

## Carrier system apps sabotage IMS registration

This issue is usually the final problem if all solution applied but your Pixel can't successfully register with your carrier's IMS service.

To determine if your phone falls into this category (assuming you've attempted all the suggested solutions), perform an IMEI check on the [Pixel repair check](https://store.google.com/us/repair?hl=en-US) page.

If you see "Unlocked" text below your phone's name, proceed to the next step. Otherwise (usually Fi) ignore this whole section.

1. Removing the Original Carrier System Apps
   1. Connect your phone to one of your computer with ADB as usual, open `adb shell`.
   2. List the carrier system apps: `pm list packages | grep verizon`. This command will generate a list similar to the following:
      ```
      package:com.verizon.mips.services
      package:com.verizon.services
      package:com.verizon.llkagent
      ```
      Note: This tutorial assumes the original carrier is Verizon, If that's not the case, try using Sprint (`sprint`), T-Mobile (`tmobile`), etc. or manually browse through the list by `pm list packages`.
   3. Disable those apps in the system user space: `pm uninstall -k --user 0 <package id after 'package:'>`. For example:
      ```
      pm uninstall -k --user 0 com.verizon.mips.services
      pm uninstall -k --user 0 com.verizon.services
      pm uninstall -k --user 0 com.verizon.llkagent
      ```
   4. (Optional) You can try rebooting your phone now to see if it works immediately, but it usually doesn't.
2. Restart the IMS registration
   1. Start Pixel IMS app. Navigate to desired SIM card page and click "Reset all settings".
   2. Enable VoLTE (and any other desired features) as usual.
   3. If possible, go to your SIM account on your carrier's website or mobile app and re-register for the VoLTE service.
   4. Reboot until it works (typically twice should be sufficient).
  
### Explanation (the boring part)

There is a possibility that your phone was carrier-locked, especially if it was originally purchased in the United States. Those can be unlocked after about two years, depending on the terms of the contract between the original owner and the carrier.

Although after your Pixel is unlocked, and you can use your SIM from different carrier (especially in countries where Pixels are not officially available), those preloaded apps from the original carrier are still persist and manipulate the system to register some special services like VoLTE, VoWifi and 5G data to the original carrier configurations. This can lead to difficulties in registering these services on your Pixel.

You may need to follow these steps again if you ever perform a factory reset on your phone. It's worth noting that some Android 14 QPR1 Beta users have reported that Pixel IMS is no longer needed. This is however possibly removed in the beta ROM for the sake of platform compability, not because Google decided to open their hands.

## VoWifi doesn't work (in flight mode)

Unfortunately, if your carrier hasn't configured Wi-Fi calling then your device will report that Wi-Fi calling is working however it won't actually work when you test it under flight mode. Contact your carrier to get them to check it out.

## Special calls redirect to emergency numbers on VoLTE

This is a carrier issue. Contact your carrier for help in relation to this problem.
