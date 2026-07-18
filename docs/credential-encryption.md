# 凭据加密设计文档

## 1. 背景

旧版凭据以明文 JSON 存储，存在安全风险。本方案将凭据存储改为加密格式，
使用 Argon2id 派生密钥 + AES-256-GCM 加密。

**前提**：旧版 APK 未发布，无需考虑明文数据迁移。

## 2. 加密方案

### 2.1 密钥派生

- 算法：Argon2id
- 参数：
  - m_cost = 16384 (16 MiB) — 移动端友好，桌面端更快
  - t_cost = 2
  - p_cost = 1
  - 输出长度 = 32 字节 (AES-256)
- 输入：用户主密码 + 随机 salt (16 字节)
- 每次初始化或改密码时生成新的 salt

> 注：曾使用 64 MiB (OWASP 桌面推荐)，在 Android 手机上导致
> 多秒延迟和 ANR。降至 16 MiB 后测试通过，安全性仍足够。

### 2.2 加密

- 算法：AES-256-GCM
- nonce：每次加密随机生成 12 字节
- AAD（附加认证数据）：完整文件头（magic + version + salt + nonce + sync_version）
  - 任何头部字段被篡改都会导致解密失败

### 2.3 文件格式

```
偏移  长度  字段            说明
0     4     magic          "TCRE" (TermFast Credential)
4     1     version        格式版本，当前 = 1
5     16    salt           Argon2id salt
21    12    nonce          AES-GCM nonce
33    8     sync_version   同步版本号（小端 u64），初始为 0
41    变长   ciphertext     AES-256-GCM 密文 + 16 字节认证 tag
```

总头部长度 = 41 字节。

### 2.4 密钥缓存

派生密钥（DerivedKey）缓存在 OS 安全存储中，用户只需输入一次主密码：

| 平台 | 缓存方式 |
|------|---------|
| macOS | Keychain（via keyring crate） |
| Windows | Windows Credential Manager（via keyring crate） |
| Linux | Secret Service / D-Bus（via keyring crate） |
| Android | EncryptedSharedPreferences（Android Keystore 支持） |

缓存失效场景：
- 用户主动锁定
- 用户重置凭据
- 改密码后（旧 key 失效，自动缓存新 key）
- 云同步下载了新文件（旧 key 解不开 → 提示输入新密码）

## 3. 用户体验流程

### 3.1 Phase 1（当前，无云同步）

```
首次启动 → 直接进入主界面（无主密码，凭据不持久化）
  ↓
添加服务器 → 输入密码/密钥 → 连接成功
  凭据仅存在内存中，App 关闭后丢失
  ↓
设置 → 凭据安全 → 设置主密码
  → 创建加密文件，内存中的凭据写入文件
  → 派生密钥缓存到 OS 安全存储
  → 之后保存的凭据都加密存储
  ↓
下次启动 → 自动用缓存的 key 解锁 → 直接进入主界面
  ↓
设置 → 凭据安全 → 修改密码 / 锁定 / 重置 / 导出 / 导入
```

**设计要点**：
- 启动时不阻挡用户进入主界面
- 未设密码时凭据不持久化（内存 only），用户每次重开 App 需重连
- 设密码后凭据才持久化，且自动解锁无需重复输密码
- 设置密码 = 首次加密初始化，不是"迁移"

### 3.2 设置页状态

| 状态 | 显示的按钮 |
|------|-----------|
| 未设密码 | "设置主密码" |
| 已设密码 | "修改密码" / "锁定" / "导出" / "导入" / "重置" |

## 4. 多平台与云同步

### 4.1 Phase 1：各平台独立

各平台的凭据文件是独立的本地文件，salt 不同，互不影响。

| 场景 | 结果 |
|------|------|
| 不同平台设相同密码 | 各自独立工作，文件内容不同（salt 不同） |
| 不同平台设不同密码 | 各自独立工作，用户需记住各设备密码 |
| 导出/导入同步 | 导入方文件被覆盖，用导出方密码解锁 |

**无冲突问题。**

### 4.2 Phase 2：云同步

同步整个加密文件。冲突处理基于 sync_version。

#### 改密码的影响

改密码 = 重新生成 salt + 重新加密整个文件。其他平台下载后：
- 缓存的 key 解不开新文件（salt 变了 → key 变了）
- 自动弹出"请输入主密码"提示
- 用户输入新密码 → 解锁成功 → 缓存新 key

**不需要分离密钥层和密码层**，当前格式足够。

#### 冲突场景与处理

| 场景 | 处理方式 |
|------|---------|
| A 改密码，B 无本地改动 | B 下载新文件 → key 失效 → 提示输新密码 → 正常 |
| A 改密码，B 有未同步的本地新增凭据 | B 下载新文件 → key 失效 → 提示输旧密码解密本地 → 合并凭据 → 用新密码加密上传 |
| A、B 都改密码 | last-write-wins，输的一方被覆盖，需输赢方密码 |
| A、B 都新增凭据（未改密码） | key 相同 → 合并凭据 → 上传 |

#### "A 改密码 + B 有本地改动"的详细流程

```
1. B 检测到下载的文件 key 失效
2. 提示："远程凭据已更新（可能是密码已变更），请输入当前主密码"
3. 用户输入旧密码 → 解密本地旧文件 → 拿到本地凭据
4. 合并：本地凭据 + 远程凭据（去重）
5. 用新密码（用户需知道）加密合并后的凭据 → 上传
   如果用户不知道新密码 → 放弃本地改动，使用远程文件
```

#### sync_version 的作用

- 每次 encrypt() 时 sync_version += 1
- 同步时比较 sync_version，高的胜出
- 如果 sync_version 相同但内容不同 → 需要合并

## 5. 安全考量

- 主密码和派生密钥从不写入日志
- DerivedKey 使用 zeroize crate，drop 时清零
- 解密后的凭据 HashMap 在 lock() 和 drop 时也 zeroize
- AES-GCM 的 AAD 绑定文件头，防止篡改
- 错误密码返回通用错误信息（不区分"密码错"和"文件损坏"）

## 6. 文件清单

### Rust 核心
- `crates/credential/src/encrypted.rs` — 加密存储核心
- `crates/credential/src/encrypted_adapter.rs` — CredentialStore trait 适配器
- `crates/credential/src/lib.rs` — 模块注册

### Desktop (Tauri + React)
- `src-tauri/src/credential_manager.rs` — IPC 命令 + keychain 缓存
- `src-tauri/src/daemon_embed.rs` — 共享加密凭据存储
- `src-tauri/src/lib.rs` — IPC 注册
- `src/components/shared/CredentialGate.tsx` — 启动解锁界面
- `src/components/shared/SettingsPage.tsx` — 凭据设置分区

### Android
- `crates/android-ffi/src/credential.rs` — 加密存储单例
- `crates/android-ffi/src/jni.rs` — JNI 函数
- `android/app/src/main/java/com/termfast/app/data/CredentialManager.kt` — 管理类
- `android/app/src/main/java/com/termfast/app/ui/screen/CredentialGateScreen.kt` — 解锁界面
- `android/app/src/main/java/com/termfast/app/ui/screen/SettingsScreen.kt` — 凭据设置分区

## 7. 测试

35 个单元测试覆盖：
- 加密/解密往返
- 错误密码
- 空凭据
- 迁移（Phase 1 不使用，但保留）
- 篡改头部
- 导出/导入
- 改密码
- 重置
- 缓存 key 解锁
- sync_version 递增
- 锁定状态操作失败
- 按服务器删除凭据
