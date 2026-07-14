# Android 版 TermFast 项目开发计划

> 本文基于 `android-migration-assessment.md` 和 `android-vpn-approach.md` 进一步细化：具体开发哪些技术点、遵循什么流程、分几个阶段、需要多长时间。
> 计划假设：3 人核心团队（1 Rust、1 Android 原生、1 前端），目标为可上架 Google Play 的 Android v1。

---

## 1. 技术栈与架构决策

| 层级 | 选型 | 理由 |
|------|------|------|
| **核心后端** | `crates/core`（Rust）复用 | 已平台无关、已 Android 交叉编译 |
| **FFI 桥接** | `jni` crate + `cbindgen` | Rust 与 Kotlin 之间标准方案 |
| **Android 原生层** | Kotlin + NDK（C） | 写 VpnService、前台 Service、Keystore 都用 Kotlin；tun2socks 用 C |
| **tun2socks** | **badvpn-tun2socks**（spike 后确认） | 成熟轻量；若 spike 失败 fallback 到 outline-go-tun2socks |
| **UI 框架** | **Tauri Mobile（React + WebView）** | 复用现有 React 资产，重写布局成本低于 RN/Compose |
| **构建工具** | `cargo-ndk` + Tauri Android CLI + Gradle | Rust 交叉编译 + Android 打包 |
| **CI/CD** | GitHub Actions | 复用现有 CI 经验；新增 Android 构建 job |
| **测试** | Rust 单元测试 + Android instrumented tests + 真机矩阵 | 重点在后台稳定性与 VPN 兼容性 |

**关键架构原则**：
1. `crates/core` 保持不依赖任何 Android/桌面平台代码；
2. 所有平台差异收敛到 `crates/android-ffi` 和 Kotlin 层；
3. 先 spike 再 full commit；每个阶段有可演示的里程碑。

<!-- === SECTION 1 END === -->

---

## 2. 技术点拆解（WBS）

### 2.1 Rust 层：`crates/android-ffi`

| 编号 | 技术点 | 详细说明 | 验收标准 |
|------|--------|---------|---------|
| R1 | **tokio runtime 初始化** | 在 FFI `init` 中创建 `tokio::runtime::Runtime`（multi-thread），后续所有调用在该 runtime spawn | 调用 `vpg_android_init()` 后 runtime 常驻，重复调用不崩溃 |
| R2 | **配置加载/保存桥接** | 接收 Kotlin 传入的 JSON 配置，构造 `ConfigManager` + `SharedPreferencesConfigStorage`（平台实现放 Kotlin，Rust 侧只接受 trait） | 配置读写 round-trip 一致 |
| R3 | **凭证 trait 实现** | 创建 `AndroidKeystoreCredentialStore`（Rust 侧 trait wrapper），底层通过 JNI 回调 Kotlin Keystore | 密码/密钥 passphrase 加密存储、可读取、重装 App 后不可恢复（符合 Keystore 设计） |
| R4 | **ServerManager FFI API** | 暴露 add/update/remove/get/list/connect/disconnect/toggle_proxy 等 C 函数 | Kotlin 能完整控制服务器生命周期 |
| R5 | **SOCKS5 启动/停止** | 暴露 `start_socks5(port)` / `stop_socks5()`，内部调用 `Socks5Server` | `127.0.0.1:1080` 可监听、可停止 |
| R6 | **事件/日志回调** | 暴露 `subscribe_events(callback)`，把 `ServerStatusChanged` / `LogEntry` / `TriggerFired` / `HostkeyMismatch` 序列化为 JSON 回调 | Kotlin 侧收到实时事件 |
| R7 | **错误码映射** | 把 `ErrorCode` 映射为稳定的整型错误码 + 英文 detail | Kotlin 能根据错误码显示用户文案 |
| R8 | **panic 捕获与日志** | FFI 边界加 `catch_unwind` + `tracing` 输出到 Android logcat | Rust panic 不导致 App 崩溃，logcat 可见 |
| R9 | **Android 交叉编译配置** | `cargo-ndk` 配置 + CI job，输出 `aarch64-linux-android` `.so` | CI 能编译出 `libtermfast.so` |

### 2.2 Android 原生层（Kotlin + NDK）

| 编号 | 技术点 | 详细说明 | 验收标准 |
|------|--------|---------|---------|
| K1 | **VpnService 基础实现** | `SshVpnService : VpnService()`，处理 `onCreate/onStartCommand/onDestroy`，调用 `Builder.establish()` | 能创建 TUN 并拿到 fd |
| K2 | **VPN 权限管理** | 启动前检查 `VpnService.prepare()`，引导用户授权；保存授权状态 | 用户未授权时不崩溃，授权后正常建立 |
| K3 | **前台 Service + 通知** | `startForeground()` + 持久通知；通知含“断开 / 打开主界面”action | Service 在通知栏常驻，系统不杀 |
| K4 | **TUN fd 传递与 tun2socks 启动** | NDK 加载 `libtun2socks.so`，把 fd、SOCKS5 地址、MTU 传给 native main loop | tun2socks 成功接管 TUN |
| K5 | **路由与 DNS 配置** | `Builder.addAddress/addRoute/addDnsServer/setMtu`；v1 默认 `0.0.0.0/0` 全隧道 | 开启 VPN 后系统所有流量走 TUN |
| K6 | **自身流量保护** | `addDisallowedApplication(packageName)` 或 `protect(socketFd)`，避免 SSH 流量被 VPN 回收 | `curl` 经 VPN 外连时，SSH 连接不进入死循环 |
| K7 | **Android Keystore** | 用 `KeyStore.getInstance("AndroidKeyStore")` 实现加密存储；RSA/AES 方案 | 凭证只在本机可用，导出后不可解密 |
| K8 | **SharedPreferences 配置存储** | 实现 `ConfigStorage` trait 的 JNI 回调：JSON 字符串存 `SharedPreferences` | 配置持久化、迁移、备份 |
| K9 | **网络状态监听** | `ConnectivityManager.NetworkCallback` 监听 Wi-Fi/蜂窝切换，通知 Rust pause/resume | 断网后触发器暂停，恢复后重连 |
| K10 | **自启动与保活** | `BOOT_COMPLETED` 接收器 + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + 厂商设置引导 | 重启后自动启动 VPN；主流机型能常驻 |
| K11 | **通知系统** | `NotificationManager` + 通知渠道；触发器/连接状态/hostkey 事件发本地通知 | 用户收到可点击通知 |
| K12 | **剪贴板/分享** | `ClipboardManager` 端口复制；`ACTION_SEND` 分享配置 | 与桌面功能对等 |
| K13 | **Quick Settings Tile** | 提供系统下拉开关 VPN 的磁贴 | 用户无需开 App 即可开关 |

### 2.3 UI 层（Tauri Mobile / React）

| 编号 | 技术点 | 详细说明 | 验收标准 |
|------|--------|---------|---------|
| F1 | **移动端导航架构** | 底部 Tab：服务器 / 日志 / 设置；列表页 → 详情全屏页 | 不再使用桌面三栏布局 |
| F2 | **服务器列表卡片** | 显示名称、状态、出口 IP、连接开关、代理开关 | 状态实时更新 |
| F3 | **添加服务器流程** | 快速模式 3 步：VPS 信息 → 测试连接 → 完成；高级模式展开认证/触发器 | 与桌面流程一致但适配小屏 |
| F4 | **服务器详情页** | Tab：概览 / 认证 / 代理 / 触发器 / 日志；全屏滚动 | 信息与桌面一致 |
| F5 | **触发器编辑器** | CodeMirror mobile 版或简化文本框；模板库、占位符提示 | 能新建/编辑/删除触发器 |
| F6 | **日志面板** | 按服务器/类型筛选、搜索、时间线 | 与后端日志回调联动 |
| F7 | **VPN 开关与状态** | 首页显式“连接 VPN”按钮；状态栏显示当前服务器、出口 IP | 用户一键开关 |
| F8 | **设置页** | 通用设置、语言、主题、通知权限、电池优化跳转 | 与桌面功能对齐 |
| F9 | **引导与权限** | 首次启动：VPN 授权 → 电池优化 → 添加服务器 | 用户不被卡在某一步 |
| F10 | **错误与空状态** | 连接失败、授权被拒、后台被杀均有对应 UI | 用户知道下一步怎么做 |

### 2.4 基础设施与工程化

| 编号 | 技术点 | 详细说明 | 验收标准 |
|------|--------|---------|---------|
| I1 | **Tauri Android 项目结构** | `src-tauri/gen/android` 或独立 mobile 项目；正确引用 Rust `.so` | `cargo tauri android init/build` 成功 |
| I2 | **cargo-ndk 配置** | 配置 NDK 路径、目标 ABI（arm64-v8a、可选 armeabi-v7a） | 一键编译 `libtermfast.so` |
| I3 | **tun2socks 构建脚本** | NDK CMake 或 ndk-build 编译 badvpn-tun2socks；打包进 APK/AAB | APK 内含 `libtun2socks.so` |
| I4 | **签名与打包** | 发布 keystore、AAB/APK 构建、版本号管理 | 能打出 release AAB |
| I5 | **CI Android job** | GitHub Actions：安装 NDK → cargo-ndk 编译 → Tauri Android build → 上传 AAB | 每次 PR 自动构建 |
| I6 | **真机测试矩阵** | Pixel/Samsung/小米/OPPO/vivo/华为；Android 10/12/14/16 | 至少 6 台真机跑通核心流程 |
| I7 | **崩溃上报（可选）** | 接入 Firebase Crashlytics；注意过滤 SSH 凭据和 IP | 崩溃可追踪但不泄露敏感信息 |
| I8 | **隐私政策与上架材料** | Google Play 数据安全表单、权限声明、截图、视频 | 满足上架要求 |

<!-- === SECTION 2 END === -->

---

## 3. 开发流程

### 3.1 分支与协作模型

```
main (稳定，可发版)
  └── develop（日常集成）
        └── feat/R01-tokio-runtime
        └── feat/K03-foreground-service
        └── feat/F02-server-list-mobile
        └── spike/tun2socks-badvpn
```

- 每个技术点一个 feature 分支；
- Rust 改动和 Kotlin 改动尽量在同一 PR，避免接口不匹配；
- PR 必须通过：CI（Android build + Rust test + tsc）+ 1 人代码审查 + 对应 spike/测试通过；
- `develop` 每周至少合并一次； milestone 结束后从 `develop` 切 `release/x.x.x`。

### 3.2 接口契约先行

**FFI 接口必须先用文档/伪代码确定，再并行实现两侧。** 建议流程：

1. Rust 侧先定义 C ABI 头文件（用 `cbindgen` 生成 `.h`）；
2. Kotlin 侧按头文件写 JNI 封装；
3. 双方约定事件 JSON schema；
4. 接口冻结后再进入 UI 开发。

示例 ABI：
```c
// vpg_android.h
int32_t vpg_android_init(const char* config_json);
int32_t vpg_android_add_server(const char* server_json);
int32_t vpg_android_connect_server(const char* server_id);
int32_t vpg_android_disconnect_server(const char* server_id);
int32_t vpg_android_start_socks5(uint16_t port);
int32_t vpg_android_stop_socks5(void);
void    vpg_android_subscribe_events(void (*callback)(const char* event_type, const char* payload));
```

### 3.3 测试策略

| 层级 | 测试内容 | 工具 | 频率 |
|------|---------|------|------|
| Rust 单元 | core 逻辑、FFI 边界 | `cargo test` | 每次 PR |
| Rust Android 集成 | 在 Android 模拟器/真机上跑 core 测试 | `cargo test --target aarch64-linux-android` | 每晚 |
| Kotlin 单元 | Keystore、配置、通知工具类 | JUnit + Robolectric | 每次 PR |
| Instrumented | VpnService、前台 Service、JNI 调用 | AndroidJUnit + Espresso | 每晚 |
| VPN/tun2socks 集成 | 真机流量转发、DNS、断线重连 | 手动 + 自动化脚本 | 每周 |
| E2E | 添加服务器 → 连接 → 开 VPN → 浏览器访问 Google | Playwright Mobile / Appium / 手动 | milestone |
| 稳定性 | 24/48 小时后台保活、耗电 | Firebase Test Lab / 手动 | release 前 |

### 3.4 Code Review 重点

- FFI 边界：内存安全（`CString` 释放、null 检查）、panic 捕获；
- Kotlin：权限处理是否完整、Service 生命周期是否正确；
- Android 资源： wake lock / 前台通知是否滥用；
- 日志：不打印密码、密钥、IP（至少 release build 不打印）；
- 性能：VPN 路径避免频繁对象分配；tun2socks 线程数控制。

<!-- === SECTION 3 END === -->

---

## 4. 开发阶段与里程碑

> 以下按 3 人团队、每周 5 个工作日估算，为经验范围，非精确排期。关键路径取决于 tun2socks spike 和保活测试结果。

### Phase 0：筹备与 spike（5-7 周）

| 周 | 任务 | 产出 | 关键决策 |
|----|------|------|---------|
| 1-2 | 项目脚手架 | Android Studio + Tauri Mobile 项目跑通；`cargo-ndk` 能编译 `libtermfast.so` | UI 路线锁定为 Tauri Mobile |
| 2-3 | spike 1：core FFI | Android 真机能 `init/add_server/connect/disconnect`，状态回调到 Kotlin | 确认 FFI 方案可行 |
| 3-5 | spike 2：tun2socks | NDK 编译 badvpn-tun2socks；TUN fd 传入后流量转给本地 mock SOCKS5 | 确认 tun2socks 可用；否则换 outline |
| 4-6 | spike 3：VpnService + 通知 | Service 启动、TUN 建立、前台通知常驻 | 确认 VpnService 基础链路 |
| 5-7 | spike 4：后台保活 | 目标机型 24 小时 Service 存活测试 | 确认保活策略可行；厂商适配难度 |
| 7 | spike 评审 | Go/No-Go 决策文档 | 决定是否进入 Phase 1 |

**Go 标准**：
- core FFI 能在真机稳定 connect/disconnect；
- tun2socks 能把 TUN 流量转成至少 TCP SOCKS5；
- Service 在 2 款主流机型（Pixel + 一个国产 ROM）上 24 小时不被系统彻底杀死（允许重连）。

### Phase 1：核心平台层（6-8 周）

| 周 | 任务 |
|----|------|
| 1-2 | R1-R3：tokio runtime、配置 JSON 桥接、Keystore credential |
| 2-4 | R4-R6：ServerManager FFI、SOCKS5 启动/停止、事件回调 |
| 3-5 | K1-K3：完整 VpnService、权限、前台通知 |
| 4-6 | K4-K6：tun2socks 集成、路由/DNS、自身流量保护 |
| 6-8 | I1-I3：Tauri Android 项目、cargo-ndk、tun2socks 构建脚本 |

**里程碑 M1**：手机上能“添加服务器 → 连接 → 开 VPN → 浏览器访问 Google”。

### Phase 2：UI 与交互（5-7 周）

| 周 | 任务 |
|----|------|
| 1-2 | F1-F3：底部导航、服务器列表、添加服务器流程 |
| 2-4 | F4-F6：详情页、触发器编辑器、日志面板 |
| 3-5 | F7-F9：VPN 开关、设置页、引导与权限 |
| 5-7 | F10 + UI 打磨：错误状态、动画、暗色模式、中文/英文 |

**里程碑 M2**：用户可完整完成“添加 VPS → 配置触发器 → 开关 VPN → 查看日志”全流程。

### Phase 3：后台稳定性与系统整合（4-6 周）

| 周 | 任务 |
|----|------|
| 1-2 | K7-K10：Keystore 完善、SharedPreferences 配置、网络监听、自启动/保活 |
| 2-4 | K11-K13：通知系统、剪贴板、Quick Settings Tile |
| 3-5 | 真机矩阵测试：三星/小米/OPPO/vivo/华为/Pixel；Android 10/12/14/16 |
| 4-6 | 稳定性调优：耗电、重连、Doze 模式、崩溃修复 |

**里程碑 M3**：在 6 款以上真机上稳定运行 48 小时。

### Phase 4：工程化与上架（3-4 周）

| 周 | 任务 |
|----|------|
| 1-2 | I4-I5：签名、release AAB、CI Android job |
| 2-3 | I6-I7：Firebase Test Lab、崩溃上报、测试报告 |
| 3-4 | I8：隐私政策、Play 商店素材、数据安全表单 |

**里程碑 M4**：Google Play 内部测试轨道可安装。

### 总体周期

| 阶段 | 乐观 | 一般 | 悲观 |
|------|------|------|------|
| Phase 0 spike | 5 周 | 6 周 | 8 周 |
| Phase 1 核心平台层 | 6 周 | 7 周 | 9 周 |
| Phase 2 UI | 5 周 | 6 周 | 8 周 |
| Phase 3 稳定性 | 4 周 | 5 周 | 7 周 |
| Phase 4 上架 | 3 周 | 3 周 | 4 周 |
| **合计** | **23 周** | **27 周** | **36 周** |

> 换算：一般情况约 **6.5 个月**，3 人并行；若团队只有 2 人，约 **9-12 个月**。若 spike 阶段 tun2socks 不可行，整体可能再加 4-6 周选型/自研。

<!-- === SECTION 4 END === -->

---

## 5. 团队分工

| 角色 | 主要职责 | 占比 |
|------|---------|------|
| **Rust 工程师** | `crates/android-ffi`、FFI 设计、core 适配 Android、tun2socks 选型/集成、性能调优 | 80% 投入 |
| **Android 原生工程师** | VpnService、前台 Service、Keystore、SharedPreferences、通知、自启动、NDK 桥接 | 80% 投入 |
| **前端工程师** | Tauri Mobile 项目、React UI 重写、移动布局、权限引导、设置/日志 | 60% 投入 |
| **兼职 PM/测试**（可产品/QA 兼任） | 真机矩阵测试、Play 商店上架、用户文档 | 按需 |

**协作节奏**：
- 每日 15 分钟站会；
- 每周三技术同步（Rust ↔ Android ↔ UI 接口对齐）；
- 每 milestone 结束做一次真机 demo + Go/No-Go 评审。

<!-- === SECTION 5 END === -->

---

## 6. 关键路径与依赖

```
[spike: core FFI] ──────┐
                         ▼
[spike: tun2socks] ──> [Phase 1: VPN 平台层] ──> [Phase 2: UI] ──> [Phase 3: 稳定性]
                         ▲                                              │
[spike: VpnService] ─────┘                                              ▼
                                                                  [Phase 4: 上架]
```

**关键路径**：tun2socks spike → Phase 1 VPN 平台层 → Phase 3 稳定性。

**最大外部依赖**：
1. **tun2socks 选型**：badvpn 是否能在目标 NDK 上稳定编译运行；
2. **国产 ROM 适配**：保活策略在小米/华为/OPPO/vivo 上差异巨大；
3. **Google Play 政策**：VPN/代理类 App 的审核可能更严格，需要隐私政策和数据安全声明。

<!-- === SECTION 6 END === -->

---

## 7. 风险与应对

| 风险 | 阶段 | 影响 | 应对 |
|------|------|------|------|
| tun2socks 在 Android 上不稳定 | Phase 0 | 高 | spike 阶段换 outline/hev；若都不行，考虑降级为“仅 SOCKS5”或延迟 Android 计划 |
| 国产 ROM 杀后台严重 | Phase 3 | 高 | 真机矩阵尽早测；引导用户手动设置；必要时在 UI 中显式提示 |
| FFI 接口频繁变更导致 Android/Kotlin 返工 | Phase 1 | 中 | 接口契约先行；用 `cbindgen` 生成头文件；接口冻结后再写 UI |
| VpnService 审核被拒 | Phase 4 | 中 | 提前准备隐私政策；明确说明是用户自有 VPS 代理工具，非公共 VPN 服务 |
| 前端 Tauri Mobile 对 Service/VpnService 支持不足 | Phase 1 | 中 | 准备 fallback：用 Kotlin 原生 Activity + WebView 或 React Native |
| 团队 Rust/NDK 经验不足 | 全程 | 高 | Phase 0 先跑通最小可运行 demo，验证团队技术栈驾驭能力 |
| 与桌面端 core 改动冲突 | 全程 | 中 | 桌面 v1/v2 修复 Bug 时同步回 core；移动端不直接改 core 接口语义 |

<!-- === SECTION 7 END === -->

---

## 8. 验收标准（M4 / Google Play 内测）

| 维度 | 标准 |
|------|------|
| 功能 | 添加服务器、连接/断开、开关 VPN、触发器模板、日志查看、设置、导出/导入配置 |
| 网络 | 开启 VPN 后，Chrome 能访问 Google；关闭 VPN 后恢复正常 |
| 稳定性 | 6 款真机各连续运行 48 小时，每 4 小时执行一次 trigger 命令，无崩溃、无内存异常增长 |
| 后台 | 锁屏 12 小时后，Service 仍存活或能在 30 秒内自动重连 |
| 耗电 | 空载待机 8 小时耗电 < 5%（对比关闭 VPN） |
| 安全 | 密码/密钥 passphrase 存 Keystore；release build 日志不打印凭据、IP、命令原文 |
| 上架 | 通过 Google Play 内部测试；隐私政策、数据安全表单完整 |

<!-- === SECTION 8 END === -->

---

## 9. 立即可执行的第一步

如果要启动项目，本周内应完成：

1. **确认团队**：至少 1 Rust + 1 Android 原生；
2. **确认真机设备清单**：Pixel + 小米/华为/OPPO/vivo 各一台；
3. **跑通最小 demo**：
   ```bash
   cargo ndk -t arm64-v8a build -p termfast-core
   # + Android Studio 新建项目加载 .so
   # + 调用一个 Rust 函数返回字符串
   ```
4. **写 spike 计划文档**：明确 4 个 spike 的通过/失败标准和时间盒；
5. **接口契约文档**：先定义 `vpg_android.h` 初版，三方对齐。

<!-- === SECTION 9 END === -->

---

*本计划为 Android 版 TermFast 的落地执行框架，实际周期受 spike 结果、团队经验和厂商适配影响。建议以 Phase 0 的 Go/No-Go 评审作为继续投入的关键节点。*
