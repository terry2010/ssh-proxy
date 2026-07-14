# TermFast — 详细开发计划

> 本文档是技术部门负责人基于 [termfast-project.md](./termfast-project.md) 设计文档制定的开发计划。
> 每个功能点严格遵循 **开发 → 测试 → E2E 测试 → 对比文档确认功能是否完成 → 未完成则补完 / 已完成则进入下一功能点** 的流程。

---

## 0. 总则

### 0.1 开发流程定义（每个功能点必须执行）

```
┌─────────────────────────────────────────────────────────────┐
│  功能点开发流程（不可跳过任何环节）                              │
│                                                             │
│  1. 开发                                                     │
│     ├── 按 design doc 规格实现代码                             │
│     ├── 遵循现有代码风格和架构约定                                │
│     └── 实现过程中记录与 design doc 的偏差（如有）                 │
│                                                             │
│  2. 单元测试                                                  │
│     ├── 为每个公开 API 编写单元测试                              │
│     ├── 覆盖正常路径 + 边界 case + 错误路径                       │
│     └── cargo test / vitest 全部通过                          │
│                                                             │
│  3. 集成测试（如涉及多模块交互）                                  │
│     ├── 用 mock SSH server 测试 SSH 相关逻辑                   │
│     └── 验证模块间接口契约                                      │
│                                                             │
│  4. E2E 测试                                                 │
│     ├── 模拟真实用户操作流程                                     │
│     ├── 覆盖 design doc 中的用户故事和任务流                       │
│     └── Tauri WebDriver / Playwright 驱动 GUI                 │
│                                                             │
│  5. 对比文档确认                                                │
│     ├── 逐条核对 design doc 中该功能点的验收标准                    │
│     ├── 列出已实现 / 未实现 / 偏差项                              │
│     └── 未实现项必须补完，偏差项需记录理由                           │
│                                                             │
│  6. 进入下一功能点                                              │
│     └── 上一功能点 100% 通过文档确认后才能开始                      │
└─────────────────────────────────────────────────────────────┘
```

### 0.2 技术栈确认

| 层 | 技术 | 版本约束 |
|----|------|---------|
| Rust | workspace + crates | edition 2021, rust 1.75+ |
| SSH | `russh` | `0.62.2`（最新稳定版，见 §20.0） |
| 异步运行时 | `tokio` | 1.x |
| 序列化 | `serde` + `serde_json` | 1.x |
| 加密 | `ring`（禁用 openssl，见 §20.1）、`aes-gcm 0.10`、`argon2 0.5` | — |
| 凭据 | `keyring` | 4.x（feature="*-native-keyring-store"） |
| 桌面框架 | `tauri` | v2 |
| 前端框架 | React 19 + TypeScript 7 | — |
| UI 组件 | shadcn/ui + Tailwind CSS v4 | — |
| 国际化 | i18next 26 + react-i18next 17 | — |
| 状态管理 | `zustand` | 5.x |
| 路由 | `react-router-dom` | 7.x |
| 日期 | `date-fns` | 4.x |
| 图标 | `lucide-react` | 1.x |
| 构建工具 | Vite 8 + Vitest 4 | — |
| 代码编辑器 | CodeMirror 6 | — |
| **CLI 参数解析** | `clap` | 4.x（derive feature） |
| **daemon IPC** | `tokio::net::UnixListener`（macOS/Linux）+ named pipe（Windows） | 内置 tokio + `tokio::net::windows::named_pipe` |
| **CLI 输出** | `comfy-table`（表格）+ `serde_json`（--json） | — |
| 测试（Rust） | 内置 `#[test]` + `tokio::test` + `crates/test-utils`（mock SSH server） | — |
| 测试（E2E） | Tauri WebDriver + Selenium（首选）/ Playwright（降级） | Phase 0 spike 验证 |

### 0.3 分阶段总览

> **本次开发计划范围：Phase 0-9**（spike → 核心功能 → 前端 → 集成 E2E），对应设计文档 v1（macOS/Windows 桌面端，含 CLI）。
> **发布策略**：Phase 0-9 完成即发布 v1（开发者自构建 `.app`/`.exe`，无签名/自动更新）。
> **Phase 10 = v1.1**（签名打包 + 自动更新 + 崩溃上报），不在本次计划内，在 v1 发布后补充。
> **注意：Phase 10 (v1.1) ≠ v2**（v2 = Linux + 流量统计，是独立的后续计划）。

| 阶段 | 内容 | 前置条件 | 本次 |
|------|------|---------|:----:|
| Phase 0 | russh spike 验证 + 项目脚手架 + CI 搭建 | 无 | ✓ |
| Phase 1 | 核心基础设施：config / credential / error / **daemon IPC 协议** | Phase 0 | ✓ |
| Phase 2 | SSH 层：client / auth / exec / channel_opener | Phase 1 | ✓ |
| Phase 3 | 代理层：socks5 / http / channel_manager | Phase 2 spike 通过 | ✓ |
| Phase 4 | 触发器引擎：template / engine / ipcheck / health | Phase 2 | ✓ |
| Phase 5 | 服务器管理：manager / instance / lifecycle | Phase 2 + 3 + 4 | ✓ |
| Phase 6a | daemon + GUI 桥接 + CLI 客户端（核心通信链路） | Phase 1-5 | ✓ |
| Phase 6b | 桌面壳：tray / autostart / platform / 日志 / 通知 / 离线检测 / 窗口效果 | Phase 6a | ✓ |
| Phase 7 | 前端基础：项目搭建 / i18n / 布局 / 路由 | Phase 6 | ✓ |
| Phase 8 | 前端功能：引导 / 服务器列表 / 详情 Tab / 触发器编辑器 / 日志 / 设置 / **CLI 结果实时显示** | Phase 7 | ✓ |
| Phase 9 | 集成与 E2E + CLI + daemon E2E + **v1 发布前全量核对 + 发布产物定义** | Phase 8 | ✓ |
| Phase 10 | v1.1：签名打包 / 自动更新 / 崩溃上报 | Phase 9 | ✗ v1.1 |

<!-- === SECTION 0 END === -->

---

## Phase 0：russh Spike 验证 + 项目脚手架 + CI

> **这是项目的 Go/No-Go 里程碑**。spike 不通过不得开始写 `crates/core` 的代理代码（见 design doc §20.0）。

### FP-0.1 russh 并发 channel 压力 spike

**开发细节**：

1. 创建 `spikes/russh-stress/` 独立 crate（不进 main 分支，不依赖 core）
   - `Cargo.toml`：只依赖 `russh 0.62.2` + `tokio` + `ring`
   - `src/main.rs`：最小代理原型（SOCKS5 监听 → SSH direct-tcpip channel → 转发）
   - `src/stress_test.rs`：S1-S8 测试场景
   - `src/mock_ssh.rs`：mock SSH server（用 russh server 模式）

2. 实现 S1-S9 测试场景（见 design doc §20.0 Spike 验证清单）：

| # | 场景 | 实现方式 | 通过标准 |
|---|------|---------|---------|
| S1 | 50+ 并发 direct-tcpip channel | 并发开 50 个 channel 下载小文件 | 与 `ssh -D` 对比延迟差 <2x |
| S2 | 100MB 大文件下载 | 通过代理下载 100MB 文件 | 完成，无 100% CPU，无 deadlock |
| S3 | 浏览器多标签（20+ channel） | 模拟 20 个并发 HTTP 请求 | 所有请求正常完成 |
| S4 | 24h 持续流量 + 定期 exec | 持续跑流量，每小时 exec 一次 | 无内存泄漏，无 channel 累积 |
| S5 | 代理 channel + exec channel 混合 | 同时跑代理流量和 exec 命令 | exec 不被代理阻塞 |
| S6 | 远端慢响应（500ms delay） | `tc qdisc add delay 500ms` | 无 deadlock，channel 正常关闭 |
| S7 | 长命令执行（apt update） | exec `apt update` | channel 不中途关闭 |
| S8 | 断线重连 100 次 | 循环断开重连 | 每次重连后代理恢复，无 channel 泄漏 |
| S9 | ring-only 编译 + 交叉编译 | 禁用 openssl feature，交叉编译 Android/iOS | 编译通过 |

3. 用真实 VPS 跑 S1-S8（S9 只需编译验证）

> **测试 VPS 凭据注入方式**（不写入文档/代码/git）：
> - 凭据通过环境变量注入：`TERMFAST_TEST_HOST` / `TERMFAST_TEST_PORT` / `TERMFAST_TEST_USER` / `TERMFAST_TEST_PASS`
> - 本地开发：放在 `.env` 文件（已加入 `.gitignore`），用 `dotenvy` crate 读取
> - CI 环境：通过 GitHub Actions Secrets 注入
> - spike 脚本 `spikes/russh-stress/` 从环境变量读取，不硬编码任何凭据
> - 用途：russh spike 压力测试、代理流量测试、触发器命令测试、E2E 全流程验证
> - 安全要求：凭据不进入正式配置文件/钥匙串，spike 代码中无明文 IP/用户/密码

**单元测试**：spike 本身就是测试，无额外单元测试

**E2E 测试**：spike 即 E2E（真实 VPS + 真实流量）

**文档确认清单**：
- [ ] S1-S9 全部通过？记录每项结果
- [ ] 如有失败，记录应急方案选择（双连接 / 混合方案 / 定时重连 / No-Go）
- [ ] Go/No-Go 决策文档已归档？
- [ ] spike crate 已删除（通过后）或保留备查（失败后）？

### FP-0.2 项目脚手架搭建

**开发细节**：

1. 初始化 Cargo workspace：
   ```
   termfast/
   ├── Cargo.toml                    # workspace 根
   ├── crates/
   │   ├── core/Cargo.toml           # 平台无关核心（不依赖 tauri）
   │   ├── credential/Cargo.toml     # 凭据抽象（feature gate）
   │   ├── daemon/Cargo.toml         # daemon 进程（持有 core 运行时，socket server）
   │   ├── cli/Cargo.toml            # CLI 客户端（独立二进制，连 daemon socket）
   │   ├── test-utils/Cargo.toml     # 测试工具（mock SSH server，dev-dependencies）
   │   └── desktop/Cargo.toml        # Tauri 桌面壳（GUI 客户端，连 daemon socket）
   ```
   - `crates/core` 依赖：`russh 0.62.2`（禁用 openssl）、`tokio`、`serde`、`ring`（不依赖 daemon/tauri，保证移动端交叉编译）
   - `crates/credential` 依赖：`core` + `keyring`（feature="*-native-keyring-store"）
   - `crates/daemon` 依赖：`core` + `credential`（持有所有运行时状态，不依赖 tauri，可独立运行）
   - `crates/cli` 依赖：`daemon`（只用 proto 类型）+ `clap`（CLI 参数解析）
   - `crates/desktop` 依赖：`core` + `credential` + `daemon`（内嵌 daemon）+ `tauri`

2. **进程模型**（混合 daemon 架构）：
   ```
   termfast（无参数）→ Tauri 进程内嵌 daemon + 打开 GUI 窗口
                        CLI 连这个 socket，操作实时推送给 GUI
                        GUI 退出 → daemon 也退出

   termfast --daemon → 独立 daemon 进程，无 GUI（无头服务器/自动化场景）

   termfast status/trigger/... → CLI 客户端，连已运行的 daemon socket

   ┌─────────────────────────────────────────────────────┐
   │           Tauri 进程（GUI 模式）                       │
   │  ┌───────────────────────────────────────────────┐  │
   │  │  内嵌 daemon（daemon_embed.rs）                  │  │
   │  │  持有 crates/core 所有运行时状态                   │  │
   │  │  SSH 连接 / 代理 / 触发器引擎 / 配置 / 日志        │  │
   │  │  监听 daemon.sock（文件权限 600）                  │  │
   │  │                                                │  │
   │  │  ┌──────────┐        ┌──────────┐             │  │
   │  │  │ GUI 前端  │        │ CLI 连接  │             │  │
   │  │  │ (Tauri   │        │ (cli bin) │             │  │
   │  │  │  IPC桥接) │        │           │             │  │
   │  │  └─────┬────┘        └─────┬────┘             │  │
   │  │        └───────┬───────────┘                   │  │
   │  │                ▼                               │  │
   │  │      core API 调用 + 事件广播                    │  │
   │  └───────────────────────────────────────────────┘  │
   └─────────────────────────────────────────────────────┘
   ```
   - **混合 daemon**：GUI 模式下 daemon 就是 Tauri 进程的一部分，无需"自动启动 daemon 子进程"的复杂流程
   - CLI 操作 → daemon 执行 → 事件广播给所有连接的客户端（含 GUI）→ UI 实时更新
   - 无头模式（`--daemon`）：独立 daemon 进程，CLI 全功能可用
   - **为什么用本地 socket 而非 HTTP**：无端口占用（用文件路径）、文件权限 600 隔离（只有当前用户能连接）、零网络暴露（外部电脑物理上不可能访问）

3. 初始化 Tauri v2 项目（`cargo tauri init`）
   - 前端目录 `src/`（React + TypeScript + Vite）
   - `tauri.conf.json` 基础配置

4. 初始化前端项目：
   - `package.json`：React 19 + TypeScript + Vite + Tailwind CSS v4 + shadcn/ui
   - `tsconfig.json`：strict mode
   - `i18n/` 目录结构

5. 创建 `.gitignore`（target/、node_modules/、dist/）

**单元测试**：
- `cargo check --workspace` 通过
- `cargo build -p termfast-core --target aarch64-linux-android` 通过（交叉编译验证，见 §20.1）
- `cargo build -p termfast-core --target aarch64-apple-ios` 通过
- `npx tsc --noEmit` 通过

**E2E 测试**：`cargo tauri dev` 能启动空白窗口

**文档确认清单**：
- [ ] 目录结构与 design doc §4.2 一致（含 `crates/daemon` + `crates/cli`）？
- [ ] `crates/core` 不依赖 `tauri`？（`cargo tree -p termfast-core | grep tauri` 无输出）
- [ ] `crates/core` 不依赖 `crates/daemon`？（保持移动端交叉编译能力）
- [ ] `crates/daemon` 不依赖 `tauri`？（可独立运行 `--daemon` 无头模式）
- [ ] russh 禁用了 openssl feature？
- [ ] 交叉编译 Android + iOS 通过？

### FP-0.3 E2E 工具链 spike + CI 搭建

**开发细节**：

#### Part 1：E2E 工具链 spike（Tauri WebDriver 验证）

> Tauri v2 的 WebDriver 支持相对较新，在 Phase 0 验证工具链可用性，避免到 Phase 9 才发现不可用。

1. **Tauri WebDriver spike**（§16.7）：
   - 安装 `tauri-driver` + `selenium-webdriver`（或 Playwright + Tauri WebDriver 桥接）
   - 验证能否启动 Tauri 窗口 → 定位元素 → 模拟点击 → 读取文本
   - 最小验证脚本：启动空白 Tauri 窗口 → 找到标题栏 → 点击按钮 → 验证文本变化
   - macOS + Windows 双平台各跑一次（Linux 不在 v1 范围但 CI 矩阵需要）

2. **Spike 验收标准**：
   - S-E2E-1：Tauri WebDriver 能启动 Tauri v2 窗口？
   - S-E2E-2：能通过 CSS selector / accessibility id 定位元素？
   - S-E2E-3：能模拟点击 + 输入文本？
   - S-E2E-4：能读取窗口内文本内容（断言用）？
   - S-E2E-5：macOS + Windows 双平台都通过？
   - 如失败：降级为 Playwright + WebView2/WebKit 直接驱动（绕过 Tauri WebDriver），记录决策

3. **E2E 测试框架选型**（spike 后确定）：
   - 方案 A（首选）：Tauri WebDriver + Selenium（官方推荐）
   - 方案 B（降级）：Playwright 直接驱动 WebView（macOS WebKit / Windows WebView2）
   - 方案 C（最后手段）：Tauri IPC mock + Vitest 组件测试（无真实窗口，覆盖度低）

#### Part 2：CI 搭建

4. GitHub Actions workflow `.github/workflows/ci.yml`（§16.5 平台矩阵）：

**开发细节**：

1. GitHub Actions workflow `.github/workflows/ci.yml`（§16.5 平台矩阵）：
   - **三平台矩阵**：`macos-latest` / `windows-latest` / `ubuntu-latest`（§16.5，`keyring`/`window-vibrancy`/`autostart` 平台行为差异需各自验证）
   - `cargo check --workspace`
   - `cargo test --workspace`
   - `cargo clippy --workspace -- -D warnings`（v1 起强制 `-D warnings`，与 §16.5 一致）
   - `npx tsc --noEmit`
   - `npx vitest run`（前端单元测试）

2. 交叉编译检查（§16.5，只在 Ubuntu runner 跑，NDK 安装最方便）：
   - `cargo build -p termfast-core --target aarch64-linux-android`
   - `cargo build -p termfast-core --target aarch64-apple-ios`（在 macOS runner 跑）

3. E2E CI 集成（§16.7，macOS + Windows 矩阵）：
   - `pnpm tauri dev &` → `sleep 10` → `pnpm playwright test --project=tauri`
   - mock SSH server 作为子进程启动

4. 可选真实 VPS 冒烟测试（§16.6）：
   - 环境变量 `TERMFAST_SMOKE_TEST=1` 开启，默认跳过
   - SSH 凭据通过 CI Secrets 注入（`TERMFAST_TEST_HOST` 等，见 FP-0.1）

5. PR 触发 CI，main 分支合并要求 CI 通过

**文档确认清单**：
- [ ] E2E 工具链 spike S-E2E-1~5 全部通过？记录每项结果
- [ ] 如有失败，记录降级方案选择（Playwright / IPC mock）+ 决策文档
- [ ] CI 三平台矩阵（macOS/Windows/Ubuntu）？（§16.5）
- [ ] CI 包含交叉编译检查（§16.5/§20.1）？
- [ ] CI clippy `-D warnings` 强制？（§16.5）
- [ ] CI 包含 clippy + test + tsc + vitest？
- [ ] E2E CI 集成（macOS + Windows）？（§16.7）
- [ ] 可选真实 VPS 冒烟测试？（§16.6）

<!-- === SECTION 1 (Phase 0) END === -->

---

## Phase 1：核心基础设施（config / credential / error）

### FP-1.1 错误类型系统

**开发细节**：

1. `crates/core/src/error.rs`：定义 `ErrorCode` 枚举 + `IpcError` 结构体
   ```rust
   pub enum ErrorCode {
       PortConflict, AuthFailed, SshConnectFailed, HostKeyMismatch,
       ConfigCorrupt, DecryptionFailed, NeedsPrivilege, Internal,
       DuplicateServer, // ... 按 design doc §3 补全
   }
   pub struct IpcError {
       pub code: ErrorCode,    // 语言无关
       pub detail: String,     // 英文调试信息
   }
   ```
   - 后端只返回 `ErrorCode` + 英文 `detail`，不返回翻译文案（§3）
   - `IpcError` 实现 `Serialize` 供 IPC 传递

2. `thiserror` 定义内部 `Error` 类型（区分 IPC 错误和内部错误）

**单元测试**：
- `ErrorCode` 所有变体序列化/反序列化正确
- `IpcError` 序列化 JSON 格式符合 `{ "code": "...", "detail": "..." }`

**文档确认清单**：
- [ ] `IpcError` 无 `message` 字段（只有 `code` + `detail`）？（§3）
- [ ] 后端不返回翻译文案？

### FP-1.2 配置结构定义与序列化

**开发细节**：

1. `crates/core/src/config/config.rs`：定义配置结构体，对应 design doc §7.2 schema
   - `Config`（顶层：version, general, trigger_templates, servers）
   - `GeneralConfig`（auto_start, minimize_to_tray, theme, language, log_*, system_proxy_server_id, proxy_test_url, crash_reporting, suppress_firewall_badge 等）
   - `TriggerTemplate`（id, name, type, description, built_in, template_version, parameters_schema, commands, check_target, check_interval）
   - `ServerConfig`（id, name, ssh, proxy, reconnect, ip_check, last_known_ip, triggers, suppress_firewall_badge）
   - `SshConfig`（host, port, user, auth_method, key_path, key_auto_generated, connection_mode, skip_hostkey_verify）
   - `ProxyConfig`（enabled, socks5_port, http_port, max_channels, channel_idle_timeout）
   - `TriggerInstance`（id, template_id, enabled, continue_on_error, timeout_secs, notify_on_success, notify_on_failure, parameters, commands, template_hash_at_addition, cooldown_secs）
   - `TriggerType` 枚举：OnConnect, OnReconnect, OnIpChange, OnProcessDead, OnPortClosed, ManualFire

2. 所有结构体 `derive(Serialize, Deserialize, Clone, Debug)`，用 `serde` rename 到 snake_case

3. 默认值实现：`Config::default()` 返回含内置模板的默认配置（见 §14）

**单元测试**：
- 配置序列化 → JSON → 反序列化 round-trip 一致
- 默认配置包含 5 个内置模板（§14.1-14.5）
- 缺失字段时反序列化使用默认值（`#[serde(default)]`）
- `version` 字段默认为 1

**文档确认清单**：
- [ ] schema 与 §7.2 完全一致？
- [ ] 内置模板 5 个（firewalld / ufw / 重启进程 / Telegram 通知 / 端口存活）？
- [ ] `trigger_templates` 的 `parameters_schema` 字段已定义？
- [ ] `template_hash_at_addition` 字段在 `TriggerInstance` 中？

### FP-1.3 ConfigStorage trait + FileConfigStorage

**开发细节**：

1. `crates/core/src/config/storage.rs`：定义 trait
   ```rust
   pub trait ConfigStorage: Send + Sync {
       fn load(&self) -> Result<String>;
       fn save(&self, content: &str) -> Result<()>;
   }
   ```

2. `crates/desktop/src/config_file.rs`：`FileConfigStorage` 实现
   - 路径：macOS `~/Library/Application Support/termfast/config.json`，Windows `%APPDATA%\termfast\config.json`，Linux `~/.config/termfast/config.json`
   - 首次创建时 `chmod 600`（§8.8 文件权限保护）
   - 原子写入：先写临时文件再 rename，避免写坏

3. `crates/core/src/config/manager.rs`：`ConfigManager`（并发写保护）
   ```rust
   pub struct ConfigManager {
       storage: Arc<dyn ConfigStorage>,
       lock: tokio::sync::Mutex<()>,
   }
   ```
   - `load()` / `save()` / `modify<F>(&self, f: F)` 方法（方法名与 design doc §11.5 一致）
   - `modify` 持锁 → load → 闭包修改 → save，保证 read-modify-write 原子性（§11.5）
   - **禁止直接调用 `save`**：所有模块必须用 `modify`，`save` 仅用于配置迁移等一次性场景（§11.5）
   - 原子写入用 `tempfile::NamedTempFile::persist()`（跨平台：Unix `rename` / Windows `MoveFileEx`，§11.5）

### FP-1.3b RuntimeStateManager（运行时状态独立存储）

**开发细节**：

1. `crates/core/src/config/runtime_state.rs`：`RuntimeStateManager`（§7.3 + §11.5）
   - **目的**：`last_known_ip`、触发器最近执行时间等高频变化数据独立存储，避免每次 IP 变化都全量重写 config.json 造成 SSD 写入放大
   - 独立文件 `runtime_state.json`，与 config.json 同目录
   - 结构（§11.5）：
     ```json
     {
       "servers": {
         "srv_tokyo": {
           "last_known_ip": "1.2.3.4",
           "last_trigger_executed_at": "2026-01-15T14:32:00Z"
         }
       }
     }
     ```
   - 持有**独立的 `tokio::sync::Mutex`**，不与 config.json 共享锁，避免高频写入阻塞用户配置操作（§11.5）
   - 同样走原子写入（`NamedTempFile::persist`）
   - 文件损坏时（JSON 解析失败）降级为空状态，触发全量检测重新填充，不影响用户配置（§11.5）

2. 数据存储分类（§11.5 高频更新与 SSD 磨损表）：

| 数据类型 | 存储位置 | 写入频率 | 说明 |
|---------|---------|---------|------|
| 用户配置（服务器、触发器、设置） | `config.json` | 低频（用户操作时） | `ConfigManager::modify` |
| `last_known_ip` | `runtime_state.json` | 中频（IP 变化时） | `RuntimeStateManager` |
| 触发器 cooldown 时间戳 | 内存 only | 高频（每次执行后） | 不持久化，重启后全量检测重新触发 |
| 触发器最近执行时间 | `runtime_state.json` | 中频（每次执行后） | `RuntimeStateManager` |

**单元测试**：
- `RuntimeStateManager` round-trip（save/load last_known_ip）
- 独立锁：`ConfigManager.modify` 和 `RuntimeStateManager.modify` 并发不阻塞
- 文件损坏 → 降级为空状态
- 原子写入

**文档确认清单**：
- [ ] `runtime_state.json` 独立于 config.json？（§7.3 + §11.5）
- [ ] 独立 `tokio::Mutex`，不与 config.json 共享锁？（§11.5）
- [ ] 文件损坏降级为空状态？（§11.5）
- [ ] `last_known_ip` 存 `runtime_state.json` 而非 config.json？（§7.3）

**FP-1.3 单元测试补充**：
- `FileConfigStorage` round-trip（用 tempdir）
- `ConfigManager` 并发写：10 个 task 同时 `modify`，最终结果一致无损坏
- 文件权限 600 验证（Unix）
- 原子写入：写入过程中模拟崩溃（kill），原文件不损坏
- `modify` 方法名与 design doc §11.5 一致（非 `update`）

**E2E 测试**：
- 启动 App → 配置文件自动创建在正确路径
- 修改设置 → 配置文件更新
- 删除配置文件 → 重启 App → 创建默认配置

**文档确认清单**：
- [ ] 存储路径与 §7.1 一致？
- [ ] 文件权限 600？
- [ ] `ConfigManager` 用 `tokio::sync::Mutex` 串行化？（§11.5）
- [ ] 原子写入实现？

### FP-1.4 配置迁移

**开发细节**：

1. `crates/core/src/config/migration.rs`：
   - `migrate(config: &mut Value, from_version: u32) -> Result<()>`
   - 链式迁移：v1→v2→v3...（§17.5）
   - 每步迁移：备份 → 迁移 → 验证 → 失败回滚
   - 迁移失败 UI 流程的错误数据结构（§17.5 配置迁移失败 UI）

2. 配置文件异常处理（§17.4）：
   - 文件不存在 → 创建默认配置
   - JSON 解析失败 → 备份为 `config.json.corrupt.{timestamp}`，创建默认配置
   - 版本号高于当前软件 → 拒绝加载

**单元测试**：
- v1→v2 迁移测试（模拟未来 schema 变更）
- 损坏 JSON → 备份 + 创建默认
- 版本号过高 → 拒绝加载
- 迁移失败 → 回滚

**文档确认清单**：
- [ ] 链式迁移实现？（§17.5）
- [ ] 损坏文件备份为 `config.json.corrupt.{timestamp}`？（§17.4）
- [ ] 迁移失败有回滚机制？

### FP-1.5 CredentialStore trait + 桌面实现

**开发细节**：

1. `crates/credential/src/lib.rs`：`CredentialStore` trait（§8.7）
   ```rust
   pub trait CredentialStore: Send + Sync {
       fn save(&self, key: &str, value: &str) -> Result<()>;
       fn load(&self, key: &str) -> Result<String>;
       fn delete(&self, key: &str) -> Result<()>;
       fn delete_all_for_server(&self, server_id: &str) -> Result<()>;
   }
   ```
   - Key 命名：`termfast::<server_id>::<credential_type>`

2. `crates/credential/src/keychain.rs`：桌面实现（feature="*-native-keyring-store"，用 `keyring` crate）
   - `delete_all_for_server`：按前缀 `termfast::<server_id>::` 遍历删除

3. 钥匙串异常降级（§17.3）：
   - 读取被拒 → 返回错误，GUI 处理
   - 条目不存在 → 返回 `NotFound` 错误
   - 服务不可用 → 降级为内存存储（`InMemoryCredentialStore`）

**单元测试**：
- `InMemoryCredentialStore` round-trip（save/load/delete/delete_all_for_server）
- Key 命名规则验证
- `delete_all_for_server` 只删除指定前缀的条目

**E2E 测试**（需真实 OS 钥匙串）：
- save → load → delete round-trip
- delete_all_for_server 清理指定服务器所有凭据

**文档确认清单**：
- [ ] trait 接口与 §8.7 一致？
- [ ] Key 命名 `termfast::<server_id>::<type>`？（§8.7）
- [ ] 降级为内存存储的实现？（§17.3）

### FP-1.6 加密导出/导入

**开发细节**：

1. `crates/core/src/migration.rs`（加密导出部分）：
   - `EncryptedBlob` 结构：`[magic(4B)][salt(16B)][nonce(12B)][ciphertext]`，magic = `VPG1`（§10.1）
   - `export_full(master_password, data) -> Result<Vec<u8>>`：Argon2id 派生密钥 → AES-256-GCM 加密
   - `import_full(master_password, blob) -> Result<FullExportData>`
   - 主密码强度校验：≥12 字符，含字母+数字（§10.1）
   - 导入错误 3 次锁定 5 分钟（§10.1 安全注意事项）

2. `FullExportData` 结构：所有服务器配置 + 所有模板 + 所有凭据 + 自动生成的密钥文件内容

**单元测试**：
- export → import round-trip
- 错误主密码 → 解密失败
- 密码强度校验：<12 字符拒绝、纯数字拒绝、纯字母拒绝
- 锁定逻辑：3 次错误后锁定 5 分钟

**文档确认清单**：
- [ ] 文件格式 `[magic][salt][nonce][ciphertext]`？（§10.1）
- [ ] Argon2id 密钥派生？
- [ ] AES-256-GCM？
- [ ] 密码强度 ≥12 字符 + 字母 + 数字？
- [ ] 3 次错误锁定 5 分钟？

### FP-1.7 daemon IPC 协议定义（socket 通信层）

> **这是 CLI + daemon 架构的协议基础**。所有 GUI 和 CLI 客户端通过此协议与 daemon 通信。
> 在 Phase 1 定义协议，Phase 6 实现 daemon server 端 + GUI/CLI client 端。

**开发细节**：

1. `crates/daemon/src/proto.rs`：IPC 协议定义（消息格式 + 请求/响应/事件）

   **传输层**：
   - macOS/Linux：`tokio::net::UnixListener`，socket 路径 `~/Library/Application Support/termfast/daemon.sock`（macOS）/ `~/.local/share/termfast/daemon.sock`（Linux）
   - Windows：`tokio::net::windows::named_pipe`，管道名 `\\.\pipe\termfast-daemon`
   - socket 文件权限 `600`（Unix），仅当前用户可连接
   - **Windows named pipe 安全**：`CreateNamedPipe` 时设置 `SECURITY_ATTRIBUTES`，DACL 仅允许当前用户 SID 访问（`ConvertStringSecurityDescriptorToSecurityDescriptor` + SDDL 字符串 `D:P(A;;GA;;;BA)(A;;GA;;;SY)(A;;GA;;;AU)` 限制为当前用户），拒绝其他用户连接
   - **为什么不用 HTTP**：无端口占用（用文件路径）、文件权限隔离（其他用户/进程无法访问）、零网络暴露（外部电脑物理上不可能访问）

2. **消息帧格式**（长度前缀 + JSON）：
   ```
   [4 字节长度（big-endian u32）][JSON payload]

   请求：{ "id": "uuid", "action": "connect_server", "params": { "server_id": "srv_xxx" } }
   响应：{ "id": "uuid", "ok": true, "data": {...} }
        或 { "id": "uuid", "ok": false, "error": { "code": "AuthFailed", "detail": "..." } }
   事件：{ "event": "server:status_changed", "data": { "server_id": "srv_xxx", "status": "connected" } }
   ```
   - `id`：请求 UUID，用于匹配响应（事件无 id，由 daemon 主动推送）
   - `action`：对应 §10.1-10.5 所有 IPC 命令（与 Tauri 命令名一致）
   - `error.code`：对应 §10.0 ErrorCode 枚举
   - 长度前缀避免粘包问题，支持流式读取

3. **协议消息类型定义**（Rust 类型，GUI/CLI/daemon 共享）：
   ```rust
   // crates/daemon/src/proto.rs

   /// 客户端 → daemon 请求
   #[derive(Serialize, Deserialize)]
   pub struct Request {
       pub id: String,           // UUID
       pub action: Action,       // 命令枚举
       pub params: serde_json::Value,  // 命令参数（按 action 不同）
   }

   /// daemon → 客户端响应
   #[derive(Serialize, Deserialize)]
   #[serde(tag = "type")]
   pub enum Response {
       Ok { id: String, data: serde_json::Value },
       Err { id: String, error: IpcError },
       Event { event: String, data: serde_json::Value },  // 主动推送
   }

   /// 所有可用命令（与 §10.1-10.5 IPC 命令一一对应）
   #[derive(Serialize, Deserialize)]
   #[serde(rename_all = "snake_case")]
   pub enum Action {
       // 服务器管理（§10.1）
       AddServer, RemoveServer, UpdateServer, ConnectServer, DisconnectServer,
       GetServerStatus, ListServers, ExportServers, ImportServers,
       ExportFull, ImportFull, CleanupAuthorizedKeys,
       // 代理控制（§10.2）
       ToggleProxy, ToggleProxyAdvanced, GetProxyStatus, TestProxy,
       SetProxyAuth, ClearProxyAuth, SetSystemProxy, ClearSystemProxy, GetSystemProxy,
       // 触发器管理（§10.3）
       AddTrigger, RemoveTrigger, UpdateTrigger, SyncTriggerFromTemplate,
       ManualFireTrigger, PauseAllTriggers, ResumeAllTriggers,
       PauseServerTriggers, ResumeServerTriggers,
       // 模板管理（§10.4）
       ListTemplates, CreateTemplate, UpdateTemplate, DeleteTemplate,
       SaveTriggerAsTemplate, ImportTemplates, ExportTemplates,
       // 凭据管理（§10.5）
       SaveCredential, HasCredential, DeleteCredential, ConfigureKeyAuth, SwitchAuthMethod,
       // 配置
       GetConfig, UpdateGeneralConfig,
       // 日志
       GetLogs, ClearLogs, ExportLogs,
       // daemon 控制
       Shutdown,           // 优雅关闭 daemon
       GetDaemonStatus,    // daemon 运行状态（版本、uptime、连接数）
   }

   /// 事件类型（daemon → 所有客户端广播，§10.6）
   #[derive(Serialize, Deserialize)]
   #[serde(rename_all = "snake_case")]
   pub enum EventType {
       ServerStatusChanged,    // { server_id, status, ip? }
       ProxyStatusChanged,     // { server_id, enabled }
       TriggerFired,           // { server_id, trigger_id, trigger_name, type, total_commands }
       TriggerCommandExecuted, // { server_id, trigger_id, command_index, total_commands, command, output, success }
       TriggerCompleted,       // { server_id, trigger_id, success, executed_commands, total_commands }
       LogEntry,              // { server_id, level, kind, message, timestamp, data? }
   }
   ```

4. **连接生命周期**：
   - 客户端连接 → daemon 分配 `ClientId`（UUID）→ 加入广播列表
   - 客户端断开 → daemon 从广播列表移除（不影响其他客户端）
   - daemon 收到请求 → 调用 `crates/core` API 执行 → 返回响应给请求方 + 广播事件给所有客户端
   - daemon 优雅关闭 → 通知所有客户端 `Shutdown` 事件 → 客户端清理

5. **daemon socket 路径发现**：
   - daemon 启动时把 socket 路径写入 `~/Library/Application Support/termfast/daemon.lock`（macOS）/ 对应 Windows 路径
   - `daemon.lock` 内容：`{ "pid": 12345, "socket_path": "...", "version": "1.0.0", "started_at": "..." }`
   - CLI/GUI 启动时读 `daemon.lock` 找到 socket 路径
   - daemon.lock 文件权限 600
   - daemon 启动时检测已有 daemon.lock → 检查 PID 是否存活 → 存活则拒绝启动（daemon 已运行）/ 不存活则覆盖（上次崩溃残留）

**单元测试**：
- 消息序列化/反序列化 round-trip（所有 Action + Response + EventType）
- 长度前缀帧编解码（正常 + 边界：空消息、大消息 1MB）
- `daemon.lock` 读写 + PID 存活检测
- socket 路径发现

**集成测试**：
- mock daemon server + mock client → 请求/响应 round-trip
- 事件广播：daemon 推送事件 → 多个客户端都收到
- 客户端断开不影响其他客户端
- daemon 关闭 → 客户端收到 Shutdown 事件

**文档确认清单**：
- [ ] Action 枚举覆盖 §10.1-10.5 所有 IPC 命令？
- [ ] EventType 覆盖 §10.6 所有事件类型？
- [ ] 错误码覆盖 §10.0 ErrorCode？
- [ ] 长度前缀帧格式？
- [ ] socket 文件权限 600？
- [ ] daemon.lock PID 存活检测？
- [ ] macOS Unix socket + Windows named pipe 双平台？

<!-- === SECTION 2 (Phase 1) END === -->

---

## Phase 2：SSH 层（client / auth / exec / channel_opener）

### FP-2.0 Mock SSH Server（测试基础设施）

> **所有 SSH 相关测试的基础**。FP-2.1-2.3、FP-3.x、FP-4.x、FP-9.x 都依赖此 mock。
> 在 Phase 2 第一个功能点实现，后续测试复用。

**开发细节**：

1. `crates/test-utils/src/mock_ssh_server.rs`：基于 russh server 模式的 mock SSH server
   - 独立 crate `test-utils`（`[dev-dependencies]`，不进 release build）
   - 启动时监听 `127.0.0.1:0`（随机端口），返回实际端口给测试代码
   - 支持多实例（每个测试用例可启动独立 mock server，互不干扰）

2. **认证方式支持**：

   | 认证方式 | 实现 | 测试场景 |
   |---------|------|---------|
   | 密码认证 | 接受预设密码 `test_password`，拒绝其他 | FP-2.2 密码认证成功/失败 |
   | 密钥认证 | 加载预设 host key，接受匹配的客户端公钥 | FP-2.2 密钥认证成功/失败 |
   | HostKey 校验 | 固定 host key（测试用），可配置不同指纹 | FP-2.2 HostKey 不匹配场景 |
   | 认证拒绝 | 可配置为"拒绝所有认证" | FP-9.8 认证失败错误路径 |

3. **exec 命令模拟**：

   | 场景 | 实现 | 测试用途 |
   |------|------|---------|
   | 正常命令 | 可配置返回 `{ exit_code, stdout, stderr }` | FP-2.3 exec 基本功能 |
   | `echo $SSH_CONNECTION` | 返回预设的 `client_ip port server_ip port` | FP-4.3 IP 检测 |
   | `pgrep <name>` / `ss -tln` | 按预设配置返回进程/端口状态 | FP-4.4 健康检查 |
   | 慢响应 | 可配置 `delay_ms`，exec 后等待指定时间再返回 | FP-2.3 超时测试 |
   | 长输出 | 返回大体积 stdout（如 100KB） | FP-2.3 输出处理 |
   | 命令超时不返回 | 可配置"永不返回"，依赖客户端超时 | FP-2.3 超时 + FP-5.4 优雅关闭 drain |
   | 非零退出码 | 返回 `{ exit_code: 1, stderr: "error" }` | FP-9.3 触发器执行失败 |

4. **连接行为模拟**：

   | 场景 | 实现 | 测试用途 |
   |------|------|---------|
   | 正常连接 | 接受连接 + 保持 | 常规测试 |
   | 立即拒绝 | TCP accept 后立即关闭 | FP-2.1 连接失败 |
   | 连接后断线 | 可配置 `disconnect_after_ms`，连接后 N ms 断开 | FP-2.1 重连 + FP-9.5 代理断连 |
   | 慢握手 | accept 后延迟 SSH 握手 | FP-2.1 连接超时 |

5. **direct-tcpip channel 支持**（代理测试需要）：
   - mock server 接受 `direct-tcpip` channel 请求
   - 可配置目标地址的响应：
     - 正常转发：将 channel 数据转发到真实目标（如本地 HTTP server）
     - 拒绝转发：返回 SSH 错误
     - 慢转发：延迟转发数据
   - 用于 FP-3.1 SOCKS5 + FP-3.2 HTTP 代理 + FP-9.9 性能基准测试

6. **配置 API**（Rust builder pattern）：
   ```rust
   let mock = MockSshServer::builder()
       .auth_password("test_password")
       .auth_key(test_public_key)
       .exec("echo $SSH_CONNECTION", ExecResult::success("1.2.3.4 12345 5.6.7.8 22"))
       .exec("pgrep nginx", ExecResult::success("12345"))
       .exec("pgrep redis", ExecResult::failure(1, "", "no process"))
       .disconnect_after(Duration::from_secs(5))  // 5s 后断线
       .start()
       .await?;
   let port = mock.port();  // 获取实际监听端口
   ```

**单元测试**：
- mock server 启动/停止
- 密码认证成功/失败
- 密钥认证成功/失败
- exec 正常/慢响应/超时/非零退出码
- 连接后断线
- direct-tcpip channel 正常转发/拒绝

**集成测试**：
- 用 `russh` 客户端连接 mock → exec 命令 → 验证返回值
- 用 `russh` 客户端连接 mock → direct-tcpip channel → 验证转发
- mock 断线 → 客户端检测到断开

**文档确认清单**：
- [ ] 支持密码 + 密钥认证？
- [ ] exec 可配置返回值（退出码/stdout/stderr/delay）？
- [ ] 可模拟断线/慢响应/长命令/超时不返回？
- [ ] 支持 direct-tcpip channel（代理测试）？
- [ ] builder pattern 配置 API？
- [ ] 独立 crate `test-utils`（dev-dependencies）？

### FP-2.1 SSH 客户端核心（连接 + 心跳 + 重连）

**开发细节**：

1. `crates/core/src/ssh/client.rs`：`SshClientHandle`
   - `connect(host, port, user, auth_method) -> Result<SshClientHandle>`
   - 内部持有 `russh::client::Handle`
   - 心跳：`heartbeat_interval`（默认 15s），**优先用 SSH keepalive（russh 内置 `keepalive` 配置，不占用 exec channel，避免与 S5 场景的 exec channel 竞争）**。如 russh keepalive 不可靠，降级为定期 exec `echo`（根据 spike S5 结果决定）
   - 重连：指数退避（`initial_backoff_secs` → `max_backoff_secs`，最多 `max_attempts` 次）
   - 连接状态回调：`on_state_change(ConnectionState)`
   - `ConnectionState` 枚举：Connected, Connecting, Reconnecting(n/max), AuthFailed, Disconnected

2. HostKey 校验（§17.2）：
   - 首次连接：记录指纹到 known_hosts
   - 后续连接：校验指纹，不匹配时不自动接受
   - `skip_hostkey_verify: true` 时跳过校验（高危降级）
   - HostKey 不匹配触发三重通知（系统通知 + 托盘变红 + 日志）

3. russh 配置：
   - 禁用 openssl，只用 ring（spike S9 验证）
   - `channel_buffer_size: 100`（PR #412 背压配置）
   - 锁定 `russh 0.62.2`

**单元测试**（用 mock SSH server）：
- 连接成功 → 状态 Connected
- 连接失败 → 状态 Disconnected + 重连
- 认证失败 → 状态 AuthFailed（不重连）
- HostKey 不匹配 → 不自动接受
- 心跳超时 → 触发重连
- 重连耗尽 → 状态 Disconnected
- 指数退避间隔正确（1s → 2s → 4s → ... → 30s cap）

**集成测试**（mock SSH server）：
- 连接 → 断开 → 重连完整流程
- 100 次断线重连（对应 spike S8）

**文档确认清单**：
- [ ] 心跳间隔默认 15s？（§7.2 reconnect.heartbeat_interval）
- [ ] 指数退避 initial=1s, max=30s, max_attempts=10？（§7.2）
- [ ] 认证失败不自动重连？（§17.2）
- [ ] HostKey 不匹配不自动接受？（§17.2）
- [ ] HostKey 不匹配三重通知？
- [ ] `skip_hostkey_verify` 降级选项？

### FP-2.2 SSH 认证（密钥 + 密码）

**开发细节**：

1. `crates/core/src/ssh/auth.rs`：
   - `AuthMethod` 枚举：`Key { key_path, passphrase }` | `Password { password }`
   - 密钥认证：读取私钥文件 → 用 passphrase 解密 → russh 认证
   - 密码认证：直接传 russh
   - 认证失败返回 `AuthFailed` 错误码

2. 密钥自动生成（§8.2-8.5）：
   - `generate_keypair(server_id) -> Result<(key_path, passphrase)>`
   - 用 `russh::keys` 生成 Ed25519 密钥对
   - 私钥文件 `~/.ssh/termfast_<server_id>_key`，权限 600
   - 公钥 `.pub`，权限 644
   - passphrase：随机 32 字节 base64，存钥匙串
   - `push_public_key(ssh_handle, public_key) -> Result<()>`：通过 SSH exec 追加到 VPS `~/.ssh/authorized_keys`

3. 密钥文件丢失检测（§8.5）：
   - 启动时检查 `key_path` 文件是否存在
   - 不存在 + `key_auto_generated` → 返回特定错误，GUI 提示重新生成或指定其他密钥

**单元测试**：
- 密钥生成：文件存在、权限 600、passphrase 存钥匙串
- 密钥认证 round-trip（mock SSH server）
- 密码认证 round-trip（mock SSH server）
- 认证失败 → AuthFailed
- 密钥文件丢失检测

**集成测试**（mock SSH server）：
- 密码认证 → 自动生成密钥 → 推送公钥 → 切换密钥认证 → 断开 → 密钥重连成功
- `cleanup_authorized_keys`：从 VPS authorized_keys 删除指定公钥

**文档确认清单**：
- [ ] 密钥文件路径 `~/.ssh/termfast_<server_id>_key`？（§8.5）
- [ ] 权限 600/644？
- [ ] passphrase 随机 32 字节 base64 存钥匙串？
- [ ] Ed25519 密钥？
- [ ] 自动推送公钥到 authorized_keys？
- [ ] 密钥文件丢失检测 + GUI 提示？

### FP-2.3 SSH exec（远程命令执行）

**开发细节**：

1. `crates/core/src/ssh/exec.rs`：
   - `exec(handle, command, timeout) -> Result<ExecResult>`
   - `ExecResult`：`{ exit_code, stdout, stderr }`
   - 超时处理：`tokio::time::timeout`，超时 kill channel（§17.1）
   - exec channel 开不起来 → 视为连接断开，触发重连（§17.1）

2. IP 检测命令（§5.2）：
   - `detect_client_ip(handle) -> Result<IpAddr>`
   - 执行 `echo $SSH_CONNECTION`，解析第 1 字段
   - Fallback 链：`$SSH_CONNECTION` → `$SSH_CLIENT` → `who -m` → `ss -tnp | grep ssh`
   - 解析失败记日志告警

**单元测试**（mock SSH server）：
- exec 成功 → exit_code=0, stdout/stderr 正确
- exec 退出码非 0 → 正确返回
- exec 超时 → channel 被 kill，返回超时错误
- IP 检测：`$SSH_CONNECTION` 输出 `1.2.3.4 12345 5.6.7.8 22` → 解析为 `1.2.3.4`
- IP 检测 fallback：`$SSH_CONNECTION` 为空 → `$SSH_CLIENT` → `who -m`
- IPv6：`2001:db8::1 12345 :: 22` → 解析为 `2001:db8::1`

**文档确认清单**：
- [ ] 超时 kill channel？（§17.1）
- [ ] IP 检测 fallback 链？（§5.2）
- [ ] IPv6 支持？（§13.3）

### FP-2.4 ChannelOpener trait + SshChannelOpener

**开发细节**：

1. `crates/core/src/proxy/channel.rs`：`ChannelOpener` trait
   ```rust
   #[async_trait]
   pub trait ChannelOpener: Send + Sync {
       async fn open(&self, host: &str, port: u16) -> Result<Channel>;
   }
   ```

2. `crates/core/src/ssh/channel_opener.rs`：`SshChannelOpener`
   - 持有 `Arc<SshClientHandle>`
   - `open()` 调用 `russh::client::Handle.channel_open_direct_tcpip()`
   - 单连接模式：proxy 和 exec 共用同一 handle
   - 双连接模式（S5 失败降级）：proxy 和 exec 各自独立 handle

**单元测试**（mock SSH server）：
- `open("example.com", 443)` → 返回 channel
- 连接断开时 `open()` → 返回错误

**文档确认清单**：
- [ ] trait 定义与 §4.2 一致？
- [ ] 单连接/双连接模式支持？（§20.0 双连接方案）

<!-- === SECTION 3 (Phase 2) END === -->

---

## Phase 3：代理层（SOCKS5 / HTTP / channel_manager）

> 前置条件：Phase 0 spike S1-S9 全部通过（或确定降级方案）

### FP-3.1 ChannelManager（channel 生命周期管理）

**开发细节**：

1. `crates/core/src/proxy/channel.rs`（扩展）：`ChannelManager`（§5.1 SSH channel 管理策略）
   ```rust
   struct ChannelManager {
       opener: Arc<dyn ChannelOpener>,
       semaphore: Arc<Semaphore>,      // 并发上限 max_channels
       idle_timeout: Duration,         // 空闲超时
   }
   ```
   - `open(host, port) -> Result<ManagedChannel>`：获取 semaphore permit → open channel → 返回 ManagedChannel
   - `ManagedChannel`：持有 channel + permit + idle timeout timer
   - 空闲超时：无数据传输超过 `channel_idle_timeout`（默认 300s）自动关闭
   - 活跃 channel 监控：超过 `max_channels * 0.8` 时日志告警

2. 配置项（per-server）：
   - `max_channels: 64`（默认，可配）
   - `channel_idle_timeout: 300s`（默认，可配，§5.1 正式配置值；§20.0 缓解措施中提到的 30s 是 spike 阶段的保守建议，正式值以 §5.1 为准）

**单元测试**：
- 并发上限：开 64 个 channel 成功，第 65 个排队等待
- 空闲超时：channel 无数据 300s 后自动关闭
- 活跃 channel 监控：超过 80% 阈值时触发告警（验证日志输出）

**集成测试**（mock SSH server）：
- 50 并发 channel 无明显延迟（对应 spike S1）
- channel 空闲超时后自动关闭，permit 释放

**文档确认清单**：
- [ ] `max_channels: 64` 默认值？（§5.1）
- [ ] `channel_idle_timeout: 300s` 默认值？（§5.1）
- [ ] Semaphore 限流？
- [ ] 空闲超时自动关闭？
- [ ] 80% 阈值告警？

### FP-3.2 SOCKS5 代理服务器

**开发细节**：

1. `crates/core/src/proxy/socks5.rs`：SOCKS5 服务器（§13.3 SOCKS5 协议合规）
   - 监听 `127.0.0.1:{socks5_port}`
   - 协议处理：
     - 认证协商：支持 `0x00 NO AUTH` + `0x02 USERNAME/PASSWORD`（RFC 1929）
     - CONNECT（`0x01`）：核心功能
     - BIND（`0x02`）：返回 `0x07 COMMAND NOT SUPPORTED`
     - UDP ASSOCIATE（`0x03`）：返回 `0x07 COMMAND NOT SUPPORTED`
     - ATYP：IPv4（0x01）、DomainName（0x03，远程 DNS 解析）、IPv6（0x04）
   - 每个连接通过 `ChannelManager.open(host, port)` 开 direct-tcpip channel
   - 双向数据转发：`tokio::io::copy` bidirectional
   - 代理认证：用户名/密码存钥匙串 `termfast::<server_id>::proxy_auth`

2. 端口冲突处理（§17.6）：端口被占用时不自动切换，返回 `PortConflict` 错误

**单元测试**：
- SOCKS5 握手：NO AUTH 模式
- SOCKS5 握手：USERNAME/PASSWORD 模式（认证成功/失败）
- CONNECT IPv4 目标
- CONNECT DomainName 目标（远程 DNS）
- CONNECT IPv6 目标
- BIND → 返回 COMMAND NOT SUPPORTED
- UDP ASSOCIATE → 返回 COMMAND NOT SUPPORTED
- 端口冲突 → PortConflict 错误

**集成测试**（mock SSH server + 真实 SOCKS5 客户端）：
- 通过 SOCKS5 代理访问 HTTP 服务
- 通过 SOCKS5 代理访问 HTTPS 服务
- 50 并发 SOCKS5 连接
- 100MB 文件通过 SOCKS5 下载

**文档确认清单**：
- [ ] 支持 CONNECT + NO AUTH + USERNAME/PASSWORD？（§13.3）
- [ ] BIND/UDP 返回 COMMAND NOT SUPPORTED？
- [ ] IPv4/DomainName/IPv6 三种 ATYP？
- [ ] 端口冲突不自动切换？（§17.6）
- [ ] 代理认证存钥匙串？

### FP-3.3 HTTP 代理

**开发细节**：

1. `crates/core/src/proxy/http.rs`：HTTP 代理（两阶段处理 → ChannelOpener）
   - 监听 `127.0.0.1:{http_port}`
   - 两阶段处理：
     - **明文 HTTP GET**：解析 `GET http://host:port/path HTTP/1.1`，提取目标 host:port，开 channel，转发请求 + 响应
     - **HTTPS CONNECT**：解析 `CONNECT host:port HTTP/1.1`，开 channel，返回 `200 Connection Established`，双向 TLS 透传
   - 每个连接通过 `ChannelManager.open(host, port)` 开 channel
   - 端口冲突处理同 SOCKS5

**单元测试**：
- HTTP GET 请求转发
- HTTPS CONNECT 隧道
- 错误请求格式 → 400 Bad Request
- 端口冲突 → PortConflict

**集成测试**（mock SSH server + 真实 HTTP 客户端）：
- 通过 HTTP 代理访问 HTTP 网站
- 通过 HTTP 代理访问 HTTPS 网站（CONNECT）
- 浏览器配置 HTTP 代理访问多网站

**文档确认清单**：
- [ ] 支持 HTTP GET + HTTPS CONNECT？（§5.1）
- [ ] 两阶段处理？
- [ ] 端口冲突处理？

### FP-3.4 代理控制接口

**开发细节**：

1. 代理启停控制：
   - `start_proxy(server_id) -> Result<()>`：启动 SOCKS5 + HTTP 服务器
   - `stop_proxy(server_id)`：关闭代理服务器
   - `toggle_proxy(server_id, enabled)`：主开关（同时控制 SOCKS5+HTTP）
   - `toggle_proxy_advanced(server_id, proto, enabled)`：单独控制 SOCKS5 或 HTTP

2. 代理测试（§10.2 test_proxy）：
   - 默认方案：SSH exec `curl -s --max-time 5 <test_url>` 在 VPS 侧执行
   - 备选方案：客户端通过代理直接访问 test_url
   - 返回 `{ exit_ip, latency_ms, error, error_type }`
   - `error_type`：`proxy_down` / `vps_blocked` / `endpoint_unreachable`

**单元测试**：
- start/stop proxy
- toggle 主开关同时控制 SOCKS5+HTTP
- toggle_advanced 单独控制
- test_proxy 默认方案（mock SSH exec curl）
- test_proxy 备选方案（mock 代理访问）
- error_type 分类

**文档确认清单**：
- [ ] 主开关同时控制 SOCKS5+HTTP？（§10.2）
- [ ] test_proxy 默认在 VPS 侧执行 curl？（§10.2）
- [ ] error_type 三种分类？

<!-- === SECTION 4 (Phase 3) END === -->

---

## Phase 4：触发器引擎（template / engine / ipcheck / health）

### FP-4.1 模板渲染（占位符 + 条件块）

**开发细节**：

1. `crates/core/src/trigger/template.rs`：模板渲染引擎
   - 占位符替换（§6.3）：
     - `{{.NewIP}}` / `{{.OldIP}}`：运行时注入，严格 IPv4/IPv6 校验
     - `{{.IPFamily}}`：根据 NewIP 格式自动注入 `ipv4` 或 `ipv6`
     - `{{.VPSHost}}` / `{{.VPSUser}}` / `{{.ServerName}}`：字符串类，禁止 `'"\`$\\n;|&`
     - `{{.ProcessName}}`：同上
     - `{{.Timestamp}}`：纯数字
     - `{{.ProtectedPort}}` / `{{.TelegramToken}}` 等：参数化占位符，从 `parameters` 字段读取
   - 条件块（§6.3）：
     - `{{#if OldIP}}...{{/if}}`：OldIP 为空时跳过整条命令
     - 支持嵌套？v1 不支持嵌套，只支持单层
   - 引擎层空值防御（§6.3）：渲染后包含空占位符残留（如 `address=""`）→ 跳过该条命令
   - 占位符注入防护（§13.4）：校验规则表

2. 渲染流程：
   ```
   render(template_commands, context) -> Vec<RenderedCommand>
   context = { NewIP, OldIP, IPFamily, VPSHost, VPSUser, ServerName, ProcessName, Timestamp, parameters... }
   ```
   - 每条命令独立渲染
   - 渲染失败（占位符校验不通过）→ 跳过 + 记日志
   - 返回 `RenderedCommand { command: String, skipped: bool, skip_reason: Option<String> }`

**单元测试**：
- 基本占位符替换：`{{.NewIP}}` → `1.2.3.4`
- `{{.IPFamily}}` 自动注入：IPv4 → `ipv4`，IPv6 → `ipv6`
- 条件块 OldIP 为空 → 跳过命令
- 条件块 OldIP 有值 → 正常渲染
- 参数化占位符：`{{.ProtectedPort}}` 从 parameters 读取
- IP 格式非法 → 跳过 + 记日志
- 字符串占位符含非法字符 `'` → 跳过 + 记日志
- 空值残留 `address=""` → 跳过 + 记日志
- 端口参数非法（非 1-65535）→ 跳过 + 记日志

**文档确认清单**：
- [ ] 占位符列表与 §6.3 一致？
- [ ] `{{.IPFamily}}` 自动注入 ipv4/ipv6？
- [ ] 条件块 `{{#if OldIP}}` 实现？
- [ ] 空值防御？
- [ ] 占位符注入防护（§13.4 校验规则表）？
- [ ] 参数化占位符从 `parameters` 字段读取？

### FP-4.2 触发器引擎（事件队列 + 执行调度）

**开发细节**：

1. `crates/core/src/trigger/engine.rs`：触发器引擎
   - 事件队列：per-server `tokio::sync::mpsc` channel（§6.7 背压策略）
   - 队列上限 100，满时丢弃 + 告警
   - per-server `Mutex` 串行化执行（§6.8）
   - 事件类型：`OnConnect`, `OnReconnect`, `OnIpChange`, `OnProcessDead`, `OnPortClosed`, `ManualFire`

2. **`EventGateway` 实现**（§6.8，非平凡的并发结构，不能隐含在"事件队列"一句话中）：
   ```rust
   struct EventGateway {
       tx: mpsc::Sender<TriggerEvent>,     // 容量 100
       dedup_set: HashSet<DedupKey>,       // 入队时插入，出队时删除
       cooldown: HashMap<TriggerKey, Instant>,  // last_fired 时间戳
       last_ip: HashMap<ServerId, String>, // IP 变化事件特殊去重
       lock: tokio::sync::Mutex<()>,       // 串行化入队/出队
   }
   ```
   - **`dedup_set`**：入队前检查是否已有相同类型事件，有则丢弃（不重复入队）。出队时同步清理 dedup_set，保证 set 与队列内容同步（§6.8 去重键表）：
     | 事件类型 | 去重键 |
     |---------|--------|
     | `OnProcessDead { name }` | `(server_id, OnProcessDead, name)` |
     | `OnPortClosed { port }` | `(server_id, OnPortClosed, port)` |
     | `OnIpChange { new_ip }` | 用 last_ip cache（见下），不进 dedup_set |
     | `OnConnect` / `OnReconnect` | `(server_id, 事件类型)` |
     | `ManualFire` | 不去重（允许排队多次） |
   - **`TriggerCooldown`**：触发器执行完毕后进入冷却期，冷却期内同类型事件不再入队。冷却期 = `3 × check_interval`（健康检查类）或 `3 × ip_check.interval_secs`（IP 变化类）。触发器实例可配置 `cooldown_secs` 覆盖默认值（最小 30s，最大 3600s，§6.8）
   - **`last_ip` cache**：OnIpChange 特殊处理——新 IP == last_ip 则丢弃，否则入队并更新 last_ip（不是丢弃，是替换，§6.8）
   - **入队串行化锁**：`EventGateway` 内部用 `tokio::sync::Mutex` 串行化所有入队和出队操作，锁粒度小（只保护 HashSet 查/插/删 + cooldown 查），持锁时间极短（§6.8）
   - **为什么不用 mpsc 中间 actor（方案 B）**：actor 模式需要额外的 spawn 任务和 channel 转发，引入一层间接。`EventGateway + Mutex` 更直接（§6.8）
   - 所有检测定时器通过 `gateway.enqueue()` 入队，不直接调用 `mpsc::send`。消费者通过 `gateway.dequeue()` 取事件，不直接调用 `mpsc::recv`

3. 执行流程（§6.6 多命令执行模型）：
   - 每条命令独立 SSH exec channel
   - 按顺序依次执行
   - 失败处理：默认中止，`continue_on_error: true` 时继续
   - 超时：`timeout_secs`（默认 30s），超时 kill channel
   - 冷却：`cooldown_secs`（默认 3×检查间隔），冷却期内不重复触发

4. 执行进度回调：
   - `on_progress(execution_id, current, total, command_result)`
   - 供 GUI 显示进度条 "2/3"（U19）

5. 执行日志：
   - `execution_id`：UUID，关联一次执行的所有日志条目
   - 日志条目：fired → command 1 → command 2 → ... → completed/aborted
   - 日志结构：`{ timestamp, server_id, trigger_id, execution_id, level, message, command?, exit_code?, stdout?, stderr? }`

6. 手动触发（§6.9）：
   - `manual_fire(server_id, trigger_id)` → 入队 ManualFire 事件
   - 与自动触发平等排队，不插队

**单元测试**（mock SSH server）：
- 单命令执行成功
- 多命令顺序执行
- 命令失败 + continue_on_error=false → 中止
- 命令失败 + continue_on_error=true → 继续
- 命令超时 → kill channel + 记日志
- 冷却期内重复触发 → 跳过
- `cooldown_secs` 自定义覆盖默认值
- 队列满 100 → 丢弃 + 告警
- 手动触发排队顺序
- execution_id 关联日志
- **EventGateway 去重**：OnProcessDead 重复入队 → 只保留一条
- **EventGateway IP 去重**：OnIpChange new_ip == last_ip → 丢弃；new_ip != last_ip → 入队 + 更新 last_ip
- **EventGateway 出队同步清理 dedup_set**
- **ManualFire 不去重**：允许排队多次

**集成测试**：
- on_connect → on_ip_change 顺序执行
- 重连后 on_reconnect 先于 on_ip_change
- 健康检查发现进程死亡 → on_process_dead 入队 → 执行

**文档确认清单**：
- [ ] per-server Mutex 串行化？（§6.8）
- [ ] 队列上限 100 + 满时丢弃？（§6.7）
- [ ] 每条命令独立 exec channel？（§6.6）
- [ ] continue_on_error 支持？
- [ ] 超时 kill channel？（§17.1）
- [ ] 冷却机制？
- [ ] execution_id 关联日志？
- [ ] 手动触发平等排队？（§6.9）

### FP-4.3 IP 变化检测

**开发细节**：

1. `crates/core/src/trigger/ipcheck.rs`：
   - 触发源 1：连接建立/重连时检测（§5.2）
     - SSH 连接成功 → exec `echo $SSH_CONNECTION` → 解析 IP → 与 `last_known_ip` 对比
     - 不同 → 更新 `last_known_ip`（保存配置）→ 入队 OnIpChange
   - 触发源 2：周期性复检（§5.2）
     - 定时器（默认 300s，可配 per-server）
     - exec `echo $SSH_CONNECTION` → 对比 → 不同则入队 OnIpChange
     - 无锁（只检测，不执行命令）
   - Fallback 链（§5.2）：`$SSH_CONNECTION` → `$SSH_CLIENT` → `who -m` → `ss -tnp | grep ssh`

2. IP 记录管理：
   - `last_known_ip` 存储在 `runtime_state.json`（通过 `RuntimeStateManager`，见 FP-1.3b），**不存 config.json**（§7.3 + §11.5 高频写入与 SSD 磨损）
   - IP 变化时通过 `RuntimeStateManager::modify` 更新，独立锁不阻塞用户配置操作
   - 首次添加服务器时 `last_known_ip` 为 `null`，首次连接后写入

**单元测试**（mock SSH server）：
- 连接时 IP 检测：IP 与上次相同 → 无操作
- 连接时 IP 检测：IP 不同 → 更新 + 入队 OnIpChange
- 首次连接（last_known_ip 为空）→ 入队 OnIpChange（OldIP 为空）
- 周期复检：IP 相同 → 无操作
- 周期复检：IP 不同 → 入队 OnIpChange
- Fallback：`$SSH_CONNECTION` 为空 → `$SSH_CLIENT`
- IPv6 IP 检测

**文档确认清单**：
- [ ] 两个触发源（连接时 + 周期复检）？（§5.2）
- [ ] 周期默认 300s？
- [ ] Fallback 链 4 级？
- [ ] last_known_ip 存配置？
- [ ] 首次连接 OldIP 为空？

### FP-4.4 进程/端口存活检查

**开发细节**：

1. `crates/core/src/trigger/health.rs`（§5.3）：
   - 每个检查独立定时器
   - 每个检查独立 exec（v1 不合并）
   - 进程检查：`pgrep <process_name>` → 退出码非 0 → 入队 OnProcessDead
   - 端口检查：`ss -tln 'sport = :<port>' | grep -q .` → 退出码非 0 → 入队 OnPortClosed
   - `check_target`：进程名或端口号
   - `check_interval`：检查间隔（秒）

**单元测试**（mock SSH server）：
- 进程存活 → 退出码 0 → 无操作
- 进程死亡 → 退出码非 0 → 入队 OnProcessDead
- 端口开放 → 无操作
- 端口关闭 → 入队 OnPortClosed
- 定时器间隔正确

**文档确认清单**：
- [ ] 每个检查独立 exec？（§5.3）
- [ ] v1 不做合并优化？
- [ ] 进程检查用 pgrep？
- [ ] 端口检查用 ss？

### FP-4.5 内置模板预设

**开发细节**：

1. 在 `Config::default()` 中注入 5 个内置模板（§14）：
   - `tpl_firewall_firewalld`：更新防火墙白名单（firewalld），on_ip_change，参数 ProtectedPort
   - `tpl_firewall_ufw`：更新防火墙白名单（ufw），on_ip_change，参数 ProtectedPort
   - `tpl_nginx_restart`：重启进程，on_process_dead，参数 check_target/check_interval
   - `tpl_telegram_notify`：重连通知（Telegram），on_reconnect，参数 TelegramToken/TelegramChatID
   - `tpl_port_check`：端口存活检查，on_port_closed，参数 check_target/check_interval

2. 内置模板版本升级（§19）：
   - `built_in: true` + `template_version` 字段
   - 升级时：用户未修改 → 自动更新；用户已修改 → GUI 提示
   - 判断修改：`content_hash = sha256(commands.join("\n"))`

**单元测试**：
- 默认配置包含 5 个内置模板
- 内置模板 `built_in: true`
- 模板版本升级：未修改 → 自动更新
- 模板版本升级：已修改 → 不自动覆盖
- content_hash 计算

**文档确认清单**：
- [ ] 5 个内置模板？（§14.1-14.5）
- [ ] `built_in` + `template_version` 字段？（§19.1）
- [ ] 升级逻辑（未修改自动更新 / 已修改提示）？（§19.2）
- [ ] hash 判断修改？

<!-- === SECTION 5 (Phase 4) END === -->

---

## Phase 5：服务器管理（manager / instance / lifecycle）

### FP-5.1 ServerInstance（单服务器运行时状态）

**开发细节**：

1. `crates/core/src/server/instance.rs`：`ServerInstance`
   - 持有：`SshClientHandle`（或双连接模式下的两个 handle）、`ChannelManager`、触发器引擎句柄、IP 检测定时器、健康检查定时器
   - 运行时状态：`ConnectionState`、当前 IP、连接时长、重连次数、上次 IP 变化时间、下次检查时间
   - 代理运行时状态：SOCKS5/HTTP 是否运行、活跃 channel 数

2. 单连接/双连接模式（§20.0）：
   - `ConnectionMode::Single`：proxy + exec 共用一个 handle
   - `ConnectionMode::Dual`：各自独立 handle，代理连接优先重连
   - 由 spike S5 结果决定，编译期或启动时配置

3. **双连接方案实现细节**（§20.0，spike S5 失败时启用 FP-5.1b）：
   - **连接结构**：`proxy_handle: SshClientHandle` + `exec_handle: SshClientHandle`，各自独立连接同一 VPS
   - **重连顺序**：代理连接优先重连（用户感知最强），exec 连接随后重连
   - **HostKey 共享**：两个连接共享同一 HostKey 记录，首次连接时校验，后续连接复用
   - **独立心跳**：两个连接各自维护心跳，一个断开不影响另一个
   - **exec 断开时触发器处理**：exec 连接断开时，触发器队列暂停（不丢弃），exec 重连后恢复执行
   - **配置**：`ConnectionMode::Dual` 时配置两个连接的独立参数（心跳间隔、重连策略）
   - **复杂度评估**：双连接增加约 30% 代码复杂度，但隔离性更好（代理流量不影响触发器执行）

**单元测试**：
- ServerInstance 创建 + 销毁
- 状态转换：Disconnected → Connecting → Connected
- 双连接模式：代理连接断开不影响 exec

**文档确认清单**：
- [ ] 单连接/双连接模式支持？（§20.0）
- [ ] 运行时状态字段完整？

### FP-5.2 ServerManager（多服务器容器）

**开发细节**：

1. `crates/core/src/server/manager.rs`：`ServerManager`
   - `HashMap<ServerId, Arc<ServerInstance>>`
   - `add_server(config) -> Result<ServerId>`：校验重复（host+port+user）+ 端口冲突
   - `remove_server(server_id)`：断开连接 → 清理钥匙串 → 删除密钥文件（auto_generated）→ 清理 authorized_keys
   - `connect(server_id)` / `disconnect(server_id)`
   - `connect_all()` / `disconnect_all()`：并发上限 3（§9.4）
   - `get_status(server_id) -> ServerStatus`
   - `get_all_statuses() -> Vec<(ServerId, ServerStatus)>`

2. 服务器添加校验（§8.5 同一 VPS 多配置策略）：
   - `(host, port, user)` 组合重复 → `DuplicateServer` 错误
   - SOCKS5/HTTP 端口全局唯一 → `PortConflict` 错误

3. 服务器删除清理（§8.5 删除服务器时的清理）：
   - 断开 SSH 连接
   - 删除钥匙串条目（`delete_all_for_server`）
   - `key_auto_generated == true` → 删除本地密钥文件
   - 仍连接时自动清理 authorized_keys
   - 已断开时提示用户后续手动清理

**单元测试**：
- add_server 成功
- add_server 重复 → DuplicateServer
- add_server 端口冲突 → PortConflict
- remove_server 清理钥匙串 + 密钥文件
- remove_server 自动清理 authorized_keys（连接时）
- connect_all 并发上限 3

**集成测试**：
- 添加 3 台服务器 → 全部连接 → 删除中间一台 → 其余不受影响

**文档确认清单**：
- [ ] `(host, port, user)` 重复校验？（§8.5）
- [ ] 端口全局唯一校验？
- [ ] 删除时清理钥匙串 + 密钥文件 + authorized_keys？（§8.5）
- [ ] connect_all 并发上限 3？（§9.4）

### FP-5.3 连接/断开/重连状态机

**开发细节**：

1. `crates/core/src/server/lifecycle.rs`：状态机
   - 5 态（§9.4）：已连接（绿●）、连接中（黄◑）、重连中（橙◐N/M）、认证失败（红▲）、断开（灰○）
   - 状态转换图：
     ```
     Disconnected → Connecting → Connected
     Connected → Reconnecting → Connected / Disconnected
     Connecting → AuthFailed（不自动重连）
     Reconnecting → AuthFailed
     Reconnecting → Disconnected（重连耗尽）
     ```
   - 状态变化回调：`on_status_change(server_id, old_state, new_state)`
   - 触发器联动：
     - Connected → 入队 OnConnect + IP 检测
     - Reconnecting → Connected → 入队 OnReconnect + IP 检测
     - IP 变化 → 入队 OnIpChange

**单元测试**：
- 状态转换：所有合法路径
- 状态转换：非法路径被拒绝
- 状态变化回调触发
- 触发器联动：Connected → OnConnect + IP 检测
- 触发器联动：Reconnect → OnReconnect + IP 检测

**文档确认清单**：
- [ ] 5 态与 §9.4 一致？
- [ ] 认证失败不自动重连？
- [ ] 重连耗尽 → 断开？
- [ ] 触发器联动顺序（on_reconnect 先于 on_ip_change）？（§6.9）

### FP-5.4 优雅关闭流程

**开发细节**：

1. `crates/core/src/server/lifecycle.rs`（扩展）：`graceful_shutdown()` 方法（§5.5）
   - 用户退出 App 时正确处理正在进行的操作，避免 VPS 上留下不一致状态（如半更新的防火墙规则）

2. 关闭流程 6 步（§5.5）：
   ```
   步骤 1：停止所有检测定时器（IP 复检、健康检查）→ 不再产生新事件
   步骤 2：等待正在执行的触发器命令完成（drain 超时 15s）
           ├─ 15s 内完成 → 正常继续
           └─ 15s 超时 → 强制终止（SSH channel close），日志记录"触发器 X 被强制中断"
              └─ 下次启动时 on_ip_change 重新执行全量更新，自动修复
   步骤 3：代理连接 drain（超时 10s）
           ├─ 已有 direct-tcpip channel 尝试 drain
           └─ 10s 超时 → 强制关闭所有 channel
   步骤 4：SSH 连接优雅关闭
           ├─ 发送 SSH_MSG_DISCONNECT（normal disconnect）
           └─ 等待对端 ACK（超时 3s），超时则直接 drop TCP
   步骤 5：配置落盘
           ├─ ConfigManager.modify() 确保 last_known_ip 等已持久化
           └─ RuntimeStateManager 同步落盘
   步骤 6：清理钥匙串中的临时凭据（如有）→ 退出进程
   ```

3. 超时参数（§5.5）：

| 阶段 | 超时 | 原因 |
|------|------|------|
| 触发器命令 drain | 15s | 大部分命令 <10s |
| 代理 channel drain | 10s | 已有连接尽量传完 |
| SSH disconnect ACK | 3s | 网络正常时 <1s |
| 总退出超时 | 30s | 超过则强制退出 |

4. 强制中断后的自愈（§5.5）：
   - 触发器被强制中断 → 下次启动时 on_ip_change 重新执行（首次连接触发全量更新），防火墙规则自动修复
   - 代理 channel 被强制关闭 → 客户端连接断开，用户重新访问时自动重连
   - SSH 连接未优雅关闭 → VPS 侧 sshd 心跳超时后自动清理

5. GUI 退出提示（§5.5）：
   - 正常退出（无正在执行的操作）→ 立即退出，无提示
   - 有触发器正在执行 → 托盘提示"正在等待触发器完成... (最多 15s)"，进度条
   - 超时强制退出 → 日志记录，无弹窗

**单元测试**：
- 正常退出：无正在执行的操作 → 立即退出
- 触发器执行中退出 → 等待 15s → 完成 → 正常退出
- 触发器执行中退出 → 超时 15s → 强制终止 → 日志记录
- 代理 channel drain 10s 超时 → 强制关闭
- SSH disconnect ACK 3s 超时 → drop TCP
- 总退出超时 30s → 强制退出
- 配置落盘验证

**E2E 测试**：
- 触发器执行中退出 → 验证 drain → 验证下次启动自愈
- 代理流量中退出 → 验证 channel drain

**文档确认清单**：
- [ ] 6 步关闭流程与 §5.5 一致？
- [ ] 超时参数（15s/10s/3s/30s）？
- [ ] 强制中断后自愈逻辑？
- [ ] GUI 退出提示（正常/执行中/超时）？

<!-- === SECTION 6 (Phase 5) END === -->

---

## Phase 6a：daemon + GUI 桥接 + CLI 客户端（核心通信链路）

> **混合 daemon 架构**：Tauri 进程内嵌 daemon（监听 socket），GUI 前端通过 Tauri IPC → `daemon_embed.rs` → core API。
> CLI 连这个 socket，操作实时推送给 GUI。`--daemon` 无头模式下独立运行 daemon。
> 这样 CLI 的所有操作都经过 daemon → core 执行 → 事件广播给 GUI → UI 实时更新。

### FP-6.1 daemon（socket server + core 运行时持有，可内嵌可独立）

**开发细节**：

1. `crates/daemon/src/server.rs`：daemon socket server
   - 初始化 `crates/core` 所有运行时状态：`ConfigManager`、`RuntimeStateManager`、`CredentialStore`、`ServerManager`（含所有 SSH 连接、代理、触发器引擎）
   - 监听 socket（FP-1.7 协议）：`UnixListener`（macOS/Linux）/ `named_pipe`（Windows）
   - 写 `daemon.lock`（PID + socket 路径 + 版本 + 启动时间）
   - 接受客户端连接 → 为每个连接 spawn 一个 `tokio` task 处理请求
   - **两种运行模式**：
     - **内嵌模式**（GUI）：`crates/desktop` 的 `daemon_embed.rs` 调用 `daemon::start()` 在 Tauri 进程内启动 socket server。GUI 退出 → daemon 也退出
     - **独立模式**（无头）：`crates/daemon` 的 `main.rs` 直接启动（`termfast --daemon`），无 Tauri 依赖

2. `crates/daemon/src/handler.rs`：请求处理
   - 收到 `Request` → 根据 `Action` 调用对应的 `crates/core` API
   - 执行结果 → 返回 `Response::Ok` 或 `Response::Err` 给请求方
   - **副作用事件广播**：执行过程中产生的状态变化（如服务器状态变化、触发器执行进度、日志条目）→ 广播 `Response::Event` 给**所有**已连接客户端（含 GUI 和 CLI）
   - 事件来源：core 的回调（`on_state_change`、`on_progress`、`on_log`）→ 转换为 `EventType` → 广播

3. **Action → core API 映射**（完整覆盖 §10.1-10.5）：

   | Action | core API | 说明 |
   |--------|----------|------|
   | `AddServer` | `ServerManager::add_server(config)` | 后端校验端口唯一性 |
   | `RemoveServer` | `ServerManager::remove_server(id)` | 清理钥匙串 + 密钥 + authorized_keys |
   | `ConnectServer` | `ServerManager::connect(id)` | 触发 on_connect + IP 检测 |
   | `DisconnectServer` | `ServerManager::disconnect(id)` | 优雅断开 |
   | `ToggleProxy` | `ServerManager::toggle_proxy(id, enabled)` | 开关代理 |
   | `SetProxyAuth` | `CredentialStore::save("termfast::<id>::proxy_auth", cred)` | 代理认证凭据存钥匙串（§13.3） |
   | `ClearProxyAuth` | `CredentialStore::delete("termfast::<id>::proxy_auth")` | 清除代理认证凭据 |
   | `ManualFireTrigger` | `TriggerEngine::manual_fire(id, trigger_id)` | 入队 ManualFire 事件 |
   | `PauseAllTriggers` | `TriggerEngine::pause_all()` | 停止所有检测定时器 |
   | `ResumeAllTriggers` | `TriggerEngine::resume_all()` | 全量检测 + 返回 pending_events |
   | `GetLogs` | `LogBuffer::query(filter)` | 日志查询 |
   | `Shutdown` | `daemon::graceful_shutdown()` | 优雅关闭（FP-5.4 流程） |
   | ... | ... | 其余 Action 按相同模式映射 |

4. **暂停/恢复触发器实现细节**（§10.3）：
   - `PauseAllTriggers` / `PauseServerTriggers`：停止所有/指定服务器的检测定时器（IP 复检 + 健康检查），不再 SSH exec 检查命令。SSH 连接保持（代理不受影响）。手动触发仍可入队但不执行（引擎也暂停消费）
   - `ResumeAllTriggers` / `ResumeServerTriggers`：恢复时**立即执行一次全量检测**，根据当前实际状态决定是否入队事件。返回 `pending_events`。不补发历史事件。重启所有检测定时器
   - 暂停期间停止检测而非丢弃事件的原因（§10.3）：避免浪费 SSH exec、符合"调试 VPS"语义、减少 SSH 连接负担

5. **事件广播机制**：
   ```
   core 状态变化 → daemon handler 回调
     → 转换为 Response::Event { event, data }
     → 遍历所有已连接客户端 → 逐个发送
   ```
   - 事件类型覆盖 §10.6：`server:status_changed`、`proxy:status_changed`、`trigger:fired`、`trigger:command_executed`、`trigger:completed`、`log:entry`
   - `log:entry` 的 `kind` 与 `data` schema 遵循 §10.6 日志 kind 表

6. **daemon 优雅关闭**（复用 FP-5.4 流程）：
   - 收到 `Shutdown` action（来自 GUI 退出或 CLI `termfast shutdown`）
   - 执行 FP-5.4 的 6 步关闭流程
   - 通知所有客户端 `Shutdown` 事件 → 客户端清理
   - 删除 `daemon.lock` + socket 文件

7. **daemon 异常恢复**：
   - daemon 崩溃 → `daemon.lock` 残留 → 下次启动检测 PID 不存活 → 覆盖 lock + 重建 socket
   - **内嵌模式**：daemon 崩溃 = Tauri 进程崩溃 → 用户重启 App 即可
   - **独立模式**：CLI 检测到 daemon 不在 → 提示"daemon 未运行，请先启动 `termfast --daemon`"

**单元测试**：
- daemon 启动 → socket 文件创建 + 权限 600 + daemon.lock 写入
- 客户端连接 → 分配 ClientId → 加入广播列表
- 请求/响应 round-trip（每个 Action 至少一个测试）
- 事件广播：core 状态变化 → 所有客户端收到事件
- 客户端断开 → 从广播列表移除 → 其他客户端不受影响
- `PauseAllTriggers` → 检测定时器停止
- `ResumeAllTriggers` → 全量检测 + 返回 pending_events
- `PauseServerTriggers` / `ResumeServerTriggers` 只影响指定服务器
- 暂停期间手动触发可入队但不执行
- daemon 优雅关闭 → 通知所有客户端 → 清理 socket + lock
- daemon 崩溃残留 lock → 下次启动覆盖
- 错误返回 `IpcError`（code + detail，覆盖 §10.0 所有 ErrorCode）

**集成测试**（mock core + 真实 socket）：
- 多客户端同时连接 → 各自独立处理请求
- 客户端 A 发 `ConnectServer` → 客户端 B 收到 `server:status_changed` 事件
- 客户端 A 发 `ManualFireTrigger` → 客户端 B 收到 `trigger:fired` + `trigger:command_executed` + `trigger:completed`
- 暂停所有触发器 → 手动触发不执行 → 恢复 → 全量检测
- **daemon 崩溃恢复**（提前验证，不等 Phase 9）：模拟 daemon 崩溃 → lock 残留 → 重启 daemon → 检测 PID 不存活 → 覆盖 lock + 重建 socket → CLI 可连接
- **daemon 启动冲突**：daemon 已运行 → 再启动 → 检测 PID 存活 → 拒绝启动 + 提示

**文档确认清单**：
- [ ] Action 枚举覆盖 §10.1-10.5 所有 IPC 命令？
- [ ] `pause/resume_all/server_triggers` 4 个命令实现？（§10.3）
- [ ] 暂停期间事件处理策略（停止检测/恢复全量检测）？（§10.3）
- [ ] `save_trigger_as_template` 实现？（§10.4）
- [ ] `import_servers` 实现？（§10.1）
- [ ] 事件广播覆盖 §10.6 所有事件类型？
- [ ] log kind 与 data schema 完整？（§10.6）
- [ ] ErrorCode 覆盖 §10.0 全部错误码？
- [ ] 错误返回 `IpcError`（code + detail）？
- [ ] daemon 优雅关闭复用 FP-5.4 流程？
- [ ] daemon 崩溃恢复（lock 残留处理）？
- [ ] socket 文件权限 600？

### FP-6.2 GUI 客户端（Tauri 内嵌 daemon + IPC 桥接）

**开发细节**：

1. `crates/desktop/src/daemon_embed.rs`：内嵌 daemon 管理
   - Tauri 进程启动时调用 `daemon::start()` 在进程内启动 socket server
   - daemon 持有 `crates/core` 所有运行时状态
   - daemon 事件 → 转换为 Tauri events → `app.emit()` 推给前端
   - GUI 退出 → daemon 随进程退出（执行 FP-5.4 优雅关闭流程）

2. `crates/desktop/src/ipc.rs`：Tauri IPC 命令层（前端 → 内嵌 daemon 的桥接）
   - 每个 `#[tauri::command]` 函数：接收前端参数 → 构造 `Request` → 直接调用 `daemon::handle_request()` → 等待 `Response` → 返回前端
   - **内嵌模式下无需 socket 连接**：前端 Tauri IPC → `daemon::handle_request()` 直接函数调用（同进程）
   - 命令列表与 §10.1-10.5 完全一致（与 FP-6.1 Action 枚举一一对应）
   - 错误处理：daemon 返回 `Response::Err` → 转换为前端可处理的错误格式

3. **Tauri 事件推送**（daemon 事件 → 前端）：
   - `daemon_embed.rs` 收到 daemon 事件 → `app.emit(event_name, data)`
   - 事件名与 §10.6 一致：`server:status_changed`、`proxy:status_changed`、`trigger:fired`、`trigger:command_executed`、`trigger:completed`、`log:entry`
   - 前端通过 `@tauri-apps/api` 的 `listen()` 监听这些事件
   - **CLI 操作产生的事件也通过此路径推给前端**（daemon 不区分事件来源）

4. **进程启动流程**：
   ```
   termfast（无参数）→ Tauri 主进程启动
     → daemon_embed.rs 调用 daemon::start()
       → 初始化 core 运行时（ConfigManager / ServerManager / ...）
       → 监听 socket（供 CLI 连接）
       → 写 daemon.lock
     → 打开 GUI 窗口
     → 前端通过 Tauri IPC → ipc.rs → daemon::handle_request() → core API
     → CLI 连 socket → daemon 执行 → 事件广播 → GUI 收到 → UI 更新
   ```

5. **无头模式启动流程**（`termfast --daemon`）：
   ```
   termfast --daemon → crates/daemon main.rs 启动
     → daemon::start()（同上，但不打开 GUI）
     → CLI 连 socket → 全功能可用
     → SIGINT/SIGTERM → 优雅关闭
   ```

**单元测试**：
- Tauri 命令 → Request 转换正确
- Response → 前端返回值转换正确
- daemon 事件 → Tauri emit 转换正确
- 内嵌 daemon 启动 → socket 监听 + daemon.lock 写入
- GUI 退出 → daemon 优雅关闭

**集成测试**：
- 前端调用 Tauri IPC → daemon 执行 → 前端收到响应
- daemon 事件 → 前端 `listen()` 收到
- CLI 连 socket → 操作 → 前端收到事件（CLI→GUI 实时更新）

**文档确认清单**：
- [ ] Tauri 命令列表与 §10.1-10.5 完全一致？
- [ ] Tauri 事件推送格式与 §10.6 一致？
- [ ] 内嵌 daemon 启动（daemon::start() in Tauri 进程）？
- [ ] CLI 操作 → 事件 → 前端实时收到？
- [ ] GUI 退出 → daemon 优雅关闭？
- [ ] `--daemon` 无头模式独立运行？

### FP-6.3 CLI 客户端（独立二进制）

**开发细节**：

1. `crates/cli/src/main.rs`：CLI 入口（用 `clap` derive）
   ```rust
   #[derive(Parser)]
   #[command(name = "termfast", version, about = "TermFast CLI")]
   struct Cli {
       /// 指定配置文件路径（默认用平台标准路径）
       #[arg(long, global = true)]
       config: Option<PathBuf>,

       /// JSON 输出模式（供脚本集成）
       #[arg(long, global = true)]
       json: bool,

       /// 详细输出
       #[arg(long, short = 'v', global = true)]
       verbose: bool,

       #[command(subcommand)]
       command: Commands,
   }

   #[derive(Subcommand)]
   enum Commands {
       /// 启动 daemon（无 GUI）
       Daemon,
       /// 显示所有服务器状态
       Status,
       /// 连接指定服务器
       Connect { server: String },
       /// 断开指定服务器
       Disconnect { server: String },
       /// 开关代理
       Proxy { server: String, #[arg(action = ArgAction::Set)] on: Option<bool> },
       /// 手动触发触发器
       Trigger {
           server: String,
           trigger: String,
           /// 异步模式：立即返回不等待执行完成
           #[arg(long)]
           async_mode: bool,
       },
       /// 暂停所有触发器
       PauseTriggers { #[arg(long)] server: Option<String> },
       /// 恢复触发器
       ResumeTriggers { #[arg(long)] server: Option<String> },
       /// 列出服务器
       List,
       /// 列出触发器
       Triggers { server: String },
       /// 列出模板
       Templates,
       /// 查看日志
       Logs {
           server: Option<String>,
           #[arg(long)] level: Option<String>,
           #[arg(long)] tail: Option<usize>,  // 最后 N 条
       },
       /// 关闭 daemon
       Shutdown,
       /// daemon 状态
       DaemonStatus,
   }
   ```

2. `crates/cli/src/client.rs`：daemon socket 客户端
   - 读 `daemon.lock` 找到 socket 路径 → 连接 daemon
   - daemon 未运行 → 提示"daemon 未运行，请先启动 `termfast`（GUI 模式）或 `termfast --daemon`（无头模式）"→ 退出码 4（配置错误）
   - 发送 `Request` → 等待 `Response` → 解析结果
   - **`--config` 参数语义**：
     - `--daemon` 模式：`--config` 指定 daemon 启动时使用的配置文件路径（有效）
     - CLI 命令模式（`termfast status` 等）：`--config` **被忽略**并打印警告"daemon 已在运行，配置由 daemon 管理，--config 参数被忽略"。CLI 命令模式连已运行的 daemon socket，配置由 daemon 持有，CLI 无法更改

3. **输出格式**（§2.4 CLI UX 设计）：

   **人类可读模式**（默认，用 `comfy-table`）：
   ```
   $ termfast status
   SERVER        STATUS    CLIENT_IP     SOCKS5  HTTP   TRIGGERS
   东京节点      已连接    1.2.3.4       1080    8080   2
   美西节点      重连 3/10 -             1081    8081   1
   韩国节点      断开      -             1082    8082   0
   ```

   **JSON 模式**（`--json`，供脚本集成）：
   ```json
   $ termfast status --json
   [
     {"name":"东京节点","status":"connected","ip":"1.2.3.4","socks5_port":1080,"http_port":8080,"triggers":2},
     {"name":"美西节点","status":"reconnecting","retry":3,"max_retry":10,"socks5_port":1081,"http_port":8081,"triggers":1}
   ]
   ```

4. **退出码**（§2.4）：
   | 退出码 | 含义 |
   |--------|------|
   | 0 | 成功 |
   | 1 | 通用错误 |
   | 2 | 认证失败 |
   | 3 | 连接失败 |
   | 4 | 配置错误 / daemon 未运行 |
   | 5 | 触发器执行失败 |

5. **`trigger` 命令的同步/异步模式**（§2.4）：
   - 默认同步：发送 `ManualFireTrigger` → 等待 `trigger:completed` 事件 → 输出结果
   - `--async`：发送 `ManualFireTrigger` → 立即返回（只确认已入队）
   - 同步模式实现：发送请求后保持连接，监听 `trigger:completed` 事件，匹配 `trigger_id` 后输出结果并退出

6. **`logs --tail` 实时模式**：
   - `termfast logs --tail 20`：显示最后 20 条日志后退出
   - `termfast logs --tail 0`（或 `termfast logs -f`）：持续监听，新日志实时输出（类似 `tail -f`）
   - 实现：发送 `GetLogs` 获取历史 → 如需实时则保持连接监听 `log:entry` 事件

7. **信号处理**（§2.4）：
   - `SIGINT`/`SIGTERM` → 优雅退出（关闭 socket 连接，不发 Shutdown）
   - daemon 不受 CLI 退出影响

8. **错误输出**（§2.4）：
   - 错误消息输出到 stderr
   - 正常结果输出到 stdout
   - `--verbose` 输出调试细节（请求/响应原始 JSON）

**单元测试**：
- clap 参数解析（每个子命令）
- 人类可读表格输出格式
- JSON 输出格式
- 退出码正确（成功 0 / 认证失败 2 / 连接失败 3 / daemon 未运行 4 / 触发器失败 5）
- daemon 未运行 → 提示 + 退出码 4
- `trigger` 同步模式：等待 `trigger:completed` 事件
- `trigger --async`：立即返回
- `logs --tail N`：显示 N 条后退出
- `logs -f`：持续监听
- 信号处理：SIGINT → 优雅退出

**集成测试**（启动 daemon + CLI 连接）：
- `termfast status` → 显示服务器列表
- `termfast connect "东京节点"` → 服务器连接 → status 变为已连接
- `termfast trigger "东京节点" "更新防火墙"` → 触发器执行 → 输出结果
- `termfast proxy "东京节点" on` → 代理开启
- `termfast pause-triggers` → 暂停
- `termfast resume-triggers` → 恢复 + 显示 pending_events
- `termfast logs --tail 10` → 显示最后 10 条
- `termfast shutdown` → daemon 优雅关闭
- `--json` 输出格式正确
- daemon 未运行 → 退出码 4

**文档确认清单**：
- [ ] 子命令覆盖所有核心功能？（§2.4）
- [ ] `--daemon` 启动无 GUI daemon？
- [ ] `--config` 指定配置文件路径？（§2.4）
- [ ] `--json` JSON 输出？
- [ ] 退出码 0-5？（§2.4）
- [ ] `trigger` 同步/异步模式？（§2.4）
- [ ] `logs --tail` + 实时模式？
- [ ] 信号处理 `SIGINT`/`SIGTERM`？
- [ ] 错误输出 stderr / 正常输出 stdout？
- [ ] daemon 未运行时提示 + 退出码 4？

<!-- === SECTION 7a (Phase 6a) END === -->

---

## Phase 6b：桌面壳功能（tray / autostart / platform / 日志 / 通知 / 离线检测 / 窗口效果）

> Phase 6b 可与 Phase 7（前端基础）并行开发——6a 完成后前端就能通过 Tauri IPC 调 daemon。

### FP-6.4 系统托盘

**开发细节**：

1. `crates/desktop/src/tray.rs`：系统托盘（§9.3 托盘菜单）
   - 托盘图标颜色：绿（全部正常）/黄（有重连中）/红（有认证失败或异常）+ badge 数字
   - 托盘菜单结构（子菜单模式）：
     ```
     TermFast
     ├── 服务器
     │   ├── 东京节点  ●  [连接/断开]  [代理 ✓]
     │   ├── 美西节点  ○  [连接/断开]  [代理 ✗]
     │   └── ...
     ├── ─────────
     ├── 全部连接
     ├── 全部断开
     ├── ─────────
     ├── 显示主窗口
     └── 退出
     ```
   - 左键点击 → 显示/隐藏主窗口
   - 右键点击 → 显示菜单
   - 状态变化时按通知偏好发送系统通知（§9.5）

**单元测试**：
- 托盘菜单结构正确
- 图标颜色根据全局健康度变化
- badge 数字计算正确

**E2E 测试**：
- 托盘右键 → 服务器子菜单 → 连接/断开，1 次点击完成（U1）
- 托盘图标颜色随状态变化

**文档确认清单**：
- [ ] 子菜单模式？（§9.3）
- [ ] 图标颜色 绿/黄/红 + badge？（U17）
- [ ] 左键显示/隐藏窗口？
- [ ] 状态变化系统通知？

### FP-6.5 开机自启

**开发细节**：

1. `crates/desktop/src/autostart.rs`：用 `tauri-plugin-autostart`
   - `enable_autostart()` / `disable_autostart()` / `is_autostart_enabled()`
   - 与设置页 `general.auto_start` 同步

**文档确认清单**：
- [ ] 用 `tauri-plugin-autostart`？
- [ ] 与配置 `auto_start` 同步？

### FP-6.6 平台适配层

**开发细节**：

1. `crates/desktop/src/platform/mod.rs`：`PlatformAdapter` trait
   - `set_system_proxy(host, port, proto) -> Result<{ needs_privilege }>`
   - `clear_system_proxy(proto) -> Result<{ needs_privilege }>`
   - `get_system_proxy() -> { server_id?, socks5_port?, http_port? }`

2. `crates/desktop/src/platform/macos.rs`（§10.2 macOS 系统代理权限方案）：
   - `networksetup -setsocksfirewallproxy` / `-setwebproxy` / `-setsocksfirewallproxystate` / `-setwebproxystate`
   - sudoers 白名单方案：首次授权 → 写 `/etc/sudoers.d/termfast` → `visudo -cf` 校验 → 后续免密
   - 校验失败 → 删除 sudoers 文件 → 回退 fallback
   - 拒绝授权 fallback → 复制命令到剪贴板 + GUI 提示

3. `crates/desktop/src/platform/windows.rs`：
   - 写注册表 `HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings`
   - `ProxyEnable=1`, `ProxyServer=socks=127.0.0.1:1080;http=127.0.0.1:8080`
   - 无需管理员权限
   - 通知系统代理变化（`InternetSetOption` with `INTERNET_OPTION_SETTINGS_CHANGED`）

**单元测试**：
- macOS：sudoers 文件内容正确
- macOS：visudo -cf 校验逻辑
- Windows：注册表写入路径正确

**E2E 测试**：
- macOS：设置系统代理 → 浏览器生效
- macOS：清除系统代理 → 浏览器恢复
- Windows：设置/清除系统代理

**文档确认清单**：
- [ ] macOS sudoers 白名单方案？（§10.2）
- [ ] visudo -cf 校验 + 失败删除？
- [ ] 拒绝授权 fallback（复制命令）？
- [ ] Windows 注册表方案？
- [ ] Windows 无需管理员？

### FP-6.7 日志系统

**开发细节**：

1. `crates/core/src/log.rs`：日志管理
   - 内存日志缓冲：环形缓冲，`max_log_entries`（默认 1000）
   - 日志条目结构：`{ id, timestamp, server_id, level, category, message, execution_id?, command?, exit_code?, stdout?, stderr? }`
   - `category`：Connection / Trigger / Proxy / Error / Config
   - 落盘日志（可选）：`log_to_file` 开启时写入文件，按 `log_max_days` + `log_max_size_mb` 轮转
   - 日志导出：导出为文件

2. 日志查询：
   - `get_logs(filter) -> Vec<LogEntry>`
   - 筛选：类型（全部/连接/触发器/错误）+ 服务器 + 搜索框（全文搜索 + 正则）
   - 按 `execution_id` 分组

**单元测试**：
- 日志添加 + 查询
- 环形缓冲超过 max_log_entries 时自动淘汰
- 筛选：按类型/服务器/关键词
- execution_id 分组
- 落盘日志轮转

**文档确认清单**：
- [ ] 内存缓冲 max_log_entries 默认 1000？（§7.2）
- [ ] 日志条目结构含 execution_id？（§9.4 日志面板）
- [ ] 落盘日志轮转（天数 + 体积）？
- [ ] 全文搜索 + 正则？

### FP-6.8 通知系统

**开发细节**：

1. 通知偏好（§9.5）：
   - `NotificationPrefs`：按类别分组（连接/代理/触发器/配置），每类独立开关
   - 通知方式三档：系统通知 + 托盘变色 / 仅托盘变色 / 仅日志
   - 默认：需用户介入 + 执行失败 → 开启；正常行为 → 关闭

2. 通知发送：
   - `send_notification(category, event, server_id)`
   - 根据偏好决定是否发送 + 发送方式
   - 系统通知用 `tauri-plugin-notification`
   - 托盘变色通过更新托盘图标

**单元测试**：
- 偏好读取 + 匹配
- 默认偏好正确（断开开、认证失败开、连接成功关...）
- 通知方式三档

**文档确认清单**：
- [ ] 通知偏好按类别分组？（§9.5 通知覆盖范围表）
- [ ] 默认值正确？（异常开、正常关）
- [ ] 三档通知方式？
- [ ] 触发器卡片单独通知开关？

### FP-6.9 离线/飞行模式检测

**开发细节**：

1. `crates/desktop/src/network.rs`：离线检测（§5.4 离线/飞行模式处理）
   - 集成 `tauri-plugin-network` 检测系统在线状态
   - 离线时（飞行模式/网线拔出/Wi-Fi 断开）：
     - 暂停所有服务器的重连定时器
     - GUI 状态显示"离线"（区别于"断开"）
     - **不消耗 `max_attempts`**（离线不计入重连次数）
   - 在线恢复时：
     - 立即触发所有"离线前已连接"的服务器重连
     - 重置重连计数器（离线不计入 max_attempts）
     - GUI 状态从"离线" → "连接中" → "已连接"

2. 与 macOS 休眠的区别（§5.4）：
   - 休眠：系统级暂停（所有定时器停止），唤醒后心跳超时触发重连（已有逻辑）
   - 离线：网络层断开但进程仍在运行，`tauri-plugin-network` 事件主动通知（新增）
   - 两者都通过"暂停重连 + 恢复时立即重连"处理，但检测机制不同

3. 状态机扩展（FP-5.3）：
   - 新增"离线"状态转换：任何状态 → 离线（系统离线）→ 连接中（系统恢复）
   - 离线状态不显示重连次数，不消耗 max_attempts

**单元测试**：
- 离线检测 → 暂停所有重连定时器
- 在线恢复 → 立即触发重连 + 重置计数器
- 离线不消耗 max_attempts
- 离线前已连接的服务器恢复时重连
- 离线前已断开的服务器恢复时不自动重连

**E2E 测试**：
- 模拟离线（断开 mock SSH server 网络）→ 验证暂停重连
- 模拟在线恢复 → 验证立即重连 + 计数器重置

**文档确认清单**：
- [ ] 集成 `tauri-plugin-network`？（§5.4）
- [ ] 离线时暂停重连 + 不消耗 max_attempts？（§5.4）
- [ ] 在线恢复立即触发重连 + 重置计数器？（§5.4）
- [ ] "离线"状态区别于"断开"？（§5.4）
- [ ] 与 macOS 休眠检测机制区分？（§5.4）

### FP-6.10 原生窗口效果与标题栏平台适配

**开发细节**：

1. `crates/desktop/src/platform/macos.rs` + `windows.rs`（§4.4 平台适配层）：
   - `apply_window_effect(&self, window: &Window) -> Result<()>`
   - macOS：`window-vibrancy` crate 的 `apply_vibrancy()`（NSVisualEffectView 毛玻璃）
   - Windows：`window-vibrancy` crate 的 `apply_mica()`（Mica 材质）
   - `PlatformAdapter` trait 已在 FP-6.4 定义，此处补充 `apply_window_effect` 方法

2. `src/components/desktop/TitleBar.tsx`（§4.4）：
   - 按平台条件渲染：
     - macOS：红绿灯按钮（关闭/最小化/最大化），内容延伸到顶部
     - Windows：标准标题栏或自绘最小化/最大化/关闭按钮
   - 平台检测：`navigator.userAgent` 或 Tauri `os` plugin

**单元测试**：
- 平台检测正确
- 标题栏按平台渲染

**E2E 测试**：
- macOS 毛玻璃效果显示
- Windows Mica 效果显示
- 标题栏按钮功能（关闭/最小化/最大化）

**文档确认清单**：
- [ ] macOS `apply_vibrancy`？（§4.4）
- [ ] Windows `apply_mica`？（§4.4）
- [ ] 标题栏按平台条件渲染？（§4.4）
- [ ] macOS 红绿灯 + 内容延伸顶部？
- [ ] Windows 标准/自绘按钮？

<!-- === SECTION 7 (Phase 6) END === -->

---

## Phase 7：前端基础（项目搭建 / i18n / 布局 / 路由）

### FP-7.1 前端项目搭建

**开发细节**：

1. `src/` 目录结构（§4.2）：
   - `App.tsx`：根组件
   - `components/ui/`：shadcn/ui 组件
   - `components/shared/`：平台无关组件
   - `components/desktop/`：桌面专属组件
   - `hooks/`：自定义 hooks
   - `stores/`：状态管理（Zustand）
   - `i18n/`：国际化
   - `types/`：TypeScript 类型定义（与 Rust 结构体对应）

2. 状态管理：Zustand
   - `serverStore`：服务器列表 + 状态
   - `logStore`：日志条目 + 筛选
   - `configStore`：全局配置
   - `triggerStore`：触发器模板 + 实例

3. Tauri IPC 封装：`hooks/useIpc.ts`
   - 封装 `invoke` 调用
   - 封装事件监听 `listen`
   - 类型安全

**单元测试**（Vitest）：
- Zustand store 状态转换
- IPC 封装 mock 测试

**文档确认清单**：
- [ ] 目录结构与 §4.2 一致？
- [ ] shared/ 与 desktop/ 分离？

### FP-7.2 国际化（i18n）

**开发细节**：

1. `src/i18n/config.ts`（§3）：
   - i18next + react-i18next
   - 语言：`system` | `zh-CN` | `zh-TW` | `en`
   - `detectSystemLanguage()`：navigator.language 检测
   - v1 实现 zh-CN + en 双语

2. 翻译文件：
   - `src/i18n/locales/zh-CN.json`
   - `src/i18n/locales/en.json`
   - 错误文案：`errors.{ErrorCode}` 格式（§3）
   - 所有用户可见文案走 i18n，不硬编码

3. 前端错误渲染（§3）：
   ```typescript
   function renderError(error: IpcError): string {
     return t(`errors.${error.code}`, { detail: error.detail });
   }
   ```

**单元测试**：
- 语言检测
- 错误文案渲染：每个 ErrorCode 有对应翻译
- 无硬编码字符串（lint 规则检查）

**文档确认清单**：
- [ ] 后端不返回翻译文案？（§3）
- [ ] 前端根据 ErrorCode 渲染？
- [ ] v1 中英文双语？
- [ ] 所有文案走 i18n？

### FP-7.3 主窗口布局

**开发细节**：

1. `src/App.tsx`：主窗口布局（§9.4 GUI 信息架构）
   ```
   主窗口
   ├── 服务器列表（左侧栏）
   ├── 全局指示器（顶部）
   ├── 待处理事件横幅区
   ├── Tab 内容区（右侧）
   └── 日志面板（底部，可折叠）
   ```

2. `src/components/desktop/TitleBar.tsx`：标题栏
   - macOS：红绿灯（MacTitleBar）
   - Windows：标准标题栏（WinTitleBar）
   - 按平台条件渲染

3. 毛玻璃效果：
   - macOS：`window-vibrancy`（NSVisualEffectView）
   - Windows：Mica 材质

4. 主题：`system` | `light` | `dark`，跟随系统或用户选择

**单元测试**：
- 布局组件渲染
- 平台条件渲染

**E2E 测试**：
- 主窗口布局正确显示
- macOS 毛玻璃效果
- 主题切换

**文档确认清单**：
- [ ] 布局与 §9.4 信息架构一致？
- [ ] 平台条件标题栏？
- [ ] 毛玻璃效果？
- [ ] 主题 system/light/dark？

### FP-7.4 键盘快捷键

**开发细节**：

1. `src/hooks/useKeyboardShortcuts.ts`（§9.8）：
   - `Cmd/Ctrl + 1...9`：切换服务器
   - `Cmd/Ctrl + 0`：第 10 个服务器
   - `Cmd/Ctrl + N`：添加服务器
   - `Cmd/Ctrl + ,`：设置
   - `Cmd/Ctrl + L`：聚焦日志
   - `Cmd/Ctrl + Shift + L`：聚焦日志搜索
   - `Cmd/Ctrl + Shift + P`：切换代理
   - `Cmd/Ctrl + Shift + T`：暂停/恢复所有触发器
   - `Cmd/Ctrl + Shift + Space`：切换连接（二次确认）
   - `Cmd/Ctrl + E`：折叠/展开日志
   - `Cmd/Ctrl + Shift + R` / `F5`：刷新状态
   - `Esc`：关闭侧滑面板/取消选中/关闭对话框

**单元测试**：
- 快捷键触发正确 action
- `Cmd/Ctrl + Shift + Space` 二次确认

**文档确认清单**：
- [ ] 快捷键列表与 §9.8 一致？
- [ ] Space 改为 `Cmd/Ctrl + Shift + Space`？（§9.8 Space 误触修复）

<!-- === SECTION 8 (Phase 7) END === -->

---

## Phase 8：前端功能（引导 / 服务器列表 / 详情 Tab / 触发器 / 日志 / 设置）

### FP-8.1 首次运行引导

**开发细节**：

1. `src/components/shared/Onboarding.tsx`（§18）：
   - 触发条件：配置文件不存在或 servers 为空
   - 快速模式（3 步，默认推荐）：
     - 步骤 1：VPS 信息 + 认证（地址/端口/用户名/别名 + 已有密钥/密码）
     - 步骤 2：点击连接（自动测试 + 自动配置密钥，保持 SSH 连接供步骤 3 用，不触发触发器）
     - 步骤 3：防火墙白名单 + 完成（默认勾选，自动检测 firewalld/ufw，填写受保护端口）
   - 高级模式（7 步，可选展开）：
     - 步骤 1：欢迎页
     - 步骤 2：VPS 信息
     - 步骤 3：认证方式
     - 步骤 4：测试连接
     - 步骤 5：代理配置（SOCKS5/HTTP 端口）
     - 步骤 6：选择触发器模板
     - 步骤 7：完成

2. 防火墙类型自动检测（§18.2 步骤 3）：
   - 通过保持的 SSH 连接 exec：
     1. `command -v firewall-cmd && firewall-cmd --state` → firewalld
     2. `command -v ufw && ufw status` → ufw
     3. 两个都装 → 优先 firewalld
     4. 都没装 → 提示用户手动配置

3. 密钥自动配置提示（§8.5 通知用户）：
   - 密钥配置成功后显示提示框（文件位置 + passphrase 位置 + 备份建议）

4. 测试连接失败不阻塞，用户可跳过（§18.3）

**单元测试**（Vitest + React Testing Library）：
- 快速模式 3 步流程
- 高级模式 7 步流程
- 防火墙自动检测逻辑
- 测试连接失败 → 可跳过
- 密钥自动配置提示显示

**E2E 测试**（Playwright / Tauri WebDriver）：
- 首次启动 → 引导流程 → 完成后进入主界面
- 快速模式完整流程（mock SSH server）
- 高级模式完整流程
- 防火墙白名单配置 → 触发器添加成功

**文档确认清单**：
- [ ] 快速模式 3 步？（§18.2）
- [ ] 高级模式 7 步？
- [ ] 步骤 3 防火墙白名单默认勾选？（§18.2 安全缺口修复）
- [ ] 防火墙类型自动检测？
- [ ] 测试连接不触发触发器？（§6.9）
- [ ] 密钥自动配置提示？（§8.5）
- [ ] 测试连接失败可跳过？（§18.3）

### FP-8.2 服务器列表

**开发细节**：

1. `src/components/shared/ServerList.tsx`（§9.4）：
   - 列表项：状态点（5 态颜色+形状+图标）+ 名称 + 代理 toggle + 地址
   - 5 态可视化（§9.4 5 态可视化详情）：
     - 已连接：绿 ● 实心圆
     - 连接中：黄 ◑ 右半圆 + ↻ 转圈
     - 重连中：橙 ◐ 左半圆 + ⟳ + N/M
     - 认证失败：红 ▲ 三角形 + ⚠
     - 断开：灰 ○ 空心圆
   - a11y：aria-label + aria-live + prefers-reduced-motion
   - 异常项置顶
   - 全局健康度：`[3 已连接 / 1 异常]` 顶部显示
   - 状态摘要面板：点击健康度展开
   - 防火墙未配置提示 badge（§9.4）
   - 端口点击复制 chip（U8）
   - [+ 添加] / [全部连接] / [全部断开] / [模板库] / [设置] 按钮

2. 代理 toggle（U6）：
   - 列表项内联 toggle，1 次点击控制 SOCKS5+HTTP
   - 与代理 Tab 总开关双向同步

**单元测试**：
- 5 态渲染正确
- 异常项置顶排序
- 代理 toggle 切换
- 端口 chip 点击复制
- 防火墙 badge 显示/关闭
- 空状态（无服务器）

**E2E 测试**：
- 添加服务器 → 列表显示
- 连接/断开 → 状态点变化
- 代理 toggle → 代理启停
- 端口 chip 点击 → 剪贴板内容正确
- 5 台服务器状态摘要面板

**文档确认清单**：
- [ ] 5 态颜色+形状+图标三重区分？（§9.4）
- [ ] a11y（aria-label + aria-live + reduced-motion）？
- [ ] 异常项置顶？
- [ ] 全局健康度 + 状态摘要面板？
- [ ] 防火墙未配置 badge？（§9.4）
- [ ] 端口点击复制 chip？（U8）
- [ ] 代理 toggle 同时控制 SOCKS5+HTTP？（U6）
- [ ] 空状态引导文案？（U15）

### FP-8.3 服务器详情 Tab

**开发细节**：

1. `src/components/shared/ServerDetail.tsx`（§9.4 Tab 分层）：
   - 4 个 Tab：状态 / 代理 / 触发器 / 认证
   - Tab 切换保留上下文（U14）：日志筛选、滚动位置、展开状态、编辑器草稿、选中服务器

2. **状态 Tab**：
   - 连接状态、当前 IP、VPS 地址、连接时长、重连次数、上次 IP 变化、下次检查时间

3. **代理 Tab**（§9.4）：
   - 主开关（与列表 toggle 双向同步）
   - 高级选项（SOCKS5/HTTP 独立控制）可展开
   - 端口复制 chip
   - 测试代理按钮 → 显示出口 IP + 延迟
   - 设为系统代理按钮（U18）
   - 系统代理出口指示器

4. **认证 Tab**（U12，§8.9）：
   - 认证方式显示（密钥/密码）
   - 安全级别显示
   - 重试/切换按钮
   - 密钥文件路径显示
   - 重新生成密钥 / 指定其他密钥

5. **触发器 Tab**：见 FP-8.4

**单元测试**：
- Tab 切换上下文保留
- 状态 Tab 信息显示
- 代理 Tab 主开关 + 高级选项
- 认证 Tab 信息 + 操作

**E2E 测试**：
- Tab 切换 → 上下文保留验证
- 代理测试 → 显示出口 IP
- 设为系统代理 → 系统代理生效

**文档确认清单**：
- [ ] 4 个 Tab？（§9.4）
- [ ] Tab 切换上下文保留？（U14 明细表）
- [ ] 代理 Tab 主开关与列表 toggle 双向同步？（U6）
- [ ] 设为系统代理？（U18）

### FP-8.4 触发器卡片列表 + 添加流程

**开发细节**：

1. `src/components/shared/TriggerList.tsx`（§6.5 触发器卡片设计）：
   - 卡片元素：类型 tag + 命令首行摘要 + 已修改 tag（黄色）+ [同步模板] [忽略] + [▶ 运行] [编辑] + ⋯ 菜单
   - 已修改 tag：`modified_from_template` 动态计算
   - 模板有更新 tag：`template_has_update` 动态计算
   - [▶ 运行]：二次确认（低危，含"本次会话不再询问"勾选项）→ 执行 → 日志实时显示
   - 执行中状态（U19）：进度条 "2/3"
   - [编辑]：打开侧滑编辑面板
   - ⋯ 菜单：移除（高危，二次确认）
   - 暂停本服务器开关
   - [+ 添加触发器] → 模板选择器

2. `src/components/shared/TriggerTemplatePicker.tsx`（§6.5 添加触发器流程）：
   - 模板列表（内置 + 用户）
   - 选中即预览（用示例值渲染命令）
   - 参数表单（模板有 parameters_schema 时显示）
   - 渲染预览（NewIP=1.2.3.4, OldIP=5.6.7.8 + 用户参数值）
   - [取消] / [添加]
   - "+ 新建自定义触发器"选项

**单元测试**：
- 卡片渲染：类型 tag + 命令摘要 + 已修改 tag
- 已修改 tag 显示/隐藏逻辑
- [▶ 运行] 二次确认
- 执行中进度条
- 模板选择器：选中即预览
- 参数表单：端口校验 1-65535
- 空状态（无触发器）

**E2E 测试**：
- 从模板添加触发器 → 卡片显示
- 手动触发 → 日志实时显示
- 编辑触发器 → 保存 → 已修改 tag 出现
- 同步模板 → 已修改 tag 消失
- 移除触发器 → 二次确认 → 卡片消失

**文档确认清单**：
- [ ] 卡片元素与 §6.5 一致？
- [ ] 已修改 tag（黄色背景，非按钮）？
- [ ] [同步模板] 只在已修改时显示？
- [ ] [▶ 运行] 二次确认 + "本次会话不再询问"？
- [ ] 执行中进度条 "2/3"？（U19）
- [ ] 选中即预览？
- [ ] 参数表单 + 端口校验？
- [ ] 空状态引导？（U15）

### FP-8.5 触发器编辑器（CodeMirror 6）

**开发细节**：

1. `src/components/shared/TriggerEditor.tsx`（§9.6）：
   - 侧滑面板（从右侧滑入，占详情区 50% 宽度）
   - 字段：名称、类型（下拉）、超时（秒）、冷却（秒）、失败继续（开关）
   - 通知：执行成功时通知（开关）、执行失败时通知（开关）
   - 命令编辑器：CodeMirror 6
     - Shell 语法高亮
     - 占位符 `{{.NewIP}}` / `{{#if OldIP}}` 不同颜色高亮
     - 条件块 `{{#if}}...{{/if}}` 可折叠/展开
     - 行号显示
     - Tab 缩进
   - [取消] / [保存]

**单元测试**：
- 侧滑面板打开/关闭
- CodeMirror 语法高亮
- 占位符高亮
- 条件块折叠
- 保存 → 配置更新

**E2E 测试**：
- 打开编辑器 → 修改命令 → 保存 → 卡片更新
- 条件块折叠/展开
- Esc 关闭侧滑面板

**文档确认清单**：
- [ ] 侧滑面板（非模态弹窗）？（§9.6）
- [ ] CodeMirror 6 shell 高亮？
- [ ] 占位符高亮？
- [ ] 条件块折叠？
- [ ] 行号 + Tab 缩进？

### FP-8.6 日志查看器

**开发细节**：

1. `src/components/shared/LogViewer.tsx`（§9.4 日志面板）：
   - 常驻底部，可折叠，可拖拽调高度
   - 筛选条：类型（全部/连接/触发器/错误）+ 服务器（当前/全部）+ 搜索框 + 清空/导出
   - 全文搜索：关键词过滤 + 高亮 + 正则（`.*` 前缀切换）
   - 自动滚动：用户在底部 → 跟随；用户向上滚 → 暂停 + "↓ 回到最新"按钮
   - 时间戳：默认本地时间 `14:32:01`，悬停显示完整 + UTC
   - 执行分组：按 `execution_id` 分组，折叠显示摘要，开关持久化
   - 默认折叠策略：1 台折叠 / 2+ 台展开 / 首次触发器执行后自动展开
   - 未读计数 badge（折叠时有新日志）
   - 切换服务器时日志面板不跟随切换

**单元测试**：
- 日志条目渲染
- 筛选：类型/服务器/关键词
- 全文搜索 + 正则
- 自动滚动行为
- 执行分组折叠/展开
- 未读计数 badge

**E2E 测试**：
- 触发器执行 → 日志实时推送
- 筛选 → 只显示匹配条目
- 执行分组 → 折叠显示摘要
- 拖拽调高度
- 空状态（无日志）

**文档确认清单**：
- [ ] 常驻底部 + 可折叠 + 可拖拽？（§9.4）
- [ ] 筛选条（类型+服务器+搜索）？
- [ ] 全文搜索 + 正则？
- [ ] 自动滚动 + "回到最新"？
- [ ] 时间戳本地 + UTC 悬停？
- [ ] 执行分组 + 摘要？
- [ ] 默认折叠策略（1台/2+台/首次执行）？
- [ ] 未读 badge？
- [ ] 切换服务器不跟随？
- [ ] 空状态？（U15）

### FP-8.7 模板库管理

**开发细节**：

1. `src/components/shared/TemplateLibrary.tsx`（U20，§9.4）：
   - 紧凑列表，内置/用户分组
   - 点击行展开命令预览
   - [新建模板] → 模板编辑器
   - [导入] / [导出] 按钮
   - 模板编辑器：名称、类型、参数 schema、命令（CodeMirror）

**单元测试**：
- 内置/用户分组
- 展开命令预览
- 新建/编辑模板
- 导入/导出

**E2E 测试**：
- 新建模板 → 列表显示
- 导出 → 文件下载
- 导入 → 列表更新
- 空状态（无自定义模板）

**文档确认清单**：
- [ ] 内置/用户分组？（U20）
- [ ] 点击展开预览？
- [ ] 导入/导出？
- [ ] 空状态？（U15）

### FP-8.8 设置页面

**开发细节**：

1. `src/components/shared/SettingsPage.tsx`（§9.5）：
   - **通用**：自启、最小化到托盘、主题、语言、系统代理默认出口
   - **日志**：级别、落盘开关、落盘目录、保留天数、单文件最大体积、恢复默认
   - **通知**：按类别分组开关（连接/代理/触发器/配置）+ 通知方式三档
   - **代理默认值**：SOCKS5 默认端口、HTTP 默认端口、排队超时、代理测试网址
   - **触发器默认值**：默认超时、默认 continue_on_error、IP 复检全局默认间隔
   - **关于**：版本号、检查更新、GitHub 链接、反馈问题
   - **数据管理**：完整导出（换机迁移）、完整导入
   - **崩溃上报**：opt-in 开关（§22.1）

2. 所有偏好持久化到配置文件

**单元测试**：
- 各设置项渲染
- 偏好修改 → 配置保存
- 通知偏好默认值
- 恢复默认设置

**E2E 测试**：
- 修改设置 → 重启 → 设置保留
- 完整导出 → 文件生成
- 完整导入 → 数据恢复

**文档确认清单**：
- [ ] 设置分区与 §9.5 一致？
- [ ] 通知偏好默认值正确？（§9.5 通知覆盖范围表）
- [ ] 通知方式三档？
- [ ] 完整导出/导入？（§10.1）
- [ ] 崩溃上报 opt-in 默认关？（§22.1）

### FP-8.9 待处理事件横幅区

**开发细节**：

1. 全局指示器下方显示需用户介入的告警列表（§9.4）：
   - 认证失败
   - HostKey 不匹配
   - 端口冲突
   - 密钥文件丢失
   - 配置迁移失败
2. 点击告警 → 跳转对应处理流程
3. 处理后消失

**单元测试**：
- 告警渲染
- 点击跳转
- 处理后消失

**文档确认清单**：
- [ ] 横幅区在全局指示器下方？（§9.4）
- [ ] 点击跳转处理流程？
- [ ] 处理后消失？

### FP-8.10 全局指示器（系统代理出口）

**开发细节**：

1. 顶部显示当前系统代理出口（§9.4）：
   - 当前出口服务器名称 + SOCKS5/HTTP 端口
   - [切换] 按钮 → 选择其他服务器作为出口
   - [清除] 按钮 → 清除系统代理
   - 未设置时显示"未设置系统代理"

**文档确认清单**：
- [ ] 全局指示器在顶部？（§9.4）
- [ ] [切换] [清除] 按钮？
- [ ] U18 一键设为系统代理？

### FP-8.11 确认对话框三档危险等级（含 undo toast）

**开发细节**：

1. `src/components/ui/ConfirmDialog.tsx` + `UndoToast.tsx`（§9.11 确认对话框 C7 修复）：
   - **低危**：默认样式确认框 + ☐ 本次会话不再询问。示例：手动触发触发器
   - **中危**：无确认框，执行后弹 undo toast（Sonner，5-10s 可撤销）。示例：暂停所有触发器、切换认证方式、移除触发器、切换系统代理出口
   - **高危**：红色警示图标 + "此操作不可撤销" + 输入名称确认。示例：删除服务器、删除触发器、删除模板

2. **undo toast 实现**（中危，§9.11）：
   - 用 `sonner` 库（React toast）
   - 执行操作后立即弹 toast "已暂停所有触发器 [撤销]"
   - 5-10s 内可点击 [撤销] 回滚操作
   - 超时后 toast 消失，操作不可撤销
   - 为什么中危用 undo toast 而非确认框：Persona A 高频操作时确认框会变成肌肉记忆（闭眼点确认），等于没防护。undo toast 更符合现代 UX——先执行，5-10s 内可撤销（§9.11）

3. **高危输入名称确认**（§9.11）：
   - 类似 GitHub 删除仓库
   - 明确列出将要执行的操作（如删除服务器：断开 SSH + 删除钥匙串 + 删除密钥文件 + 清理 authorized_keys）
   - 删除按钮用红色 + 动词"删除"（非"确认"）

**单元测试**：
- 低危确认框 + "本次会话不再询问"勾选
- 中危 undo toast：执行 → toast 显示 → 点撤销 → 回滚
- 中危 undo toast：执行 → 超时 → 不可撤销
- 高危输入名称确认：名称不匹配 → 删除按钮禁用
- 高危：列出操作清单

**E2E 测试**：
- 手动触发触发器 → 低危确认框
- 暂停所有触发器 → undo toast → 撤销 → 恢复
- 删除服务器 → 高危输入名称 → 删除

**文档确认清单**：
- [ ] 三档危险等级？（§9.11）
- [ ] 低危：确认框 + "本次会话不再询问"？
- [ ] 中危：undo toast（Sonner，5-10s 可撤销）？
- [ ] 高危：红色图标 + 输入名称确认？
- [ ] 中危操作列表（暂停触发器/切换认证/移除触发器/切换系统代理出口）？
- [ ] 高危操作列表（删除服务器/删除触发器/删除模板）？

### FP-8.12 加载态与骨架屏

**开发细节**：

1. `src/components/ui/Skeleton.tsx` + 加载态规范（§9.11 加载态 C8 修复）：

| 场景 | 加载态设计 |
|------|-----------|
| 添加服务器（含自动生成密钥，3-5s） | 按钮 → 转圈 + "正在连接并配置密钥..."，步骤进度条（连接 → 生成密钥 → 推送公钥 → 完成） |
| 导入服务器/模板 | 全屏遮罩 + 进度条 + "正在导入 N/M..." |
| 启动时加载配置 | 服务器列表区域显示骨架屏（灰色占位条），配置加载完成后淡入真实内容 |
| 切换服务器详情 | Tab 内容区短暂闪现骨架屏（<200ms 时不显示，避免闪烁） |
| 触发器执行中 | 卡片显示进度条 "2/3"（U19），日志面板实时推送 |
| 连接服务器 | 服务器列表项显示 ◐ + 转圈动画，详情区状态 Tab 显示"正在连接..." |

2. **骨架屏规范**（§9.11）：
   - 用灰色占位条模拟内容布局（shadcn/ui Skeleton 组件）
   - 占位条有轻微脉冲动画（遵守 `prefers-reduced-motion`）
   - 加载时间 <200ms 时不显示骨架屏（避免快加载时闪烁）
   - 加载时间 >3s 时显示"加载时间较长，请稍候..."文字

**单元测试**：
- 骨架屏渲染
- <200ms 不显示骨架屏
- >3s 显示"加载时间较长"文字
- prefers-reduced-motion 时静态显示
- 步骤进度条（添加服务器）

**E2E 测试**：
- 添加服务器 → 步骤进度条显示
- 启动时 → 骨架屏 → 淡入真实内容
- 导入 → 全屏遮罩 + 进度条

**文档确认清单**：
- [ ] 6 种加载态场景？（§9.11）
- [ ] 骨架屏规范（脉冲动画/reduced-motion/<200ms 不显示/>3s 文字）？
- [ ] 步骤进度条（添加服务器）？

### FP-8.13 错误呈现规范与多错误聚合

**开发细节**：

1. 错误严重级与呈现方式（§9.12）：

| 严重级 | 用户在 GUI 前 | 用户不在 GUI 前（回来后） | 示例 |
|--------|-------------|-------------------------|------|
| **致命** | 模态弹窗 + 系统通知 | 主界面顶部红色告警条 + 托盘红点 badge | HostKey 不匹配、认证失败 |
| **警告** | toast（5s 自动消失）+ 日志 | 状态 Tab "最近失败"卡片 + 托盘黄点 badge | 触发器命令失败、代理端口占用、SSH 非主动断开 |
| **信息** | 仅日志 + 状态点更新 | 仅日志，无主动提示 | IP 变化、重连成功、代理开关 |

2. **多错误聚合规则**（§9.12）：

| 场景 | 聚合规则 | 呈现 |
|------|---------|------|
| 多台服务器同时断线 | 10s 内累积，合并为一条 | toast: "3 台服务器已断开 [查看详情]" |
| 多个触发器同时失败 | 同一次事件触发的多个触发器，合并为一条 | toast: "2 个触发器执行失败 [查看详情]" |
| 同一服务器连续失败 | 同一错误 30s 内重复，不重复弹 toast | 仅更新日志和状态 Tab 卡片 |
| 致命 + 警告混合 | 致命优先，警告降级为日志 | 模态弹窗只显示致命，警告在日志面板 |

3. **聚合实现**（§9.12）：
   - 前端维护 10s 错误缓冲窗口，窗口内同级别错误合并展示
   - 致命错误不受聚合限制（每条都弹模态弹窗），但 5s 内有多个致命错误时第二个起降级为告警条，避免弹窗轰炸

4. **状态 Tab "最近失败"卡片**（§9.12）：
   - 警告错误累积显示
   - 最多 5 条，更早的在日志面板查看
   - 用户处理后卡片消失

5. **待处理事件横幅区**（扩展 FP-8.9）：
   - 致命错误红色背景，警告错误黄色背景，按严重级排序（致命在上）
   - 最多显示 3 条，超出显示"... 及 N 条更多 [全部查看]"
   - 点击 [×] 关闭单条告警（错误仍记录在日志和状态 Tab 中）

**单元测试**：
- 致命错误 → 模态弹窗 + 系统通知
- 警告错误 → toast + 日志
- 信息事件 → 仅日志
- 多台服务器断线 10s 内聚合
- 同一错误 30s 内不重复弹 toast
- 致命 + 警告混合 → 致命优先
- 5s 内多个致命 → 第二个起降级为告警条
- "最近失败"卡片最多 5 条

**E2E 测试**：
- HostKey 不匹配 → 模态弹窗 + 系统通知 + 托盘红点
- 触发器失败 → toast + 日志 + 托盘黄点
- 多台服务器同时断线 → 聚合 toast

**文档确认清单**：
- [ ] 三档严重级（致命/警告/信息）？（§9.12）
- [ ] 多错误聚合规则（10s 窗口/30s 去重/致命优先）？（§9.12）
- [ ] 状态 Tab "最近失败"卡片？
- [ ] 横幅区致命红色 + 警告黄色 + 最多 3 条？
- [ ] 5s 内多个致命降级为告警条？

### FP-8.14 暗色模式色板与 a11y 规范

**开发细节**：

1. **暗色模式色板**（§9.10）：
   - 浅色/暗色独立色板（含 hex 值），不简单反转
   - 状态色暗色模式适当提亮（如绿色 `#22C55E` → `#4ADE80`）
   - "已修改" tag 用蓝色（避免与"连接中"黄色冲突）
   - 对比度验证：所有文本与背景组合通过 WCAG AA（≥4.5:1）

2. **a11y 规范**（§9.9，9 条）：
   - 状态可视化：颜色 + 形状 + 图标三重区分（已在 FP-8.2 覆盖）
   - 动画：遵守 `prefers-reduced-motion: reduce`
   - **焦点可见性**：所有可交互元素 `:focus-visible` 蓝色 outline，不用 `outline: none`
   - **Tab 导航**：遵循逻辑 Tab 顺序，跳过纯装饰元素
   - **模态/侧滑面板 focus trap**：打开时 Tab 不跳出面板，关闭后焦点回到触发元素
   - ARIA live region：状态变化、日志新增、触发器进度通过 `aria-live="polite"` 通知
   - **键盘可达**：所有操作可通过键盘完成，不依赖鼠标 hover
   - **对比度** ≥4.5:1（WCAG AA）
   - **图标按钮** `aria-label`（如端口复制按钮）
   - **表单**：输入框关联 `<label>`，错误消息 `aria-describedby`，`aria-invalid="true"`

**单元测试**：
- 暗色模式色板渲染正确
- `:focus-visible` 样式
- focus trap（模态/侧滑面板）
- Tab 导航顺序
- aria-label 存在
- 对比度检查（自动化工具）

**文档确认清单**：
- [ ] 暗色模式独立色板？（§9.10）
- [ ] 状态色暗色模式提亮？
- [ ] "已修改" tag 蓝色（非黄色）？
- [ ] 对比度 ≥4.5:1？（§9.9 + §9.10）
- [ ] `:focus-visible` 蓝色 outline？
- [ ] focus trap（模态/侧滑面板）？
- [ ] 键盘可达所有操作？
- [ ] 图标按钮 aria-label？
- [ ] 表单 label/describedby/invalid？

### FP-8.15 触发器实例→模板回写（save_trigger_as_template UI）

**开发细节**：

1. 触发器卡片 ⋯ 菜单增加"保存为模板"选项（§10.4 C4 修复）：
   - **选项 A：创建新模板** → 输入名称 → 保存为新模板
   - **选项 B：更新原模板** → 只在实例有 `template_id` 且模板仍存在时显示
     - 二次确认："这将更新模板'更新防火墙白名单'的命令。其他从该模板添加的触发器实例不会自动更新（快照式）。[确认更新] [取消]"
     - 更新后模板 `template_version` 递增

2. 调用 IPC `save_trigger_as_template(server_id, trigger_id, name, overwrite_template_id?)`（§10.4）

**单元测试**：
- 选项 A：创建新模板
- 选项 B：更新原模板（有 template_id 时显示）
- 选项 B 不显示（无 template_id 或模板已删除）
- 更新确认提示
- template_version 递增

**E2E 测试**：
- 触发器卡片 → ⋯ → 保存为模板 → 创建新模板 → 模板库显示
- 触发器卡片 → ⋯ → 保存为模板 → 更新原模板 → 确认 → 模板更新

**文档确认清单**：
- [ ] "保存为模板"菜单项？（§10.4）
- [ ] 选项 A 创建新模板？
- [ ] 选项 B 更新原模板（有 template_id 时显示）？
- [ ] 更新确认提示（快照式说明）？
- [ ] template_version 递增？

### FP-8.16 导入安全提示

**开发细节**：

1. `import_servers` 导入前弹安全提示（§10.1）：
   - "第三方模板可能含恶意命令，请确认来源可信后再导入"
   - 用户确认后才执行导入

2. 模板库导入（`import_templates`）同样弹安全提示

**单元测试**：
- 导入前安全提示显示
- 用户确认 → 执行导入
- 用户取消 → 不导入

**文档确认清单**：
- [ ] `import_servers` 导入前安全提示？（§10.1）
- [ ] `import_templates` 导入前安全提示？

### FP-8.17 CLI 操作结果在 UI 实时显示

> **核心需求**：CLI 执行的任何操作（连接/断开/代理/触发器/暂停恢复等），GUI 必须实时反映，用户无需手动刷新。

**开发细节**：

1. **事件驱动架构**（已在 FP-6.1 daemon + FP-6.2 GUI 桥接中实现基础）：
   - CLI 发送请求 → daemon 执行 → core 状态变化 → daemon 广播事件 → GUI 收到 → UI 更新
   - GUI 前端通过 `@tauri-apps/api` 的 `listen()` 监听所有事件类型
   - **关键点**：daemon 不区分事件来源（GUI 还是 CLI），所有状态变化都广播给所有客户端

2. **CLI 操作 → UI 更新映射**：

   | CLI 命令 | daemon 事件 | UI 更新 |
   |---------|------------|---------|
   | `termfast connect "东京"` | `server:status_changed` | 服务器列表状态点变绿 + 详情 Tab 更新 |
   | `termfast disconnect "东京"` | `server:status_changed` | 状态点变灰 |
   | `termfast proxy "东京" on` | `proxy:status_changed` | 代理 toggle 变为开启 |
   | `termfast trigger "东京" "防火墙"` | `trigger:fired` → `trigger:command_executed` × N → `trigger:completed` | 触发器卡片进度条 + 日志面板实时推送 |
   | `termfast pause-triggers` | `log:entry`（暂停日志） | 触发器卡片显示"已暂停"状态 |
   | `termfast resume-triggers` | `log:entry` + 可能的 `trigger:fired`（全量检测） | 恢复状态 + 全量检测触发的执行进度 |
   | `termfast shutdown` | `Shutdown` 事件 | GUI 显示"daemon 已关闭" |

3. **前端事件监听**（`src/hooks/useDaemonEvents.ts`）：
   - 统一注册所有事件监听器
   - `server:status_changed` → 更新服务器列表 store + 详情 Tab store
   - `proxy:status_changed` → 更新代理状态 store
   - `trigger:fired` / `trigger:command_executed` / `trigger:completed` → 更新触发器执行进度 store + 日志 store
   - `log:entry` → 追加到日志 store
   - **无需区分事件来源**：GUI 自己操作和 CLI 操作产生的事件格式完全一致，前端处理逻辑统一

4. **CLI 操作的可视化提示**（v1 要求）：
   - 检测到非 GUI 发起的操作时，日志面板标注来源 `[CLI]`
   - 实现：daemon 在事件中增加 `source: "cli"` / `source: "gui"` 字段
   - 前端日志面板渲染时如 `source == "cli"` 则显示 `[CLI]` tag
   - **这是 v1 要求**（非可选增强）：用户需要知道哪些操作是 CLI 发起的，便于排查"为什么 GUI 突然显示已断开"等问题

5. **GUI 与 CLI 并发操作的一致性**：
   - GUI 和 CLI 同时操作同一服务器 → daemon 串行化处理（core API 有锁）
   - 如 GUI 正在连接服务器 A，CLI 同时发 `connect A` → daemon 返回"已在连接中"
   - 如 CLI 发 `disconnect A`，GUI 同时发 `connect A` → daemon 按到达顺序处理

**单元测试**：
- CLI `connect` → GUI 收到 `server:status_changed` → UI 状态更新
- CLI `trigger` → GUI 收到 `trigger:fired` + `trigger:command_executed` + `trigger:completed` → 进度条 + 日志更新
- CLI `pause-triggers` → GUI 收到日志 → 触发器卡片显示"已暂停"
- CLI `proxy on` → GUI 收到 `proxy:status_changed` → toggle 更新
- CLI `shutdown` → GUI 收到 `Shutdown` 事件 → 显示"daemon 已关闭"
- GUI 和 CLI 并发操作同一服务器 → 串行化处理
- `[CLI]` 来源标注（如实现）

**E2E 测试**：
- GUI 打开 + CLI 执行 `termfast connect "东京节点"` → GUI 服务器列表实时显示"已连接"
- GUI 打开 + CLI 执行 `termfast trigger "东京节点" "更新防火墙"` → GUI 触发器卡片显示进度条 + 日志面板实时推送
- GUI 打开 + CLI 执行 `termfast pause-triggers` → GUI 触发器卡片显示"已暂停"
- GUI 打开 + CLI 执行 `termfast proxy "东京节点" on` → GUI 代理 toggle 实时变为开启
- GUI 和 CLI 同时操作 → 无冲突

**文档确认清单**：
- [ ] CLI 所有操作 → GUI 实时反映？
- [ ] 事件驱动架构（daemon 广播 → GUI 监听）？
- [ ] CLI 操作映射到 UI 更新（连接/断开/代理/触发器/暂停恢复）？
- [ ] 触发器执行进度 + 日志实时推送？
- [ ] GUI 和 CLI 并发操作串行化？
- [ ] `[CLI]` 来源标注在日志面板显示？

<!-- === SECTION 9 (Phase 8) END === -->

---

## Phase 9：集成与 E2E（全流程端到端验证）

### FP-9.1 任务流 1 E2E：添加新服务器并使其可用

**开发细节**：

1. E2E 测试脚本（Playwright / Tauri WebDriver + mock SSH server）：
   - 启动 App（无配置）→ 进入引导
   - 快速模式：输入 VPS 信息 → 密码认证 → 连接成功 → 自动配置密钥
   - 步骤 3：勾选防火墙白名单 → 填写端口 3000 → 自动检测 firewalld → 完成
   - 进入主界面 → 自动连接 → on_connect + on_ip_change 触发
   - 验证：代理可用（SOCKS5 + HTTP）、防火墙规则已生效、日志面板有记录

2. 错误路径测试：
   - 连接失败 → 显示错误 → 修改后重试
   - 连接失败 → 跳过测试 → 进入主界面

**文档确认清单**：
- [ ] 覆盖任务流 1 正常路径？（§1.7 任务流 1）
- [ ] 覆盖错误路径？
- [ ] 首次 on_ip_change 的 OldIP 为空，条件块跳过 remove 命令？（§1.7）

### FP-9.2 任务流 2 E2E：IP 变化后用户能看到什么

**开发细节**：

1. E2E 测试：
   - 服务器已连接 → 模拟 IP 变化（mock SSH server 返回不同 IP）
   - 验证：on_ip_change 触发 → 防火墙命令执行 → 日志记录 "IP X→Y, 防火墙已更新"
   - 验证：通知偏好开启时发送系统通知
   - 验证：用户无需任何操作

**文档确认清单**：
- [ ] IP 变化自动检测 + 触发？（§1.7 任务流 2）
- [ ] 日志记录完整？
- [ ] 通知按偏好发送？

### FP-9.3 任务流 3 E2E：触发器执行失败后处理

**开发细节**：

1. E2E 测试：
   - 触发器执行 → 命令失败（mock SSH server 返回非 0 退出码）
   - 验证：日志记录失败 + continue_on_error 行为
   - 验证：通知偏好开启时系统通知 + 托盘变色
   - 用户操作：查看日志 → 编辑触发器 → 手动重试

**文档确认清单**：
- [ ] 覆盖任务流 3？（§1.7 任务流 3）
- [ ] 失败通知 + 托盘变色？
- [ ] 查看日志 → 编辑 → 手动重试路径？

### FP-9.4 任务流 4 E2E：从模板添加触发器并自定义

**开发细节**：

1. E2E 测试：
   - 触发器 Tab → [+ 添加] → 选择"更新防火墙白名单"
   - 填写受保护端口 → 预览渲染命令 → 添加
   - 卡片显示 → 编辑 → 修改端口 → 保存 → 已修改 tag
   - 同步模板 → tag 消失

**文档确认清单**：
- [ ] 覆盖任务流 4？（§1.7 任务流 4）
- [ ] 选中即预览？
- [ ] 参数表单 + 校验？
- [ ] 已修改 tag + 同步模板？

### FP-9.5 任务流 5 E2E：代理断连时的用户体验

**开发细节**：

1. E2E 测试（§1.7 任务流 5：代理断连时的用户体验）：
   - 服务器已连接 + 代理开启 → mock SSH server 主动断开
   - 验证：GUI 状态变为"重连中" + 显示 N/M
   - 验证：新连接排队等待（30s 超时）
   - 验证：已有连接尝试 drain（10s 超时）
   - 验证：指数退避重连（1s → 2s → 4s → ... → 30s cap）
   - 验证：超过 max_attempts 后停止 → 状态"断开" → 排队请求全部拒绝
   - 验证：重连成功后 on_reconnect 触发 → 代理恢复 → 排队连接开始处理
   - 验证：离线检测（模拟系统离线）→ 暂停重连 + 不消耗 max_attempts → 在线恢复 → 立即重连

**文档确认清单**：
- [ ] 覆盖任务流 5（代理断连 UX）？（§1.7 任务流 5）
- [ ] 重连期间新请求排队（30s 超时）？
- [ ] 已有连接 drain（10s 超时）？
- [ ] 指数退避（1s → 30s cap）？
- [ ] 超过 max_attempts 停止 + 排队请求拒绝？
- [ ] 重连成功后 on_reconnect + 代理恢复？
- [ ] 离线检测暂停重连 + 恢复立即重连？

### FP-9.5b 系统代理一键配置 E2E

**开发细节**：

1. E2E 测试（U18，原 FP-9.5 的系统代理部分）：
   - 服务器已连接 → 代理 Tab → 开启代理
   - 点击 [设为系统代理] → macOS 授权流程（sudoers 白名单）/ Windows 直接设置
   - 验证：系统代理生效（浏览器通过代理访问）
   - 验证：全局指示器显示当前出口
   - 切换出口 → 验证系统代理切换（自动清除旧的再设置新的）
   - 清除系统代理 → 验证恢复
   - 验证：`system_proxy_server_id` 生命周期边缘场景（§7.3）：
     - 删除出口服务器 → 自动清除系统代理
     - 出口服务器代理被关闭 → 保留 ID + 全局指示器显示"已断开"（黄色警告）
     - 出口服务器断开 → 保留 ID（重连后自动恢复）
     - App 启动时校验系统代理设置是否与记录一致

**文档确认清单**：
- [ ] 一键设为系统代理？（U18）
- [ ] 全局指示器显示出口？
- [ ] 切换/清除出口？
- [ ] `system_proxy_server_id` 生命周期完整？（§7.3 生命周期表）
- [ ] 删除出口服务器自动清除？
- [ ] 代理关闭/断开时保留 ID？

### FP-9.6 多服务器场景 E2E

**开发细节**：

1. E2E 测试：
   - 添加 5 台服务器（不同端口）
   - 全部连接（并发上限 3）
   - 状态摘要面板显示
   - 切换服务器 → 日志面板不跟随
   - 删除中间一台 → 其余不受影响
   - 端口冲突拒绝

**文档确认清单**：
- [ ] 多服务器独立运行？（§1.2 核心设计原则）
- [ ] 并发连接上限 3？
- [ ] 删除不影响其他？

### FP-9.7 导入/导出 E2E

**开发细节**：

1. E2E 测试：
   - 配置 3 台服务器 + 自定义模板 → 完整导出（设置主密码）
   - 验证：.termfast 文件生成
   - 清除配置 → 完整导入 → 输入主密码
   - 验证：服务器配置 + 模板 + 凭据 + 密钥文件恢复
   - 端口冲突时跳过并报告

2. 模板导入/导出（不含凭据）

**文档确认清单**：
- [ ] 完整导出/导入？（§10.1）
- [ ] AES-256-GCM + Argon2id？
- [ ] 主密码强度校验？
- [ ] 端口冲突跳过 + 报告？

### FP-9.8 异常恢复 E2E

**开发细节**：

1. E2E 测试场景：
   - 配置文件损坏 → 备份 + 创建默认配置
   - 钥匙串读取被拒 → GUI 提示 + 手动输入
   - 密钥文件丢失 → 提示重新生成/指定其他
   - HostKey 不匹配 → 三重通知 + 三个操作选项
   - 端口被占用 → 报错 + 不自动切换
   - 配置迁移失败 → 错误页 + 从备份恢复/导出日志/重置

**文档确认清单**：
- [ ] 覆盖 §17 所有异常场景？
- [ ] HostKey 三重通知？（§17.2）
- [ ] 配置迁移失败错误页？（§17.5）

### FP-9.9 代理性能基准测试

**开发细节**：

1. 基准测试（§16.8，在 spike 通过后、代理代码实现完成后跑）：

| 指标 | 测试方法 | 合格标准 |
|------|---------|---------|
| 吞吐量 | 通过代理下载 100MB 文件，测速率 | > 80% of 直连速率（SSH 加解密开销 < 20%） |
| 并发连接 | 100 个并发 TCP 连接通过代理，测建立时间和稳定性 | 100 连接全部成功，建立时间 < 5s |
| 延迟 | 通过代理 ping 目标（TCP connect 时间），对比直连 | 延迟增加 < 20ms |
| 长连接稳定性 | SSE 连接保持 30 分钟，检查是否被 channel idle timeout 断开 | 30 分钟不断开 |
| 内存 | 100 并发连接 + 持续 10 分钟，监控内存增长 | 内存增长 < 50MB（无泄漏） |
| CPU | 100 并发连接 + 50Mbps 流量，监控 CPU | < 30% 单核 |

2. 测试工具：`curl`（吞吐量）、`hey` 或 `wrk`（并发连接）、自定义 Rust 基准测试（内存/CPU 监控）

3. **CI 集成**：基准测试不进每次 PR CI（太慢），在 release 前手动跑一次，结果记录到 `docs/benchmark-results.md`（§16.8）

4. **§12.3 运行时性能预算验证**（与基准测试同期跑）：

   | 指标 | 预算（§12.3） | 测试方法 |
   |------|--------------|---------|
   | idle 内存 | < 80 MB | 无服务器连接时 Activity Monitor / Task Manager |
   | 活跃内存 | < 200 MB | 1 台服务器连接 + 代理活跃 |
   | 5 服务器内存 | < 300 MB | 5 台服务器全部连接 |
   | idle CPU | < 1% | 无操作时 Activity Monitor |
   | 活跃 CPU | < 15% 单核 | 代理传输 100MB 文件时 |
   | 冷启动时间 | < 2s | 从启动到托盘显示（不含自动连接） |

**文档确认清单**：
- [ ] 6 项性能指标全部覆盖？（§16.8）
- [ ] 吞吐量 > 80% 直连？
- [ ] 100 并发连接 < 5s 建立？
- [ ] 延迟增加 < 20ms？
- [ ] SSE 30 分钟稳定？
- [ ] 内存增长 < 50MB？
- [ ] CPU < 30% 单核？
- [ ] 结果记录到 `docs/benchmark-results.md`？
- [ ] §12.3 性能预算验证（idle/活跃/5服务器内存 + CPU + 冷启动）？

### FP-9.10 UX 测试计划

**开发细节**：

1. **阶段 1：内部走查**（开发完成后，§16.9）：
   - 开发团队按 top 5 任务流逐一走查
   - 检查：每个任务流能否在预期时间内完成（快速模式引导 < 60s，添加触发器 < 30s）
   - 错误路径是否有明确的恢复操作

2. **阶段 2：Beta 测试**（发布前，§16.9）：
   - 5-8 人 beta 用户（Persona A + B 混合）
   - 观察用户完成 top 5 任务流，记录痛点
   - 量化指标：引导完成率 > 95%、防火墙配置率 > 80%

3. **阶段 3：发布后反馈**（§16.9）：
   - 收集 GitHub Issue + 邮件反馈
   - 分类：bug / UX 改进 / 功能请求
   - 按优先级排期

**文档确认清单**：
- [ ] 三阶段 UX 测试？（§16.9）
- [ ] 内部走查覆盖 top 5 任务流？
- [ ] Beta 5-8 人？
- [ ] 量化指标（引导完成率 95%、防火墙配置率 80%）？

### FP-9.11a CLI + daemon 基础 E2E（生命周期 + 全功能）

**开发细节**：

1. **daemon 生命周期 E2E**：
   - `termfast --daemon` 启动 → socket 文件创建 + daemon.lock 写入
   - `termfast daemon-status` → 显示版本/uptime/连接数
   - `termfast shutdown` → daemon 优雅关闭 → socket + lock 清理
   - daemon 崩溃 → lock 残留 → 重启 → 覆盖 lock + 重建 socket

2. **CLI 全功能 E2E**（启动 daemon + mock SSH server）：
   - `termfast list` → 显示服务器列表
   - `termfast status` → 显示所有服务器状态（人类可读表格）
   - `termfast status --json` → JSON 输出格式正确
   - `termfast connect "东京节点"` → 服务器连接 → status 变为已连接
   - `termfast disconnect "东京节点"` → 服务器断开
   - `termfast proxy "东京节点" on` → 代理开启
   - `termfast proxy "东京节点" off` → 代理关闭
   - `termfast triggers "东京节点"` → 显示触发器列表
   - `termfast trigger "东京节点" "更新防火墙"` → 同步等待执行完成 → 输出结果
   - `termfast trigger "东京节点" "更新防火墙" --async` → 立即返回
   - `termfast pause-triggers` → 暂停所有 → `termfast resume-triggers` → 恢复 + 显示 pending_events
   - `termfast pause-triggers --server "东京节点"` → 只暂停一台
   - `termfast logs --tail 20` → 显示最后 20 条
   - `termfast logs -f` → 持续监听新日志
   - `termfast templates` → 显示模板列表

**文档确认清单**：
- [ ] daemon 生命周期（启动/状态/关闭/崩溃恢复）？
- [ ] CLI 全功能覆盖（status/connect/disconnect/proxy/trigger/pause/resume/logs/templates）？
- [ ] `--json` 输出格式？
- [ ] `--config` 指定配置文件路径？
- [ ] 退出码 0-5？（§2.4）
- [ ] `trigger` 同步/异步模式？
- [ ] `logs --tail` + 实时模式？

### FP-9.11b CLI + daemon 进阶 E2E（并发 + 错误 + 冲突）

**开发细节**：

1. **CLI + GUI 并发 E2E**（核心验证：CLI 操作 → GUI 实时反映）：
   - GUI 打开 + CLI `termfast connect "东京节点"` → GUI 服务器列表实时显示"已连接"
   - GUI 打开 + CLI `termfast trigger "东京节点" "更新防火墙"` → GUI 触发器卡片显示进度条 + 日志面板实时推送 + `[CLI]` 来源标注
   - GUI 打开 + CLI `termfast pause-triggers` → GUI 触发器卡片显示"已暂停"
   - GUI 打开 + CLI `termfast proxy "东京节点" on` → GUI 代理 toggle 实时变为开启
   - GUI 打开 + CLI `termfast disconnect "东京节点"` → GUI 状态点变灰
   - GUI 操作 + CLI `termfast status` → CLI 反映 GUI 的操作结果
   - **多 CLI 客户端并发压力测试**：3+ CLI 同时操作不同服务器 → 无状态竞争

2. **CLI 错误路径 E2E**：
   - daemon 未运行 → `termfast status` → 提示"daemon 未运行" + 退出码 4
   - 连接不存在的服务器 → 退出码 1
   - 认证失败 → 退出码 2
   - 连接失败 → 退出码 3
   - 触发器执行失败 → 退出码 5
   - `--json` 错误输出格式正确

3. **daemon --daemon 无头模式 E2E**：
   - `termfast --daemon` 启动（无 GUI）→ CLI 全功能可用
   - 适合无头服务器/自动化场景
   - `SIGINT`/`SIGTERM` → daemon 优雅关闭

4. **daemon 启动冲突 E2E**：
   - GUI 已运行（内嵌 daemon）→ 再执行 `termfast --daemon` → 检测到 `daemon.lock` PID 存活 → 拒绝启动 + 提示"daemon 已在运行（GUI 模式，PID xxx）"
   - `termfast --daemon` 已运行 → 再执行 `termfast --daemon` → 拒绝启动 + 提示"daemon 已在运行（无头模式，PID xxx）"
   - `termfast --daemon` 已运行 → 启动 GUI（`termfast` 无参数）→ GUI 检测到 daemon 已运行 → 连接已有 daemon socket（不内嵌新 daemon）+ 打开 GUI 窗口
   - daemon 崩溃后 lock 残留 → `termfast --daemon` → 检测 PID 不存活 → 覆盖 lock + 重建 socket

**文档确认清单**：
- [ ] CLI + GUI 并发操作 → GUI 实时反映？
- [ ] `[CLI]` 来源标注在日志面板显示？
- [ ] 多 CLI 客户端并发压力测试无状态竞争？
- [ ] daemon 启动冲突（GUI 运行中 → --daemon 拒绝 / --daemon 运行中 → GUI 连接已有 daemon）？
- [ ] CLI 错误路径（daemon 未运行/认证失败/连接失败/触发器失败）？
- [ ] `--daemon` 无头模式？
- [ ] `SIGINT`/`SIGTERM` 优雅退出？

### FP-9.12 内置模板版本升级验证 + v1 发布前全量核对

> **v1 质量门**：这是 v1 发布前的最终核对环节，包含原 FP-10.4（模板升级验证）和 FP-10.5（全量文档确认）。

**开发细节**：

#### Part 1：内置模板版本升级验证（§19）

1. 验证软件升级时内置模板更新逻辑：
   - 用户未修改 → 自动更新
   - 用户已修改 → GUI 提示
   - 用户删除 → 不恢复

#### Part 2：最终文档确认（全量核对）

2. 逐节核对 design doc（Phase 0-9 范围）：
   - §1 项目概述：所有核心功能实现
   - §3 国际化：中英文双语
   - §4 架构设计：core 不依赖 tauri/daemon，交叉编译通过
   - §5 代理 + IP 检测 + 健康检查
   - §6 触发器系统：模板 + 引擎 + 事件队列 + 执行模型
   - §7 配置结构：schema 完整
   - §8 认证与密钥管理：密钥生成 + 推送 + 清理
   - §9 GUI 设计：所有用户故事 U1-U20 验收
   - §10 IPC 接口：所有命令实现（通过 daemon socket 协议）
   - **§2.4 CLI 功能：所有功能命令行可调 + 结果在 UI 实时显示**
   - §13 安全：占位符注入防护 + daemon socket 安全模型
   - §14 内置模板：5 个
   - §17 错误处理：所有异常场景
   - §18 首次运行引导：快速 + 高级模式
   - §20.0 spike：S1-S9 通过
   - §20.1 交叉编译：CI 通过
   - ~~§22 崩溃上报：opt-in~~（Phase 10，v1.1）

3. 用户故事 U1-U20 逐条验收：
   - U1：托盘快速连接/断开
   - U2：所有服务器状态 + 实时日志
   - U3：添加/删除服务器 + 清理
   - U4：触发器卡片（类型 + 摘要 + 已修改）
   - U5：手动触发 + 二次确认
   - U6：一键开关代理
   - U7：5 态视觉标识
   - U8：端口点击复制
   - U9：导入/导出
   - U10：日志筛选
   - U11：触发器编辑器（CodeMirror）
   - U12：认证方式 + 安全级别
   - U13：设置页
   - U14：Tab 上下文保留
   - U15：空状态引导
   - U16：键盘快捷键
   - U17：托盘图标颜色 + badge
   - U18：一键设为系统代理
   - U19：触发器执行进度
   - U20：模板库管理

4. 评审意见修复确认（逐条核对）：
   - [ ] B1：测试 VPS 凭据已改为环境变量引用，无明文？（FP-0.1）
   - [ ] B2：`RuntimeStateManager` 已实现？（FP-1.3b）
   - [ ] B3：优雅关闭流程 6 步？（FP-5.4）
   - [ ] B4：离线/飞行模式检测？（FP-6.9）
   - [ ] B5：任务流 5 改为代理断连 UX？（FP-9.5）
   - [ ] I1：`pause/resume_all/server_triggers` IPC？（FP-6.1）
   - [ ] I2：`EventGateway` 实现细节？（FP-4.2）
   - [ ] I3：代理性能基准测试？（FP-9.9）
   - [ ] I4：三档确认对话框 + undo toast？（FP-8.11）
   - [ ] I5：加载态与骨架屏？（FP-8.12）
   - [ ] I6：多错误聚合规则？（FP-8.13）
   - [ ] I7：`save_trigger_as_template` UI？（FP-8.15）
   - [ ] I8：`import_servers` 安全提示？（FP-8.16）
   - [ ] I9：双连接方案实现细节？（FP-5.1）
   - [ ] G1：暗色模式色板？（FP-8.14）
   - [ ] G2：a11y 规范完整？（FP-8.14）
   - [ ] G3：原生窗口效果？（FP-6.10）
   - [ ] G4：标题栏平台适配？（FP-6.10）
   - [ ] G5：CI 三平台矩阵 + E2E CI？（FP-0.3）
   - [ ] G6：UX 测试计划？（FP-9.10）
   - [ ] G7：`system_proxy_server_id` 生命周期边缘场景？（FP-9.5b）
   - [ ] G8：CI clippy `-D warnings` 明确？（FP-0.3）
   - [ ] G9：`modify` 方法名统一？（FP-1.3）
   - [ ] G10：心跳方式明确（SSH keepalive 优先）？（FP-2.1）
   - [ ] G11：`channel_idle_timeout` 300s 确认？（FP-3.1）
   - [ ] G12：各 Phase 工期估算？（附录 D）
   - [ ] G13：Sentry 过滤实现 + 单元测试？（FP-10.3，v1.1）
   - [ ] CLI-1：daemon IPC 协议定义？（FP-1.7）
   - [ ] CLI-2：daemon（socket server + core 运行时）？（FP-6.1）
   - [ ] CLI-3：GUI 客户端（Tauri 内嵌 daemon + IPC 桥接）？（FP-6.2）
   - [ ] CLI-4：CLI 客户端（独立二进制，所有功能命令行可调）？（FP-6.3）
   - [ ] CLI-5：CLI 操作结果在 UI 实时显示？（FP-8.17）
   - [ ] CLI-6：CLI + daemon 全流程 E2E？（FP-9.11）
   - [ ] S1：设计文档 §4.2/§4.5/§10 已同步更新为 daemon 架构？
   - [ ] S2：版本术语统一（v1=含 CLI，Phase 10=v1.1 ≠ v2）？
   - [ ] S3：设计文档 §2.4 已改为"v1 包含 CLI"？
   - [ ] I2-review：Phase 6 拆分为 6a + 6b？
   - [ ] I3-review：`set_proxy_auth`/`clear_proxy_auth` 在 Action 映射表中？
   - [ ] I4-review：§12.3 性能预算验证在 FP-9.9？
   - [ ] I5-review：设计文档 §4.5 补充 daemon 与移动端架构关系？
   - [ ] N1：FP-0.2 步骤编号无重复？
   - [ ] N3：CLI 参数风格统一（位置参数，设计文档 §2.4 已更新）？
   - [ ] N5：设计文档 §13.0 daemon socket 安全模型？
   - [ ] P1：发布策略明确（Phase 0-9 即发布 v1，Phase 10 属于 v1.1）？设计文档 §11.1 已同步？
   - [ ] P1-1：React 19 + Tailwind CSS v4 版本号？
   - [ ] P2-2：FP-6.3 `--config` 参数 + 清单？
   - [ ] P2-3：FP-9.11 daemon 启动冲突 E2E？
   - [ ] P2-4：附录 C 风险表补充 CLI 相关风险？
   - [ ] P0-1：FP-10.4/10.5 已移至 Phase 9（FP-9.12）？
   - [ ] P0-2：FP-9.13 v1 发布产物定义？
   - [ ] P0-3：术语统一（Phase 10=v1.1 ≠ v2）？
   - [ ] P1-1：退出码 4 = 配置错误 / daemon 未运行（设计文档 §2.4 已同步）？
   - [ ] P1-2：`--config` 语义澄清（仅 --daemon 模式有效）？
   - [ ] P1-3：Phase 6a 工期 2-3 周？
   - [ ] P1-4：Phase 6a 增加 daemon 崩溃恢复集成测试？
   - [ ] P2-1：Windows named pipe 安全描述符（SDDL）？
   - [ ] P2-2：FP-9.11 拆分为 9.11a + 9.11b？
   - [ ] P2-3：附录 C 补充 daemon 内嵌与 Tauri 生命周期耦合风险？
   - [ ] P2-4-review：FP-8.17 `[CLI]` 来源标注明确为 v1 要求？
   - [ ] P1-review：mock SSH server 设计完整（FP-2.0）？
   - [ ] P3-review：E2E 工具链 spike 在 Phase 0 验证（FP-0.3）？
   - [ ] P4-review：FP-9.11b 编号从 1 开始？

**文档确认清单**：
- [ ] 内置模板升级逻辑与 §19 一致？
- [ ] Design doc 所有章节核对完毕？
- [ ] U1-U20 全部验收通过？
- [ ] 5 个任务流 E2E 全部通过？
- [ ] CLI 全功能命令行可调？（§2.4）
- [ ] CLI 操作结果在 UI 实时显示？
- [ ] daemon + CLI + GUI 三端 E2E 通过？
- [ ] 评审意见修复确认全部打勾？

### FP-9.13 v1 发布产物定义

> **v1 发布门**：明确 v1 的产物形态和发布流程，确保"发布 v1"可执行。

**开发细节**：

1. **v1 产物**：
   - `tauri build` 生成的 `.app`（macOS）/ `.exe`（Windows，便携版）
   - **无代码签名**：macOS 需用户右键打开（Gatekeeper 绕过），Windows SmartScreen 警告
   - **无自动更新**：用户手动下载新版本替换
   - **无崩溃上报**：无 Sentry 集成
   - 开发者手动构建后通过 GitHub Release / 网盘分发

2. **v1 不含**（明确划到 v1.1）：
   - 代码签名（Apple Developer ID / Windows EV 证书）
   - 自动更新（tauri-plugin-updater + latest.json）
   - 崩溃上报（Sentry opt-in）
   - 安装包优化（NSIS installer / .dmg 制作）

3. **v1 → v1.1 过渡条件**：
   - v1 稳定运行 2-4 周
   - 无 P0/P1 级 bug
   - 用户反馈收集完毕
   - 启动 Phase 10（v1.1）：签名 + 自动更新 + 遥测

4. **v1 构建流程**（开发者执行）：
   ```bash
   # macOS
   cargo tauri build
   # 产出：target/release/bundle/macos/TermFast.app

   # Windows
   cargo tauri build
   # 产出：target/release/bundle/nsis/TermFast_1.0.0_x64-setup.exe（无签名）
   ```

5. **v1 Release Checklist**：
   - [ ] `cargo tauri build` 双平台成功
   - [ ] FP-9.12 全量核对通过
   - [ ] FP-9.9 性能基准达标
   - [ ] FP-9.10 UX 测试完成
   - [ ] GitHub Release 创建 + 产物上传
   - [ ] Release Notes 编写（功能列表 + 已知限制）

**文档确认清单**：
- [ ] v1 产物形态明确（`tauri build` 无签名）？
- [ ] v1 不含项明确（签名/自动更新/崩溃上报/安装包优化）？
- [ ] v1 → v1.1 过渡条件明确？
- [ ] v1 构建流程可执行？
- [ ] v1 Release Checklist 完整？

<!-- === SECTION 10 (Phase 9) END === -->

---

## Phase 10：发布准备（打包 / 签名 / 自动更新 / 遥测）

> **⚠️ 本阶段不在本次开发计划范围内，属于 v1.1（签名打包 + 自动更新 + 遥测），在 v1 发布后补充。**
>
> 本次开发计划覆盖 Phase 0-9（spike → 核心功能 → 前端 → 集成 E2E）。
> Phase 10 的打包/签名/自动更新/遥测在 v1 功能验证通过后，作为发布准备的独立阶段执行。
>
> 以下内容保留为 v1.1 的规划参考，本次不实施。

### FP-10.1 打包与签名

**开发细节**：

1. Tauri 打包配置：
   - macOS：`.dmg` + `.app`，Universal Binary（x86_64 + aarch64）
   - Windows：`.msi` + `.exe`（NSIS），x86_64
   - 代码签名：macOS Developer ID + Windows EV 证书（如有）

2. 体积优化：
   - `strip` 符号
   - `lto = true` + `codegen-units = 1`（Cargo.toml profile.release）
   - 不用 UPX（§15 注意事项 6）

**文档确认清单**：
- [ ] macOS Universal Binary？
- [ ] 不用 UPX？（§15）

### FP-10.2 自动更新

**开发细节**：

1. `tauri-plugin-updater` 集成：
   - 签名密钥配置
   - 更新清单 `latest.json` 托管在 GitHub Pages
   - 安装包存储 GitHub Releases
   - 国内加速：URL 用 `gh-proxy.com` 前缀（如需要）

2. 客户端行为（参考 AGENTS.md 自动更新章节）：
   - 启动后 5 秒静默检查更新
   - 有新版本 → 弹窗显示版本信息 + 更新内容
   - 用户确认 → 下载（显示进度/速度/ETA）→ 验证签名 → 静默安装
   - 安装完成 → 提示重启
   - 设置页"关于"有"检查更新"按钮

**文档确认清单**：
- [ ] `tauri-plugin-updater`？
- [ ] 启动后自动检查？
- [ ] 签名验证？
- [ ] 手动检查更新按钮？

### FP-10.3 崩溃上报（Opt-in）

**开发细节**：

1. Sentry 集成（§22.1）：
   - Rust SDK + 前端 SDK
   - 完全 opt-in，默认关闭
   - 首次启动弹窗询问
   - **严格过滤**（§22.1，需显式数据脱敏逻辑 + 单元测试）：
     - 不收集：服务器地址、密码、密钥、IP、命令文本
     - 只收集：崩溃堆栈、App 版本、OS 版本
     - 实现方式：Sentry `before_send` 回调中扫描所有 event payload，正则匹配 + 字段白名单过滤
     - 过滤规则：移除所有匹配 IP 格式的字符串、移除 `password`/`passphrase`/`key`/`secret` 字段、移除触发器命令文本
     - 单元测试：构造含敏感数据的 event → 过滤后验证无敏感数据残留

2. App 内反馈通道（§22.3）：
   - GitHub Issue（Persona A）
   - 邮件反馈（Persona B，预填模板）

**文档确认清单**：
- [ ] Opt-in 默认关？（§22.1）
- [ ] 首次弹窗询问？
- [ ] 严格过滤敏感数据（服务器地址/密码/密钥/IP/命令文本）？（§22.1）
- [ ] `before_send` 回调过滤实现 + 单元测试？
- [ ] App 内反馈通道？（§22.3）

> **FP-10.4（内置模板版本升级验证）和 FP-10.5（最终文档确认）已移至 Phase 9 末尾**（FP-9.12），因为它们是 v1 发布前的质量门，不属于 v1.1 范围。

---

## 附录 A：功能点依赖关系图

```
Phase 0 (spike + 脚手架)
  │
  ├──► Phase 1 (config / credential / error / daemon IPC 协议)
  │       │
  │       └──► Phase 2 (SSH 层)
  │               │
  │               ├──► Phase 3 (代理层) ──────────────┐
  │               │                                    │
  │               └──► Phase 4 (触发器引擎) ────────┐ │
  │                                                    │ │
  └──► Phase 5 (服务器管理) ◄── Phase 2 + 3 + 4 ◄──────┘─┘
          │
          └──► Phase 6a (daemon + GUI 桥接 + CLI 客户端)
                  │  daemon 内嵌 Tauri 进程，CLI 通过 socket 连接
                  ├──► Phase 6b (tray / autostart / platform / 日志 / 通知 / 窗口效果)  ← 可与 Phase 7 并行
                  │
                  └──► Phase 7 (前端基础)
                          │
                          └──► Phase 8 (前端功能 + CLI 结果实时显示)
                                  │
                                  └──► Phase 9 (集成 E2E + CLI + daemon E2E)
                                          │
                                          └──► Phase 10 (发布) ← v1.1，本次不做
```

## 附录 B：每个功能点的完成定义（Definition of Done）

一个功能点视为完成，必须满足以下全部条件：

| # | 条件 | 说明 |
|---|------|------|
| 1 | 代码实现完成 | 按 design doc 规格实现，遵循代码风格 |
| 2 | 单元测试通过 | 公开 API 覆盖正常 + 边界 + 错误路径，`cargo test` / `vitest` 全绿 |
| 3 | 集成测试通过（如适用） | 多模块交互用 mock SSH server 验证 |
| 4 | E2E 测试通过（如适用） | 模拟真实用户操作，覆盖用户故事和任务流 |
| 5 | 文档确认清单全部打勾 | 逐条核对 design doc 验收标准 |
| 6 | CI 通过 | check + test + clippy + tsc + 交叉编译 |
| 7 | 无未解决的偏差 | 如有与 design doc 的偏差，已记录理由 |
| 8 | 代码已提交 | git commit，commit message 清晰 |

## 附录 C：风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| russh spike 失败 | 中 | 高 | 双连接方案 / 混合方案 / 定时重连（§20.0 应急方案） |
| russh 并发 channel 不稳定 | 中 | 高 | channel 并发上限 + 空闲超时 + 监控告警 |
| macOS 系统代理权限被拒 | 低 | 中 | sudoers 白名单 + fallback 复制命令 |
| 钥匙串移动端不支持 | 高 | 中（v3+） | v1 不涉及，v3 前写 JNI/Swift FFI |
| 配置迁移失败 | 低 | 高 | 备份 + 回滚 + 错误页 + 从备份恢复 |
| CodeMirror 6 体积过大 | 低 | 低 | 按需加载语言包 |
| daemon socket 在 Windows named pipe 上不兼容 | 低 | 高 | Phase 1 集成测试覆盖双平台；named pipe 与 Unix socket 行为差异（权限模型）在 FP-1.7 单元测试验证 |
| CLI 与 GUI 并发操作导致 core 状态竞争 | 中 | 中 | FP-6.1 已设计串行化（core API 有锁）；FP-9.11 E2E 增加并发压力测试（多 CLI 客户端同时操作） |
| daemon 崩溃后 CLI 无法连接 | 低 | 中 | daemon.lock PID 检测 + 崩溃恢复已在 FP-1.7 设计，FP-9.11a 已覆盖崩溃恢复 E2E |
| daemon 内嵌模式与 Tauri 生命周期耦合（GUI 崩溃时 daemon 是否正确清理？GUI 重启时是否能重连已有 daemon？） | 中 | 中 | FP-6.1 集成测试覆盖 daemon 崩溃恢复 + 启动冲突；FP-9.11b 覆盖 GUI 重启连已有 daemon；GUI 退出走 FP-5.4 优雅关闭流程 |

## 附录 D：各 Phase 粗略工期范围（参考）

> 以下为单人全职开发的粗略估算，实际工期取决于经验和遇到的问题。多人并行可缩短，但需注意依赖关系（附录 A）。

| Phase | 内容 | 粗略工期 | 备注 |
|-------|------|---------|------|
| Phase 0 | russh spike + 脚手架 + CI | 1-2 周 | spike 3-5 天，脚手架 + CI 2-3 天 |
| Phase 1 | 核心基础设施 + daemon IPC 协议 | 1-1.5 周 | config/credential/error + socket 协议定义 |
| Phase 2 | SSH 层 | 1-1.5 周 | russh API 学习曲线 |
| Phase 3 | 代理层 | 1 周 | SOCKS5 + HTTP 代理 |
| Phase 4 | 触发器引擎 | 1.5-2 周 | EventGateway 并发结构 + 模板渲染 |
| Phase 5 | 服务器管理 | 1 周 | 状态机 + 优雅关闭 |
| Phase 6a | daemon + GUI 桥接 + CLI 客户端 | 2-3 周 | daemon socket（双平台）+ 内嵌/独立双模式 + 完整 Action 映射 + 事件广播 + CLI 独立二进制（项目最复杂的并发架构阶段） |
| Phase 6b | 桌面壳功能 | 1-1.5 周 | 托盘 + 自启 + 平台适配 + 日志 + 通知 + 离线检测 + 窗口效果（可与 Phase 7 并行） |
| Phase 7 | 前端基础 | 1 周 | 项目搭建 + i18n + 布局 |
| Phase 8 | 前端功能 + CLI 结果实时显示 | 3-4 周 | 功能最多，UI 组件密集 + CLI→UI 事件驱动 |
| Phase 9 | 集成 E2E + CLI + daemon E2E | 2-2.5 周 | E2E 测试 + CLI 全流程 + 性能基准 + UX 测试 |
| ~~Phase 10~~ | ~~发布准备~~ | ~~1 周~~ | **v1.1，本次不做** |
| **总计（Phase 0-9）** | | **15-19 周** | 约 3.5-4.5 个月 |

<!-- === SECTION 11 (Phase 10 + Appendix) END === -->
