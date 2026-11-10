# Smart Pixel (Overlay App for Android)

Smart Pixel is an Android overlay app that replicates the **Smart Pixel** feature found in many custom ROMs (such as LineageOS-based ROMs, crDroid, and Project Infinity X). It draws a configurable grid mask over the screen to turn off a percentage of pixels (rendered as black), helping reduce **OLED/AMOLED burn-in** and potentially lower power usage.

> **Disclaimer:** This project is **vibecoded** (AI-assisted/"vibe coded") and provided as-is. Review, test, and use at your own risk.

## Why Smart Pixel?

On OLED/AMOLED displays, black pixels are effectively turned off. A Smart Pixel-style grid can:
- reduce uneven wear on static UI areas (burn-in mitigation),
- slightly reduce power draw depending on intensity and content,
- provide a configurable compromise between image quality and protection.

## Features

- **Enable/disable** Smart Pixel filter
- **Intensity** control (percentage of pixels turned off)
- **Patterns**: 2x2, 3x3, 4x4, 6x6 grid, diagonal, checkerboard
- **Pixel shift interval**: off, 15s, 30s, 1m, 2m, 5m, 10m
- **Enable on battery saver** (optional)
- **Quick Settings tile** for fast toggling
- **Start on boot** (when enabled and overlay permission is granted)
- **Optional root helper** to grant overlay permission via `su`/`appops`

## How It Works

The app runs a foreground service and displays a full-screen, non-touchable overlay (`TYPE_APPLICATION_OVERLAY`) with a repeating mask pattern. Selected cells are drawn black; on OLED panels those pixels are effectively off.

## Project Status & Disclaimer

- **Vibecoded project**: generated and iterated with AI assistance.
- **No warranty**: provided "AS IS" without warranty of any kind (see [LICENSE](LICENSE)).
- **Not a ROM/system integration**: this is an app overlay, not a framework/SystemUI patch.
- Test on your device/emulator before relying on it.

## Limitations (vs ROM-integrated Smart Pixel)

- Not integrated into system Settings/SystemUI (standalone app UI).
- Uses an app overlay, not a native display pipeline/SystemUI filter layer.
- May not appear over some protected/secure surfaces due to Android restrictions.
- Root/Shizuku support is for convenience (e.g. granting permissions), not deep system integration.

## Requirements

- Android 8.0+ (API 26+)
- **Display over other apps** permission
- OLED/AMOLED display recommended for real effect

## Install

### Option 1: Install APK
1. Download the APK from [Releases](../../releases) or use `SmartPixel-debug.apk` from this repository.
2. Install it on your device (enable install from unknown sources if needed).
3. Open the app and grant overlay permission.
4. Enable Smart Pixel and configure intensity/pattern/shift.

### Option 2: ADB
```bash
adb install -r SmartPixel-debug.apk
# optional: grant overlay permission via ADB
adb shell appops set com.zampediverse.smartpixel SYSTEM_ALERT_WINDOW allow
```

## Build from Source

### Prerequisites
- JDK 17
- Android SDK (API 34 platform/build-tools)
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` configured, or `local.properties` with `sdk.dir=...`

### Debug build
```bash
gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

### Release build
```bash
gradle assembleRelease
# output: app/build/outputs/apk/release/app-release.apk (if signing configured)
```

## Repository Notes

- `smart-pixel-app/` project root in this repository context contains source, Gradle config, and a prebuilt debug APK.
- `local.properties` is machine-specific and should not be committed.

## SEO / Keywords

Android, Smart Pixel, SmartPixels, OLED, AMOLED, burn-in protection, screen burn-in, pixel shift, display overlay, no root, root optional, Quick Settings tile, battery saver, custom ROM feature port.

## License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for details.
