# ColorOS 17 适配核对

## 样本

- 全量包：OnePlus PLK110 `PLK110_17.0.0.102(CN01)`，Android 17（SDK 37），`CP2A.260605.016`
- 提取分区：`system`、`system_ext`、`my_product`、`my_stock`
- 宿主版本：

| 包名 | 版本 | 位置 |
| --- | --- | --- |
| `com.android.bluetooth` | Android 17 | `system/app/Bluetooth`（非 Mainline APEX） |
| `com.heytap.mydevices` | 17.25.10 | `my_stock/priv-app/MyDevices` |
| `com.heytap.accessory` | 17.6.15 | `my_stock/priv-app/AccessoryFramework` |
| `com.oplus.melody` | 17.5.1 | `my_stock/del-app/Melody` |
| `com.oplus.wirelesssettings` | ColorOS 17 | `system_ext/priv-app/WirelessSettings` |
| SystemUI | ColorOS 17 | `system_ext/priv-app/SystemUI` |

核对方式：dexdump 导出方法/字段签名逐项比对，关键逻辑用 jadx 反编译确认。

## 现有 Hook 核对

| Hook 点 | ColorOS 17 | 处理 |
| --- | --- | --- |
| `A2dpService.handleConnectionStateChanged(BluetoothDevice,int,int)`、构造器、`mHandler`、`getConnectedDevices()` | 一致；父类改为 `profile.ConnectableProfile → profile.ProfileService`，仍是 `ContextWrapper` | 不变 |
| `HeadsetStateMachine.processUnknownAt(String,BluetoothDevice)`、`mNativeInterface`、`mDevice`、`mHeadsetService`、`mAdapterService` | 一致 | 不变 |
| `HeadsetNativeInterface.atResponseString/atResponseCode` | 一致 | 不变 |
| `AdapterService.getAdapterService()` 静态方法 | **已移除** | 通过 `ProfileService.getAdapterService()` 获取 |
| 状态栏 `wireless_headset` 图标 | 蓝牙 UID 无 `STATUS_BAR` 权限 | 首次被拒后停止上游的图标保活 |
| SystemUI 流体云 | `OplusLiveAlertNotificationsRepository`：`isPromotedOngoing()` 或 OPLUS live alert 配置即交给 Seedling 插件 | 现有 promoted ongoing 通知不变 |
| 我的设备详情页 | `com.oplus.mydevices.bluetooth.fragment.BtDetailPageFragment` → **`com.heytap.mydevices.plugin.bluetooth.fragment.BtDetailPageFragment`**，改为 Preference 页面，`mMac`/`viewModel`/`_binding` 均不存在 | 新增 Preference 路径：按唯一 MAC 字段取地址，改写 `pref_bt_audio_settings`；按参数类型定位被混淆的 `Preference.onBindViewHolder` |
| 快速设备连接 `DialogActivity` 自动关闭 | 静态 `h(DialogActivity)` → **`g(DialogActivity)`** | 按签名 `static void (DialogActivity)` 定位 |
| 快速设备连接卡片数据 | 混淆的 `fd.c` → **`com.oplus.pantaconnect.data.BaseViewData`**，插件数据 `getPackageName/getDialogTitle` 不变 | 先新类名后旧类名 |
| `SceneService` + `com.heytap.accessory.plugin.discovery.action.DIALOG`、`DialogParams` | 一致，服务需 `com.oplus.permission.safe.CONNECTIVITY`（蓝牙进程具备） | 不变 |
| 设置 `Settings$BluetoothDeviceDetailActivity` | 导出，需 `BLUETOOTH_CONNECT` | 不变 |
| 电池 `StartupAppListActivity` | 需 `oplus.permission.OPLUS_COMPONENT_SAFE`，三方应用无法直接打开 | 维持应用详情页兜底 |

## ColorOS 的三方耳机能力框架

ColorOS 17 蓝牙栈内置了面向 AirPods 与合作三方耳机的扩展能力框架（`oplus_bluetooth_common_ext.jar`，由 `OplusBTFactory` 在运行时加载，应用类加载器中可通过 `OplusFeatureCache.get(IOplusRemoteDevice.DEFAULT)` 访问）：

- 设备属性：协议通道（`TRANSPORT_3RD_ECHO`=0、`TRANSPORT_HFP_AT`=1、`TRANSPORT_AIRPOD`=2）、厂商 ID / 产品 ID、特性掩码（电量、降噪、查找响铃、佩戴、低时延、低电量提醒）。
- 电量：`notifyBatteryLevelChanged(device, int[6])`，数组为左、右、盒电量与三者充电状态（1 充电、2 未充电、4 断开，-1 未知），同时写入 Android 标准电量（左右较低值）。耳机未入盒时对外隐藏盒电量。
- 佩戴：`notifyWearStatusChanged(device, left, right)`，0 佩戴、1 未佩戴、2 入盒、255 未知。
- 降噪：`setOplusDeviceNoiseModeSupport`、`notifyNoiseModeChanged`；系统界面通过 `setExtendFeatureStatus` 下发控制，HFP AT 通道最终进入 `OplusThirdPartyEarbudAtHandler`（`+OBUDS` 协议）。
- 消费方：蓝牙设置（`getBatteryInfo` 显示左右耳电量，需厂商 ID 在 `persist.bluetooth.3rd_earbuds_hfp_at_vendor` 白名单，默认为 5 家合作厂商）、我的设备（`OplusBluetoothAdapterWrapper`：电量、特性掩码、降噪状态读写、回调注册）、快速设备连接（`HeadSetConNetworking` 与三方耳机弹窗、按厂商/产品 ID 的云端资源）。

## 本版接入

`ColorOsSystemBatteryBridge` 在蓝牙进程中为华为耳机：

1. 设置协议通道为 `TRANSPORT_HFP_AT`，使 `getBatteryInfo` 返回左右耳 / 盒电量；
2. 按充电状态推导入盒（2）或佩戴（0），让系统在入盒时显示盒电量；
3. 调用 `notifyBatteryLevelChanged`，并用在线耳机较低值校正 Android 标准电量；
4. 会话断开时清除标准电量，OPLUS 属性由蓝牙栈在 ACL 断开时自行重置。

**不声明任何可控特性**，特性掩码保持 0：系统不会尝试用 OBUDS AT 指令控制华为耳机，也不会触发三方耳机的回连、共享与查找逻辑。框架缺失时（如 ColorOS 16）只写入 Android 标准电量。

## 三方耳机能力的实际消费方（1.9.0.1 调研）

对照 ColorOS 17 固件（蓝牙扩展 `oplus_bluetooth_common_ext.jar`、我的设备 17.25.10、快速设备连接 17.6.15、蓝牙设置、SystemUI）逐一核对了三方耳机框架各项能力由谁消费：

| 能力 | 系统消费方 | 华为耳机能否接入 |
| --- | --- | --- |
| 左右耳 / 盒电量（`getBatteryInfo`） | 蓝牙设置电量摘要、我的设备、快速设备连接 | 已接入（`TRANSPORT_HFP_AT` + `notifyBatteryLevelChanged`） |
| 降噪模式（`notifyNoiseModeChanged` / `setExtendFeatureStatus(4, mode)`，1 关闭、2 降噪、3 通透、4 自适应） | 仅我的设备的 AirPods 插件有降噪界面；合作耳机详情页只有电量与查找响铃；SystemUI 无耳机降噪接口 | 登记为三方耳机也拿不到系统降噪按钮 |
| 低电量提醒（`FEATURE_EXT_LOW_BATTERY_NOTIFY`=256） | 快速设备连接 `HeadSetConNetworking` 的低电量流体云胶囊 | 要求厂商 ID 在云端 `thirdHeadSetSupportList` 且有云端设备资源，华为耳机无法进入 |
| 佩戴状态（`notifyWearStatusChanged`） | 系统据此决定是否展示盒电量；我的设备 / 快速设备连接仅对合作耳机使用 | 已接入 |
| “耳机设置”入口与电量摘要 | 蓝牙设置：厂商 ID 在 `persist.bluetooth.3rd_earbuds_hfp_at_vendor` 白名单（缺省 0x0A80、0x054C、0x012D、0x07E0、0x0E66）时显示 | 在设置进程内对华为耳机放行，入口改开 HuaweiPods |

另外，`OplusThirdPartyEarbudAtHandler` 会对白名单厂商（缺省 0x012D、0x054C）在 HFP 连接后主动发起 `+OBUDS` 握手，因此**不能**在蓝牙栈中给华为耳机登记这些厂商 ID。

## 1.9.0.1 的原生化处理

- **我的设备快捷控制**：在已接管的“HuaweiPods 电量与设置”入口下方按机型能力加入关闭 / 降噪 / 通透三段切换，以及低时延、佩戴检测开关，指令带地址与机型路由发给蓝牙进程执行，状态随耳机同步。佩戴检测使用抓包验证过的 2B10 写入与 2B11 回读，蓝牙进程以回读结果为准广播状态；页面打开时先回读，未读到状态前不允许切换。
- **流体云低时延按钮**：支持低时延的型号在流体云（promoted ongoing 通知）上增加“开启 / 关闭低时延”按钮。通知按钮只能触发本应用的 PendingIntent，由应用以自身身份把指令转发给蓝牙进程；写入成功后应用请求蓝牙进程重发电量，通知按新状态重建。
- **快速设备连接卡片电量刷新**：卡片描述改为“左耳 · 右耳 · 耳机盒”并标记充电。`DialogClientImpl` 在卡片显示时把任意状态的数据经 `show_dialog` 交给当前卡片，未显示时过滤非 0 状态，因此卡片弹出后 45 秒内电量文字变化时只补发状态 2，用于刷新而不会重新弹出。
- **蓝牙设置**（新作用域 `com.oplus.wirelesssettings`）：仅在设置进程内让华为耳机通过三方耳机白名单判断，设备列表显示左右耳电量，详情页出现“耳机设置”，点击打开 HuaweiPods。不修改蓝牙栈中的设备属性。
- **低电量提醒**：在线且未充电的耳机降到 20%、10% 时由蓝牙进程各发出一次高优先级通知，充电或电量回升后重新布防；可在设置中关闭或发送测试提醒。
- **佩戴状态**：解析耳机主动上报的入耳状态（服务 2B、命令 03，字段 08/09，1 为入耳；与 OpenFreebuds 一致），在 15 秒电量刷新时随回包读出，写入系统佩戴状态。协议只有整体入耳状态，不区分左右耳。

以上均为固件静态分析结果，需实机验证。
