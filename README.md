# HuaweiPods ColorOS

Nshpiter/HuaweiPods 的 ColorOS 发行分支，当前测试版本 **1.9.1-rc2**。

以 HuaweiPods 1.9.1 为协议底座，移植 [Artifical0/HuaweiPods-ColorOS](https://github.com/Artifical0/HuaweiPods-ColorOS) 的 ColorOS 接入、第一代 FreeClip 控制和后台恢复逻辑。保留 FreeBuds 6、FreeClip 2 典藏版、共享控制通道及手动低时延优先策略。

已发布 [ColorOS 1.9.1-rc2 预发布版](https://github.com/Nshpiter/HuaweiPods/releases/tag/23-1.9.1-coloros.1-rc2)，[下载 APK](https://github.com/Nshpiter/HuaweiPods/releases/download/23-1.9.1-coloros.1-rc2/HuaweiPods-ColorOS-1.9.1-rc2.apk)。本轮移植需要在 ColorOS 真机完成验证，不能将单元测试或固件静态分析视为实机通过。

## 安装与系统集成

- Android 15+，目标为 OPPO／OnePlus／realme 的 ColorOS 16／17。
- 正式变体按 LSPosed API 102 构建并保留 API 101 运行时回退；API 102 支持热重载。Debug 协议采集版使用 API 101。
- 独立包名 `moe.chenxy.huaweipods.coloros`，可与 HyperOS 版共存。它不能覆盖安装 Artifical0 的独立包，也不会读取原版的应用数据。
- 核心作用域为 `com.android.bluetooth`；官方连接弹窗另需 `com.heytap.accessory`，“我的设备”另需 `com.heytap.mydevices`。
- 可选 `com.oplus.wirelesssettings` 提供系统蓝牙详情入口；已安装智慧音频时可选 `com.huawei.smartaudio` 提供已接入的同步能力。

完整步骤见 [快速开始](docs/guide/getting-started.md)。

## 本版接入

- ColorOS 官方开盒／连接弹窗，以及机型和配色图片。
- 左右耳／盒电量写入 ColorOS 电量通道，私有接口缺失时回退 Android 标准电量。
- “我的设备”电量、模块入口和按机型开放的快捷控制；系统蓝牙详情入口。
- Android 16+ 流体云，后台唤醒、处理确认、有限重试和断连清理。
- A2DP／HFP 任一连接时保留会话，两个音频 Profile 明确断开才清理。
- 跨进程发送方校验，兼容 1.9.1 的共享控制和 FreeBuds 7 空间音频动作。
- 连接期间后台刷新电量、亮屏刷新，以及 20%／10% 低电量提醒。

本分支不加载 HyperOS 的私有 Hook、融合设备中心或超级岛。系统私有宿主会随 OTA 变化；不匹配的能力可能不可用。ColorOS 17 的参考适配包含固件静态分析，仍需实机验证。

## 支持型号

以下列出已接入的协议能力；ColorOS 上的实际表现仍需逐型号验证。

| 型号 | 当前能力 |
| --- | --- |
| HUAWEI FreeBuds 3 | 电量、降噪开关、9 档降噪空间方向、双击手势与系统界面集成 |
| HUAWEI FreeBuds 4E | 电量、降噪 / 关闭与轻度 / 均衡两档降噪、状态回读、左右耳双击与按住、佩戴检测、轻滑音量说明、3 种官方音效和官方配色图片 |
| HUAWEI FreeBuds 5 | 电量、降噪 / 关闭与状态回读、智慧动态 / 轻度 / 均衡三档降噪、佩戴检测、4 种官方音效、高清音质与低时延自动保持；手势设置待补充 |
| HUAWEI FreeBuds 5i | 电量、通透 / 降噪 / 关闭与状态回读、智慧动态 / 轻度 / 均衡 / 深度四档降噪、左右耳双击、佩戴检测、4 种官方音效、高清音质、低时延自动保持与官方配色图片；长按和滑动设置待补充 |
| HUAWEI FreeBuds SE 4 ANC | 电量、通透 / 降噪 / 关闭与状态回读、智慧动态 / 轻度 / 均衡 / 深度四档降噪（智慧动态待实机对照）、防风噪、4 种官方音效、10 段自定义均衡器、低时延自动保持与官方配色图片；手势设置待补充 |
| HUAWEI FreeBuds 6i | 电量、通透 / 降噪 / 关闭与状态回读、4 档降噪、通透人声模式、双击 / 三击手势、4 种官方音效、10 段自定义均衡器、低时延设置保存与重连自动应用与专属图片 |
| HUAWEI FreeBuds 6 | 电量与官方配色图片、降噪 / 关闭及 3 档降噪回读、部分音效、自适应音量、佩戴检测、掉落提醒、盒提示音、低时延回读与重连自动应用；[协议与测试范围](docs/FREEBUDS6_ADAPTATION.md) |
| HUAWEI FreeBuds Pro 3 | 电量、三态控制与状态回读、4 档降噪、通透人声模式、长按 / 捏合 / 滑动手势与低时延自动保持 |
| HUAWEI FreeBuds Pro 4 | 电量、降噪 / 关闭两态控制；暂不支持降噪状态回读与手势设置 |
| HUAWEI FreeBuds Pro 5 | 电量、三态回读、4 档降噪、通透 / 透传人声 / 智慧动态透传、三击 / 按捏 / 轻滑音量、佩戴检测、自适应音量、头动与语音控制、空间音频、9 种悦彰 / 场景 / AI 音效、10 段自定义均衡器、高清音质、低时延自动保持、双设备连接、开盒提示音与耳塞材质 |
| HUAWEI FreeBuds 7 | 电量与官方图片、三态回读与 3 档降噪、空间音频、双击/三击/捏合/滑动设置、佩戴检测、8 种音效与 10 段均衡器、高清音质、低时延及部分智慧设置；[实机验证范围](docs/FREEBUDS7_ADAPTATION.md) |
| HUAWEI FreeBuds 7i | 电量、通透 / 降噪 / 关闭与状态回读、4 档降噪、双击 / 三击 / 长按 / 滑动音量、佩戴检测、头动控制、空间音频、4 种官方音效、10 段自定义均衡器、高清音质、低时延自动保持、双设备列表管理与官方配色图片 |
| HUAWEI FreeClip | 左右耳与充电盒电量、双击／三击、佩戴检测、左右耳自适应、掉落提醒、4 种音效与低时延；不提供传统主动降噪或自定义 EQ |
| HUAWEI FreeClip 2 | 电量、双击 / 三击 / 滑动手势、空间音频、4 种官方音效、已保存的自定义音效、点头 / 摇头来电动作设置、嘈杂环境大音量、低时延自动保持及部分佩戴和音频设置；不支持传统主动降噪 |
| HUAWEI FreeClip 2 典藏版 | 独立产品身份与官方配色图片，复用 FreeClip 2 电量、手势、空间音频、音效及已接入设置，新增头动动作与大音量状态回读；[协议与测试范围](docs/FREECLIP2_COLLECTOR_ADAPTATION.md) |
| HUAWEI FreeArc | 左右耳与充电盒电量、双击 / 三击 / 按住 / 滑动手势、5 种官方音效、10 段自定义均衡器与官方配色图片；不支持传统主动降噪 |
| 华为智能眼镜（第一代） | 左右镜腿电量与系统界面集成；不提供主动降噪 |
| HUAWEI Eyewear 2 | 左右镜腿电量、双击 / 滑动手势与低时延自动保持；不提供主动降噪 |
| HUAWEI Eyewear 3 | 协议型号识别、左右镜腿电量、系统眼镜分类与官方配色图片；不提供主动降噪 |

## 构建

需要 JDK 23、Android SDK Platform 37.0 和 Build Tools 36.0.0。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest
./gradlew :app:assembleDebug
```

本地 Release 默认不签名；Debug 包用于开发测试。发布包由 CI 使用本仓库的固定签名构建，不能把 Debug 包当作正式签名升级包。

## 发布与更新

- Release 标题为 `HuaweiPods ColorOS <上游版本>[-rcN]`，签名 APK 为 `HuaweiPods-ColorOS-<上游版本>[-rcN].apk`，例如 `HuaweiPods-ColorOS-1.9.1-rc2.apk`。
- 应用内部版本保留 `<上游版本>-coloros.<修订号>[-rcN]`（当前为 `1.9.1-coloros.1-rc2`）；Git 标签保留 `<versionCode>-<versionName>`，用于更新渠道识别。
- 当前试用版标签为 `23-1.9.1-coloros.1-rc2`，按预发布渠道发布。
- ColorOS 工作流只监听 `coloros` 分支与 ColorOS 标签，所有 ColorOS Release 均不设置为仓库 Latest。
- 应用从本仓库发行列表筛选 ColorOS 版本；稳定版忽略预发布，RC／Beta 版可检查同渠道预发布。不会退回仓库通用 Latest。
- 具体移植范围与待测项见 [开发记录](docs/coloros-port-20261020.md)。

## 致谢与许可

- [HuaweiPods ColorOS · Artifical0](https://github.com/Artifical0/HuaweiPods-ColorOS)：ColorOS 集成、FreeClip 1 控制及后台恢复的移植来源（`061fad7`）。
- [HuaweiPods · Nshpiter](https://github.com/Nshpiter/HuaweiPods)：本项目主线与 1.9.1 协议底座（`1f013d2`）。
- [OppoPods · 1812z](https://github.com/1812z/OppoPods)、[OppoPods · Leaf-lsgtky](https://github.com/Leaf-lsgtky/OppoPods)、[OpenFreebuds](https://github.com/melianmiko/OpenFreebuds) 及主线致谢的其他项目。

遵循 [GPL-3.0](LICENSE)，保留源文件中的作者与许可信息。
