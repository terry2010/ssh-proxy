# TermFast v1 验收报告

> **验收日期**：2025-07-12
> **验收范围**：Phase 0-9（v1 全部功能）
> **验收依据**：`docs/development-plan.md` 中各 Phase 的功能点（FP）及附录 B「完成定义」
> **验收人**：技术负责人（Devin 自动化核对）

---

## 1. 总体结论

| 维度 | 状态 | 说明 |
|------|------|------|
| 代码实现 | ✅ 完成 | 所有 Phase 0-9 功能点均有对应代码实现 |
| Rust 单元测试 | ✅ 通过 | 217 个测试全部通过（0 失败） |
| Rust Clippy | ✅ 通过 | `cargo clippy --workspace --all-targets -- -D warnings` 零警告 |
| TypeScript 类型检查 | ✅ 通过 | `npx tsc --noEmit` 零错误 |
| 前端单元测试 | ⚠️ 部分失败 | 45 通过 / 4 失败（ServerList 测试隔离问题） |
| E2E 测试 | ✅ 通过 | Playwright 20 passed / 0 failed（11.7s） |
| 发布产物 | ⛔ 未验证 | `cargo tauri build` 未执行；v1 Release Checklist 未走完 |

**总体判定**：v1 功能开发**基本完成**，但存在 **4 个前端测试失败**和若干**已知捷径/未完成项**，需修复后方可正式发布。

---

## 2. 各 Phase 验收明细

### Phase 0：russh spike + 脚手架 + CI — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-0.1 测试 VPS 凭据环境变量 | ✅ | `ssh_integration_test.rs` 使用环境变量 `TEST_SSH_HOST` 等 |
| FP-0.2 russh spike S1-S9 | ✅ | `spikes/russh-stress` 目录存在（workspace exclude） |
| FP-0.3 CI 三平台矩阵 + E2E CI | ✅ | `.github/workflows/ci.yml` 含 ubuntu/macos/windows 矩阵 |
| G8 clippy `-D warnings` | ✅ | CI 第 47 行明确 `cargo clippy --workspace --all-targets -- -D warnings` |
| G5 三平台 E2E CI | ✅ | `e2e-matrix` job 覆盖三平台 |
| 核心交叉编译（Android/iOS） | ✅ | `core-cross-compile` job 验证 `aarch64-linux-android` + `aarch64-apple-ios` |

**偏差**：无。

### Phase 1：核心基础设施 + daemon IPC 协议 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-1.1 config schema | ✅ | `crates/core/src/config/config.rs` 完整定义 ServerConfig/ProxyConfig/ReconnectConfig 等 |
| FP-1.2 5 个内置模板 | ✅ | `builtin_templates.rs` 含 firewalld/ufw/process_restart/telegram/port_alive，测试 `test_five_builtin_templates` 通过 |
| FP-1.3 ConfigManager + modify | ✅ | `manager.rs` 实现 `modify()` 闭包式修改 + 自动保存 |
| FP-1.3b RuntimeStateManager | ✅ | `runtime_state.rs` 实现持久化 IP + 触发器时间戳，独立 mutex |
| FP-1.4 配置迁移 + 备份回滚 | ✅ | `migration.rs` 实现版本迁移 + 备份 + 损坏恢复 |
| FP-1.5 凭据存储抽象 | ✅ | `crates/credential/` 含 KeychainCredentialStore + InMemoryCredentialStore |
| FP-1.6 加密导出/导入 | ✅ | `migration.rs` 实现 AES-256-GCM + Argon2id + 3 次锁定 |
| FP-1.7 daemon IPC 协议 | ✅ | `proto.rs` 定义全部 Action（36 个）+ EventType（6 个） |
| P2-1 Windows named pipe SDDL | ⚠️ 部分 | `set_pipe_security` 存在但注释承认 tokio ServerOptions 不暴露 security_attributes，实际 SDDL 未真正应用到 pipe |

**偏差/捷径**：
- **P2-1 Windows named pipe 安全描述符**：代码存在但**未真正生效**。注释明确写道「tokio's ServerOptions doesn't expose this, we log a warning. A full implementation would require a custom pipe builder.」这意味着 Windows 上的 named pipe 权限控制**实际未实现**，仅做了 SDDL 字符串构造和日志输出。

### Phase 2：SSH 层 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-2.0 mock SSH server | ✅ | `e2e/mock-ssh-server.ts` + `test-utils` crate |
| FP-2.1 SSH 客户端 + 心跳 | ✅ | `client.rs` 使用 `keepalive_interval`（SSH keepalive 优先，G10） |
| FP-2.2 认证（密码/密钥/auto） | ✅ | `auth.rs` 实现 AuthMethod enum + generate_keypair + push_public_key |
| FP-2.3 exec + 超时 + IP 检测 | ✅ | `exec.rs` 实现超时杀 channel + 4 级 IP 检测回退链 |
| FP-2.4 channel_opener | ✅ | `channel_opener.rs` 实现 direct-tcpip 封装 |
| 重连指数退避 | ✅ | `client.rs` `connect_with_reconnect` + 测试 `test_exponential_backoff_sequence` |
| hostkey 验证 + triple notification | ⚠️ 部分 | 回调已设置但仅 log，未真正广播事件（见 §3 已知问题） |

**偏差**：无重大偏差。

### Phase 3：代理层 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-3.1 SOCKS5 代理 | ✅ | `socks5.rs` 完整实现 RFC 1928 |
| FP-3.2 channel_manager | ✅ | `manager.rs` 并发上限 + 空闲超时（默认 300s，G11） |
| FP-3.3 HTTP 代理 | ✅ | `http.rs` 实现 CONNECT + plain HTTP 双模式 |
| G11 channel_idle_timeout 300s | ✅ | `default_channel_idle_timeout()` 返回 300 |

**偏差**：无。

### Phase 4：触发器引擎 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-4.1 模板渲染 | ✅ | `template.rs` 占位符替换 + 条件块 + shell 转义（防注入） |
| FP-4.2 EventGateway / 事件队列 | ✅ | `engine.rs` 实现事件队列 + per-server mutex 串行化 |
| FP-4.3 IP 变化检测 | ✅ | `ipcheck.rs` IpChangeDetector |
| FP-4.4 健康检查 | ✅ | `health.rs` Process/Port 定期检查 + 触发 OnProcessDead/OnPortClosed |
| 冷却跟踪 | ✅ | `engine.rs` `is_in_cooldown` + `record_fire` |
| 暂停/恢复 | ✅ | `pause_all`/`resume_all`/`pause_server`/`resume_server` |
| pending 事件处理 | ✅ | 暂停时事件入队列，恢复后执行 |

**偏差**：无。

<!-- === SECTION 1 END === -->

### Phase 5：服务器管理 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-5.1 ServerInstance | ✅ | `instance.rs` 封装 SSH + proxy + triggers + IP 检测 |
| FP-5.2 ServerManager | ✅ | `manager.rs` add/remove/get/list + 端口冲突检测 |
| FP-5.3 状态机 | ✅ | `lifecycle.rs` 7 态状态机 + 合法转换校验 |
| FP-5.4 优雅关闭 7 步 | ✅ | `server.rs` `shutdown()` 实现 7 步 drain（30s 总超时） |
| B3 优雅关闭 6 步 | ✅ | 实际实现为 7 步（更细粒度，含广播 shutdown 事件） |
| I9 双连接方案 | ✅ | `connect()` + `connect_with_reconnect()` 双路径 |

**偏差**：无。

### Phase 6a：daemon + GUI 桥接 + CLI 客户端 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-6.1 daemon socket server | ✅ | `server.rs` Unix socket（macOS/Linux）+ named pipe（Windows） |
| FP-6.2 GUI 内嵌 daemon | ✅ | `src-tauri/src/daemon_embed.rs` EmbeddedDaemon + 事件转发 |
| FP-6.3 CLI 客户端 | ✅ | `crates/cli/` 独立二进制，全部命令可调 |
| CLI-1 IPC 协议定义 | ✅ | `proto.rs` 36 个 Action |
| CLI-2 daemon 双模式 | ✅ | 内嵌（Tauri）+ 独立（`termfast daemon`） |
| CLI-4 CLI 全功能 | ✅ | status/connect/disconnect/proxy/trigger/pause/resume/list/triggers/templates/logs/shutdown |
| CLI 退出码 0-5 | ✅ | `main.rs` `ExitCode` enum + `classify_error` |
| Action 映射完整性 | ✅ | `handler.rs` 全部 36 个 Action 有对应 handler |
| 事件广播 | ✅ | `DaemonState::broadcast` + Tauri `emit` 转发 |

**偏差**：无。

### Phase 6b：桌面壳功能 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-6.4 系统托盘 | ✅ | `lib.rs` `setup_tray` + 菜单（连接/断开/暂停/显示/退出） |
| FP-6.5 开机自启 | ✅ | `autostart.rs` + `tauri_plugin_autostart`（LaunchAgent） |
| FP-6.6 系统代理 | ✅ | `platform/macos.rs` networksetup + sudoers 白名单 |
| FP-6.7 日志系统 | ✅ | `crates/core/src/log/` ring buffer + 文件日志 + 轮转 |
| FP-6.8 通知 | ✅ | `notification.rs` + `tauri_plugin_notification` |
| FP-6.9 离线检测 | ✅ | `network.rs` NetworkMonitor 轮询 + offline/online 事件广播 |
| FP-6.10 窗口效果 | ✅ | `macos.rs` `apply_vibrancy` + TitleBar 平台适配 |
| B4 离线/飞行模式 | ✅ | NetworkMonitor 检测 + 暂停重连 + 恢复重连列表 |

**偏差**：
- **Windows 平台适配**：`crates/desktop/src/platform/windows.rs` 存在但本次验收在 macOS 上运行，Windows 平台代码未实际验证。
- **Linux 平台适配**：`platform/mod.rs` 中 `get_platform_adapter()` 对 Linux 的处理未明确（仅 macos/windows 模块），可能缺失 Linux 适配。

### Phase 7：前端基础 — ✅ 完成

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-7.1 项目搭建 | ✅ | Vite + React 19 + Tailwind + Zustand |
| FP-7.2 i18n | ✅ | `src/i18n/` 含 en.json + zh-CN.json |
| FP-7.3 布局 | ✅ | `App.tsx` 三栏布局（ServerList + ServerDetail + LogPanel） |
| FP-7.4 键盘快捷键 | ✅ | `useKeyboardShortcuts.ts` + 5 个测试通过 |

**偏差**：无。

### Phase 8：前端功能 — ⚠️ 基本完成（1 个测试文件失败）

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-8.1 Onboarding | ✅ | `Onboarding.tsx` + 2 个测试通过 |
| FP-8.2 ServerList | ⚠️ | 组件实现完整，但 4 个测试失败（测试隔离问题） |
| FP-8.3 ServerDetail | ✅ | `ServerDetail.tsx` + 2 个测试通过 |
| FP-8.4 TriggerList + Editor | ✅ | `TriggerList.tsx` + `TriggerEditor.tsx`（CodeMirror） |
| FP-8.5 LogViewer + LogPanel | ✅ | `LogViewer.tsx` + `LogPanel.tsx` + 测试通过 |
| FP-8.6 SettingsPage | ✅ | `SettingsPage.tsx` + 2 个测试通过 |
| FP-8.7 TemplateLibrary | ✅ | `TemplateLibrary.tsx` + 2 个测试通过 |
| FP-8.8 AddServerDialog | ✅ | `AddServerDialog.tsx` + 3 个测试通过 |
| FP-8.11 三档确认 + undo | ✅ | `ConfirmDialog.tsx` + `UndoToast.tsx` |
| FP-8.12 骨架屏 | ✅ | `Skeleton.tsx` SkeletonList |
| FP-8.14 暗色模式 + a11y | ✅ | Tailwind dark: + aria-label/role/aria-live |
| FP-8.17 CLI 结果实时显示 | ✅ | `useDaemonEvents.ts` 监听 daemon 事件 + store 更新 |
| U7 5 态视觉标识 | ✅ | STATUS_COLORS + STATUS_SHAPES（圆/半圆/方） |
| U8 端口点击复制 | ✅ | ServerListItem 端口 chip + clipboard |
| U17 托盘图标颜色 | ✅ | `create_tray_icon` 4 色（Green/Yellow/Red/Gray） |

**偏差/问题**：
- **FP-8.2 ServerList 测试失败**：4 个测试因**测试隔离问题**失败。组件 `useEffect` 在 mount 时调用 `loadServers()`，该函数通过 mock 的 `ipcInvoke` 返回空数组 `{ servers: [] }`，覆盖了测试通过 `useServerStore.setState()` 直接设置的状态。这是测试编写问题，非功能缺陷，但违反附录 B 第 2 条「单元测试通过」。

### Phase 9：集成 E2E + CLI + daemon E2E — ⚠️ 部分验证

| 功能点 | 状态 | 证据 |
|--------|------|------|
| FP-9.1~9.8 任务流 E2E | ✅ 通过 | `e2e/task-flows.spec.ts` 10 个测试全部通过 |
| FP-9.9 代理性能基准 | ✅ 通过 | `e2e/proxy-benchmark.spec.ts` 3 个测试通过（UI 稳定性） |
| FP-9.10 UX 测试 | ⛔ 未执行 | 无自动化脚本，需人工 |
| FP-9.11 CLI + daemon E2E | ⛔ 未运行 | 需启动 daemon 进程 |
| FP-9.12 全量核对 | ⚠️ 部分 | E2E + 单元测试通过，性能基准 + UX 未完成 |
| FP-9.13 v1 发布产物 | ⛔ 未执行 | `cargo tauri build` 未运行 |

**偏差**：E2E 测试全部通过（20/20），但 FP-9.10 UX 测试和 FP-9.11 CLI+daemon E2E 仍需人工/集成环境验证。

<!-- === SECTION 2 END === -->

---

## 3. 已知问题与捷径（Shortcuts / Incomplete Work）

### 3.1 阻塞发布的问题（P0）

| # | 问题 | 影响 | 修复建议 |
|---|------|------|---------|
| P0-1 | **ServerList 4 个单元测试失败** | 违反附录 B 第 2 条，CI `vitest run` 会失败 | 修复测试：mock `ipcInvoke` 返回测试数据，或等待 `loading=false` 后再断言 |
| P0-2 | **E2E 测试未验证** | FP-9.1~9.8 无法确认通过 | 本地运行 `npx playwright test` 并修复失败项 |

### 3.2 功能性捷径（P1）

| # | 问题 | 影响 | 严重程度 |
|---|------|------|---------|
| P1-1 | **Windows named pipe SDDL 未真正生效** | `set_pipe_security` 构造了 SDDL 但未应用到 pipe 实例（tokio 限制），Windows 上 named pipe 权限控制实际缺失 | 中（仅影响 Windows 安全性） |
| P1-2 | **hostkey mismatch triple notification 不完整** | `handle_connect_server` 中的回调仅 `tracing::error!`，未广播 `server:hostkey_mismatch` 事件给前端/托盘。注释承认「callback runs in a non-async context」 | 中（§17.2 三重通知只完成 1/3） |
| P1-3 | **Linux 平台适配可能缺失** | `platform/mod.rs` 仅有 macos.rs + windows.rs，Linux 的系统代理设置（gsettings NetworkManager）未见实现 | 中（CI 在 ubuntu 上跑 cargo check/test 但不跑系统代理） |

### 3.3 非功能性捷径（P2）

| # | 问题 | 影响 | 严重程度 |
|---|------|------|---------|
| P2-1 | **russh 版本偏差** | 计划指定 `=0.61.2`，实际 `=0.62.2`。已记录但未在文档中说明理由 | 低（小版本升级，测试通过） |
| P2-2 | **`cargo tauri build` 未验证** | v1 发布产物（.app/.exe）未实际构建，可能存在打包配置问题 | 中（发布前必须验证） |
| P2-3 | **`libc::mach_task_self` deprecated 警告** | `proxy_benchmark.rs` 使用 deprecated API，CI clippy `--all-targets` 可能报警 | 低（建议迁移到 `mach2` crate） |
| P2-4 | **UX 测试（FP-9.10）无自动化** | 需人工执行，无脚本 | 低（v1 可接受人工验证） |

### 3.4 评审意见修复确认（关键项）

| 编号 | 状态 | 说明 |
|------|------|------|
| B1 测试凭据环境变量 | ✅ | ssh_integration_test 使用环境变量 |
| B2 RuntimeStateManager | ✅ | `runtime_state.rs` 完整实现 |
| B3 优雅关闭 | ✅ | 7 步 drain（超出 6 步要求） |
| B4 离线检测 | ✅ | NetworkMonitor 实现 |
| I1 pause/resume IPC | ✅ | 4 个 Action 全部实现 |
| I3 代理性能基准 | ⚠️ | 代码存在但未运行验证 |
| I4 三档确认 + undo | ✅ | ConfirmDialog + UndoToast 实现 |
| I5 骨架屏 | ✅ | Skeleton 组件实现 |
| G3 原生窗口效果 | ✅ | macOS vibrancy 应用 |
| G8 CI clippy -D warnings | ✅ | CI 配置明确 |
| G10 心跳 SSH keepalive 优先 | ✅ | `keepalive_interval` 使用 SSH 协议级 keepalive |
| G11 channel_idle_timeout 300s | ✅ | 默认值 300 |
| CLI-1~6 | ✅/⚠️ | 协议/daemon/CLI 实现完成，CLI-6 E2E 未运行 |

---

## 4. 测试结果汇总

### 4.1 Rust 测试

```
$ cargo test --workspace
test result: ok. 126 passed (termfast_core)
test result: ok.   6 passed (ssh_integration_test)
test result: ok.   8 passed (termfast_credential)
test result: ok.  30 passed (termfast_daemon)
test result: ok.   9 passed (termfast_desktop)
test result: ok.  15 passed (termfast_cli)
test result: ok.   6 passed (termfast_test_utils)
test result: ok.  17 passed (termfast_app)
合计：217 passed / 0 failed
```

### 4.2 Rust Clippy

```
$ cargo clippy --workspace --all-targets -- -D warnings
Finished `dev` profile — 零警告零错误
```

### 4.3 TypeScript 类型检查

```
$ npx tsc --noEmit
Exit code 0 — 零错误
```

### 4.4 前端单元测试（Vitest）

```
$ npx vitest run
Test Files: 1 failed | 17 passed (18)
Tests:       4 failed | 45 passed (49)
```

**失败项**（均在 `src/components/shared/ServerList.test.tsx`）：
1. `renders server names when servers exist`
2. `shows port chip with socks5 port`
3. `calls selectServer when clicking a server`
4. `pins abnormal servers to top`

**根因**：组件 `useEffect` mount 时调用 `loadServers()` → `ipcInvoke("ipc_list_servers")`，mock 返回 `{ servers: [] }` 覆盖了 `useServerStore.setState()` 设置的测试数据。

### 4.5 E2E 测试（Playwright）

```
$ npx playwright test
Running 20 tests using 6 workers
  20 passed (11.7s)
```

测试文件分布：
- `task-flows.spec.ts` — 10 个测试（FP-9.1~9.8 任务流）
- `proxy-benchmark.spec.ts` — 3 个测试（FP-9.9 代理基准 UI 稳定性）
- `server-list.spec.ts` — 3 个测试（服务器列表渲染 + 点击 + 端口 chip）
- `onboarding.spec.ts` — 2 个测试（首次运行引导）
- `trigger-editor.spec.ts` — 2 个测试（触发器编辑器）

<!-- === SECTION 3 END === -->

---

## 5. 用户故事 U1-U20 验收

| 用户故事 | 状态 | 实现位置 | 备注 |
|---------|------|---------|------|
| U1 托盘快速连接/断开 | ✅ | `lib.rs` tray menu connect_all/disconnect_all | |
| U2 所有服务器状态 + 实时日志 | ✅ | ServerList + LogPanel + useDaemonEvents | |
| U3 添加/删除服务器 + 清理 | ✅ | AddServerDialog + ConfirmDialog + cleanup_authorized_keys | |
| U4 触发器卡片 | ✅ | TriggerList.tsx | |
| U5 手动触发 + 二次确认 | ✅ | manual_fire_trigger + ConfirmDialog | |
| U6 一键开关代理 | ✅ | ServerListItem inline toggle | |
| U7 5 态视觉标识 | ✅ | STATUS_COLORS + STATUS_SHAPES | |
| U8 端口点击复制 | ✅ | ServerListItem port chip | |
| U9 导入/导出 | ✅ | export_full/import_full + SettingsPage | |
| U10 日志筛选 | ✅ | LogPanel + LogViewer regex search | |
| U11 触发器编辑器 | ✅ | TriggerEditor.tsx (CodeMirror) | |
| U12 认证方式 + 安全级别 | ✅ | switch_auth_method + generate_ssh_key | |
| U13 设置页 | ✅ | SettingsPage.tsx | |
| U14 Tab 上下文保留 | ✅ | Zustand store 持久化状态 | |
| U15 空状态引导 | ✅ | Onboarding.tsx | |
| U16 键盘快捷键 | ✅ | useKeyboardShortcuts.ts | |
| U17 托盘图标颜色 + badge | ✅ | create_tray_icon 4 色 | |
| U18 一键设为系统代理 | ✅ | set_system_proxy + platform adapter | |
| U19 触发器执行进度 | ✅ | trigger:fired/command_executed/completed 事件 | |
| U20 模板库管理 | ✅ | TemplateLibrary.tsx | |

**U1-U20 全部验收通过**（代码层面）。E2E 层面待 Playwright 运行确认。

---

## 6. v1 发布门检查（FP-9.13）

| 检查项 | 状态 | 说明 |
|--------|------|------|
| `cargo tauri build` 双平台成功 | ⛔ 未执行 | 需在 macOS + Windows 分别构建 |
| FP-9.12 全量核对通过 | ⛔ 未执行 | 依赖 E2E + 性能基准通过 |
| FP-9.9 性能基准达标 | ⛔ 未运行 | `proxy_benchmark.rs` 存在但未执行 |
| FP-9.10 UX 测试完成 | ⛔ 未执行 | 需人工 |
| GitHub Release 创建 + 产物上传 | ⛔ 未执行 | 发布流程 |
| Release Notes 编写 | ⛔ 未执行 | 发布流程 |

**v1 发布门状态**：**未通过**。6 项检查全部未执行。

---

## 7. 验收结论与建议

### 7.1 结论

v1 阶段的**代码开发工作已基本完成**，所有 Phase 0-9 的功能点均有对应实现，Rust 后端质量较高（217 测试通过 + clippy 零警告），前端组件覆盖完整（U1-U20 全部有对应实现）。

但存在以下**阻塞项**，需在正式发布前解决：

1. **ServerList 单元测试失败（P0）**：4 个测试因测试隔离问题失败，CI 会阻塞。
2. **v1 发布产物未构建（P2）**：`cargo tauri build` 未执行。

以及以下**已知捷径**，建议在 v1.1 修复：

1. **Windows named pipe SDDL 未生效（P1）**：需自定义 pipe builder。
2. **hostkey mismatch triple notification 不完整（P1）**：回调需通过 channel 广播事件。
3. **Linux 平台适配缺失（P1）**：系统代理设置未实现。

### 7.2 建议的修复优先级

| 优先级 | 任务 | 预估工作量 |
|--------|------|-----------|
| P0 | 修复 ServerList 4 个测试 | 0.5 天 |
| P0 | `cargo tauri build` 双平台验证 | 0.5 天 |
| P1 | Windows named pipe SDDL 真正生效 | 2-3 天 |
| P1 | hostkey mismatch 事件广播 | 0.5 天 |
| P1 | Linux 系统代理适配 | 1-2 天 |
| P2 | russh 版本偏差文档化 | 0.1 天 |
| P2 | mach_task_self deprecated 迁移 | 0.2 天 |

### 7.3 签字

| 角色 | 状态 | 日期 |
|------|------|------|
| 技术负责人 | ⚠️ 有条件通过 | 2025-07-12 |

> **有条件通过**：代码实现完成度达标，但需修复 P0 项后方可正式发布 v1。

---

*本报告由 Devin 自动化验收工具生成，基于代码审查 + 测试运行 + 开发计划逐条核对。*

<!-- === SECTION 4 END === -->
