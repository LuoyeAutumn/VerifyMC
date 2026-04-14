# 邮件模板问题修复 Spec

## Why
邮件模板系统存在多个问题：XSS/HTML 注入漏洞、硬编码过期时间与配置不一致、死代码、模板加载逻辑重复且不统一、缺少语言回退机制等。这些问题影响安全性、可维护性和用户体验。

## What Changes
- 修复模板变量替换时的 XSS/HTML 注入漏洞，对所有动态内容进行 HTML 转义
- 将验证码过期时间从硬编码改为动态读取配置，并在模板中使用 `{expire_minutes}` 占位符
- 删除 `ResourceManager.getEmailTemplate()` 死代码
- 统一模板加载逻辑，消除 `MailService` 与 `ResourceManager` 之间的重复
- 修复 `loadEmailTemplate()` 的异常处理，确保 IOException 不会阻止使用 fallback 发送邮件
- 为 `loadEmailTemplate()` 添加语言回退机制（非 en/zh 语言回退到 en 模板文件）
- 在验证码模板中添加 `{server_name}` 占位符支持
- 清理 `MailService` 中冗余的公共 API 方法

## Impact
- Affected specs: 邮件发送功能、模板系统
- Affected code:
  - `MailService.java` — 核心修改文件
  - `ResourceManager.java` — 删除死代码
  - `verify_code_en.html` / `verify_code_zh.html` — 添加 `{expire_minutes}` 和 `{server_name}` 占位符
  - `review_approved_*.html` / `review_rejected_*.html` — 确认占位符一致性

---

## ADDED Requirements

### Requirement: HTML 内容转义
系统 SHALL 对所有注入到邮件模板中的动态变量进行 HTML 转义，防止 XSS/HTML 注入攻击。

#### Scenario: 用户名包含 HTML 标签
- **WHEN** 管理员拒绝用户名为 `<script>alert('xss')</script>` 的申请
- **THEN** 邮件内容中 `{username}` 被替换为 `&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;`，而非原始 HTML

#### Scenario: 拒绝原因包含 HTML
- **WHEN** 拒绝原因为 `<img src=x onerror=alert(1)>`
- **THEN** 邮件内容中 `{reason}` 被替换为转义后的安全文本

### Requirement: 动态验证码过期时间
系统 SHALL 从配置中读取 `captcha.expire_seconds` 并将过期时间动态注入邮件模板，而非硬编码 "5 分钟"。

#### Scenario: 配置了非默认过期时间
- **WHEN** 管理员将 `captcha.expire_seconds` 设为 600（10 分钟）
- **THEN** 邮件模板中的过期提示显示 "10 分钟" 而非 "5 分钟"

### Requirement: 验证码模板支持服务器名称
系统 SHALL 在验证码邮件模板中支持 `{server_name}` 占位符，让用户知道验证码来自哪个服务器。

#### Scenario: 验证码邮件包含服务器名称
- **WHEN** 用户收到验证码邮件
- **THEN** 邮件中显示服务器名称（来自 `web_server_prefix` 配置）

### Requirement: 模板加载语言回退
系统 SHALL 在请求的语言模板不存在时，先回退到英文模板文件，再回退到硬编码 fallback。

#### Scenario: 请求日语模板但不存在
- **WHEN** 用户语言为 `ja`，且 `verify_code_ja.html` 不存在
- **THEN** 系统先尝试加载 `verify_code_en.html`，若仍不存在才使用硬编码 fallback

### Requirement: 模板加载异常容错
系统 SHALL 在模板文件读取失败时自动降级到 fallback 内容，而非阻止邮件发送。

#### Scenario: 文件系统模板损坏
- **WHEN** 自定义模板文件存在但读取时抛出 IOException
- **THEN** 系统降级使用 JAR 内置模板或硬编码 fallback 继续发送邮件

---

## MODIFIED Requirements

### Requirement: MailService 公共 API 精简
`MailService` 的公共发送方法 SHALL 精简为以下核心方法：
- `sendVerificationCode(String to, String code, String language)` — 发送验证码
- `sendReviewResult(String email, String username, boolean approved, String reason, String language)` — 发送审核结果

其他冗余方法（`sendCode`、`sendVerifyCode` 的各种重载）SHALL 被移除或标记为 `@Deprecated`，调用方 SHALL 迁移到核心方法。

### Requirement: 模板加载逻辑统一
模板加载 SHALL 仅由 `MailService.loadEmailTemplate()` 负责，`ResourceManager.getEmailTemplate()` SHALL 被移除，消除重复逻辑。

---

## REMOVED Requirements

### Requirement: ResourceManager.getEmailTemplate() 方法
**Reason**: 该方法从未被调用（死代码），且功能不如 `MailService.loadEmailTemplate()` 完整（不支持 JAR 资源加载和语言回退）
**Migration**: 所有模板加载统一通过 `MailService.loadEmailTemplate()` 进行
