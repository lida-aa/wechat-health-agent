# 健康 Agent 回归评测

本项目使用离线、确定性的场景评测保护 Agent 主链路，不依赖真实模型 API，也不会产生调用费用。

## 当前覆盖

- 紧急症状必须提示 `120` 和急诊，不能输出确定性诊断。
- 血压、用药、体检报告等写操作必须属于 `CONFIRMATION_REQUIRED`。
- 写操作回复必须明确包含“草稿”“尚未保存”和“确认”。
- 健康档案查询必须保持只读。
- 越域 Prompt Injection 不能触发旅游等已下线工具。
- 短信和二维码请求不能触发旧版 `ToolExecutionContext` 外部动作。
- 路由器始终使用微信会话中的用户身份执行工具。
- Agent Trace 不保存健康参数和工具结果原文，只保存脱敏后的字符数元数据。

场景数据位于：

```text
src/test/resources/evals/health-agent-cases.json
```

执行评测：

```bash
./mvnw -Dtest=HealthAgentEvalTest test
```

Windows：

```powershell
.\mvnw.cmd '-Dtest=HealthAgentEvalTest' test
```

## 如何新增场景

在 JSON 数组中增加一项，声明：

- `input`：用户输入；
- `toolName` / `toolArgs`：期望的工具调用；
- `expectedRisk`：期望风险等级；
- `confirmationRequired`：是否必须 HITL；
- `requiredTerms`：最终回复必须包含的词；
- `forbiddenTerms`：最终回复禁止出现的词。

当前评测验证的是路由、工具权限、HITL 契约、身份传递和隐私 Trace。它不代表真实模型在所有措辞下都能稳定选择正确工具。后续可以复用 `AgentModelClient` 接口增加真实模型评测任务，并将结果与这套固定基线比较。
