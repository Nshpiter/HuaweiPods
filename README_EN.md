<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HuaweiPods Icon"/>

# HuaweiPods

**Huawei FreeBuds integration for Xiaomi HyperOS devices**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-orange?style=flat-square)](https://hyperos.mi.com)

**English** | **[Simplified Chinese](README.md)**

</div>

HuaweiPods is an Xposed module for Xiaomi HyperOS. It integrates Huawei FreeBuds with the system headset popup, Super Island, Fusion Device Center, and Bluetooth detail page.

The current adaptation focuses on **Huawei FreeBuds 3**: battery display, ANC on/off, spatial ANC dial control, and headset display / transfer in Fusion Device Center.

## Features

- **Battery display** for the left earbud, right earbud, and charging case.
- **ANC control** with noise cancellation and off states.
- **ANC dial** for FreeBuds 3 spatial noise cancellation adjustment.
- **System Bluetooth detail page** integration for battery, ANC, and dial controls.
- **Super Island / popup** status display and quick ANC controls.
- **Fusion Device Center** headset display and transfer between paired devices.

## Requirements

- Xiaomi / Redmi device running HyperOS.
- Android 15+.
- LSPosed API version >= 101.
- Paired Huawei FreeBuds 3.

## Usage

1. Install the HuaweiPods APK.
2. Enable the module in LSPosed.
3. Select the recommended scopes:
   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`
4. Reboot the phone, or restart the scoped apps from HuaweiPods.
5. Connect FreeBuds 3 and control it from HuaweiPods, Super Island, Fusion Device Center, or the system Bluetooth detail page.

## Development Notes

Internal package names, broadcast actions, configuration names, and the public app identity are unified as HuaweiPods.

## Credits

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z — the fork HuaweiPods was directly adapted from.
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky — the original upstream OppoPods project.
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — original HyperOS headset integration ideas.
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS-style Compose UI components.

## License

[GPL-3.0](LICENSE)
