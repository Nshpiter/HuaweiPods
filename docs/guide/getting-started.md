---
title: 快速开始
description: 在小米 HyperOS 上安装、启用并检查 HuaweiPods。
---

# 快速开始

HuaweiPods 是面向小米 HyperOS 的 Xposed 模块。从 1.2.0 起项目使用统一 APK，当前已接入 14 个型号；各型号的实机验证程度和可用控制并不相同，请先查看[支持状态](../support/index.md)。

::: warning 安装前确认
HuaweiPods 需要正常工作的 LSPosed 环境，并会修改系统蓝牙相关进程的行为。请先确认你了解 Xposed 模块的启用、停用与恢复方式。
:::

## 环境要求

- 小米或 Redmi 设备，运行 HyperOS；
- Android 15 或更高版本；
- LSPosed API 版本 101 或更高；
- 已在系统蓝牙中配对[支持列表](../support/index.md)中的设备。

## 1. 安装 HuaweiPods

从 [GitHub Releases](https://github.com/Nshpiter/HuaweiPods/releases) 下载正式 APK，正常安装后打开 HuaweiPods。1.2.0 及以上版本无需寻找机型专用包。首次安装会显示简短引导，用于检查 LSPosed 服务和两个核心蓝牙作用域；缺少项目时仍可继续，之后再到 LSPosed 补齐。

## 2. 启用 LSPosed 作用域

在 LSPosed 中启用 HuaweiPods，并勾选以下作用域：

```text
com.android.bluetooth
com.android.settings
com.milink.service
com.xiaomi.bluetooth
com.huawei.smartaudio
```

## 3. 重启并连接耳机

启用后可先在 HuaweiPods 中重启相关作用域；若系统组件没有重新加载模块，再完整重启手机。之后连接已配对的受支持设备。

模块会优先读取耳机协议中的设备标识，并按蓝牙地址记住已确认的型号。首次连接、耳机被改名或旧型号无法返回设备标识时，仍可在设备选择页选择一次真实型号；该选择只影响当前地址，不会把同名设备的控制协议混用。

首页右上角提供“使用文档”和“赞助支持”入口。“使用文档”会在 App 内安全加载 [HuaweiPods 官网](https://huaweipods.npiter.de/)：站内页面留在 App，GitHub 等外部链接才交给系统浏览器；文档内容会随官网持续更新。

正式版获取图片时不依赖、也不会启动华为智慧音频。支持现代 DeviceInfo 的耳机会由系统蓝牙进程直接读取 `modelId/subModelId`，再从华为官方 CDN 下载并严格校验当前配色图片；FreeBuds 3 等旧协议型号可在 HuaweiPods 的图片设置中检索官方配色并手动确认一次。身份不完整、网络不可用或校验失败时会继续使用已有缓存或内置图，绝不会按默认配色猜测。仅当智慧音频本来就在运行时，HuaweiPods 会使用一个被动桥接 Hook 同步 FreeClip 2 空间音频；它不会主动启动或保活智慧音频。

你可以依次检查：

1. HuaweiPods 首页是否显示模块已激活；
2. 左右耳与充电盒电量，或眼镜左右镜腿电量是否更新；
3. 系统蓝牙详情页是否出现该型号支持的状态与控制；
4. 重新连接耳机后，超级岛或系统弹窗是否出现；
5. 融合设备中心是否显示耳机。

FreeClip、FreeClip 2 和两代 Eyewear 不提供传统主动降噪，看不到降噪入口是正常现象。标记为“待复测”的功能如果与官方 App 表现不一致，请保留复现步骤并反馈。

## 没有生效时

按下面顺序排查，通常不需要反复卸载：

1. 确认 LSPosed 中 HuaweiPods 已启用，且 API 版本满足要求；
2. 核对五个作用域是否全部勾选；未安装华为智慧音频时可忽略对应项；
3. 在 HuaweiPods 内重启相关作用域；仍无效时再重启手机；
4. 在系统蓝牙中断开再连接耳机；
5. 在设备选择页确认当前蓝牙地址绑定的是实际型号；
6. 对照[支持状态](../support/index.md)，确认该入口确实属于当前机型。

仍无法复现时，可以到 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues) 提交耳机型号、手机型号、HyperOS 版本、LSPosed 版本、HuaweiPods 版本和复现步骤，也可以加入 QQ 群 `1022359908` 参与复测。

## 更新或卸载

- 同签名的新版本可以直接覆盖安装；
- 可在“设置 → 关于 HuaweiPods”中手动检查 GitHub 更新，也可关闭启动时自动检查；
- 覆盖安装完成后，HuaweiPods 会提示选择并重启作用域，使新版 Hook 生效；
- 如果系统提示签名不一致，请改用同一发布渠道提供的版本；
- 停用或卸载前，先在 LSPosed 中取消 HuaweiPods 作用域，再重启相关进程或手机。
