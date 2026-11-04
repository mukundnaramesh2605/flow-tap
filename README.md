# FlowTap

A personal Android automation app in the spirit of Apple Shortcuts. You build **flows**, which are named lists of actions such as "ringer silent, Wi-Fi off, brightness low". You run a flow with one tap from a home-screen grid, or let it run on its own at a set time or when a Bluetooth device connects.

FlowTap is built for sideloading onto a couple of personal phones. It works on any Android 8.0+ phone, and works best with [Shizuku](https://shizuku.rikka.app/), which lets it toggle Wi-Fi and Bluetooth silently.

---

## Contents

- [Features](#features)
- [Prerequisites](#prerequisites)
- [Build and install](#build-and-install)
- [Set up Shizuku (recommended)](#set-up-shizuku-recommended)
- [First launch](#first-launch)
- [Using FlowTap](#using-flowtap)
- [Copying flows to another phone](#copying-flows-to-another-phone)
- [What happens without Shizuku](#what-happens-without-shizuku)
- [Permissions FlowTap asks for](#permissions-flowtap-asks-for)
- [Troubleshooting](#troubleshooting)
- [For developers](#for-developers)
- [Known limitations](#known-limitations)

---

## Features

- **Flows on a home grid.** Each tile has a name, an icon and a colour. Tap a tile to run it and watch each step on the tile; long-press it to edit.
- **11 actions:** open URL, open app, Wi-Fi, Bluetooth, flashlight, ringer mode, brightness, send SMS, phone call, toast or notification, and wait.
- **Automations:**
  - time-of-day schedules on chosen days, as many per flow as you like
  - Bluetooth device connect or disconnect, e.g. your car
- **Runs in the background with the screen off.** It uses exact alarms, re-arms them after a reboot, and runs each flow in a short foreground service.
- **Shizuku integration** for silent Wi-Fi and Bluetooth toggles. Shizuku can also grant FlowTap's special permissions without you visiting system settings.
- **Never fails silently.** If a step can't finish on its own, you get a settings panel to tap (for manual runs) or a "tap to finish" notification (for automations).
- **Export and import** flows, schedules included, as a JSON file you can send to another phone.
- **Two sample flows** out of the box: **Bedtime** and **Driving**.

---

## Prerequisites

### To build the app (on your computer)

| Requirement | Version / notes |
|---|---|
| **JDK** | 17 (e.g. `brew install openjdk@17` on macOS, or the JDK bundled with Android Studio) |
| **Android SDK** | Platform **36** (Android 16) and Build-Tools **36.x** |
| **Android SDK Platform-Tools** | For `adb` (installing to a phone over USB) |
| **Gradle** | Not needed separately; the project includes the Gradle wrapper (`./gradlew`, Gradle 9.1) |
| **Android Studio** | Optional. Any recent version that supports Android Gradle Plugin 9.0, if you want to open the project in an IDE |
| **Internet access** | The first build downloads dependencies |

If you install the SDK with Android Studio, it lives in `~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux and `%LOCALAPPDATA%\Android\Sdk` on Windows. The command-line route:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

### To run the app (on each phone)

| Requirement | Notes |
|---|---|
| **Android 8.0 (API 26) or newer** | Tested against Android 16 as the target |
| **A way to install the APK** | USB debugging (for `adb install`), or allow "Install unknown apps" for your file manager or messaging app |
| **Shizuku** (optional, recommended) | Needed for silent Wi-Fi and Bluetooth toggles on Android 10+ and 13+. See [Set up Shizuku](#set-up-shizuku-recommended) |
| **Wi-Fi network** (for Shizuku on Android 11+) | Shizuku starts itself over Wireless debugging, which needs Wi-Fi |

---

## Build and install

### 1. Get the code

```bash
git clone https://github.com/mukundnaramesh2605/flow-tap.git
```

```bash
cd flow-tap
```

### 2. Point Gradle at your Android SDK

Create `local.properties` in the project root. Android Studio does this for you, and the file is git-ignored.

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

Alternatively, set the `ANDROID_HOME` environment variable.

### 3. Build

Debug build, best while trying things out:

```bash
./gradlew :app:assembleDebug
```

This produces `app/build/outputs/apk/debug/app-debug.apk`.

Release build, which is about 10× smaller because it's minified:

```bash
./gradlew :app:assembleRelease
```

This produces `app/build/outputs/apk/release/app-release.apk`.

> **Signing:** release builds are signed with your machine's **debug key** (`~/.android/debug.keystore`), so the APK installs without any keystore setup. Android only accepts an update signed with the **same key**, so always build updates on the **same computer**. An APK built elsewhere can't update an existing install. You'd have to uninstall first, which deletes your flows, so **export them first**.

### 4. Install on a phone

**Over USB (adb):**

1. On the phone, turn on **Developer options**: go to *Settings → About phone* and tap **Build number** 7 times.
2. Turn on **Settings → System → Developer options → USB debugging**.
3. Plug the phone in and accept the "Allow USB debugging?" prompt.
4. Install the debug build:

```bash
./gradlew :app:installDebug
```

Or install a specific APK:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

With two phones plugged in at once, pick one with `adb devices` and then `adb -s <serial> install -r …`.

**Without a cable:** send the APK to the phone any way you like (Quick Share, Google Drive, email). Open it from the Files app and allow **Install unknown apps** for that app when Android asks.

---

## Set up Shizuku (recommended)

Shizuku lets regular apps run a small set of commands with the same rights as `adb`, without root. FlowTap uses it to:

- toggle **Wi-Fi** (blocked for apps since Android 10) and **Bluetooth** (blocked since Android 13) silently
- open apps, URLs and calls from the background during automations
- grant itself **Do Not Disturb access**, **Modify system settings** and **unrestricted battery** without you visiting settings

### Install and start Shizuku

1. Install **Shizuku** from the [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [GitHub releases](https://github.com/RikkaApps/Shizuku/releases).
2. Start it.
   - **Android 11 and newer (no computer needed):**
     1. Connect to Wi-Fi.
     2. Turn on **Developer options**, then **Wireless debugging**.
     3. In Shizuku, tap **Pairing**. Open *Wireless debugging → Pair device with pairing code*, and type the code into Shizuku's notification.
     4. Tap **Start** in Shizuku.
   - **Android 10 and older, or if you prefer a cable:** connect over USB with USB debugging on, then run:
     ```bash
     adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
     ```
   - **Rooted phones:** tap **Start** in Shizuku. It can also start itself on boot.
3. Open FlowTap. Shizuku asks whether to **allow FlowTap**; tap **Allow**.

### Check it's working

Go to **FlowTap → Settings → Shizuku**. The dot should be green with **Connected**. Tap **Test connection**; you should see something like *"Working — commands run as shell (uid 2000)"*.

### After every reboot

**Shizuku stops whenever the phone restarts** (unless the phone is rooted). Open Shizuku and tap **Start** again; on Android 11+ you won't need to pair again. Until you do, FlowTap still works, but uses the fallbacks described in [What happens without Shizuku](#what-happens-without-shizuku). The Automations screen shows a reminder card while Shizuku is down.

---

## First launch

1. **Allow FlowTap in Shizuku**, if Shizuku is running and prompts you.
2. You'll see the two sample flows:

   | Flow | Actions | Schedule |
   |---|---|---|
   | **Bedtime** | Ringer silent → Wi-Fi off → Brightness 10% | 11:00 PM every day (**off by default**) |
   | **Driving** | Bluetooth on → Ringer normal → Open Google Maps | none |

3. If a **Finish setting up** banner appears above the grid, tap it. It opens the **Permissions** screen, which lists only what your flows actually use, each with a reason.
4. Before you rely on automations, open the **Automations** tab. Act on any cards it shows, especially **Battery optimization is on**. With Shizuku running, **Fix with Shizuku** handles that card in one tap.

Deleted the samples and want them back? Go to **Settings → Add sample flows**.

---

## Using FlowTap

### Run a flow

- **Tap** a tile. A progress bar and the current step ("2/3 · Wi-Fi off") show on the tile. It then shows **Done**, **Done · N notes** or **Didn't run**.
- If something needed your attention, a snackbar appears. Tap **Details** to see a step-by-step report. Steps FlowTap can help with have a **Fix** button, which opens the right permission prompt or settings screen.
- One failed step doesn't stop the rest. If Wi-Fi can't change, Bedtime still silences the ringer.
- The first time you tap a flow that needs a permission (for example SMS), FlowTap explains why before Android's prompt appears. **Run anyway** is always available.

### Create or edit a flow

- **Create:** tap **New flow** on the home screen.
- **Edit:** long-press a tile, or tap its **⋯** button.

In the editor:

1. **Name** the flow, then pick a **colour** and an **icon**.
2. Tap **Add action** and pick one from the bottom sheet. A settings sheet opens for that action; tap **Add** when it's set up.
3. **Reorder** actions by dragging the **≡** handle. **Tap** an action to change it, or tap **✕** to remove it.
4. Under **Automations**, add a **Schedule** or a **Bluetooth** trigger (see below).
5. Tap **Save**. If you go back with unsaved changes, FlowTap asks whether to discard them.

The editor's **⋮** menu has:

- **Test as automation:** runs the saved flow exactly the way a schedule would. Use it to check the Shizuku and notification fallbacks without waiting.
- **Export flow:** see [Copying flows to another phone](#copying-flows-to-another-phone).
- **Delete flow**

### Actions

| Action | What you set | Notes |
|---|---|---|
| **Open app** | App, picked from a searchable list | Only apps with a launcher icon are listed |
| **Open URL** | Web address | `https://` is added if you leave it out |
| **Wi-Fi** | On / Off / Toggle | Silent with Shizuku; otherwise opens the Wi-Fi panel |
| **Bluetooth** | On / Off / Toggle | Silent with Shizuku; otherwise asks Android or opens Bluetooth settings |
| **Flashlight** | On / Off / Toggle | No permission needed. Turns off if Android closes FlowTap |
| **Ringer mode** | Silent / Vibrate / Normal | Needs Do Not Disturb access, which Shizuku grants automatically |
| **Brightness** | 0–100% | Also turns off adaptive brightness so the level sticks. Uses a curve like the system slider, so 10% is genuinely dim |
| **Send SMS** | Number + message | Sent in the background; long messages are split automatically |
| **Phone call** | Number | Starts the call directly |
| **Show toast** | Message | Short pop-up text |
| **Notification** | Message | Posted with the flow's name as the title |
| **Wait** | 1–3600 seconds | Pauses before the next action |

### Schedules

In the editor, go to **Automations → Schedule** and pick a time and the days to repeat on. The **Every day**, **Weekdays** and **Weekends** shortcuts fill in the days for you. A flow can have several schedules, for example 7:00 on weekdays and 9:00 at weekends.

Schedules use exact alarms. They fire at the chosen minute even when the phone is asleep, and are re-armed automatically after a reboot or a time zone change.

### Bluetooth triggers

In the editor, go to **Automations → Bluetooth**. Choose **Connects** or **Disconnects**, then pick a **paired** device. This works well for running Driving when the car connects. FlowTap needs the **Nearby devices** permission to list devices and to notice connections; the dialog asks for it.

Repeat connects from the same device within 60 seconds are ignored, because some car kits reconnect a few times in a row.

### The Automations tab

- **Every schedule and Bluetooth trigger** across all your flows, each with its **next run time** ("in 3h 20m · 11:00 PM") and an **on/off switch**. Tap a row to open its flow.
- **Health cards** at the top appear only when something would make automations late or unreliable:
  - **Battery optimization is on.** Android may delay or skip runs overnight. Fix this.
  - **Exact alarms are off.** Schedules may run several minutes late.
  - **Notifications are off.** FlowTap can't ask you to finish steps.
  - **Bluetooth triggers can't fire.** The Nearby devices permission is missing.
  - **Shizuku isn't connected.** Some steps will need a tap. Restart Shizuku.

### What a background run looks like

When a trigger fires, a quiet **Running automation** notification shows while the flow runs, then disappears. You may also see:

- **"Bedtime: tap to turn Wi-Fi off"**: a step couldn't finish on its own, usually because Shizuku isn't running. Tap it to finish.
- **"Bedtime: 1 step didn't run"**: something was blocked, such as a missing permission. Run the flow by hand from the app to see its **Fix** options.
- **"Tap to run Bedtime"**: Android refused to start the background run at all. This is rare, and usually fixed by turning battery optimization off.

---

## Copying flows to another phone

**On the phone that has the flows:**

- To send everything: **⋮ → Export all flows** on the home screen, or **Settings → Export all flows**.
- To send one flow: open it and use **⋮ → Export flow**.
- Choose **Share** (Quick Share, Messages, email…) or **Save to file**.

**On the other phone:**

- Open the received `.json` file and choose **Import into FlowTap**.
- Or use **⋮ → Import flows** on the home screen, or **Settings → Import flows**, and pick the file.

Before importing, FlowTap shows a **preview** of the flows:

- **Apps not installed on this phone** are flagged. The action is kept and marked *"Not installed on this phone"* in the editor, so it works if you install the app later.
- **Anything this version can't read**, such as an action from a newer FlowTap, is skipped and counted. The rest of the file still imports.
- **Imported flows are added as new flows**, even if a flow with the same name exists.
- **Schedules keep their on/off state.** Each is given a fresh internal ID, so it never clashes with existing ones.
- **Bluetooth triggers carry over.** They store the other device's address (your car's), which is the same from both phones.

---

## What happens without Shizuku

FlowTap always tries the silent path first and falls back gracefully. A manual run is one you start by tapping a tile; an automation run starts from a schedule or Bluetooth trigger.

| Step | With Shizuku | Without Shizuku, manual run | Without Shizuku, automation run |
|---|---|---|---|
| Wi-Fi (Android 10+) | Silent toggle | Opens the Wi-Fi panel | "Tap to finish" notification |
| Wi-Fi (Android 9 and older) | Silent | Silent | Silent |
| Bluetooth (Android 13+) | Silent toggle | Android's "turn on?" dialog, or Bluetooth settings to turn it off | "Tap to finish" notification |
| Bluetooth (Android 12 and older) | Silent | Silent (needs Nearby devices on 12) | Silent |
| Open app / URL / call | Opens directly | Opens directly | Notification, unless *Display over other apps* is allowed |
| Ringer, brightness | Permission granted automatically | One-time trip to settings via **Fix** | Reported as *didn't run* until granted |
| Flashlight, SMS, toast, notification, wait | Same either way | | |

---

## Permissions FlowTap asks for

Everything is listed with its reason under **Settings → Permissions**, and FlowTap only asks for what your flows use.

| Permission | Why | How it's granted |
|---|---|---|
| Notifications | "Notify" actions; "tap to finish" prompts from automations | Android prompt |
| Send SMS | "Send SMS" actions | Android prompt |
| Phone calls | "Phone call" actions | Android prompt |
| Nearby devices (Android 12+) | Bluetooth without Shizuku; Bluetooth triggers | Android prompt |
| Modify system settings | Brightness | Shizuku, or a settings screen |
| Do Not Disturb access | Switching the ringer to or from silent | Shizuku, or a settings screen |
| Alarms & reminders | On-time schedules | Granted automatically on Android 13+ |
| Unrestricted battery | Keeps automations from being delayed or skipped | Shizuku, or Android's "allow?" dialog |
| Display over other apps (optional) | Opening apps from automations when Shizuku isn't running | Shizuku, or a settings screen |

**Camera permission is not needed.** Android's flashlight switch works without it. Nothing FlowTap does leaves your phone.

---

## Troubleshooting

**Wi-Fi or Bluetooth opens a panel instead of switching silently.**
Shizuku isn't connected. Go to *Settings → Shizuku*: start Shizuku if it says *Not running*, or tap **Allow FlowTap** if it says *Permission needed*. After a reboot, Shizuku always needs restarting.

**Shizuku says FlowTap was denied and won't ask again.**
Open Shizuku, go to *Authorized apps*, and turn on FlowTap.

**A schedule ran late, or not at all.**
Open the **Automations** tab and fix any cards there, battery optimization first. Some brands (Xiaomi, Samsung, OnePlus, Huawei…) add their own "auto-start" or "sleeping apps" lists on top of Android's. Allow FlowTap there too. [dontkillmyapp.com](https://dontkillmyapp.com/) has per-brand steps.

**A schedule didn't run while the phone was switched off.**
That's expected. Missed runs aren't caught up; the next occurrence is scheduled instead.

**Ringer or brightness says "didn't run".**
Grant *Do Not Disturb access* or *Modify system settings* from *Settings → Permissions*, or let Shizuku grant it.

**The Bluetooth trigger never fires.**
Check that the device is **paired** in Android's Bluetooth settings, that FlowTap has **Nearby devices**, and that the trigger's switch is on in the Automations tab.

**An "Open app" step says the app isn't installed.**
The flow was imported from a phone that had that app. Install the app, or edit the step to pick another one.

**The update won't install ("App not installed" / signature conflict).**
The APK was built on a different computer, so it has a different signing key. Export your flows, uninstall FlowTap, install the new APK, then import them.

---

## For developers

### Tech stack

- **Kotlin**, **Jetpack Compose** with **Material 3**, a single activity and type-safe Navigation Compose
- **MVVM** with ViewModels and StateFlow, **Hilt** for dependency injection, **Room** for storage (actions and triggers stored as JSON columns)
- **kotlinx.serialization** for the database columns and the export format
- **Shizuku API 13**, with an AIDL user service that runs shell commands
- **AlarmManager** (`setExactAndAllowWhileIdle`) and a `specialUse` foreground service for automations

### Project layout

```
app/src/main/java/com/flowtap/
├── domain/        Models (TapFlow, FlowAction, Trigger), run results, next-run maths
├── data/          Room DB + repository, export/import codec, action executors, run manager
├── automation/    Alarm scheduler, receivers (alarm/boot/Bluetooth), run service, health checks
├── system/        Shizuku manager + shell service, permissions, notifications, activity launcher
├── di/            Hilt modules
└── ui/            Compose screens: home, editor, automations, settings, permissions, run report
```

### Common tasks

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run Android lint:

```bash
./gradlew :app:lintDebug
```

Install a debug build on a connected phone:

```bash
./gradlew :app:installDebug
```

Watch logs while testing automations:

```bash
adb logcat -s AlarmScheduler AutomationEngine FlowRunner ShizukuManager
```

### Export file format

Exports are plain JSON (`"format": "flowtap"`, `"version": 1`). Action and trigger types are identified by stable names such as `"wifi"`, `"open_app"`, `"time"` and `"bluetooth_device"`. Don't rename the `@SerialName` values or the `FlowIcon`/`FlowColor` enum entries: files that are already exported depend on them.

---

## Known limitations

- **Shizuku must be restarted after every reboot** on non-rooted phones. Until then, Wi-Fi, Bluetooth and background app launches fall back to a tap.
- **The flashlight turns off if Android closes FlowTap.** This is how Android's flashlight API works.
- **Missed schedules aren't caught up** after the phone was off.
- **No charger or Wi-Fi-network triggers.** Android no longer delivers charger events to closed apps, and reading Wi-Fi network names needs background location access.
- **Built for sideloading.** It uses exact-alarm and battery-exemption permissions that Google Play restricts to certain app types.
