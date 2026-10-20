# HuaweiPods ColorOS

The ColorOS distribution of Nshpiter/HuaweiPods, currently **1.9.1-coloros.1-rc1**.

Built on HuaweiPods 1.9.1 (`1f013d2`), with ColorOS integration, original FreeClip controls, and lifecycle recovery ported from [Artifical0/HuaweiPods-ColorOS](https://github.com/Artifical0/HuaweiPods-ColorOS) (`061fad7`). FreeBuds 6, FreeClip 2 Collector Edition, shared control connections, and manual low-latency priority are retained.

This is an unpublished development candidate. The current port requires real-device verification; unit tests and firmware analysis do not establish hardware compatibility.

## Requirements and setup

- Android 15+, targeting OPPO / OnePlus / realme ColorOS 16 / 17.
- Release uses LSPosed API 102 with API 101 runtime fallback; hot reload requires API 102. Debug capture builds use API 101.
- Independent application ID: `moe.chenxy.huaweipods.coloros`.
- Core scope: `com.android.bluetooth`. Enable `com.heytap.accessory` for the official popup and `com.heytap.mydevices` for My Devices.
- Optional: `com.oplus.wirelesssettings` and, when installed, `com.huawei.smartaudio`.
- Allow Bluetooth / notification permissions, auto-start, background activity, and ColorOS live notifications.

See the [setup guide](docs/guide/getting-started.md) and [port / verification record](docs/coloros-port-20261020.md) (Chinese).

## Integrated features

Official Quick Connect popup, system battery reporting, My Devices controls, Bluetooth settings entry, Android 16+ live alerts, bounded background recovery, dual A2DP/HFP session tracking, sender validation, original FreeClip controls, and the 1.9.1 device/protocol additions. HyperOS-specific hooks are excluded from this distribution.

Private host compatibility depends on firmware. The reference ColorOS 17 adaptation includes static analysis and still requires device testing.

## Build

JDK 23, Android SDK Platform 37.0, Build Tools 36.0.0:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest
./gradlew :app:assembleDebug
```

Local Release builds are unsigned; Debug builds are for testing. CI signs published packages with this repository's stable key. ColorOS releases never replace repository Latest. The updater filters ColorOS tags and excludes prereleases for stable installations.

## Credits and license

ColorOS integration is ported from [Artifical0](https://github.com/Artifical0/HuaweiPods-ColorOS). Protocol support comes from the [HuaweiPods main branch](https://github.com/Nshpiter/HuaweiPods) and its credited upstream projects. [GPL-3.0](LICENSE).
