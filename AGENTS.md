# AI 助手开发规范

## Subagent 使用规则（仅允许 reviewer）

**只允许 spawn `reviewer` 这一个只读 subagent profile，禁止其他所有 subagent。**

### 允许

- 使用 `run_subagent` 且 profile 为 `reviewer`（定义在 `.devin/agents/reviewer/AGENT.md`）
- reviewer 是只读 agent：只能 `read/grep/glob/exec`，禁止 `write/edit`
- 用途：每个功能点开发完成后，spawn reviewer 对改动做独立评审

### 禁止

- 禁止使用内置的 `subagent_explore` 或 `subagent_general` profile
- 禁止 spawn 任何其他自定义 subagent profile
- 禁止用 subagent 做实现工作（写代码、改代码一律由主 agent 直接完成）
- 禁止嵌套 subagent（reviewer 不能再 spawn subagent）

### 为什么要限制

历史经验表明，多 subagent 并发会导致 GUI OOM（内存溢出）、界面卡死。
只保留一个只读、工具受限的 reviewer，既能获得独立评审（防偷工），
又把 OOM 风险压到最低：reviewer 只用 auto-approve 的只读工具，
不触发权限弹窗，不并发，不嵌套。

### 何时 spawn reviewer

每个功能点完成开发 + 单元测试后，进入"对比文档确认"环节时，
主 agent 必须 spawn reviewer，在 task prompt 里包含：
1. 本次改动涉及的文件列表
2. design doc 中该功能点的验收标准（逐条）
3. 要求 reviewer 逐条核对并输出带证据的结论

reviewer 返回报告后，主 agent 根据报告决定是否进入下一功能点。
未通过项必须补完后重新 spawn reviewer 复审。

## 文件写入规范（防止 GUI OOM）

写入文件时必须**一段一段地写入**，不能一次性写入整个文件的全部内容。

一次性写入大文件会导致 GUI OOM（内存溢出），工具会崩溃或卡死。

### 正确做法

1. 先用 `write` 工具写入文件的第一段内容（如文件头、imports、第一个类/函数）
2. 再用 `edit` 工具逐段追加后续内容

### 分段阈值

- 超过 **200 行**的文件，必须分 **3 次以上**写入
- 每次写入建议 ≤ 150 行
- 写入前先估算行数，规划分段点（按类/函数/逻辑块切分）

### 示例

```text
# 错误 ❌
write(file_path="big_file.rs", content="<800 行完整内容>")

# 正确 ✅
write(file_path="big_file.rs", content="<第 1 段：imports + 结构体定义>")
edit(file_path="big_file.rs", old_string="// === SECTION 1 END ===",
     new_string="// === SECTION 1 END ===\n<第 2 段：方法实现>")
edit(file_path="big_file.rs", old_string="// === SECTION 2 END ===",
     new_string="// === SECTION 2 END ===\n<第 3 段：测试>")
```

### 实施要点

- 在 `write` 的内容末尾留一个明确的分隔标记注释（如 `// === SECTION N END ===`），便于后续 `edit` 用唯一锚点追加
- 不要用文件末尾的空行作为锚点，容易因空白处理不一致导致 `old_string` 不唯一
- 若文件已存在且需大改，先 `read` 全文，再分段 `edit`，不要 `write` 覆盖



## 自动更新（Tauri Updater）

### 架构

- 使用 `tauri-plugin-updater` 实现自动更新
- 签名密钥：`~/.tauri/ai-subtrans.key`（私钥）+ `tauri.conf.json` 里的 `pubkey`（公钥）
- 更新清单：`latest.json` 托管在 GitHub Pages（`gh-pages` 分支）
- 安装包存储：GitHub Releases
- 国内加速：`latest.json` 里的 URL 用 `gh-proxy.com` 前缀

### 发布流程

```
node scripts/publish.mjs <版本号> "更新内容"
```

脚本自动完成：改版本号 → 带签名构建 → 创建 GitHub Release → 上传 .exe + .sig → 更新 latest.json

### 环境变量

- `GITHUB_TOKEN`：GitHub Personal Access Token（repo 权限）
- `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`：私钥密码

### 客户端行为

- 启动后 5 秒静默检查更新
- 有新版本时弹窗显示版本信息和更新内容
- 用户确认后下载安装包（显示进度/速度/ETA），验证签名，静默安装
- 安装完成后提示重启
- 设置页"关于"分区有"检查更新"按钮可手动触发

### 注意事项

- 私钥丢了就无法发布更新，务必备份
- `TAURI_SIGNING_PRIVATE_KEY` 需要传私钥内容（不是路径），脚本会自动读取文件
- `--build-only` 参数可只构建不发布（本地测试用）

## 构建与测试命令

### 启动开发环境

```bash
# 启动前后端（推荐）— 自动启动 vite + tauri，HMR 热更新
npm start

# 等价于 npm start
npm run tauri dev

# 仅启动前端 dev server（浏览器调试用）
npm run dev
```

> **HMR 配置**：`vite.config.ts` 中固定配置了 HMR WebSocket（`localhost:1421`），
> 确保 Tauri webview 能正确接收前端热更新。修改前端代码后会自动刷新，无需重启。

### Rust 后端

```bash
# 编译检查
cd src-tauri && cargo check --lib

# 运行单元测试 
cd src-tauri && cargo test --lib

# Clippy 检查 
cd src-tauri && cargo clippy --lib

# 前端类型检查
npx tsc --noEmit
```
 