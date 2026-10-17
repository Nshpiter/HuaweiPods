<div align="center">

<img src="docs/public/huaweipods-logo.png" width="120" height="120" style="border-radius: 24px;" alt="HuaweiPods Icon"/>

# HuaweiPods

**Huawei audio device integration for Xiaomi HyperOS**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-orange?style=flat-square)](https://hyperos.mi.com)

**English** | **[Simplified Chinese](README.md)**

[Documentation](https://huaweipods.npiter.de/) · [Support the project](docs/sponsor/index.md) · [Report an issue](https://github.com/Nshpiter/HuaweiPods/issues)

</div>

HuaweiPods is an Xposed module for Xiaomi HyperOS. It integrates supported Huawei audio devices with the system headset popup, Super Island, Fusion Device Center, and Bluetooth detail page.

Version 1.9.1 integrates the 19 models below, including FreeClip 2 Collector Edition, in one APK. This release adds FreeBuds 6, FreeClip 2 Collector Edition, and Android 14 / HyperOS 2 compatibility, alongside notification-name, persistent-island, and low-latency improvements.

## Supported models

| Model | Integrated capabilities |
| --- | --- |
| HUAWEI FreeBuds 3 | Battery, ANC on/off, nine-position spatial ANC dial, double-tap gestures, and system UI integration |
| HUAWEI FreeBuds 4E | Battery, ANC/off with Light/Balanced levels and readback, left/right double-tap and press-and-hold, wear detection, fixed swipe-volume guidance, three official sound presets, and official color images |
| HUAWEI FreeBuds 5 | Battery, ANC/off readback, Smart/Light/Balanced ANC levels, wear detection, four official sound presets, high-quality audio and low-latency auto-apply; gesture settings remain pending |
| HUAWEI FreeBuds 5i | Battery, transparency/ANC/off readback, Smart/Light/Balanced/Deep ANC levels, left/right double-tap, wear detection, four official sound presets, high-quality audio, low-latency auto-apply, and official color images; long-press and swipe settings remain pending |
| HUAWEI FreeBuds SE 4 ANC | Battery, transparency/ANC/off readback, Smart/Light/Balanced/Deep ANC levels (Smart requires device comparison), wind-noise reduction, four official sound presets, a 10-band custom EQ, low-latency auto-apply, and official color images; gesture settings remain pending |
| HUAWEI FreeBuds 6i | Battery, transparency/ANC/off readback, four ANC levels, voice transparency, double/triple-tap gestures, four official sound presets, a 10-band custom EQ, saved low-latency preference with reconnect auto-apply, Fusion Device Center synchronization, and dedicated images |
| HUAWEI FreeBuds 6 | Battery and official color images, ANC/off with three ANC levels and readback, selected sound presets, adaptive volume, wear detection, drop reminder, case sounds, low-latency readback and reconnect auto-apply; [protocol and validation scope](docs/FREEBUDS6_ADAPTATION.md) (Chinese) |
| HUAWEI FreeBuds Pro 3 | Battery, three-mode control and readback, four ANC levels, voice transparency, long-press/pinch/swipe gestures, and low-latency auto-apply |
| HUAWEI FreeBuds Pro 4 | Battery and ANC/off; no verified ANC state readback or gesture settings |
| HUAWEI FreeBuds Pro 5 | Battery, three-mode readback, four ANC levels, standard/voice/adaptive transparency, triple-tap/pinch/swipe-volume gestures, wear detection, adaptive volume, head-motion and voice controls, spatial audio, nine Yuezhang/scene/AI sound presets, a 10-band custom EQ, high-quality audio, low-latency auto-apply, dual-device connection, case-open sound, and ear-tip material |
| HUAWEI FreeBuds 7 | Battery and official images, three ANC levels with readback, spatial audio, double/triple-tap, pinch and swipe settings, wear detection, eight sound effects, a 10-band EQ, high-quality audio, low latency, and selected smart settings; native Settings and fusion-center controls ([validation scope](docs/FREEBUDS7_ADAPTATION.md), Chinese) |
| HUAWEI FreeBuds 7i | Battery, transparency/ANC/off readback, four ANC levels, double/triple-tap, long-press and swipe-volume gestures, wear detection, head-motion control, spatial audio, four sound presets, a 10-band custom EQ, high-quality audio, low-latency auto-apply, dual-device list management, and official color images |
| HUAWEI FreeClip | Left/right/case battery; no traditional ANC |
| HUAWEI FreeClip 2 | Battery, double/triple-tap and swipe gestures, spatial audio, four official sound presets, saved custom presets, separate nod/shake call actions, noisy-environment volume boost, low-latency auto-apply, and selected wearing/audio settings; no traditional ANC |
| HUAWEI FreeClip 2 Collector Edition | Separate product identity and official color images, sharing the integrated FreeClip 2 controls, with head-action and volume-boost readback; [protocol and validation scope](docs/FREECLIP2_COLLECTOR_ADAPTATION.md) (Chinese) |
| HUAWEI FreeArc | Left/right/case battery, double/triple-tap, press-and-hold and swipe gestures, five official sound presets, a 10-band custom EQ, and official color images; no traditional ANC |
| HUAWEI Eyewear (1st generation) | Left/right temple battery and system UI integration; no ANC |
| HUAWEI Eyewear 2 | Left/right temple battery, double-tap/swipe gestures, and low-latency auto-apply; no ANC |
| HUAWEI Eyewear 3 | Protocol-model identification, left/right temple battery, system eyewear classification, and official color images; no ANC |

The table lists currently integrated capabilities. Unlisted official features should not be assumed to work, and behavior can vary by earbud firmware, HyperOS version, and system component version.

## Features

- **Battery display** for earbuds, charging cases, or the left/right temples of supported eyewear.
- **Model-aware controls** for ANC, transparency, ANC levels, and gestures where verified protocol data is available.
- **ANC dial** for FreeBuds 3 spatial noise cancellation adjustment only.
- **System Bluetooth detail page** integration for battery and controls supported by the selected model.
- **Super Island / popup** status display and quick ANC controls.
- Independent switches for lock-screen headset notifications and all Super Island notifications.
- Notification taps can open the module popup, system settings, or Huawei Smart Audio; verified models can toggle low latency in the popup.
- Notifications and Super Island prefer the name saved in system Bluetooth settings and update when renamed while connected. Model identification still uses the original device information and address binding.
- An optional persistent battery island is off by default, updates with battery reports, and is removed on disconnect. System style also requires the persistent headset notification and ROM support for Super Island.
- **Fusion Device Center** headset display and transfer between paired devices, with an optional low-latency quick card on verified legacy hosts.
- **Manual model binding** by Bluetooth address when a device has been renamed or cannot be identified automatically.
- **First-run setup guide** and in-app GitHub release checks.
- **Standalone About page** for version details, updates, feedback, and community access, with full settings available from its top-right action.
- **Post-update scope restart prompt** after installing a newer APK.
- **Official model images** downloaded from Huawei's CDN after modern devices report an exact model and color identity over Bluetooth; manual and built-in images remain available as fallbacks.

## Requirements

- Xiaomi / Redmi device running HyperOS.
- Android 14+ (Android 14 / HyperOS 2 compatibility is available starting with 1.9.1).
- LSPosed API version >= 102 (the protocol-capture Debug build uses API 101).
- A paired device listed in the support table above.

## Usage

1. Install the HuaweiPods APK and follow the first-run guide to check LSPosed and the core scopes.
2. Enable the module in LSPosed.
3. Select the recommended scopes:
   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`
   - `com.huawei.smartaudio` (when installed, for FreeClip 2 spatial-audio and custom-EQ synchronization)
4. Reboot the phone, or restart the scoped apps from HuaweiPods.
5. Connect a supported device and view its integrated capabilities in HuaweiPods, Super Island, Fusion Device Center, or the system Bluetooth detail page. Modern models are identified from their protocol identity; if a renamed or legacy device is not identified, select its actual model once in HuaweiPods.

The release build no longer needs to install, run, or hook HUAWEI AI Life Audio for official images. Modern models provide the model and color identity over Bluetooth; legacy models can browse the verified Huawei color catalog in the image settings and ask the user to confirm once. Failures always fall back to cached or bundled images and never guess the default color.

The Fusion Device Center low-latency card is enabled only on verified legacy hosts and can be disabled in HuaweiPods settings; on HyperOS 4, use the module popup or detail page instead. Version 1.8.3 fixes a popup issue where a successful FreeBuds 6i low-latency write was not saved and the switch appeared off when reopened. Because the protocol has no reliable readback for this setting, the UI shows the last successful write that will be reapplied after reconnection rather than a live device-reported state. The effect on the earbuds still needs device testing.

For model-specific retesting and protocol capture, join QQ group `1022359908`.

Version 1.9.1 routes module low-latency writes through the Bluetooth host to avoid duplicate connections, prevents queued auto-enable operations from overriding manual changes, and filters unintended disable requests when leaving legacy Fusion details. If auto-apply was previously turned off by this issue, enable low latency once after updating. The Collector Edition reconnect fix still awaits device retesting; a successful write is not device-state confirmation.

## Development Notes

Internal package names, broadcast actions, configuration names, and the public app identity are unified as HuaweiPods.

## Credits

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z — the initial adaptation source for HuaweiPods.
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky — the original upstream OppoPods project.
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — original HyperOS headset integration ideas.
- [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) by melianmiko — Huawei earphone protocol reference.
- [HyperIsland](https://github.com/1812z/HyperIsland) by 1812z — interaction reference for the About page, update checks, and onboarding.
- [HyperLight](https://github.com/KiminonawaResa/HyperLight) by KiminonawaResa — HyperOS material hierarchy and motion reference.
- [Miuix](https://github.com/compose-miuix-ui/miuix) by YuKongA — HyperOS-style Compose UI components.

## License

[GPL-3.0](LICENSE)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Nshpiter/HuaweiPods&type=Date)](https://star-history.com/#Nshpiter/HuaweiPods&Date)
