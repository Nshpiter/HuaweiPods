# 上游选择性移植（2026-10-16，1.9.0.2-rc3）

来源：[Nshpiter/HuaweiPods 59d5fa8](https://github.com/Nshpiter/HuaweiPods/commit/59d5fa876c6a1b4cec2f4e40d4aae8b5e3f3e2be)。
本轮按功能移植，不进行整分支合并，不恢复已删除的小米 / HyperOS 集成。

## 纳入

- RFCOMM SDP 候选过滤：移植 `HuaweiRfcommServicePolicy`，不向 HFP/HSP/A2DP/AVRCP
  等音频服务发送华为控制帧；保留 SPP、未知厂商服务和已有信道回退顺序。
- A2DP 回调兼容：优先 `handleConnectionStateChanged`，其次 `connectionStateChanged`，
  严格匹配设备类型和两个 primitive `int` 参数、非静态 `void` 返回，并支持继承方法。
  未知签名不按参数数量猜测。回调后的双 Profile 会话策略完全保留。
- 名称展示：移植别名优先、空白回退和中文 / emoji 保留规则，接入 ColorOS 流体云、
  通知及连接卡片；原始 `device_name`、设备地址和路由仍用于型号 / 图片 / 协议识别，
  独立 `device_display_name` 只用于展示。旧广播缺少该字段时仍兼容。
- 改名刷新：只消费系统原生名称 / 别名变更，且只刷新当前会话已有电量；使用缓存标记，
  不伪造真实电量读取、不发连接事件、不重新打开弹窗或触发低电提醒。

## 未纳入

FreeBuds 6、新增 SE 4 ANC 档位、6i / SE 4 ANC 共享控制通道、HyperOS 2 私有宿主、
超级岛常驻续期、Android 14 最低版本调整。包名、图片资源、签名及发布配置不变。
发布阶段仅将版本提升为 `1.9.0.2-rc3` / versionCode `26`，保留原有固定发布签名。

## 验证边界

回归测试覆盖音频服务 UUID 排除与控制服务保留、精确签名 / 方法名 / 继承 / 错误签名，
以及显示名回退、机型隔离、其他设备改名与无电量会话不刷新。
热重载 Parcel 测试扩展为检查原始机型、显示名、路由和缓存标记均可交接。
设备测试仅编译，不代表已执行；本轮不自动安装或重启蓝牙。

本地执行结果：551 项 Debug 单元测试全部通过（新增 21 项，0 失败 / 0 错误），
Debug Lint 无错误（仍有既有的 187 项 Warning、36 项 Hint），Release 构建及
Android 测试代码编译通过。源码验证阶段的 APK 包名 / 版本与原配置一致，对齐和构建签名校验通过。
本地 Release 变体仍按仓库配置使用 Debug 签名，不是固定发布签名的覆盖安装包；
用户随后授权将改动提交推送并发布 RC3；正式测试包由 GitHub CI 使用固定发布签名构建，
不是前述本地 Debug 签名包。ADB 未发现连接设备，
实际固件回调匹配、耳机改名、后台重连及关盖清理仍需实机验证。
