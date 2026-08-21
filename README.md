<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" alt="HuaweiPods Icon"/>

# HuaweiPods

**让华为耳机接入小米 HyperOS 的系统体验**

[![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![HyperOS](https://img.shields.io/badge/ROM-HyperOS-FF6900?style=flat-square)](https://hyperos.mi.com/)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-6F42C1?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![License](https://img.shields.io/github/license/Nshpiter/HuaweiPods?style=flat-square)](LICENSE)

[下载安装](https://github.com/Nshpiter/HuaweiPods/releases) ·
[使用文档](https://huaweipods.npiter.de/) ·
[赞助支持](docs/sponsor/index.md) ·
[问题反馈](https://github.com/Nshpiter/HuaweiPods/issues) ·
QQ群 `1022359908`

**简体中文** · **[English](README_EN.md)**

</div>

HuaweiPods 是一个面向小米 / Redmi HyperOS 设备的 Xposed 模块，将华为耳机接入系统蓝牙详情页、连接弹窗、超级岛与融合设备中心。

> 当前统一版已集成下列 14 个型号，所有型号使用同一个 APK，不再按型号单独分发测试包。

## 支持型号

| 型号 | 状态 | 当前能力 |
| --- | --- | --- |
| HUAWEI FreeBuds 3 | 稳定 | 电量、降噪开关、9 档降噪空间方向、双击手势与系统界面集成 |
| HUAWEI FreeBuds 4E | 扩展支持 | 电量、降噪 / 关闭与轻度 / 均衡两档降噪、状态回读、左右耳双击与按住、佩戴检测、轻滑音量说明、3 种官方音效和官方配色图片 |
| HUAWEI FreeBuds 5 | 扩展支持 | 电量、降噪 / 关闭与状态回读、智慧动态 / 轻度 / 均衡三档降噪、佩戴检测、4 种官方音效、高清音质与低时延自动保持；手势设置待补充 |
| HUAWEI FreeBuds 5i | 扩展支持 | 电量、通透 / 降噪 / 关闭与状态回读、智慧动态 / 轻度 / 均衡 / 深度四档降噪、左右耳双击、佩戴检测、4 种官方音效、高清音质、低时延自动保持与官方配色图片；长按和滑动设置待补充 |
| HUAWEI FreeBuds 6i | 扩展支持 | 电量、通透 / 降噪 / 关闭、4 档降噪、通透人声模式、双击 / 三击手势、4 种官方音效、10 段自定义均衡器、低时延自动保持与专属图片 |
| HUAWEI FreeBuds Pro 3 | 扩展支持 | 电量、三态控制与状态回读、4 档降噪、通透人声模式、长按 / 捏合 / 滑动手势与低时延自动保持 |
| HUAWEI FreeBuds Pro 4 | 基础支持 | 电量、降噪 / 关闭两态控制；暂不支持降噪状态回读与手势设置 |
| HUAWEI FreeBuds Pro 5 | 基础支持 | 电量、通透 / 降噪 / 关闭与状态回读、低时延自动保持；降噪等级和手势待补充 |
| HUAWEI FreeBuds 7i | 扩展支持 | 电量、通透 / 降噪 / 关闭与状态回读、4 档降噪、双击 / 三击 / 长按 / 滑动音量、佩戴检测、头动控制、空间音频、4 种官方音效、10 段自定义均衡器、高清音质、低时延自动保持、双设备列表管理与官方配色图片 |
| HUAWEI FreeClip | 基础支持 | 左右耳与充电盒电量；不提供传统主动降噪 |
| HUAWEI FreeClip 2 | 扩展支持 | 电量、双击 / 三击 / 滑动手势、空间音频、低时延自动保持及部分佩戴和音频设置；不支持传统主动降噪 |
| HUAWEI FreeArc | 扩展支持 | 左右耳与充电盒电量、双击 / 三击 / 按住 / 滑动手势、5 种官方音效、10 段自定义均衡器与官方配色图片；不支持传统主动降噪 |
| 华为智能眼镜（第一代） | 基础支持 | 左右镜腿电量与系统界面集成；不提供主动降噪 |
| HUAWEI Eyewear 2 | 基础支持 | 左右镜腿电量、双击 / 滑动手势与低时延自动保持；不提供主动降噪 |

“稳定”表示已完成较充分的实机验证；“扩展支持”表示已接入更多协议控制；“基础支持”表示已接入识别、电量或核心控制。除稳定型号外，其余型号仍建议继续进行真机回归。表中未列出的官方功能不代表已经支持。

需要适配其他华为耳机，可加入 QQ 群 `1022359908` 参与测试与协议采集。

## 主要功能

- 在系统蓝牙详情页显示电量及机型支持的控制项
- 接入 HyperOS 连接弹窗和超级岛
- 可分别控制锁屏耳机通知与超级岛通知
- 通知可打开模块弹窗、系统设置或华为智慧音频；模块弹窗可直接切换低时延
- 接入融合设备中心，并支持已配对设备间流转；支持低时延的机型可复用“查找耳机”卡片快速切换
- 显示左右耳、充电盒或眼镜左右镜腿电量
- 按机型提供主动降噪、通透模式、降噪等级和手势设置
- 直接从蓝牙协议读取现代型号的精确资源身份，并从华为官方 CDN 校验、缓存对应机型与配色图片
- 耳机名称被修改或无法自动识别时，可按蓝牙地址手动选择型号
- 首次启动提供设置引导，并可在应用内检查 GitHub 更新
- 覆盖安装新版本后提示重启作用域，无需直接重启手机

## 使用要求

- 小米或 Redmi 设备
- HyperOS，Android 15 及以上
- LSPosed API 101 及以上
- 表中任一已集成型号

## 快速开始

1. 从 [GitHub Releases](https://github.com/Nshpiter/HuaweiPods/releases) 下载并安装 APK；首次打开可按引导检查 LSPosed 与核心作用域。
2. 在 LSPosed 中启用 HuaweiPods。
3. 勾选以下作用域：

   - `com.android.bluetooth`
   - `com.android.settings`
   - `com.milink.service`
   - `com.xiaomi.bluetooth`

4. 在 HuaweiPods 内重启相关作用域，或重启手机。
5. 连接设备后，即可在 HuaweiPods、蓝牙详情页、超级岛或融合设备中心查看已接入能力。现代型号会直接读取设备标识；改名设备或旧协议型号识别失败时，再在 HuaweiPods 中选择一次真实型号。

正式版不需要安装或运行华为智慧音频来获取图片：现代型号由蓝牙协议直接确认机型与配色，旧协议型号可在图片设置中检索华为官方配色并手动确认。下载失败时始终回退到已有缓存或内置图，不会猜测默认配色。

融合设备中心低时延卡片可在 HuaweiPods 设置中关闭。由于现有协议没有可靠的低时延状态回读，界面显示最近一次成功写入且会在重连时恢复的设置，不代表耳机主动上报的实时状态。

更完整的安装说明见 [快速开始](docs/guide/getting-started.md)。

## 适配新型号

未支持型号需要先采集华为智慧音频（`com.huawei.smartaudio`）与耳机之间的真实通信数据。通用协议采集版按设备、噪声与音量、手势、音质、连接和充电盒等类别引导单变量操作；只负责记录，不代表该型号已经适配。

请勿直接公开包含设备地址、账号或其他个人信息的原始采集文件。提交前请检查并脱敏，完整流程见 [华为耳机协议采集指南](docs/DEBUG_CAPTURE_GUIDE.md)。

建议优先加入 QQ 群 `1022359908` 参与对应型号测试交流；可复现问题也可提交至 [GitHub Issues](https://github.com/Nshpiter/HuaweiPods/issues)。

## 构建

```bash
# 正式版
./gradlew :app:assembleRelease

# 协议采集与调试版
./gradlew :app:assembleDebug
```

`release` 的图片识别不依赖华为智慧音频；仅在智慧音频本来就在运行时，注入一个 FreeClip 2 空间音频同步桥，不会主动启动或保活它。`debug` 另外包含面向适配工作的智慧音频协议采集功能。两者使用相同应用 ID，无法同时安装。

## 致谢

- [OppoPods](https://github.com/1812z/OppoPods) by 1812z（HuaweiPods 直接基于）
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by Leaf-lsgtky（上游原始项目）
- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen
- [HyperIsland](https://github.com/1812z/HyperIsland) by 1812z（更新与首次引导交互参考）
- [Miuix](https://github.com/YuKongA/miuix)

## 许可证

本项目基于 [GPL-3.0](LICENSE) 开源。
