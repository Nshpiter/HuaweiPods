# ColorOS 1.9.1 移植记录（2026-10-20）

## 来源与分支

- 开发分支：`Nshpiter/HuaweiPods:coloros`，原位置 `006df5d`。
- 协议底座：`Nshpiter/HuaweiPods:main`，`1f013d2`（1.9.1）。
- ColorOS 参考：`Artifical0/HuaweiPods-ColorOS:main`，`061fad7`（1.9.0.2-rc3）。
- 使用共同祖先做三方合并，逐项处理修改冲突，保留参考提交的来源历史与 GPL 许可。

## 保留与接入

保留主线 FreeBuds 6、FreeClip 2 典藏版、SE 4 ANC 档位、共享控制通道和手动低时延优先逻辑。

接入参考分支的 ColorOS 官方弹窗、我的设备、系统电量桥、蓝牙设置入口、流体云、第一代 FreeClip、双 Profile 会话、事件确认与重试、热重载交接和发送方信任策略。移除本发行版不使用的小米宿主 Hook。

合并中特别补齐共享控制请求／结果、FreeBuds 7 空间音频动作的信任分类，避免原有控制被新策略拦截。

## 身份与发布

- 包名：`moe.chenxy.huaweipods.coloros`；命名空间继续沿用 `moe.chenxy.huaweipods`。
- 版本：`1.9.1-coloros.1-rc1`，versionCode 22。
- 更新读取本仓库发行列表，筛选 ColorOS 标签和预发布渠道，不使用通用 Latest 回退。
- ColorOS CI 只监听本分支和 ColorOS 标签，不将任何 ColorOS Release 标为 Latest。
- 本地 Release 不签名；Debug 只作开发测试；正式签名由本仓库 CI 提供。
- 初次本地交付时未创建标签、未上传 APK、未发布 Release。

后续试用发布使用 `1.9.1-coloros.1-rc2`（versionCode 23）。rc1 的 CI 在 SDK 安装阶段请求已下架的 `tools` 包而失败；rc2 显式安装 `platform-tools`，仅调整 CI 与版本信息，保留业务实现及审查发现的已知问题。原 rc1 标签保留，避免改写已推送的标签。

## 验证范围

本轮执行结果：581 项 Debug 单元测试通过，0 失败、0 错误；Debug Lint 0 错误（175 项 Warning、39 项 Hint）；Debug、Release 与设备测试 APK 均构建通过。安装包的包名、版本、五个作用域、API 102 入口、热重载元数据、APK v3 测试签名及 16 KB 对齐校验通过。

没有实机验证时，不能宣称 ColorOS 16／17、FreeBuds 6、典藏版或任何单 Profile 场景已在本版本通过。

实机验证顺序：

1. 首次启用与作用域加载；开盒弹窗和正确图片。
2. 左右耳／盒电量、“我的设备”与系统蓝牙详情。
3. A2DP-only、HFP-only、单 Profile 断开、全部断开、切换设备。
4. 关盖／断连后通知及流体云清理；后台进程回收后的重连恢复。
5. 快捷降噪、EQ、防风噪、手动低时延及自动保持之间的交互。
6. FreeClip 1、FreeBuds 6、FreeClip 2 典藏版的各自协议操作。
7. 覆盖安装与 API 102 热重载，旧缓存与迟到回调不覆盖新状态。

参考分支历史验证材料保留在 `coloros17-adaptation.md` 与 `coloros16-freeclip-wakeup-20261013.md`，仅说明来源，不能当作本轮结果。


## 本地构建环境与测试包

本机使用缓存中的 JDK 23.0.2、Gradle 9.6.1、SDK 37.0 和 Build Tools 36.0.0。仓库 Wrapper 9.4.1 保留未改，GitHub CI 尚未执行。

Google Maven DNS 故障影响了测试依赖解析；验证时使用工作目录中的临时 Maven 仓库提供相同版本的 AndroidX Tracing 1.1.0，来自项目已配置的 Google Maven 镜像，AAR SHA-256 与模块元数据一致。未修改生产依赖版本或提交本地仓库路径。

交付 APK 使用本地测试证书（CN=HuaweiPods Local Test），只用于开发验证，不是仓库正式发布签名。未来正式签名包不能直接覆盖此测试包，需要卸载测试包后安装，卸载会清除其应用数据。

本轮没有连接 Android 设备，设备测试仅编译；没有推送分支、创建标签或发布 Release。
