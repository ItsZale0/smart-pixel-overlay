# Smart Pixel (Overlay App)

An Android app that replicates the **Smart Pixel** feature found in many custom ROMs (e.g. LineageOS derivatives, crDroid, Project Infinity X). It applies a configurable grid overlay that turns off a percentage of pixels (renders them black) to help reduce OLED/AMOLED burn-in and slightly lower power usage.

> **Note:** This is a standalone overlay app, not a system/framework integration. It provides equivalent controls and behavior, but it is implemented using an Android overlay window rather than a ROM-level SystemUI/display filter.

## Features

- **Enable/disable Smart Pixel filter**
- **Intensity**: percentage of pixels turned off (1–100%)
- **Patterns**: 2x2, 3x3, 4x4, 6x6 grid, diagonal, checkerboard
- **Pixel shift interval**: off, 15s, 30s, 1m, 2m, 5m, 10m (moves the mask to distribute wear)
- **Enable on battery saver** (optional)
- **Quick Settings tile** for fast toggling
- **Start on boot** (when enabled and overlay permission is granted)
- **Optional root helper**: in-app button to grant overlay permission via `su`/`appops` (root required)

## How it works

The app runs a foreground service and draws a full-screen, non-touchable overlay (`TYPE_APPLICATION_OVERLAY`) with a repeating black mask pattern. On OLED/AMOLED displays, black pixels are effectively off, which mimics the Smart Pixel concept.

## Requirements

- Android 8.0+ (API 26+)
- **"Display over other apps"** (overlay) permission
- OLED/AMOLED screen recommended for real benefit (on LCD there is little/no power saving)

## Limitations vs ROM-integrated Smart Pixel

- Not integrated into system Settings/SystemUI (separate app UI and controls).
- Implemented as an app overlay, not a native framework/display pipeline filter.
- May not appear over some protected/system-secure surfaces due to Android restrictions.
- Root/Shizuku support in this app is limited to convenience (e.g. granting overlay permission); it does not provide true system-level integration.

## Build

Project: Gradle (Kotlin), AGP 8.5.2, compile/target SDK 34, min SDK 26.

### Prerequisites
- JDK 17
- Android SDK (platforms/build-tools for API 34)
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` set, or `local.properties` with `sdk.dir=...`

### Build APK
```bash
# From smart-pixel-app/
./gradlew assembleDebug
# or, if you have Gradle installed:
gradle assembleDebug
```

Output:
- `app/build/outputs/apk/debug/app-debug.apk`

## Install (ADB)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# optional: grant overlay permission via ADB
adb shell appops set com.zampediverse.smartpixel SYSTEM_ALERT_WINDOW allow
```

After installation, open the app, grant overlay permission, configure intensity/pattern/shift, and enable the filter (or use the Quick Settings tile).

## Notes

- Higher intensity turns off more pixels and increases visible grain; choose a balance that is comfortable.
- For burn-in prevention, combine with dark mode, moderate brightness, shorter screen timeout, and avoiding long-lived static UI elements.

## License

No license file is included by default. Add one before distributing if needed.
