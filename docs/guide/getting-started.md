---
title: 快速开始
description: 在小米 HyperOS 上安装、启用并检查 HuaweiPods。
---

# 快速开始

HuaweiPods 是面向小米 HyperOS 的 Xposed 模块。目前只有 **HUAWEI FreeBuds 3** 的控制协议经过真机验证。

::: warning 安装前确认
HuaweiPods 需要正常工作的 LSPosed 环境，并会修改系统蓝牙相关进程的行为。请先确认你了解 Xposed 模块的启用、停用与恢复方式。
:::

## 环境要求

- 小米或 Redmi 设备，运行 HyperOS；
- Android 15 或更高版本；
- LSPosed API 版本 101 或更高；
- 已在系统蓝牙中配对 HUAWEI FreeBuds 3。

## 1. 安装 HuaweiPods

从 [GitHub Releases](https://github.com/Nshpiter/HuaweiPods/releases) 下载已发布的 APK，正常安装后打开 HuaweiPods。

## 2. 启用 LSPosed 作用域

在 LSPosed 中启用 HuaweiPods，并勾选以下作用域：

```text
com.android.bluetooth
com.android.settings
com.milink.service
com.xiaomi.bluetooth
```

## 3. 重启并连接耳机

首次启用时建议完整重启手机，确保蓝牙、系统设置、融合设备中心等进程都加载模块。之后连接已配对的 FreeBuds 3。

你可以依次检查：

1. HuaweiPods 首页是否显示模块已激活；
2. 左耳、右耳和充电盒电量是否更新；
3. 系统蓝牙详情页是否出现耳机状态与降噪控制；
4. 重新连接耳机后，超级岛或系统弹窗是否出现；
5. 融合设备中心是否显示耳机。

## 没有生效时

按下面顺序排查，通常不需要反复卸载：

1. 确认 LSPosed 中 HuaweiPods 已启用，且 API 版本满足要求；
2. 核对四个作用域是否全部勾选；
3. 在 HuaweiPods 内重启相关作用域，或直接重启手机；
4. 在系统蓝牙中断开再连接耳机；
5. 确认设备名称确实是受支持的 FreeBuds 3，而不是名称相似的其他型号。

仍无法复现时，可以到 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues) 提交手机型号、HyperOS 版本、LSPosed 版本、HuaweiPods 版本和复现步骤。

## 更新或卸载

- 同签名的新版本可以直接覆盖安装；
- 如果系统提示签名不一致，请改用同一发布渠道提供的版本；
- 停用或卸载前，先在 LSPosed 中取消 HuaweiPods 作用域，再重启相关进程或手机。
