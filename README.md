<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HuaweiPods Icon"/>

# HuaweiPods

**为小米 HyperOS 设备适配 Huawei FreeBuds**

[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-orange?style=flat-square)](https://hyperos.mi.com)

**简体中文** | **[English](README_EN.md)**

</div>

HuaweiPods 是一个面向小米 HyperOS 的 Xposed 模块，用于把 Huawei FreeBuds 接入系统耳机弹窗、超级岛、融合设备中心和蓝牙详情页。

当前主要围绕 **Huawei FreeBuds 3** 适配：电量显示、主动降噪开关、降噪空间圆盘调节，以及融合设备中心内的耳机显示和流转能力。

## 交流与反馈

- QQ 群：`1022359908`，用于使用交流、待适配型号登记、测试招募和采集协调。
- 可复现的 Bug、功能请求和充分检查、脱敏后的普通采集包也可以提交到 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues)，便于长期跟踪。

## 功能

- **电量显示**：显示左耳、右耳和充电盒电量。
- **主动降噪**：支持降噪 / 关闭两态切换。
- **降噪圆盘**：支持 FreeBuds 3 的空间降噪方向调节。
- **系统蓝牙详情页**：在系统设置中显示电量、降噪控制和圆盘调节。
- **超级岛 / 弹窗**：显示耳机状态，并提供快速降噪控制。
- **融合设备中心**：显示 FreeBuds，并支持在已配对设备间流转。

## 正式版系统要求

- 小米 / Redmi 设备，运行 HyperOS。
- Android 15+。
- LSPosed API 版本 >= 101。
- 已配对 Huawei FreeBuds 3。

## 正式版使用

1. 安装 HuaweiPods APK。
2. 在 LSPosed 中启用模块。
3. 勾选推荐作用域：
   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`
4. 重启手机，或在应用内重启相关作用域。
5. 连接 FreeBuds 3 后，在 HuaweiPods、超级岛、融合设备中心或系统蓝牙详情页中控制耳机。

## Debug 采集版

Debug 版面向尚未适配的 FreeBuds、FreeClip、FreeLace 等华为蓝牙耳机，需要安装平时管理该耳机的智慧生活或智慧音频，并在 LSPosed 中额外勾选对应官方 App。它只负责引导和记录官方 App 与耳机的蓝牙交互，不代表对应型号已经可以在正式版中安全控制。

采集前先搜索 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues)：已有对应型号时填写 Issue 编号并在原 Issue 跟进；没有时可以留空，导出并检查采集包后再新建 Issue。提交时请说明耳机显示名称、手机与系统版本、官方 App 版本、`protocol_event_count` 以及实际操作结果；事件数为 `0` 时只反馈环境信息，不要把仅含向导标记的 ZIP 当作有效样本。

完成导出后，请在 LSPosed 中取消智慧生活 / 智慧音频作用域并重启相关进程或手机，然后换回同签名 Release 版或卸载 Debug 版；若附加过 HCI 日志，请清除 Debug 版应用数据，删除其私有副本。

## 开发说明

项目内部包名、广播 action、配置命名和对外应用身份已统一为 HuaweiPods。

项目区分两个构建变体：`release` 是不接触华为官方 App 的正式版；`debug` 是面向所有待适配华为蓝牙耳机的通用协议采集版。Debug 会读取当前已连接的蓝牙音频设备名称供测试者确认，并以统一功能清单引导采集；这不代表能够自动识别设备能力。两者使用相同应用 ID，不能同时安装。

```bash
./gradlew :app:assembleRelease
./gradlew :app:assembleDebug
```

完整的测试者操作流程、隐私边界和 HCI 兜底方案见 [华为耳机通用协议采集指南](docs/DEBUG_CAPTURE_GUIDE.md)。未取得并复核对应型号的真机数据前，任何未知型号都不会复用 FreeBuds 3 的写指令。

## 致谢

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z — HuaweiPods 直接基于该分支适配开发。
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky — OppoPods 上游原项目。
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — 原始 HyperOS 耳机集成思路。
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 风格 Compose UI 组件。

## 许可证

[GPL-3.0](LICENSE)
