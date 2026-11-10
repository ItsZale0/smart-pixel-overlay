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
- **Shizuku support** to grant overlay permission and control settings via ADB/shell privileges (when Shizuku is running)
- **Native ROM Smart Pixels mode (root/Shizuku, when detected)**: if the ROM exposes compatible Smart Pixel system settings, the app can toggle/control the native implementation instead of the overlay (best-effort detection on ROMs that use common `smart_pixels_*` keys)
- **Battery optimized overlay**: drawing throttled to 20fps max, drawing disabled when overlay not needed, shift timer only active when interval > 0
- **Localization**: English by default, follows system language (Italian translation included)

## Battery Optimization

The app is designed to minimize battery impact:

### Overlay Mode (default)
- **Drawing throttling**: Redraws limited to max 20fps (50ms minimum interval)
- **On-demand drawing**: Overlay only draws when actually visible and enabled
- **Smart shift timer**: Only runs when shift interval > 0; disabled otherwise
- **Low priority notification**: Foreground service uses `IMPORTANCE_LOW` channel
- **Cleanup on detach**: Bitmap resources recycled when overlay removed
- **Efficient bitmap patterns**: Pre-rendered tile patterns cached and reused

### Native ROM Mode (when available)
- **Near-zero battery impact**: Uses system-level Smart Pixel settings
- **No overlay rendering**: Completely bypasses app overlay
- **System handles scheduling**: ROM manages pixel shifting efficiently

### Expected Battery Usage
| Mode | Estimated Impact |
|------|------------------|
| Overlay (intensity 25%, shift 30s) | ~1-3% per hour |
| Overlay (intensity 50%, shift 15s) | ~3-5% per hour |
| Native ROM Mode | ~0.5-1% per hour |
| Disabled | 0% |

### Tips to Maximize Battery Life
1. **Use Native Mode** when available (Settings → "Usa Smart Pixel nativo ROM")
2. **Lower intensity** (25% is usually sufficient for burn-in protection)
3. **Increase shift interval** (30s or 60s instead of 15s)
4. **Enable "Battery Saver mode"** to only activate when power save is on
5. **Disable when not needed** via Quick Settings tile

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
- Some Android/ROM configurations may restrict overlay opacity for non-touchable system alert windows, which can reduce the "black pixel" effect compared to ROM-integrated implementations.
- Root/Shizuku support is for convenience (e.g. granting permissions/automation), not deep system integration. Shizuku cannot bypass WindowManager/compositor limitations or make this a true ROM-level Smart Pixel.

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
