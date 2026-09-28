# 持续集成说明

## 当前状态

仓库已经包含 GitHub Actions 配置，但目前只存在本地，没有上传到 GitHub，因此不会产生任何远程运行记录。

工作流文件：仓库根目录 `.github/workflows/ci.yml`

## 自动检查

代码推送或创建 Pull Request 后，CI 会在 Ubuntu 环境执行：

1. 使用 Java 21 和 Maven 依赖缓存。
2. 检查是否跟踪了真实配置、会话文件、数据库、用户上传文件等敏感数据。
3. 扫描常见 API Key、云密钥、GitHub Token 和私钥文本特征。
4. 执行 61 个后端回归测试，其中包含 CI 工作流契约测试。
5. 单独执行 8 个 Agent Eval 案例。
6. 使用 Node 22 执行管理端 `npm ci` 和生产构建。

工作流只有 `contents: read` 权限，不需要项目 API Key，也不会连接真实 MySQL、Redis、微信或模型服务。

## 本地检查

Git Bash、WSL 或 macOS/Linux：

```bash
bash scripts/ci/check-sensitive-files.sh
```

后端测试：

```bash
bash mvnw --batch-mode --no-transfer-progress clean \
  -Dtest=HealthToolPolicyTest,ToolRegistryRiskGuardTest,WeChatBotServiceBoundaryTest,AdminBoundaryTest,GithubWorkflowContractTest,HealthActionAuditServiceTest,HealthActionCoordinatorTest,PendingHealthActionStoreRedisTest,HealthRecordStoreTest,MySqlHealthRecordRepositoryTest,SchemaContractTest,AdminAccountBootstrapTest,HealthAgentStartupValidatorTest,HealthManagementServiceTest,RagPipelineUserIsolationTest,RagServiceTest,ReminderConfigServiceTest \
  test

bash mvnw --batch-mode --no-transfer-progress \
  -Dtest=HealthAgentEvalTest \
  test
```

## 上传前安全结论

当前索引已经取消跟踪 `data/videos.db`，本地数据库文件保持原样，并由 `.gitignore` 阻止再次加入。当前版本的敏感文件检查可以通过。

但是旧仓库的 187 个提交和多个历史分支中出现过：

- 真实 `application.yaml`；
- 微信登录会话文件；
- 用户上传的图片和文档；
- 健康提醒数据和 SQLite 数据库。

因此不能把这份仓库的完整历史和全部分支直接推到公开 GitHub。推荐从当前净化后的工作树建立一个全新仓库，只提交面试项目需要的源码、测试、SQL 和文档；旧 Gitee 仓库继续留作本地或私有历史参考。

## 前端说明

`admin` 已与 `backend` 合并到同一个干净仓库。CI 使用 Node 22 执行 Vue SFC 检查和 Vite 生产构建。本机 Windows 沙箱可能因 `esbuild spawn EPERM` 无法完成生产构建，因此以 GitHub Ubuntu Runner 的构建结果作为最终验证。
