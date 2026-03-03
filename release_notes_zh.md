[English](https://github.com/KiteMC/VerifyMC/releases/tag/v1.7.0) | 简体中文 | [官方文档](https://kitemc.com/docs/verifymc/)

# VerifyMC v1.7.0 更新日志

## 架构：模块化重构

- 引入 `PluginContext` 作为中央服务容器，替代 `VerifyMC` 中散落的字段和 `WebServer` 的 13 参数构造器
- 提取 `ConfigManager`、`I18nManager`、`OpsManager`、`ResourceManager` 到 `core/` 包，职责分离
- 拆分巨型 `WebServer`（~1800 行）为 20+ 个单一职责 `HttpHandler`，位于 `web/handler/`
- 新增 `ApiRouter` 集中路由注册
- 新增 `VmcCommandExecutor` 游戏内管理命令（`/vmc approve/reject/ban/unban/delete/list/info`）
- 新增 `PlayerLoginListener`，支持插件模式白名单拦截和基于状态的踢出消息
- 统一 admin handler 认证，通过 `AdminAuthUtil` 工具类

## 标识符迁移：UUID → 用户名

- 移除 UUID 作为主要用户标识，用户名成为所有层的唯一键
- 更新 `UserDao`、`FileUserDao`、`MysqlUserDao` 使用基于用户名的查找和操作
- `UserDao` 接口新增 `getUserByEmail`、`getUserByUsernameExact` 等方法
- 新增便捷 default 方法：`banUser`、`unbanUser`、`getUsers`、`getTotalUsers` 等

## 管理员登录重构

- 移除静态 `admin.password` 配置，改为基于玩家数据的认证
- 管理员登录现在验证已注册玩家的凭据，仅服务器 OP 可访问管理面板
- 登录支持用户名和邮箱查找，集成 AuthMe 密码验证

## 安全增强

- 引入 `PasswordUtil`，SHA-256 + 盐值哈希（兼容 AuthMe `$SHA$` 格式）
- 明文密码回退现在会记录警告日志以推动迁移
- 统一 admin 端点认证，通过 `AdminAuthUtil`
- 添加查询参数 URL 解码防止编码字符注入
- Proxy 异常处理从 fail-open 改为 fail-close（拒绝登录），修复安全漏洞

## API 端点重构

- RESTful API 路径：`/api/admin/users`、`/api/admin/user/approve`、`/api/admin/user/reject` 等
- 问卷端点迁移到 `/api/questionnaire/config` 和 `/api/questionnaire/submit`
- 新增 `/api/admin/sync` 端点用于 AuthMe 数据同步
- 新增 `/api/user/status` 端点用于用户状态查询

## 前端改进

- 优化前端对话框系统：统一所有弹窗使用共享 `Dialog` 组件
- 重构用户管理：拆分用户列表和待审核为独立组件
- 改进无障碍访问：添加 `aria-labelledby`、`role="dialog"` 和焦点管理
- 修复 z-index 问题：提升对话框层级到 z-60 防止 UI 重叠
- 管理员登录表单更新为用户名 + 密码字段
- API 服务更新匹配新 RESTful 端点路径

## 依赖更新

- MySQL 驱动从 `mysql-connector-java` 更新为 `mysql-connector-j`
- 移除重复的 `jakarta.mail-api` 依赖
- Maven Shade Plugin 更新至 3.5.0

## Bug 修复

- 修复 AuthMe 同步时跳过封禁用户的状态升级问题
- 修复问卷结果中评分服务不可用的检测
- 修复 `WebResponseHelper.readJson` 对格式错误 JSON 的优雅处理
