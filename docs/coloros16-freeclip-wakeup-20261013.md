# ColorOS 16 / FreeClip 修复记录（2026-10-13）

本文记录当天较早候选的修复与实机验证，之后作为基础纳入 `1.9.0.2-rc1`。验证设备为 OPPO Pad Mini（OPD2515），固件
`OPD2515_16.0.10.500(CN01)`，Android 16；不能据此宣称手机、其他固件或长期待机全部通过。
本文候选不是本次 RC；RC 新增的生产端双 Profile 会话与故障重试验证范围见
[测试版发布说明](../.github/release-notes/1.9.0.2-rc1.md)。

## 修复范围

- 流体云：HFP 或 A2DP 单独断开不再直接清除胶囊；同一设备两个音频 Profile
  均明确断开才清理。未知状态、其他设备断开不误清理，蓝牙关闭立即清理。
- 后台唤醒：四种连接/电量生命周期事件显式投递到应用的 manifest 接收器，使用蓝牙
  宿主 `Utils.getTempBroadcastOptions()` 提供的临时 FGS 许可，并设置
  `DEFERRAL_POLICY_NONE`，避免缓存进程一直等到用户打开应用才收到状态。
  UI 另外接收 registered-only 广播，删除重复注册的流体云接收器。
  不修改系统永久白名单，不绕过用户“强行停止”，不放宽发送方身份验证。
- ColorOS 冻结兜底：由蓝牙进程绑定独立服务，通过短同步 Binder 入口递交四种生命周期
  事件。服务入口要求 `BLUETOOTH_PRIVILEGED`，并用内核提供的调用 UID 查询实际包名，
  所有图片/连接查询/通知操作转到应用线程；蓝牙主线程不做同步 IPC。发送队列上限 16，
  确认清理后解除绑定，旧清理回执不能结束更新的连接，热重载关闭工作线程。
- 通知设置：常驻通知关闭立即清理；重新开启请求真实电量；锁屏设置立即更新已有卡片，
  保留通知渠道及用户的渠道设置；排队中的服务启动也重新检查设置。
- 跨进程状态：MyDevices 限定四种状态动作和蓝牙发送方；发送端显式共享身份。
- 佩戴检测：读取失败也结束等待，且一次读取只完成一次，热重载后的旧回调仍被过滤。
- 模块状态：检查中、当前版本在线、旧版本需要重载、真正超时分别显示。
- 私有接口：按精确签名缓存可选电量方法；缺失或失败不阻断其他方法及 Android
  标准电量回退，诊断限频。反射包装的状态栏权限拒绝不再导致无限重试。

## 固件证据

实机 Bluetooth.apk 中存在 `com.android.bluetooth.Utils.getTempBroadcastOptions()`，
返回 `android.app.BroadcastOptions`。安装第一轮候选后，系统广播记录确认为蓝牙 UID
发出，临时许可 20 秒、reason 203；但没有显式禁用延迟时，电量广播仍为 `DEFERRED`。
因此不能只用“应用进程存在”作为唤醒成功证据。

实际断连后两个 Profile 都是 `DISCONNECTED`，但应用记录为
`DEFER_BY_OPLUS SUB_REASON: FROZEN`；即使设置 `DEFERRAL_POLICY_NONE`，系统仍缓存
广播。单纯绑定加单向 Messenger 消息也会被缓存，故最终候选使用短同步 Binder 入口。

图片资源没有被替换。实机截图中一轮候选的弹窗退回通用长柄耳机图，应用私有目录的
原 FreeClip 缓存图仍正确；重新加载快速设备连接进程后，已在截图确认恢复耳机/盒图。

该固件 `IOplusRemoteDevice` 不具备 ColorOS 17 的全部电量接口，不能把回退到
Android 标准电量描述成已适配原生左右耳/盒卡片。

固件文件 SHA-256：

- Bluetooth.apk: `9160D6B1242B31FE026A51A05564BC1141816EA12287F0625342951B4329A849`
- oplus_bluetooth_common_ext.jar: `8387DCA1EDBC04BEF4E1041AF612E65AD15B20955B7E9C2990BE1C212C53B930`
- oplus-framework.jar: `3B47A02BEC547B3218F8A9FF94E11BB443E985B9B980314A9E0BEDD82DF8AF78`
- oplus-services.jar: `BCA5F72C4DAF7A5AAACD78E5B8F82431CC371169DEE6CC7AB42F87170BCE2180`

## 验证与边界

- `testDebugUnitTest`：497 项测试，0 失败、0 错误。
- `lintDebug`、`assembleRelease`、`compileDebugAndroidTestKotlin` 已在最终同步 Binder
  候选上全部通过。
- 设备测试代码仅编译，未运行会管理/覆盖目标应用的 connectedAndroidTest。
- 已使用原签名保留数据覆盖安装最终候选，重载蓝牙及快速设备连接进程。
- 最终候选：`b1791874294056-release`；APK SHA-256：
  `624796647D079CAD1D790788E369DCBA339ED237821D8C74EE0BE16930C95E5A`。
- 应用在后台保持超过 100 秒后，真实电量广播仍于 30–36 毫秒内送达，服务最近更新时间
  持续推进，没有再次出现该应用的 `DEFER_BY_OPLUS` 队列。
- 用户实际关盖后确认流体云消失；ADB 同时确认 A2DP、HFP 均断开，通知 10005 和两个
  应用服务都已清除，蓝牙绑定已释放。
- 关盖清理后，应用成为后台空闲进程（cached/empty），空闲超过 30 秒。用户直接开盒
  再连接，确认图片正确、流体云自动出现；未要求打开 HuaweiPods。随后再次断开时，
  ADB 再次确认两个音频 Profile 都已断开、通知与应用服务都已清除。
- HFP-only 场景目前保证胶囊生命周期；上游 A2DP 会话结束仍可能停止电量轮询，
  未宣称仅通话连接时持续实时刷新。
- 深度待机、手动强行停止后、手机固件与物理反复开合盒还需单独验证。
