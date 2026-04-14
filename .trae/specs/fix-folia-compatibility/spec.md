# Folia/Lophine 兼容性修复 Spec

## Why
VerifyMC 在 `plugin.yml` 中声明了 `folia-supported: true`，但代码中使用了 Folia 不兼容的 Bukkit 调度器 API（`Bukkit.getScheduler().runTaskTimerAsynchronously()` 和 `Bukkit.getScheduler().runTask()`），导致在 Lophine（Folia 分支核心）上启动时抛出 `UnsupportedOperationException`，插件完全无法启用。

## What Changes
- 新增 `FoliaCompat` 工具类，封装 Folia/非 Folia 环境下的调度器调用差异
- 将所有 `Bukkit.getScheduler().runTaskTimerAsynchronously()` 调用替换为 `FoliaCompat.runTaskTimerAsync()`
- 将所有 `Bukkit.getScheduler().runTask()` 调用替换为 `FoliaCompat.runTaskGlobal()`
- 将 `DiscordService` 中的 `BukkitRunnable.runTaskTimerAsynchronously()` 替换为 `FoliaCompat.runTaskTimerAsync()`

## Impact
- Affected specs: 插件生命周期、AuthMe 同步、Discord 服务、白名单管理
- Affected code:
  - 新增: `util/FoliaCompat.java`
  - 修改: `VerifyMC.java` — AuthMe 周期同步调度
  - 修改: `service/DiscordService.java` — Token 清理定时任务
  - 修改: `service/AuthmeService.java` — 白名单命令调度
  - 修改: `web/RegistrationProcessingHandler.java` — 白名单命令调度
  - 修改: `web/handler/AdminUserDeleteHandler.java` — 白名单命令调度
  - 修改: `web/handler/AdminUserUnbanHandler.java` — 白名单命令调度
  - 修改: `web/handler/AdminUserApproveHandler.java` — 白名单命令调度
  - 修改: `web/handler/AdminUserBanHandler.java` — 白名单命令调度

---

## ADDED Requirements

### Requirement: Folia 兼容调度工具
系统 SHALL 提供 `FoliaCompat` 工具类，自动检测运行环境（Folia 或非 Folia），并提供统一的调度接口：
- `runTaskTimerAsync(Plugin, Runnable, long delayTicks, long periodTicks)` — 异步周期任务
- `runTaskGlobal(Plugin, Runnable)` — 全局同步任务（主线程执行命令等）
- `cancelTasks(Plugin, List<?>)` — 取消已注册的任务

#### Scenario: 在 Folia 环境下运行
- **WHEN** 检测到 `io.papermc.paper.threadedregions.RegionizedServer` 类存在
- **THEN** `runTaskTimerAsync` 使用 `Bukkit.getAsyncScheduler().runAtFixedRate()`，`runTaskGlobal` 使用 `Bukkit.getGlobalRegionScheduler().run()`

#### Scenario: 在非 Folia 环境下运行
- **WHEN** 未检测到 Folia 类
- **THEN** `runTaskTimerAsync` 使用 `Bukkit.getScheduler().runTaskTimerAsynchronously()`，`runTaskGlobal` 使用 `Bukkit.getScheduler().runTask()`

### Requirement: AuthMe 周期同步 Folia 兼容
系统 SHALL 使用 `FoliaCompat.runTaskTimerAsync()` 替代 `Bukkit.getScheduler().runTaskTimerAsynchronously()` 来调度 AuthMe 周期同步任务。

#### Scenario: AuthMe 同步在 Lophine 上正常调度
- **WHEN** 插件在 Lophine 核心上启用且 AuthMe 同步间隔 > 0
- **THEN** 周期同步任务正常注册，不再抛出 `UnsupportedOperationException`

### Requirement: Discord Token 清理 Folia 兼容
系统 SHALL 使用 `FoliaCompat.runTaskTimerAsync()` 替代 `BukkitRunnable.runTaskTimerAsynchronously()` 来调度 Discord Token 清理任务。

#### Scenario: Discord 清理在 Lophine 上正常调度
- **WHEN** 插件在 Lophine 核心上启用且 Discord 集成已配置
- **THEN** Token 清理任务正常注册，不再抛出 `UnsupportedOperationException`

### Requirement: 白名单命令调度 Folia 兼容
系统 SHALL 使用 `FoliaCompat.runTaskGlobal()` 替代 `Bukkit.getScheduler().runTask()` 来在主线程执行白名单命令（`whitelist add/remove`）。

#### Scenario: 审批用户后在 Lophine 上执行白名单命令
- **WHEN** 管理员审批用户或用户注册成功
- **THEN** `whitelist add` 命令通过 Folia 兼容方式在全局区域调度器上执行

#### Scenario: 封禁/删除用户后在 Lophine 上执行白名单命令
- **WHEN** 管理员封禁或删除用户
- **THEN** `whitelist remove` 命令通过 Folia 兼容方式在全局区域调度器上执行

---

## MODIFIED Requirements

### Requirement: 插件生命周期
插件 SHALL 在 Folia（Lophine）和非 Folia（Paper/Spigot）环境下均能正常启用和禁用，不再因调度器 API 不兼容而崩溃。

---

## REMOVED Requirements

无
