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
