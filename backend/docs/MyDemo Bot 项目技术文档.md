# WeChat Health Agent 技术文档

> 文件名为兼容旧链接而保留；内容已更新为健康 Agent 当前架构。

## 1. 技术栈

| 层级 | 技术 |
| --- | --- |
| 运行时 | Java 21、Spring Boot 4 |
| Agent | LangChain4j、OpenAI 兼容模型接口 |
| 微信接入 | wechat-ilink-sdk-java |
| 数据访问 | MyBatis、JdbcTemplate、MySQL 8 |
| 短期状态 | Redis，故障时有限度降级到内存 |
| 管理端 | Vue 3、Vite |
| 测试 | JUnit 5、Mockito、AssertJ、数据驱动 Eval |

## 2. 请求链路

```text
微信用户
  → WeChatBotService：识别消息类型、取得可信 userId
  → CommandRouter：组合上下文、RAG、模型 Tool Call 循环
  → ToolRegistry：白名单检查、参数绑定、身份注入
  → 健康只读服务，或 HealthActionCoordinator 创建写操作草稿
  → 用户确认后由对应 HealthActionHandler 执行
  → HealthRecordRepository / HealthReminderRepository
```

关键原则是：模型输出只是一项提议，不能直接代表授权或持久化结果。

## 3. 主要模块

| 模块 | 关键类 | 职责 |
| --- | --- | --- |
| 微信渠道 | `WeChatBotService`、`SessionManager` | 接收文字、转写后的语音和健康图片，维护渠道会话 |
| Agent 编排 | `CommandRouter`、`AgentModelClient` | 调用模型、执行受限 Tool Call 循环、拼接 RAG 上下文 |
| 工具边界 | `ToolRegistry`、`HealthToolPolicy` | 工具注册、白名单、风险等级、`userId` 注入 |
| HITL | `HealthActionCoordinator`、`HealthActionDraftService` | 草稿、修改、取消、确认、过期和幂等控制 |
| 写操作分发 | `HealthActionHandlerRegistry` 与各 Handler | 按草稿类型执行健康业务写入 |
| 健康领域 | `health/service` | 档案、用药、指标、报告、饮食和健康计划 |
| 数据层 | `HealthRecordRepository`、`HealthReminderRepository` | MySQL 持久化与并发控制 |
| 提醒 | `ReminderScheduler`、`ReminderConfigService` | 到期扫描、抢占、发送、重试与去重 |
| RAG | `RagPipelineService`、`RagService` | 健康知识检索、用户隔离和缓存 |
| 管理边界 | `AdminAccessService`、Admin Controllers | 管理员登录、会话查看和知识库管理 |

## 4. 身份与工具权限

### 4.1 可信身份

工具方法通过 `@CurrentUserId` 声明内部身份参数。生成给模型的 Tool Schema 时该参数被隐藏，执行时由 `ToolRegistry` 使用当前微信会话身份注入。因此模型不能通过构造 `userId` 访问其他用户数据。

### 4.2 风险等级

`HealthToolPolicy` 将工具分为：

- `READ_ONLY`：可以直接执行查询或安全初筛。
- `CONFIRMATION_REQUIRED`：只允许创建待确认草稿。
- 未登记：拒绝注册和执行。

新增写工具时，测试会检查它既在策略白名单中，又带有 `@CreatesHealthActionDraft`。

## 5. HITL 状态与幂等

待确认操作的主要状态是：

```text
WAITING_CONFIRMATION → EXECUTING → COMPLETED
                    └────────────→ FAILED
WAITING_CONFIRMATION → CANCELLED / EXPIRED
```

确认不是简单读取后执行，而是先做原子领取。只有成功把状态从等待确认迁移到执行中的请求才能写数据，重复“确认”不会再次执行。草稿设置 TTL，并可通过 Redis 在应用重启后恢复。

## 6. 持久化设计

### 6.1 健康档案

`health_record` 按 `user_id` 保存 JSON 聚合文档，并使用 `version` 进行更新控制。所有健康领域服务通过 `HealthRecordStore` 和仓储接口访问数据。

### 6.2 健康提醒

`health_reminder` 保存提醒时间、状态、下次重试时间、锁信息和最近触发日期。调度器使用条件更新抢占任务；成功后记录当日已触发，失败则退避重试，超过上限进入 `DEAD`。

### 6.3 数据库版本

- 新环境以 `src/main/resources/schema.sql` 为权威结构。
- 已有环境按 `sql/migrations` 顺序手动升级。
- `sql/optional` 中的旧表清理脚本不会自动执行。

## 7. RAG 与管理端

健康知识库只允许管理员维护。上传接口限制类型和大小，并以事务完成文档替换。检索时 SQL 包含用户过滤，缓存键也包含 `userId`，避免数据库和缓存层面的跨用户泄漏。

管理端密码使用 BCrypt；JWT Secret 从环境变量或系统属性读取。若开发环境未配置固定 Secret，会使用进程内随机值，因此重启后旧 Token 失效。

## 8. 隐私和医疗安全

- 微信消息日志只记录长度等元数据。
- 图片使用临时文件，处理结束后删除。
- Agent Trace 对工具参数和返回结果做最小化记录。
- 审计日志不保存完整健康正文。
- 症状和报告结果必须包含非诊断声明与就医建议。
- 项目不能替代医生、药师或紧急医疗服务。

## 9. 测试策略

| 测试层 | 重点 |
| --- | --- |
| 单元测试 | 工具策略、HITL、仓储、提醒、管理员初始化 |
| 边界测试 | 微信入口、管理员接口、模型不可伪造身份 |
| Schema 契约 | Java/SQL 字段和关键表一致 |
| Agent Eval | 工具选择、拒绝越权、医疗安全、Prompt Injection |

当前目标回归为 69 个测试，其中 Agent Eval 包含 8 个固定案例。真实微信、MySQL、Redis 和模型服务组成的自动化端到端测试仍是后续工作。

## 10. 当前边界与下一步

当前实现适合作为实习项目 Demo，不是生产医疗系统。后续优先级建议：

1. 增加 Testcontainers 集成测试，覆盖 MySQL 与 Redis 的真实事务和并发。
2. 用 WireMock 或 Mock Server 验证模型、微信和第三方 API 失败路径。
3. 加入 CI、配置校验、依赖安全扫描和可发布的演示环境。
4. 增加带引用的 RAG 回答和知识文档 Prompt Injection 扫描。

更详细的组件图见 [架构说明](ARCHITECTURE.md)，演示流程见 [面试演示指南](DEMO_GUIDE.md)。
