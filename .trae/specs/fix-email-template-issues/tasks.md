# Tasks

- [x] Task 1: 添加 HTML 转义工具方法
  - [x] SubTask 1.1: 在 `MailService` 中添加 `escapeHtml(String input)` 私有方法，对 `&`, `<`, `>`, `"`, `'` 进行转义
  - [x] SubTask 1.2: 在 `sendReviewResultNotification()` 中对 `{username}`, `{reason}`, `{server_name}` 的值进行转义后再替换
  - [x] SubTask 1.3: 在 `sendVerificationCode()` 中对 `{code}`, `{server_name}` 的值进行转义后再替换

- [x] Task 2: 支持动态验证码过期时间
  - [x] SubTask 2.1: 在 `verify_code_en.html` 和 `verify_code_zh.html` 中将硬编码的 "5 minutes" / "5 分钟" 替换为 `{expire_minutes}` 占位符
  - [x] SubTask 2.2: 在 `getDefaultVerifyCodeTemplate()` 硬编码 fallback 中同样替换为 `{expire_minutes}`
  - [x] SubTask 2.3: 在 `sendVerificationCode()` 中从配置读取 `captcha.expire_seconds`，计算分钟数，替换 `{expire_minutes}` 占位符

- [x] Task 3: 验证码模板添加 `{server_name}` 支持
  - [x] SubTask 3.1: 在 `verify_code_en.html` 和 `verify_code_zh.html` 中适当位置添加 `{server_name}` 显示
  - [x] SubTask 3.2: 在 `getDefaultVerifyCodeTemplate()` 硬编码 fallback 中添加 `{server_name}`
  - [x] SubTask 3.3: 在 `sendVerificationCode()` 中添加 `{server_name}` 的替换逻辑

- [x] Task 4: 修复 `loadEmailTemplate()` 异常处理和语言回退
  - [x] SubTask 4.1: 将 `loadEmailTemplate()` 内部 IOException 捕获，降级到 JAR 资源或 fallback，而非向上抛出
  - [x] SubTask 4.2: 在 `loadEmailTemplate()` 中添加语言回退逻辑：当请求语言的模板不存在时，先尝试英文模板文件，再使用硬编码 fallback
  - [x] SubTask 4.3: 修改方法签名，去掉 `throws IOException`

- [x] Task 5: 删除 `ResourceManager.getEmailTemplate()` 死代码
  - [x] SubTask 5.1: 确认 `getEmailTemplate()` 无任何调用方
  - [x] SubTask 5.2: 删除 `ResourceManager.getEmailTemplate()` 方法及其相关 `readFileAsString()` 辅助方法

- [x] Task 6: 精简 `MailService` 冗余公共 API
  - [x] SubTask 6.1: 将 `sendCode(String, String, String)`、`sendVerifyCode(String, String, String)`、`sendVerifyCode(String, String, String, String)` 标记为 `@Deprecated`
  - [x] SubTask 6.2: 将 `sendReviewResult(String, String, boolean, String)` 标记为 `@Deprecated`
  - [x] SubTask 6.3: 调用方已使用 `sendReviewResult(5参数)` 和 `sendVerificationCode` 核心方法，无需额外迁移

# Task Dependencies
- Task 1 → Task 6 (转义方法需先存在，API 精简时需使用)
- Task 2 和 Task 3 可并行
- Task 4 独立于 Task 1/2/3
- Task 5 独立于其他任务
- Task 6 依赖 Task 1（需要先有转义逻辑）
