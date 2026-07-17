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

## 功能

- **电量显示**：显示左耳、右耳和充电盒电量。
- **主动降噪**：支持降噪 / 关闭两态切换。
- **降噪圆盘**：支持 FreeBuds 3 的空间降噪方向调节。
- **系统蓝牙详情页**：在系统设置中显示电量、降噪控制和圆盘调节。
- **超级岛 / 弹窗**：显示耳机状态，并提供快速降噪控制。
- **融合设备中心**：显示 FreeBuds，并支持在已配对设备间流转。

## 系统要求

- 小米 / Redmi 设备，运行 HyperOS。
- Android 15+。
- LSPosed API 版本 >= 101。
- 已配对 Huawei FreeBuds 3。

## 使用

1. 安装 HuaweiPods APK。
2. 在 LSPosed 中启用模块。
3. 勾选推荐作用域：
   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`
4. 重启手机，或在应用内重启相关作用域。
5. 连接 FreeBuds 3 后，在 HuaweiPods、超级岛、融合设备中心或系统蓝牙详情页中控制耳机。

## 开发说明

项目内部包名、广播 action、配置命名和对外应用身份已统一为 HuaweiPods。

## 致谢

- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky — HuaweiPods 基于该项目适配演进而来。
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — 原始 HyperOS 耳机集成思路。
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 风格 Compose UI 组件。

## 许可证

[GPL-3.0](LICENSE)
