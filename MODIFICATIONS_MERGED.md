# HuaweiPods Modification Report / 合并版修改说明

Date / 日期: 2026-09-07  
Project: `Nshpiter/HuaweiPods`  
Scope: Miuix 0.9.3, HyperOS Settings UI, ANC/transparency, EQ redesign, and FreeLace Pro 2 integration.

## 说明 / Notes

这是 `MODIFICATIONS_CN_EN.md` 与 `MODIFICATIONS_CN_EN_COPY.md` 的合并独立版本。当前工作区没有 `.git`，所以不能生成严格的 commit-level `git diff`；下面内容依据本地源码、文件更新时间和测试内容核对整理。

`build/`、`app/build/`、Gradle cache、APK、`local.properties` 和 crash logs 属于生成物或本地环境文件，不计入源码修改。

## 1. Build / 构建依赖

### `gradle/libs.versions.toml`

- Changed code: `miuix = "0.9.3"`、`navigation3 = "1.1.4"`，以及 Miuix UI/Preference/Icons/Blur/Navigation3 dependencies。
- Reason: 完成 Miuix v0.9.3 适配，统一 Compose UI 与 Navigation3 版本，避免旧组件 API 不兼容。

## 2. Device route and protocol / 设备路由与协议

### `app/src/main/java/moe/chenxy/huaweipods/config/DeviceRoutePrefs.kt`

- Changed code: `HUAWEI_FREELACE_PRO2` route encode/decode and preference mapping。
- Reason: 持久化 FreeLace Pro 2 型号路由，重新进入页面后仍保持正确设备类型。

### `app/src/main/java/moe/chenxy/huaweipods/pods/DeviceCapabilities.kt`

- Changed code: `HUAWEI_FREELACE_PRO2` route and `routeCapabilities` entry。
- Important code: `supportsAnc`、`supportsTransparency`、`supportsRfcommBattery`、`supportsHighQualityAudio`、`hasSingleBatteryCell`。
- Reason: FreeLace Pro 2 是颈挂式单电池、无充电盒设备，不能沿用普通 TWS 的能力和电量模型。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiDeviceInfoRoutePolicy.kt`

- Changed code: `00014D` / `sub_model=04` route resolution。
- Reason: 根据抓包准确识别 FreeLace Pro 2，防止路由到错误型号。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiAncLevelProfile.kt`

- Changed code: FreeLace Pro 2 ANC level and transparency sub-mode mapping。
- Reason: 支持轻度、均衡、深度和通透模式，并复用已验证的协议编码。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiRfcommResponseParser.kt`

- Changed code: `parseBattery(..., singleCellBattery: Boolean = false)` and `rightPod` selection。
- Reason: 单电池设备的右耳槽位是恒定占位值，使用左侧槽位作为整机电量，避免界面显示假 100%。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiL2capAncController.kt`

- Changed code: passes `singleCellBattery = route.hasSingleBatteryCell` to battery parsing。
- Reason: 将型号能力传到解析层，正确处理 FreeLace Pro 2 电量。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiHfpController.kt`

- Changed code: battery, ANC, EQ and connection-state refresh coordination。
- Reason: 统一连接状态和协议刷新，减少重复请求。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiFreeBuds5Controller.kt`

- Changed code: FreeLace Pro 2 route support and high-quality-audio query/set/readback methods。
- Reason: 接入 FreeLace Pro 2 的高音质开关协议。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiEqualizerCodec.kt`

- Changed code: FreeLace Pro 2 preset IDs `0x01`, `0x02`, `0x03`, `0x09` and custom EQ packet handling。
- Reason: 根据抓包补齐官方音效和自定义均衡器协议。

### `app/src/main/java/moe/chenxy/huaweipods/pods/HuaweiEqualizerController.kt`

- Changed code: route-independent EQ state read/write dispatch。
- Reason: 新型号未必已经写入旧 preference route，EQ 不能只依赖旧型号匹配。

## 3. HyperOS Settings / 系统蓝牙详情页

### `app/src/main/java/moe/chenxy/huaweipods/hook/SettingsHeadsetPolicy.kt`

- Changed code: ANC、transparency、gesture、native-row hiding and capability policy。
- Reason: 只显示设备支持的功能，避免注入控件和 HyperOS 原生按钮冲突。

### `app/src/main/java/moe/chenxy/huaweipods/hook/SettingsHeadsetHook.kt`

- Changed code: `SettingsFragmentRenderState`、`fragmentRenderStates`、injected view tags、refresh scheduling、native cleanup and EQ card reuse。
- Reason: 通过 render-state cache 和控件复用解决重复重绘、按钮冲突与页面 flashing/flicker，并在退出时清理注入控件。

### `app/src/main/java/moe/chenxy/huaweipods/hook/HeadsetStateDispatcher.kt`

- Changed code: state receiver registration and app-request dispatch lifecycle。
- Reason: 统一耳机状态分发，减少重复回调和重复刷新。

### `app/src/main/java/moe/chenxy/huaweipods/hook/HuaweiAncLevelSliderView.kt`

- Changed code: unknown selection handling、label measurement、endpoint clamping、thumb/track drawing conditions。
- Reason: 修复“降噪/通透下面的字不显示”；扩大文字区域，避免父容器裁剪，并禁止未知状态错误选中第一个等级。

### `app/src/main/java/moe/chenxy/huaweipods/hook/HuaweiAncSubModeSelectorView.kt`

- Changed code: ANC sub-mode selection and anchor callback。
- Reason: 点击具体 ANC 等级时回传正确协议子模式。

### `app/src/main/java/moe/chenxy/huaweipods/hook/HuaweiFreeClip2AudioControlsView.kt`

- Changed code: compact FreeClip 2 audio controls render/update path。
- Reason: 修复宿主页面中的音频控件状态更新。

### `app/src/main/java/moe/chenxy/huaweipods/hook/HuaweiEqualizerExpandableCard.kt`

- Changed code: title/subtitle hierarchy、selected preset subtitle、themed background、stable arrow and selection update。
- Reason: 重新设计音效卡片，显示当前预设并改善展开收起时的布局稳定性。

### `app/src/main/java/moe/chenxy/huaweipods/ui/components/AncSwitch.kt`

- Changed code: ANC switch rendering and state change handling。
- Reason: 统一 ANC 状态展示，防止状态回填被误判成用户点击。

### `app/src/main/java/moe/chenxy/huaweipods/ui/components/FreeLacePro2Controls.kt`

- Changed code: `freeLacePro2Presets`、EQ selection、custom EQ、high-quality cache/readback and route binding。
- Reason: 为 FreeLace Pro 2 提供独立 EQ 和高音质控制 UI。

### `app/src/main/java/moe/chenxy/huaweipods/ui/pages/PodDetailPage.kt`

- Changed code: `HUAWEI_FREELACE_PRO2` branch and `FreeLacePro2Controls` insertion。
- Reason: 在详情页挂载 FreeLace Pro 2 专属控制区。

### `app/src/main/java/moe/chenxy/huaweipods/utils/miuiStrongToast/data/HuaweiPodsAction.kt`

- Changed code: HuaweiPods action/state payload definitions。
- Reason: 让系统提示、Hook 和应用页面传递新的 ANC/EQ/device actions。

## 4. Resources / 资源

### `app/src/main/res/drawable/`

- `ic_anc_noise_cancellation.xml`: 降噪状态 icon。
- `ic_anc_transparency.xml`: 通透状态 icon。
- `ic_anc_off.xml`: 关闭状态 icon。
- Reason: 明确区分 Noise Cancellation、Transparency and Off 状态。

## 5. Tests / 测试

### `app/src/test/java/moe/chenxy/huaweipods/pods/`

- `DeviceCapabilitiesTest.kt`: FreeLace Pro 2 capabilities。
- `HuaweiDeviceInfoRoutePolicyTest.kt`: `00014D` / `sub_model=04` route resolution。
- `HuaweiLowLatencyControllerTest.kt`: low-latency protocol。
- `HuaweiRfcommResponseParserTest.kt`: ANC、transparency、battery and single-cell parsing。
- `HuaweiAncLevelProfileTest.kt`: ANC level mapping。
- `HuaweiAncPacketsTest.kt`: ANC enable/mode packets。
- `HuaweiEqualizerCodecFreeLacePro2Test.kt`: FreeLace Pro 2 EQ packets。

### `app/src/test/java/moe/chenxy/huaweipods/hook/`

- `SettingsHeadsetPolicyTest.kt`: Settings capability policy。
- `MiBluetoothToastAncPolicyTest.kt`: Mi Bluetooth toast ANC action/state。

### `app/src/test/java/moe/chenxy/huaweipods/hook/milink/`

- `MiLinkAncRoutingTest.kt`: MiLink ANC routing and command mapping。

### `app/src/test/java/moe/chenxy/huaweipods/smartaudio/`

- `OfficialImageCatalogPolicyTest.kt`: FreeLace Pro 2 official image/model mapping。

### `app/src/test/java/moe/chenxy/huaweipods/utils/`

- `PodImageLoaderTest.kt`: device image loading and route mapping。

## 6. Validation / 验证结果

- `:app:compileDebugKotlin --offline`: **BUILD SUCCESSFUL**。
- `:app:assembleDebug --offline`: **BUILD SUCCESSFUL**。
- APK: `app/build/outputs/apk/debug/app-debug.apk`。
- Remaining physical-device checks: connect FreeLace Pro 2 and verify battery, ANC labels, EQ card and flicker behavior。
