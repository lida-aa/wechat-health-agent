# WeChat Health Agent

面向微信渠道的健康管理 Agent 实习项目。项目重点是将大模型安全地接入真实业务流程：可信用户身份、工具权限、Human-in-the-loop、持久化、失败恢复、隐私保护和 Agent Eval。

> 本项目用于健康信息整理与工程演示，不提供医疗诊断，不能替代医生、药师或紧急医疗服务。

## 项目结构

```text
wechat-health-agent/
├─ backend/                 # Java 21 + Spring Boot + LangChain4j
├─ admin/                   # Vue 3 管理后台
└─ .github/workflows/       # 后端、Agent Eval、前端构建与安全检查
```

## 核心亮点

- 微信会话提供可信 `userId`，模型不能指定或伪造用户身份。
- 健康工具使用白名单和风险分级，写操作必须由用户确认。
- HITL 支持草稿、修改、取消、过期、原子领取、幂等和 Redis 恢复。
- MySQL 保存健康档案和可靠提醒，支持并发控制、去重、重试和失败状态。
- RAG 知识库具有管理员权限、事务入库和用户隔离。
- 日志、Trace 和审计数据采用隐私最小化策略。
- 61 个后端回归测试与 8 个 Agent Eval 场景。

## 本地运行

后端配置、数据库和启动方式见 [backend/README.md](backend/README.md)。

前端：

```bash
cd admin
npm ci
npm run dev
```

CI 与上传前安全要求见 [backend/docs/CI.md](backend/docs/CI.md)。

## 当前边界

- 尚未完成真实微信、MySQL、Redis 和模型服务组成的自动化端到端测试。
- 当前不是经过认证的医疗产品。
- 真实密钥、微信会话、数据库和用户上传内容禁止进入 Git。
- 本目录是从旧项目净化得到的新工作树，不包含旧仓库历史，当前尚未提交或上传。
