# Tasks

- [x] Task 1: 创建 `FoliaCompat` 工具类
  - [x] SubTask 1.1: 在 `team.kitemc.verifymc.util` 包下创建 `FoliaCompat.java`
  - [x] SubTask 1.2: 实现 Folia 环境检测（通过 `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`）
  - [x] SubTask 1.3: 实现 `runTaskTimerAsync(Plugin, Runnable, long, long)` — Folia 下使用反射调用 `AsyncScheduler.runAtFixedRate()`，非 Folia 下使用 `Bukkit.getScheduler().runTaskTimerAsynchronously()`
  - [x] SubTask 1.4: 实现 `runTaskGlobal(Plugin, Runnable)` — Folia 下使用反射调用 `GlobalRegionScheduler.run()`，非 Folia 下使用 `Bukkit.getScheduler().runTask()`
  - [x] SubTask 1.5: 实现 `cancelTasks(Plugin, List<?>)` — 用于取消已注册的定时任务

- [x] Task 2: 修复 `VerifyMC.java` 中的 AuthMe 周期同步调度
  - [x] SubTask 2.1: 将 `Bukkit.getScheduler().runTaskTimerAsynchronously(this, ...)` 替换为 `FoliaCompat.runTaskTimerAsync(this, ...)`
  - [x] SubTask 2.2: 保存返回的任务句柄，在 `onDisable()` 中取消

- [x] Task 3: 修复 `DiscordService.java` 中的 Token 清理定时任务
  - [x] SubTask 3.1: 将 `new BukkitRunnable().runTaskTimerAsynchronously(plugin, ...)` 替换为 `FoliaCompat.runTaskTimerAsync(plugin, ...)`
  - [x] SubTask 3.2: 保存返回的任务句柄，添加 `stop()` 方法用于取消任务

- [x] Task 4: 修复 `AuthmeService.java` 中的白名单命令调度
  - [x] SubTask 4.1: 将 `Bukkit.getScheduler().runTask(plugin, ...)` 替换为 `FoliaCompat.runTaskGlobal(plugin, ...)`

- [x] Task 5: 修复 `RegistrationProcessingHandler.java` 中的白名单命令调度
  - [x] SubTask 5.1: 将 `org.bukkit.Bukkit.getScheduler().runTask(plugin, ...)` 替换为 `FoliaCompat.runTaskGlobal(plugin, ...)`

- [x] Task 6: 修复 Admin Handler 文件中的白名单命令调度
  - [x] SubTask 6.1: `AdminUserDeleteHandler.java` — 将 `Bukkit.getScheduler().runTask(...)` 替换为 `FoliaCompat.runTaskGlobal(...)`
  - [x] SubTask 6.2: `AdminUserUnbanHandler.java` — 将 `Bukkit.getScheduler().runTask(...)` 替换为 `FoliaCompat.runTaskGlobal(...)`
  - [x] SubTask 6.3: `AdminUserApproveHandler.java` — 将 `Bukkit.getScheduler().runTask(...)` 替换为 `FoliaCompat.runTaskGlobal(...)`
  - [x] SubTask 6.4: `AdminUserBanHandler.java` — 将 `Bukkit.getScheduler().runTask(...)` 替换为 `FoliaCompat.runTaskGlobal(...)`

- [x] Task 7: 编译验证
  - [x] SubTask 7.1: 运行 Maven 编译，确保所有修改无编译错误

# Task Dependencies
- Task 1 是所有后续任务的前置依赖
- Task 2-6 相互独立，可并行执行
- Task 7 依赖所有前置任务完成
