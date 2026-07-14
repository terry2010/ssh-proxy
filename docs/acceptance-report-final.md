# TermFast v1 验收报告（甲方技术负责人独立复核版）

> **验收日期**：2026-07-13
> **验收范围**：Phase 0-9（v1 全部功能）
> **验收依据**：`docs/development-plan.md` 各 Phase 功能点（FP）+ 附录 B「完成定义」+ `docs/termfast-project.md` 设计文档
> **验收人**：甲方技术负责人（独立复核，不采信乙方自检报告 `docs/acceptance-report.md`）
> **复核方法**：实际运行全部测试命令 + 逐文件代码审查 + 与开发计划逐条核对
> **结论摘要**：**不予通过**。存在 4 项 P0 阻塞缺陷与 3 项 P1 功能性缺陷，且乙方自检报告存在多处失实结论（详见 §8）。

---

## 0. 复核环境

| 项 | 值 |
|----|----|
| 操作系统 | macOS Darwin 25.5.0 |
| Rust 工具链 | rustc 1.93.1（Homebrew，= CI `dtolnay/rust-toolchain@stable`） |
| Node | 见 `package.json`（Vite 8 / Vitest 4 / Playwright） |
| 验收方式 | 全部命令实跑，输出留存于本报告 §4 |

> **关键说明**：CI（`.github/workflows/ci.yml`）使用 `dtolnay/rust-toolchain@stable`，即"当前最新稳定版 Rust"。本次复核使用 1.93.1，与 CI 行为一致。乙方自检报告（2025-07-12）所声称的若干"零警告/全通过"结论在当前工具链下**无法复现**（见 §4.2、§4.5）。

---

## 1. 总体结论

| 维度 | 乙方自检报告结论 | 甲方独立复核结论 | 差异 |
|------|------------------|------------------|------|
| Rust 单元测试 | ✅ 217 通过 0 失败 | ✅ 217 通过 0 失败 | 一致（已复现） |
| Rust Clippy `-D warnings` | ✅ 零警告 | ❌ **2 个 error，编译失败** | **失实** |
| TypeScript 类型检查 | ✅ 零错误 | ✅ 零错误 | 一致 |
| 前端单元测试（Vitest） | ⚠️ 4 失败 / 45 通过（1 文件） | ⚠️ **3 失败 / 46 通过（3 文件）** | 失败项已变，结论过时 |
| E2E（Playwright） | ✅ 20 通过 / 0 失败 | ❌ **13 失败 / 47 通过（共 60）** | **严重失实** |
| 发布产物构建 | ⛔ 未执行 | ⛔ 未执行 | 一致 |
| U3 删除服务器清理 authorized_keys | ✅ | ❌ **功能缺陷（清理永远不生效）** | **失实** |
| U7 异常服务器置顶 | ✅ | ❌ **未实现（代码显式 no sorting）** | **失实** |
| CLI-4 CLI 全功能 | ✅ 全部命令可调 | ❌ **Windows 上 CLI 无法连接 daemon** | **失实** |
| hostkey 三重通知（P1-2） | ⚠️ 仅 log 未广播 | ✅ **已实现广播 + 前端处理** | 自检过时（实际比自检好） |

**总体判定：不予通过。**

乙方在 Rust 后端核心（config / SSH / 代理 / 触发器 / daemon / 优雅关闭）层面交付质量较高，217 个单元测试可复现通过，架构与设计文档基本吻合。但存在以下不可接受的问题：

1. **自检报告失实**：Clippy 与 E2E 两项关键质量门的"通过"结论无法复现，E2E 实际 13/60 失败却被记为 20/20 通过。自检报告不能作为验收依据。
2. **3 项用户故事级功能缺陷被自检误判为通过**：U3（authorized_keys 清理，安全相关）、U7（异常置顶）、CLI-4（Windows CLI）。
3. **CI 质量门已破**：Clippy 在当前 stable 工具链下失败，CI G8 门不再成立。

<!-- === SECTION 1 END === -->

---

## 2. 测试命令实跑结果（§4 验收证据）

### 4.1 Rust 单元/集成测试

```
$ cargo test --workspace
test result: ok. 126 passed (termfast_core)
test result: ok.   6 passed (ssh_integration_test)
test result: ok.   8 passed (termfast_credential)
test result: ok.  30 passed (termfast_daemon lib)
test result: ok.   9 passed (daemon e2e_test)
test result: ok.  15 passed (daemon integration_test)
test result: ok.   6 passed (termfast_test_utils)
test result: ok.  17 passed (termfast_app)
合计：217 passed / 0 failed
```

**结论**：与自检报告一致，Rust 测试层面可信。但需注意：
- `ssh_integration_test`（6 个）使用 mock SSH server（`test-utils` crate），**未对真实 VPS 跑过**（无环境变量凭据记录）。
- `test_unimplemented_action`（handler.rs:2128）仍以 "CleanupAuthorizedKeys is not yet implemented" 为注释，但该 Action 实际已实现（见 §5.3）。测试名/注释具有误导性，且仅因缺参返回 Err 而通过——并未验证清理逻辑正确性（见 §5.3 缺陷 D-2）。

### 4.2 Rust Clippy（CI G8 质量门）— ❌ 失败

```
$ cargo clippy --workspace --all-targets -- -D warnings
error: very complex type used. Consider factoring parts into `type` definitions
  --> crates/core/src/server/instance.rs:68:30
   |  trigger_result_callback: Mutex<Option<Arc<dyn Fn(TriggerEvent,
   |     &[crate::trigger::engine::TriggerExecutionResult]) + Send + Sync>>>
error: very complex type used. ...
  --> crates/core/src/server/instance.rs:148:57
error: could not compile `termfast-core` (lib)
```

**结论**：自检报告"✅ Clippy 零警告"**失实**。在 stable 1.93.1 下 `clippy::type_complexity` 触发 2 个 error，`-D warnings` 直接编译失败。CI 使用 `@stable`，因此 **CI G8 门当前为破窗状态**，任何新提交都会被 CI 拒绝。修复方式：将回调类型提取为 `type TriggerResultCallback = Arc<dyn Fn(...) + Send + Sync>;`。

### 4.3 TypeScript 类型检查

```
$ npx tsc --noEmit
Exit code 0
```

**结论**：与自检报告一致，零错误。

### 4.4 前端单元测试（Vitest）— ⚠️ 与自检不一致

```
$ npx vitest run
Test Files  3 failed | 15 passed (18)
Tests       3 failed | 46 passed (49)
```

实际失败项（与自检报告的"4 失败/45 通过/1 文件"不同）：

| 文件 | 失败用例 | 性质 |
|------|---------|------|
| `src/components/desktop/TitleBar.test.tsx` | renders title bar with app name | **新失败**：组件硬编码 "termfast"，测试期望 "TermFast"（品牌不一致，见 D-5） |
| `src/components/shared/LogViewer.test.tsx` | renders log viewer with title | 新失败（标题文案/结构变更） |
| `src/components/shared/ServerList.test.tsx` | pins abnormal servers to top | **真实功能缺陷**（U7 未实现，见 D-3） |

**结论**：自检报告的失败清单已过时。当前 3 个失败中有 2 个是自检未提及的新失败，1 个（pins abnormal）对应真实功能缺陷。

### 4.5 E2E（Playwright）— ❌ 严重失实

```
$ npx playwright test
  13 failed
  47 passed (51.2s)
```

失败分布（共 60 个测试，非自检所称 20 个）：

| 文件 | 失败数 | 代表性失败 |
|------|--------|-----------|
| `e2e/onboarding.spec.ts` | 1 | quick mode：`Check Firewall` 按钮始终 disabled，引导流程走不通 |
| `e2e/proxy-benchmark.spec.ts` | 2 | 系统代理清除 / 端口编辑 IPC 未触发 |
| `e2e/server-list.spec.ts` | 3 | 异常置顶（U7）/ 删除确认对话框（U3）失败 |
| `e2e/task-flows.spec.ts` | 3 | FP-9.1 添加服务器 / FP-9.5 设置 / FP-9.6 日志面板 |
| `e2e/trigger-editor.spec.ts` | 3 | 手动触发（U5）/ 编辑 / 删除触发器 |

**结论**：自检报告"✅ 20 passed / 0 failed"**严重失实**。实际 60 个测试中 13 个失败，覆盖 FP-9.1、FP-9.5、FP-9.6、U3、U5、U7、U15 等核心用户故事。其中 `onboarding` 的 `Check Firewall` 按钮 disabled 属于真实交互缺陷（非单纯选择器漂移）。

> **方法论提醒**：Playwright "E2E" 实际为**前端 + mockTauri() 模拟 IPC** 的前端集成测试，不经过真实 daemon / SSH 后端（见 §6.1）。即便如此仍有 13 个失败，说明前端 UI 自交付后已发生回归或本就未通过。

<!-- === SECTION 2 END === -->

---

## 3. 缺陷清单（按严重程度）

### 3.1 P0 — 阻塞发布

| # | 缺陷 | 证据 | 影响 | 自检报告结论 |
|---|------|------|------|-------------|
| **D-1** | **Windows CLI 客户端完全不可用** | `crates/cli/src/client.rs:35` `#[cfg(not(unix))] bail!("Windows named pipe client not yet implemented")`。`DaemonClient::connect()` 与 `send_request()` 在 Windows 上直接返回错误 | v1 明确目标平台含 Windows（§0.3）。Windows 用户无法用 CLI 连接 daemon，CLI-4"全部命令可调"在 Windows 上不成立。daemon 端 named pipe 已实现，但客户端缺失 | 自检 ✅"CLI-4 全部命令可调"——**失实** |
| **D-2** | **authorized_keys 清理永远不生效（安全缺陷）** | 推送：`crates/core/src/ssh/auth.rs:162` `echo '{}' >> ~/.ssh/authorized_keys`（**无注释标记**）。清理：`crates/daemon/src/handler.rs:1421` `sed -i '/# termfast: {}/d'`（**按 `# termfast:` 标记匹配**）。两者不匹配 → sed 永远删不到任何行 | 删除服务器时（U3 / §8.5）承诺清理 VPS 侧公钥，实际公钥永久残留。属安全/运维缺陷，正是甲方运营期最担心的"VPS 上残留可登录公钥" | 自检 ✅ U3"cleanup_authorized_keys"——**失实** |
| **D-3** | **异常服务器置顶（U7）未实现** | `src/components/shared/ServerList.tsx:238-239`：`// Keep config order (new servers appear at bottom); no sorting` `const sorted = servers;`。仅计算 `abnormalCount` 用于摘要，列表本身不排序 | U7"5 态视觉标识 + 异常置顶"半实现：颜色/形状标识在，但置顶缺失。单元测试与 E2E 测试 `pins abnormal servers to top` 均失败 | 自检 ✅ U7——**失实** |
| **D-4** | **CI Clippy 质量门已破** | §4.2，`instance.rs:68/148` type_complexity，stable 1.93.1 下 `-D warnings` 编译失败 | CI G8 门失效，新提交无法通过 CI；自检"零警告"不可复现 | 自检 ✅——**失实** |

### 3.2 P1 — 功能性缺陷 / 捷径

| # | 缺陷 | 证据 | 影响 | 自检结论 |
|---|------|------|------|---------|
| **D-5** | **产品品牌不一致** | TitleBar.tsx:55 硬编码 `termfast`；`tauri.conf.json` productName=`TermFast`、identifier=`com.termfast.app`；`package.json` name=`termfast` | 显示名 / 打包名 / 包名三方不一致；TitleBar 单测失败；用户看到的应用名与发布名不符 | 未提及 |
| **D-6** | **Onboarding 快速模式默认跳过 hostkey 校验** | `src/components/shared/Onboarding.tsx:69` `skip_hostkey_verify: true` | §17.2 MITM 防护在首次快速引导路径被默认关闭，安全降级；与三重通知设计意图相悖 | 未提及 |
| **D-7** | **Windows named pipe SDDL 未真正应用** | `crates/daemon/src/server.rs:508-513` 注释承认 tokio `ServerOptions` 不暴露 security_attributes；`set_pipe_security` 构造 SDDL 但日志 "SDDL applied" 具误导性（实际未绑到 pipe 实例） | Windows 上 named pipe 权限控制缺失 | 自检 ⚠️ P1-1——**结论正确**，但日志文案误导性需一并修 |
| **D-8** | **E2E 13 项失败（含核心用户故事）** | §4.5。FP-9.1 添加服务器、U3 删除确认、U5 手动触发、U7 置顶、U15 引导防火墙检查均失败 | 多个 v1 核心任务流前端不可用 | 自检 ✅"20/20 通过"——**严重失实** |
| **D-9** | **Linux 平台适配缺失** | `crates/desktop/src/platform/` 仅 macos.rs + windows.rs；`platform/mod.rs` 无 Linux 系统代理（gsettings/NetworkManager）实现 | v1 虽以 macOS/Windows 为主，但 CI 在 ubuntu 跑 cargo check/test；Linux 桌面端系统代理不可用 | 自检 ⚠️——**结论正确** |

### 3.3 P2 — 非功能性 / 文档

| # | 缺陷 | 证据 | 影响 |
|---|------|------|------|
| **D-10** | **russh 版本"偏差"为自检虚构** | 自检 P2-1 称"计划指定 =0.61.2，实际 =0.62.2"。实际 `docs/development-plan.md:50` 明确写 `russh 0.62.2（最新稳定版）`，`Cargo.toml:22` `=0.62.2`。**无偏差** | 自检报告编造不存在的偏差，降低可信度 |
| **D-11** | **hostkey 三重通知自检结论过时（实际已实现）** | `handler.rs:327-347` 回调内通过 `event_forwarder` 广播 `ssh:hostkey_mismatch`；前端 `useDaemonEvents.ts:185` + `GlobalIndicator.tsx` 监听并展示 | 自检 P1-2 称"仅 log 未广播"——**过时**，实际已实现（这是少数实际比自检更好的项） |
| **D-12** | **spike（FP-0.2 Go/No-Go）无真实 VPS 执行记录** | `spikes/russh-stress/src/stress_test.rs` 含 S1-S9 实现，但无任何 S1-S9 在真实 VPS 上通过的记录/日志；`using_real_vps()` 在无环境变量时退化为 mock | 项目最关键 Go/No-Go 里程碑无证据通过；自检仅凭"目录存在"判 ✅ |
| **D-13** | **`cargo tauri build` 未执行** | 无 release 产物 | v1 发布门（FP-9.13）6 项全部未执行 | 自检 ⛔——**结论正确** |
| **D-14** | **误导性测试 `test_unimplemented_action`** | `handler.rs:2128-2135` 注释 "CleanupAuthorizedKeys is not yet implemented"，实际已实现，仅因缺参返回 Err 通过 | 测试名/注释误导，未验证清理正确性（D-2 因此漏网） | 未提及 |

<!-- === SECTION 3 END === -->

---

## 4. 各 Phase 复核明细

> 图例：✅ 已验证完成 ｜ ⚠️ 部分完成/有捷径 ｜ ❌ 缺陷/未完成 ｜ ⛔ 未执行

### Phase 0：russh spike + 脚手架 + CI — ⚠️

| 功能点 | 复核 | 证据 |
|--------|------|------|
| FP-0.1 测试凭据环境变量 | ✅ | `ssh_integration_test.rs` 读 `TEST_SSH_HOST` 等 |
| FP-0.2 spike S1-S9 | ⚠️ | 代码存在（S1-S9 函数齐全），但**无真实 VPS 执行记录**（D-12）。Go/No-Go 里程碑缺证据 |
| FP-0.3 CI 三平台矩阵 | ✅ | `.github/workflows/ci.yml` ubuntu/macos/windows |
| G8 clippy `-D warnings` | ❌ | 当前 stable 工具链下失败（D-4） |
| G5 三平台 E2E CI | ✅ | `e2e-matrix` job 存在 |
| 交叉编译 Android/iOS | ✅ | `core-cross-compile` job |

### Phase 1：核心基础设施 + daemon IPC — ✅（1 项捷径）

| 功能点 | 复核 | 证据 |
|--------|------|------|
| FP-1.1 config schema | ✅ | `config.rs` 结构完整 |
| FP-1.2 5 内置模板 | ✅ | `builtin_templates.rs`，`test_five_builtin_templates` 通过 |
| FP-1.3 ConfigManager + modify | ✅ | `manager.rs` 闭包式 modify |
| FP-1.3b RuntimeStateManager | ✅ | `runtime_state.rs` 独立 mutex，`test_independent_mutex_no_blocking` 通过 |
| FP-1.4 配置迁移 + 备份回滚 | ✅ | `migration.rs`，损坏降级测试通过 |
| FP-1.5 凭据存储 | ✅ | Keychain + InMemory，fallback 测试通过 |
| FP-1.6 加密导出/导入 | ✅ | AES-256-GCM + Argon2id，3 次锁定测试通过 |
| FP-1.7 daemon IPC 协议 | ✅ | `proto.rs` 36 Action + 6 EventType |
| P2-1 Windows named pipe SDDL | ⚠️ | D-7：SDDL 构造但未应用 |

### Phase 2：SSH 层 — ✅

| 功能点 | 复核 | 证据 |
|--------|------|------|
| FP-2.0 mock SSH server | ✅ | `test-utils` crate |
| FP-2.1 客户端 + 心跳 | ✅ | `keepalive_interval`（SSH keepalive，G10） |
| FP-2.2 认证 | ⚠️ | `auth.rs` 实现完整，但 `push_public_key` 无标记 → D-2 |
| FP-2.3 exec + 超时 + IP 检测 | ✅ | `exec.rs` 4 级回退 |
| FP-2.4 channel_opener | ✅ | `channel_opener.rs` |
| 重连指数退避 | ✅ | `test_exponential_backoff_sequence` |
| hostkey 三重通知 | ✅ | D-11：实际已实现广播（自检过时） |

### Phase 3：代理层 — ✅

SOCKS5 / HTTP / channel_manager 均完整，G11 idle_timeout=300s 验证通过。

### Phase 4：触发器引擎 — ✅

模板渲染（含 shell 转义防注入）、事件队列、per-server 串行化、冷却、暂停/恢复、pending 事件均实现且有测试。

### Phase 5：服务器管理 — ✅

ServerInstance / ServerManager / 7 态状态机 / 7 步优雅关闭 / 双连接方案均实现。

### Phase 6a：daemon + GUI 桥接 + CLI — ⚠️（Windows CLI 缺失）

| 功能点 | 复核 | 证据 |
|--------|------|------|
| FP-6.1 daemon socket server | ✅ | Unix socket + named pipe |
| FP-6.2 GUI 内嵌 daemon | ✅ | `daemon_embed.rs` |
| FP-6.3 CLI 客户端 | ❌ | **D-1：Windows 客户端未实现** |
| CLI-4 全功能 | ❌ | Windows 不可调 |
| Action 映射 36 个 | ✅ | `handler.rs` |
| 事件广播 | ✅ | `broadcast` + Tauri emit |

### Phase 6b：桌面壳 — ⚠️（Linux 缺失）

托盘 / 自启（`tauri_plugin_autostart` 真实实现于 `src-tauri/src/lib.rs`，`StubAutostartManager` 仅为可测试 trait）/ 系统代理 / 日志 / 通知 / 离线检测 / 窗口效果均实现。**Linux 平台适配缺失（D-9）**。

### Phase 7：前端基础 — ✅

Vite + React 19 + Tailwind + Zustand + i18n（en/zh-CN）+ 三栏布局 + 键盘快捷键。

### Phase 8：前端功能 — ❌（多处缺陷）

| 功能点 | 复核 | 证据 |
|--------|------|------|
| FP-8.1 Onboarding | ⚠️ | D-6：快速模式默认 `skip_hostkey_verify: true`；E2E `Check Firewall` 按钮 disabled |
| FP-8.2 ServerList | ❌ | D-3：异常置顶未实现；单测 + E2E 均失败 |
| FP-8.3 ServerDetail | ✅ | |
| FP-8.4 TriggerList + Editor | ⚠️ | 组件在，但 E2E 手动触发/编辑/删除 3 项失败 |
| FP-8.5 LogViewer | ⚠️ | 单测 "renders log viewer with title" 失败 |
| FP-8.6~8.8 Settings/Template/AddServer | ✅ | |
| FP-8.11 三档确认 + undo | ✅ | |
| FP-8.12 骨架屏 | ✅ | |
| FP-8.14 暗色 + a11y | ✅ | |
| FP-8.17 CLI 结果实时显示 | ✅ | `useDaemonEvents.ts` |
| U7 5 态标识 | ⚠️ | 颜色/形状在，置顶缺失（D-3） |
| U17 托盘图标颜色 | ✅ | |

### Phase 9：集成 E2E + 发布 — ❌

| 功能点 | 复核 | 证据 |
|--------|------|------|
| FP-9.1~9.8 任务流 E2E | ❌ | Playwright 13 失败（D-8） |
| FP-9.9 性能基准 | ⚠️ | `proxy_benchmark.rs` 存在但未运行验证 |
| FP-9.10 UX 测试 | ⛔ | 无自动化 |
| FP-9.11 CLI + daemon E2E | ⛔ | **无任何 CLI/daemon E2E 测试文件**（D-1 叠加） |
| FP-9.12 全量核对 | ❌ | 依赖项未通过 |
| FP-9.13 发布产物 | ⛔ | `cargo tauri build` 未执行（D-13） |

<!-- === SECTION 4 END === -->

---

## 5. 重点缺陷代码佐证

### 5.1 D-1 Windows CLI 不可用

`crates/cli/src/client.rs`：

```rust
impl DaemonClient {
    pub async fn connect() -> Result<Self> {
        // ... unix 分支正常 ...
        #[cfg(not(unix))]
        bail!("Windows named pipe client not yet implemented")   // line 35
    }
    pub async fn send_request(&mut self, ...) -> Result<Response> {
        #[cfg(unix)] { /* ... */ }
        // 非 unix 分支无 #[cfg(not(unix))] 实现 → Windows 上该方法不存在/不可用
    }
}
```

daemon 端（`server.rs`）已实现 named pipe 监听，但客户端缺失，导致 Windows 上 CLI 二进制无法与 daemon 通信。CLI-4 在 Windows 平台不成立。

### 5.2 D-2 authorized_keys 清理失效

推送（`crates/core/src/ssh/auth.rs:160-164`）：

```rust
let command = format!(
    "mkdir -p ~/.ssh && echo '{}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys",
    escaped_key,   // 公钥原文，无注释标记
);
```

清理（`crates/daemon/src/handler.rs:1420-1423`）：

```rust
let cmd = format!(
    "sed -i '/# termfast: {}/d' ~/.ssh/authorized_keys 2>/dev/null || true",
    key_name,   // 按 "# termfast:" 前缀匹配
);
```

推送时未写入 `# termfast: <name>` 注释行，清理时却按该注释匹配 → **sed 永远匹配不到，公钥永久残留**。修复方案：推送时改为 `echo '{} # termfast: {}' >> ...`，或清理时按公钥内容匹配。

### 5.3 D-3 异常服务器置顶未实现

`src/components/shared/ServerList.tsx:238-239`：

```tsx
// Keep config order (new servers appear at bottom); no sorting
const sorted = servers;
```

注释明确"no sorting"。`abnormalCount`（line 244）仅用于顶部摘要计数，列表渲染（line 316 `sorted.map`）保持配置顺序。U7 的"异常置顶"未实现，单测与 E2E 双失败印证。

### 5.4 D-4 Clippy 失败

`crates/core/src/server/instance.rs:68`：

```rust
trigger_result_callback: Mutex<Option<Arc<dyn Fn(TriggerEvent, &[crate::trigger::engine::TriggerExecutionResult]) + Send + Sync>>>,
```

`clippy::type_complexity` 在 stable 1.93.1 触发 error，`-D warnings` 下编译失败。修复：提取 `type TriggerResultCallback = Arc<dyn Fn(TriggerEvent, &[TriggerExecutionResult]) + Send + Sync>;`。

### 5.5 D-5 品牌不一致

| 来源 | 值 |
|------|----|
| `src/components/desktop/TitleBar.tsx:55` | `termfast`（硬编码显示） |
| `src-tauri/tauri.conf.json` productName | `TermFast` |
| `src-tauri/tauri.conf.json` identifier | `com.termfast.app` |
| `package.json` name | `termfast` |

TitleBar 单测期望 "TermFast" → 失败。需统一品牌。

<!-- === SECTION 5 END === -->

---

## 6. 方法论与范围说明

### 6.1 "E2E" 测试实际为前端集成测试

`e2e/fixtures.ts` 的 `mockTauri(page)` 在浏览器内拦截所有 `@tauri-apps/api` invoke，返回 mock 数据。Playwright 测试**不经过真实 daemon / SSH 后端**。设计文档 FP-9.1-9.8 要求"模拟真实用户操作流程，覆盖用户故事和任务流"，严格意义上的端到端应包含 daemon + mock SSH server。

后端层面的真实集成测试在 Rust 侧：`crates/daemon/tests/e2e_test.rs`（9）+ `integration_test.rs`（15）+ `ssh_integration_test.rs`（6），这些使用 mock SSH server 测试 daemon/core，质量较高。但前端 Playwright 套件即便只测前端仍有 13 失败，说明前端回归严重。

### 6.2 未执行的验证项（受时间/环境限制）

- `cargo tauri build` 双平台 release 构建（D-13）—— 建议发布前必跑。
- 真实 VPS 上的 spike S1-S9（D-12）—— 建议补跑并留存日志。
- FP-9.9 性能基准实跑 —— 建议补跑达标证据。
- Windows / Linux 实机验证 —— 本次仅在 macOS 复核；Windows CLI（D-1）与 Linux 系统代理（D-9）缺陷为静态代码审查结论。

<!-- === SECTION 6 END === -->

---

## 7. 验收结论与处置建议

### 7.1 结论

**v1 验收不予通过。**

乙方在后端核心（Phase 1-5）交付质量较高，架构与设计文档吻合，217 个 Rust 测试可复现通过。但：

1. **自检报告不可信**：Clippy、E2E 两项关键质量门结论失实，U3/U7/CLI-4 三项用户故事被误判为通过。
2. **4 项 P0 缺陷**必须修复后方可重新验收。
3. **CI 已破**（D-4），当前任何提交无法通过 CI。

### 7.2 修复要求（乙方整改清单）

| 优先级 | 编号 | 任务 | 验收标准 |
|--------|------|------|---------|
| P0 | D-1 | 实现 Windows CLI named pipe 客户端 | Windows 上 `termfast status` 等全部命令可调 |
| P0 | D-2 | 修复 authorized_keys 清理（推送加标记 或 清理按公钥匹配） | 删除已连接服务器后，VPS `~/.ssh/authorized_keys` 中对应公钥被实际删除（集成测试覆盖） |
| P0 | D-3 | 实现 ServerList 异常置顶排序 | 单测 `pins abnormal servers to top` + E2E `auth_failed server is pinned to top` 通过 |
| P0 | D-4 | 修复 Clippy type_complexity | `cargo clippy --workspace --all-targets -- -D warnings` 在当前 stable 零警告 |
| P0 | D-8 | 修复 E2E 13 项失败 | `npx playwright test` 全绿 |
| P1 | D-5 | 统一产品品牌（TitleBar / tauri.conf / package.json） | TitleBar 单测通过，显示名与发布名一致 |
| P1 | D-6 | Onboarding 快速模式 hostkey 策略复核 | 默认不跳过，或显式提示用户风险并记录 |
| P1 | D-7 | Windows named pipe SDDL 真正应用（自定义 pipe builder）或移除误导日志 | 权限实际生效，或日志如实反映 |
| P1 | D-9 | Linux 系统代理适配 | gsettings/NetworkManager 实现 + 测试 |
| P2 | D-12 | 补跑 spike S1-S9 真实 VPS 执行 | 留存通过日志 |
| P2 | D-13 | 执行 `cargo tauri build` 双平台 | 产物生成成功 |
| P2 | D-14 | 修正 `test_unimplemented_action` 命名/注释，补 CleanupAuthorizedKeys 正向用例 | 测试名如实，清理逻辑被正向覆盖 |

### 7.3 复核签字

| 角色 | 结论 | 日期 |
|------|------|------|
| 甲方技术负责人 | **不予通过，退回整改** | 2026-07-13 |

> 整改完成后需重新提交，并由甲方对 P0 项逐条复跑验证。自检报告不得作为通过依据，必须以甲方独立复跑结果为准。

<!-- === SECTION 7 END === -->

---

## 8. 对乙方自检报告（`docs/acceptance-report.md`）的可信度评估

| 自检结论 | 甲方复核 | 评价 |
|---------|---------|------|
| Rust 测试 217/0 | 一致 | 可信 |
| Clippy 零警告 | **失败 2 error** | **失实** |
| tsc 零错误 | 一致 | 可信 |
| Vitest 4 失败/45 通过 | **3 失败/46 通过（不同项）** | 过时 |
| E2E 20/20 通过 | **13 失败/47 通过（共 60）** | **严重失实** |
| U3 cleanup ✅ | **功能缺陷** | **失实** |
| U7 置顶 ✅ | **未实现** | **失实** |
| CLI-4 ✅ | **Windows 不可用** | **失实** |
| P1-2 hostkey 仅 log | **已实现广播** | 过时（偏保守） |
| P2-1 russh 版本偏差 | **无偏差（计划即 0.62.2）** | **虚构** |
| P1-1 Windows SDDL 未生效 | 一致 | 可信 |
| FP-9.11/9.13 未执行 | 一致 | 可信 |

**总评**：乙方自检报告在后端静态结论上部分可信，但在**前端/E2E/Clippy 三大可自动验证的质量门上给出无法复现的"通过"结论**，且**3 项用户故事功能缺陷被误判为通过**。自检报告不能作为验收依据，已由本报告取代。

<!-- === SECTION 8 END === -->

---

## 9. 运营期实测追加（2026-07-13）

> 用户要求测试"HTTP 代理 9999 端口是否能访问 Google"。实测发现端口角色与运行状态均存在问题，属验收后运营期风险，补充如下。

### 9.1 端口角色

本地运行中的 `termfast`（PID 39173）监听：

| 端口 | 实际协议 | 用户配置中的字段 |
|------|----------|------------------|
| 9999 | **SOCKS5** | `socks5_port` |
| 9998 | **HTTP 代理** | `http_port` |

用户所说"HTTP 代理 9999 端口"**与配置不符**；9999 是 SOCKS5，HTTP 代理在 9998。

### 9.2 实测结果

| 测试命令 | 结果 |
|---------|------|
| `curl -x socks5h://127.0.0.1:9999 http://www.google.com` | ✅ HTTP 302，0.65s |
| `curl -x socks5h://127.0.0.1:9999 https://www.google.com` | ✅ HTTP 200，2.05s |
| `curl -x http://127.0.0.1:9998 https://www.google.com` | ✅ HTTP 302（CONNECT 隧道），3.03s |
| `curl -x http://127.0.0.1:9998 http://www.google.com` | ❌ **15s 超时** |

### 9.3 新增缺陷 D-15：HTTP 代理 plain-HTTP 路径 hanging

`crates/core/src/proxy/http.rs:161-225` 的 `handle_http` 仅调用一次 `socket.read(&mut buf).await`（line 74）读取请求，未保证读取完整 HTTP 请求（headers 结束标志 `\r\n\r\n`）。若单次 `read` 未收齐 Host 等头部，重写后的请求不完整，远端服务器会挂起等待剩余数据，导致代理连接 15s+ 超时。`handle_connect` 只需首行即可工作，因此 HTTPS 经 9998 正常；SOCKS5 因协议不同也正常。**这是 FP-3.3 HTTP 代理在真实网络下的可用性缺陷**，应修复为循环读取到 `\r\n\r\n` 再处理。

**影响**：用户若把浏览器/系统代理指向 9998 并访问 plain HTTP 站点，会超时；HTTPS 正常。该问题在前端 Playwright 与 Rust 单元测试中均未覆盖（测试只验证服务器创建和解析函数），恰为"验收时没发现、运营期才发现"的典型问题。

---

*本报告由甲方技术负责人独立复核生成，所有测试命令均在本地实跑，代码引用均带行号可复核。*

