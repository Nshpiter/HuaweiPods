# HuaweiPods Modification Report / 修改说明

日期 Date: 2026-09-07  
Project: `Nshpiter/HuaweiPods`  
Purpose: Miuix 0.9.3 adaptation, HyperOS Settings compatibility, ANC UI fixes, EQ redesign, and Huawei FreeLace Pro 2 support.

## 0. Scope and baseline / 范围与基线

本报告记录当前工作区中与本次适配任务相关的源码修改。当前目录没有 `.git`，因此不能生成严格意义上的 `git diff`；文件范围根据本地源码更新时间、实际代码内容和测试内容核对得到。

`build/`、`app/build/`、Gradle cache、APK、`local.properties` and crash logs are local/generated files. They are excluded from the source modification list.

## 1. Build and dependency / 构建和依赖

### `gradle/libs.versions.toml`

- Changed code: `[versions]` 中的 `miuix = "0.9.3"`、`navigation3 = "1.1.4"`，以及 Miuix UI/Preference/Icons/Blur/Navigation3 library declarations。
- Reason / 修改理由: 适配 Miuix v0.9.3 API，统一 Compose UI 与 Navigation3 依赖版本，避免旧版组件 API 不兼容。
- Verification / 验证: Debug Kotlin compilation and Debug APK assembly completed successfully with local offline Gradle.

## 2. Device route and FreeLace Pro 2 protocol / 设备路由与 FreeLace Pro 2 协议

### Directory: `app/src/main/java/moe/chenxy/huaweipods/config/`

#### `DeviceRoutePrefs.kt`

- Changed code: `HuaweiDeviceRoute.HUAWEI_FREELACE_PRO2` route encode/decode and preference mapping。
- Reason: 让 FreeLace Pro 2 的设备路由可以持久化，重启或重新进入详情页后仍能恢复正确型号。

### Directory: `app/src/main/java/moe/chenxy/huaweipods/pods/`

#### `DeviceCapabilities.kt`

- Changed code: `HUAWEI_FREELACE_PRO2` enum route and `routeCapabilities` entry。
- Important fields: `supportsAnc`、`supportsTransparency`、`supportsRfcommBattery`、`supportsHighQualityAudio`、`hasSingleBatteryCell`。
- Reason: FreeLace Pro 2 是颈挂式单电池设备，没有充电盒；必须和普通 TWS 耳机区分能力与电量模型。

#### `HuaweiDeviceInfoRoutePolicy.kt`

- Changed code: `00014D` / `sub_model=04` route resolution。
- Reason: 根据抓包识别 FreeLace Pro 2，避免被错误分配到其他 FreeBuds route。

#### `HuaweiAncLevelProfile.kt`

- Changed code: FreeLace Pro 2 ANC level profile、transparency sub-mode mapping and default mode。
- Reason: 复用已验证的 FreeBuds 6i 协议编码，正确展示并设置轻度、均衡、深度和通透模式。

#### `HuaweiRfcommResponseParser.kt`

- Changed code: `parseBattery(..., singleCellBattery: Boolean = false)` and the `rightPod` selection branch。
- Reason: 单电池设备的右耳槽位是协议占位值，不能直接显示为恒定 100%；FreeLace Pro 2 使用左侧槽位作为整机电量。

#### `HuaweiL2capAncController.kt`

- Changed code: battery parsing call passes `singleCellBattery = route.hasSingleBatteryCell`。
- Reason: 将设备能力传递到协议解析层，避免单电池电量在 UI 中显示错误。

#### `HuaweiHfpController.kt`

- Changed code: connection state, battery, ANC, EQ and feature refresh coordination。
- Reason: 统一 HFP/RFCOMM 状态读取，减少重复刷新并让详情页拿到最新设备状态。

#### `HuaweiFreeBuds5Controller.kt`

- Changed code: FreeLace Pro 2 route support plus `highQualityAudioStateQueryPacket`、`setHighQualityAudio` and `parseHighQualityAudioState` reuse。
- Reason: FreeLace Pro 2 的高音质协议与该协议族一致，补齐查询、设置和回读能力。

#### `HuaweiEqualizerCodec.kt`

- Changed code: FreeLace Pro 2 built-in preset IDs `0x01`, `0x02`, `0x03`, `0x09` and custom EQ packet handling。
- Reason: 根据 FreeLace Pro 2 抓包补齐官方音效和自定义均衡器编码。

#### `HuaweiEqualizerController.kt`

- Changed code: route-independent EQ state read/write dispatch。
- Reason: 新型号可能尚未写入旧的 preference route，因此 EQ 控制不能只依赖旧的精确型号匹配。

## 3. HyperOS Settings page / HyperOS 系统蓝牙详情页

### Directory: `app/src/main/java/moe/chenxy/huaweipods/hook/`

#### `SettingsHeadsetPolicy.kt`

- Changed code: ANC, transparency, gesture, native-row hiding and capability policy。
- Reason: 只显示设备真实支持的功能，避免 HuaweiPods 注入控件与 HyperOS 原生按钮重叠或冲突。

#### `SettingsHeadsetHook.kt`

- Changed code: `SettingsFragmentRenderState`、`fragmentRenderStates`、injected view tags、refresh scheduling、native capability cleanup and EQ card reuse。
- Reason: 解决系统详情页重复重绘造成的 flashing/flicker，避免重复创建 ANC slider、transparency selector 和 EQ card，同时在页面退出时清理注入控件。

#### `HeadsetStateDispatcher.kt`

- Changed code: state receiver registration and app-request dispatch lifecycle。
- Reason: 统一耳机状态分发，减少多个 Hook 同时刷新系统页面造成的重复回调。

#### `HuaweiAncLevelSliderView.kt`

- Changed code: unknown selection handling, measured label area, endpoint text clamping, thumb/track drawing conditions。
- Reason: 修复“降噪/通透下面的字不显示”问题；此前文字区域过小并且父容器裁剪了滑块子内容，边缘标签也可能被截断。

#### `HuaweiAncSubModeSelectorView.kt`

- Changed code: ANC sub-mode selection and anchor callback。
- Reason: 让用户点击具体 ANC 等级时能正确回传协议子模式，不触发错误的默认选项。

#### `HuaweiFreeClip2AudioControlsView.kt`

- Changed code: compact FreeClip 2 audio controls render/update path。
- Reason: 修复系统宿主页中的音频控件状态更新，并保持与新的设备能力模型一致。

#### `HuaweiEqualizerExpandableCard.kt`

- Changed code: title/subtitle hierarchy, selected preset subtitle, rounded themed background, stable expand arrow and selection update。
- Reason: 重新设计音效卡片，显示当前预设并改善展开/收起时的布局稳定性和可读性。

### Directory: `app/src/main/java/moe/chenxy/huaweipods/ui/components/`

#### `AncSwitch.kt`

- Changed code: ANC switch rendering and state change handling。
- Reason: 统一 ANC 状态展示，避免系统状态回填时误触发用户操作。

#### `FreeLacePro2Controls.kt`

- Changed code: `freeLacePro2Presets`、EQ selection、custom EQ、high-quality audio cache/readback and device route binding。
- Reason: 为 FreeLace Pro 2 提供独立控制 UI，支持官方音效、自定义均衡器和高音质开关。

### Directory: `app/src/main/java/moe/chenxy/huaweipods/ui/pages/`

#### `PodDetailPage.kt`

- Changed code: `HUAWEI_FREELACE_PRO2` branch and `FreeLacePro2Controls` insertion。
- Reason: 在设备详情页挂载 FreeLace Pro 2 专属控制区，而不是错误复用普通 FreeBuds 布局。

### Directory: `app/src/main/java/moe/chenxy/huaweipods/utils/miuiStrongToast/data/`

#### `HuaweiPodsAction.kt`

- Changed code: HuaweiPods action/state payload definitions。
- Reason: 让系统提示、Hook 和应用页面之间传递新的 ANC/EQ/device actions。

## 4. ANC resources / ANC 图标资源

### Directory: `app/src/main/res/drawable/`

- `ic_anc_noise_cancellation.xml`: 降噪状态 icon。
- `ic_anc_transparency.xml`: 通透状态 icon。
- `ic_anc_off.xml`: 关闭状态 icon。
- Reason: 在 HyperOS 页面中明确区分 Noise Cancellation、Transparency and Off 三种状态，避免只依赖文字造成状态混淆。

## 5. Tests / 测试文件

### Directory: `app/src/test/java/moe/chenxy/huaweipods/pods/`

- `DeviceCapabilitiesTest.kt`: 验证 FreeLace Pro 2 capabilities。
- `HuaweiDeviceInfoRoutePolicyTest.kt`: 验证 `00014D` / `sub_model=04` route resolution。
- `HuaweiLowLatencyControllerTest.kt`: 验证低延迟控制协议。
- `HuaweiRfcommResponseParserTest.kt`: 验证 ANC、transparency and battery response parsing，包括单电池场景。
- `HuaweiAncLevelProfileTest.kt`: 验证不同型号的 ANC level mapping。
- `HuaweiAncPacketsTest.kt`: 验证 ANC enable/mode packets。
- `HuaweiEqualizerCodecFreeLacePro2Test.kt`: 验证 FreeLace Pro 2 built-in and custom EQ packets。

### Directory: `app/src/test/java/moe/chenxy/huaweipods/hook/`

- `SettingsHeadsetPolicyTest.kt`: 验证系统设置页只显示支持的功能。
- `MiBluetoothToastAncPolicyTest.kt`: 验证 Mi Bluetooth toast 中的 ANC action/state。

### Directory: `app/src/test/java/moe/chenxy/huaweipods/hook/milink/`

- `MiLinkAncRoutingTest.kt`: 验证 MiLink ANC routing and command mapping。

### Directory: `app/src/test/java/moe/chenxy/huaweipods/smartaudio/`

- `OfficialImageCatalogPolicyTest.kt`: 验证 FreeLace Pro 2 的官方图片和型号映射。

### Directory: `app/src/test/java/moe/chenxy/huaweipods/utils/`

- `PodImageLoaderTest.kt`: 验证耳机图片加载和新型号资源路由。

## 6. Validation status / 当前验证状态

- `:app:compileDebugKotlin --offline`: **BUILD SUCCESSFUL**。
- `:app:assembleDebug --offline`: **BUILD SUCCESSFUL**。
- APK output: `app/build/outputs/apk/debug/app-debug.apk`。
- 仍需 physical-device verification: 连接 FreeLace Pro 2 后确认 battery、ANC labels、EQ card and flicker behavior。
- 当前工作区没有 Git history，因此本报告不能提供 commit-level diff 或远程仓库逐行差异。
