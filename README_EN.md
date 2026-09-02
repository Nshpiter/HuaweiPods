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

The unified build supports the 15 models below in one APK. Model-specific test APKs are no longer distributed.

## Supported models

| Model | Status | Integrated capabilities |
| --- | --- | --- |
| HUAWEI FreeBuds 3 | Stable | Battery, ANC on/off, nine-position spatial ANC dial, double-tap gestures, and system UI integration |
| HUAWEI FreeBuds 4E | Extended support | Battery, ANC/off with Light/Balanced levels and readback, left/right double-tap and press-and-hold, wear detection, fixed swipe-volume guidance, three official sound presets, and official color images |
| HUAWEI FreeBuds 5 | Extended support | Battery, ANC/off readback, Smart/Light/Balanced ANC levels, wear detection, four official sound presets, high-quality audio and low-latency auto-apply; gesture settings remain pending |
| HUAWEI FreeBuds 5i | Extended support | Battery, transparency/ANC/off readback, Smart/Light/Balanced/Deep ANC levels, left/right double-tap, wear detection, four official sound presets, high-quality audio, low-latency auto-apply, and official color images; long-press and swipe settings remain pending |
| HUAWEI FreeBuds 6i | Extended support | Battery, transparency/ANC/off readback, four ANC levels, voice transparency, double/triple-tap gestures, four official sound presets, a 10-band custom EQ, low-latency auto-apply, Fusion Device Center synchronization, and dedicated images |
| HUAWEI FreeBuds Pro 3 | Extended support | Battery, three-mode control and readback, four ANC levels, voice transparency, long-press/pinch/swipe gestures, and low-latency auto-apply |
| HUAWEI FreeBuds Pro 4 | Basic support | Battery and ANC/off; no verified ANC state readback or gesture settings |
| HUAWEI FreeBuds Pro 5 | Extended support | Battery, three-mode readback, four ANC levels, standard/voice/adaptive transparency, triple-tap/pinch/swipe-volume gestures, wear detection, adaptive volume, head-motion and voice controls, spatial audio, nine Yuezhang/scene/AI sound presets, a 10-band custom EQ, high-quality audio, low-latency auto-apply, dual-device connection, case-open sound, and ear-tip material |
| HUAWEI FreeBuds 7i | Extended support | Battery, transparency/ANC/off readback, four ANC levels, double/triple-tap, long-press and swipe-volume gestures, wear detection, head-motion control, spatial audio, four sound presets, a 10-band custom EQ, high-quality audio, low-latency auto-apply, dual-device list management, and official color images |
| HUAWEI FreeClip | Basic support | Left/right/case battery; no traditional ANC |
| HUAWEI FreeClip 2 | Extended support | Battery, double/triple-tap and swipe gestures, spatial audio, four official sound presets, saved custom presets, low-latency auto-apply, and selected wearing/audio settings; no traditional ANC |
| HUAWEI FreeArc | Extended support | Left/right/case battery, double/triple-tap, press-and-hold and swipe gestures, five official sound presets, a 10-band custom EQ, and official color images; no traditional ANC |
| HUAWEI Eyewear (1st generation) | Basic support | Left/right temple battery and system UI integration; no ANC |
| HUAWEI Eyewear 2 | Basic support | Left/right temple battery, double-tap/swipe gestures, and low-latency auto-apply; no ANC |
| HUAWEI Eyewear 3 | Basic support | Protocol-model identification, left/right temple battery, system eyewear classification, and official color images; no ANC |

“Stable” means the model has received substantial device testing. “Extended support” includes additional protocol controls, while “Basic support” covers identification, battery, or core controls. Models not marked stable still benefit from real-device regression testing, and unlisted official features should not be assumed to work.

## Features

- **Battery display** for earbuds, charging cases, or the left/right temples of supported eyewear.
- **Model-aware controls** for ANC, transparency, ANC levels, and gestures where verified protocol data is available.
- **ANC dial** for FreeBuds 3 spatial noise cancellation adjustment only.
- **System Bluetooth detail page** integration for battery and controls supported by the selected model.
- **Super Island / popup** status display and quick ANC controls.
- Independent switches for lock-screen headset notifications and all Super Island notifications.
- Notification taps can open the module popup, system settings, or Huawei Smart Audio; verified models can toggle low latency in the popup.
- **Fusion Device Center** headset display and transfer between paired devices, with an optional low-latency quick card on verified legacy hosts.
- **Manual model binding** by Bluetooth address when a device has been renamed or cannot be identified automatically.
- **First-run setup guide** and in-app GitHub release checks.
- **Standalone About page** for version details, updates, feedback, and community access, with full settings available from its top-right action.
- **Post-update scope restart prompt** after installing a newer APK.
- **Official model images** downloaded from Huawei's CDN after modern devices report an exact model and color identity over Bluetooth; manual and built-in images remain available as fallbacks.

## Requirements

- Xiaomi / Redmi device running HyperOS.
- Android 15+.
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

The Fusion Device Center low-latency card is enabled only on verified legacy hosts and can be disabled in HuaweiPods settings; on HyperOS 4, use the module popup or detail page instead. Because the verified protocol has no reliable readback for this setting, the UI shows the last successful write that will be reapplied after reconnection rather than a live device-reported state.

For model-specific retesting and protocol capture, join QQ group `1022359908`.

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
