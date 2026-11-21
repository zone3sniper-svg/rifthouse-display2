# RiftHouse Display (Android tablet app)

A full-screen window onto the RiftHouse Pantry page, for a tablet on the kitchen wall.

* **Asks for the computer's address** the first time (for example `192.168.1.50:3000`), remembers it, and opens the Pantry page. No browser bars, screen stays awake, you stay signed in.
* **After 15 minutes with nobody touching the screen it goes back to the Pantry page** (the number of minutes can be changed). If someone wandered off to another page, such as Shop, it is back on Pantry when you return.
* **Comes back to the front after you have been away in another app** (also after 15 minutes). Android only allows an app to do this if you give it "Display over other apps" once (see below). Without it, it still resets the page after 15 minutes, but cannot pull itself over another app.
* **Seasonal themes** that switch on by the date: Christmas (1 to 26 Dec), New Year (27 Dec to 3 Jan), Easter (two weeks before Easter Sunday to Easter Monday), Halloween (24 Oct to 1 Nov). Each has a string of lights or bunting along the top edge and gentle falling or flying decorations (snow, confetti and fireworks, painted eggs, bats and embers). The decorations never block a touch. You can also force any theme, or turn them all off, in Settings.
* If the computer cannot be reached it shows a "Cannot reach the kitchen screen" page and keeps trying every 15 seconds (so it recovers by itself after a power cut or a restart).

## Making the app file (APK)

This is a normal Android Studio project with no extra libraries.

1. On a computer, install **Android Studio** (free, from developer.android.com/studio) and open this folder (`File > Open`). Let it finish "Gradle sync" (it downloads what it needs the first time).
2. Plug the tablet in with a cable, or just build the file: **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file appears at `app/build/outputs/apk/debug/app-debug.apk`.
3. Put that file on the tablet (cable, Google Drive, USB stick), open it there and allow "Install unknown apps" when asked. Or with the tablet plugged in and USB debugging on: `adb install app-debug.apk`.

The app name on the tablet is **RiftHouse Display**.

## First use

1. Open the app. It asks for the address. Type the computer's address on your home wifi and the port RiftHouse uses (3000 unless you changed it), then **Save**. Give the computer a fixed address in your router so it does not change.
2. To get back to Settings later: **press and hold the top-left corner of the screen for 3 seconds**. (On the "cannot reach" page there is also a **Change address** button.)

## Settings

Address, page to open (default `/pantry/`), minutes before it goes back to that page (default 15), theme (Automatic, None, Christmas, New Year, Easter, Halloween), whether decorations move, and whether to come back after being away.

To let it come back from another app: tap **Allow "display over other apps"** in Settings, switch it on for RiftHouse Display, then come back. For the strongest kiosk behaviour, also pick RiftHouse Display as the tablet's **Home app** (Android Settings > Apps > Default apps > Home app), so the Home button always lands here, and consider Android's **Screen pinning** to stop people leaving the app at all.

## Things to know

* **Not tested on a real tablet.** It was written without access to Android tools, so the first build may need a small fix. Tell me any error Android Studio shows and I will correct it. What was checked: the date logic for the four themes (including Easter 2025, 2026 and 2027) and the address tidy-up.
* The tablet and the RiftHouse computer must be on the same network. The address starts with `http://`, which this app allows; a nearby wifi name changing, or the computer getting a new address, needs the address changed in Settings.
* Away from home (not on the home wifi) it cannot reach RiftHouse. RiftHouse is only running in test mode for now.
* Press and hold in the corner is the only way into Settings, on purpose, so small hands cannot change the address.
