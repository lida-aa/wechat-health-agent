# WeChat Health Agent

一个面向微信渠道的健康管理 Agent Demo。项目重点不是“接入了多少模型 API”，而是展示如何把大模型接入真实业务：可信身份、工具权限、人工确认、数据持久化、失败恢复、隐私保护和 Agent Eval。

> 本项目用于健康信息整理和工程演示，不提供医疗诊断，不能替代医生、药师或紧急医疗服务。

## 项目亮点

- 微信文字、语音转文字和健康图片入口。
- 健康档案、药物、生命体征、报告、饮食和健康计划管理。
- 写操作统一走 Human-in-the-loop：草稿、确认、修改、取消、执行。
- 工具白名单与风险分级，非健康工具无法注册或调用。
- `userId` 从微信会话注入，模型不能指定或伪造用户身份。
- MySQL 保存健康档案和提醒任务，Redis 恢复待确认操作与会话状态。
- 提醒任务支持原子抢占、每日去重、失败重试和 DEAD 状态。
- 健康知识库支持用户隔离、事务入库、文件类型和大小限制。
- 日志、Trace 和审计数据采用最小化记录策略。
- 目前包含 69 个目标回归测试，其中有 8 个 Agent Eval 场景。

## 核心流程

```text
微信消息
  → WeChatBotService（渠道身份、消息类型、短期图片缓存）
  → CommandRouter（Prompt、RAG、模型调用循环）
  → HealthToolPolicy / ToolRegistry（白名单、风险、身份注入）
  → HealthActionCoordinator（HITL 草稿和状态机）
  → 健康领域服务
  → MySQL / Redis / 审计日志
```

敏感操作不会因为模型发起 Tool Call 就直接写库：

```text
模型提出操作 → 创建草稿 → 用户确认/修改/取消 → 原子领取 → 执行 → 审计
```

详细设计见 [架构说明](docs/ARCHITECTURE.md)。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4、MyBatis |
| Agent | LangChain4j、DashScope/OpenAI 兼容接口 |
| 微信渠道 | wechat-ilink-sdk |
| 数据 | MySQL、Redis |
| 管理端 | Vue 3、Vite |
| 测试 | JUnit 5、Mockito、AssertJ、数据驱动 Agent Eval |

## 目录

```text
backend/
├─ src/main/java/com/Myself/demo/
│  ├─ bot/                 # Agent 路由、工具策略、微信入口
│  ├─ health/              # HITL 与健康领域服务
│  ├─ repository/          # 健康档案持久化
│  ├─ controller/          # 管理后台和健康知识库接口
│  └─ security/            # 管理员与渠道安全边界
├─ src/main/resources/
│  ├─ mapper/              # MyBatis 映射
│  └─ schema.sql           # 新环境的权威数据库结构
├─ src/test/               # 单元测试、边界测试和 Agent Eval
├─ sql/                    # 现有数据库的手动迁移与可选清理脚本
└─ docs/                   # 架构、功能、Eval 和演示文档

../admin/                  # Vue 管理后台
```

## 本地启动

### 1. 环境要求

- JDK 21
- MySQL 8.x
- Redis 6+（不可用时部分状态退化为进程内存）
- Node.js 18+
- DashScope API Key

### 2. 初始化数据库

新数据库执行：

```text
src/main/resources/schema.sql
```

已有旧版 Demo 数据库按顺序执行 `sql/migrations`。详细说明见 [sql/README.md](sql/README.md)。迁移前必须备份；`sql/optional` 下的删除脚本不会自动执行。

### 3. 配置

复制模板：

```powershell
Copy-Item src/main/resources/application.example.yaml src/main/resources/application.yaml
```

至少设置以下环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
DASHSCOPE_API_KEY
HEALTH_ADMIN_USERNAME
HEALTH_ADMIN_PASSWORD
HEALTH_AGENT_JWT_SECRET
```

`HEALTH_ADMIN_PASSWORD` 至少 12 位，`HEALTH_AGENT_JWT_SECRET` 配置后至少 32 位。不要把真实密钥提交到 Git。

应用启动时会校验模型 Key、模型名称和模型服务地址。管理员用户名与密码必须同时配置；JWT Secret 一旦配置，长度不能少于 32 位。开发环境未配置 JWT Secret 时会使用进程内随机密钥，重启后旧令牌失效。

### 4. 启动后端

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux：

```bash
./mvnw spring-boot:run
```

首次微信连接按终端提示完成登录。会话凭证保存在被 Git 忽略的本地文件中。

### 5. 启动管理端

```bash
cd ../admin
npm install
npm run dev
```

管理端只允许管理员查看用户会话和管理健康知识库，不提供任意 `userId` 登录入口。

## 测试

核心回归覆盖：

- 健康工具白名单和风险分级
- 渠道用户身份注入
- HITL 状态迁移、幂等和 Redis 恢复
- 健康档案并发写入与旧数据导入
- 提醒抢占、重试和去重
- 管理员权限与 BCrypt 登录
- RAG 用户隔离和 Schema 契约
- Agent 行为、安全拒绝和 Prompt Injection

Agent Eval 说明见 [docs/AGENT_EVALS.md](docs/AGENT_EVALS.md)。

仓库内已提供 GitHub Actions：在 Java 21 环境执行 61 个后端回归测试和 8 个 Agent Eval，并阻止真实配置、运行数据库、用户上传文件和常见明文密钥进入远程仓库。当前工作流仅保存在本地，说明及上传前阻塞项见 [docs/CI.md](docs/CI.md)。

## 演示建议

推荐依次展示：

1. 查询健康概览，只读工具直接执行。
2. 添加药物或记录血压，展示 HITL 确认。
3. 重复发送“确认”，展示幂等保护。
4. 发送胸痛等高风险症状，展示安全分流。
5. 要求生成二维码或规划旅游，展示能力边界。
6. 管理后台展示会话、健康知识库和用户隔离。

完整脚本见 [面试演示指南](docs/DEMO_GUIDE.md)。

## 当前边界

- 这是实习项目 Demo，不是经过医疗器械认证的产品。
- 尚未完成真实微信、MySQL、Redis、模型服务组成的自动化端到端测试。
- 图片 OCR 和模型输出仍需用户核对，健康写操作必须确认。
- 生产部署还需要 HTTPS、密钥托管、限流、监控和备份方案。
- 管理员密码初始化由环境变量控制；未配置固定 JWT Secret 时，重启会使旧令牌失效。
