# MateClaw DataAgent Python SDK

用于 AI 测评的 Python 客户端 SDK，支持通过 HTTP 调用 MateClaw DataAgent 的 REST API。

## 功能特性

- ✅ JWT 认证登录
- ✅ 同步/流式对话（SSE）
- ✅ 大模型直连（无持久化，类似 HTTP 直连大模型）
- ✅ 会话管理（列表、删除、重命名）
- ✅ Agent 管理
- ✅ 结构化 SSE 事件解析
- ✅ AI 测评框架
- ✅ 测评结果验证
- ✅ 测评报告导出

## 安装

```bash
# 克隆或复制此目录
cd mateclaw-dataagent-client-python

# 安装依赖
pip install requests

# 或安装为包
pip install -e .
```

## 快速开始

### 1. 基础对话

```python
from mateclaw_dataagent import DataAgentClient

# 初始化客户端
client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1
)

# 登录
client.login(username="admin", password="admin123")

# 获取 Agent 列表
agents = client.list_agents()
print(f"可用 Agent: {[a.name for a in agents]}")

# 同步对话
response = client.chat(
    agent_id=agents[0].id,
    message="请分析销售数据",
    conversation_id="test-001"
)
print(f"回答：{response.content}")
print(f"Token 使用：{response.prompt_tokens} + {response.completion_tokens}")

# 关闭连接
client.close()
```

### 2. 流式对话

```python
from mateclaw_dataagent import DataAgentClient

client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1
)
client.login(username="admin", password="admin123")

# 流式对话，处理 SSE 事件（默认使用名称为「数据分析助手」的 Agent）
print("AI 回答：", end="", flush=True)
agent = client.get_agent_by_name("数据分析助手")
for event in client.stream_chat(
    agent_id=agent.id,
    message="请分析销售数据",
    conversation_id="test-002"
):
    if event.event == "content_delta":
        print(event.delta, end="", flush=True)
    elif event.event == "thinking_delta":
        print(f"[思考]{event.delta}", end="", flush=True)
    elif event.event == "done":
        print(f"\n\n完成！Token: {event.data.get('promptTokens', 0)} + {event.data.get('completionTokens', 0)}")

client.close()
```

### 2b. 大模型直连（无持久化）

不需要 Agent、不产生任何会话/消息记录，直接调用大模型，等价于通过 HTTP 直连大模型 API：

```python
from mateclaw_dataagent import DataAgentClient, LlmChatMessage

client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1
)
client.login(username="admin", password="admin123")

# 同步直连：messages 元素可为 dict 或 LlmChatMessage
response = client.llm_chat(
    messages=[
        {"role": "system", "content": "你是一个数据分析专家"},
        LlmChatMessage(role="user", content="你好，请简单介绍一下你自己"),
    ],
    # provider="dashscope",   # 可选
    # model="qwen-max",       # 可选，缺省使用默认模型
    temperature=0.7,
)
print(f"回答：{response.content}")
print(f"模型：{response.model}（{response.provider}）")
print(f"Token：{response.prompt_tokens} + {response.completion_tokens}")

# 流式直连
print("流式回答：", end="", flush=True)
for event in client.llm_chat_stream(
    messages=[{"role": "user", "content": "讲个冷笑话"}],
):
    if event.event == "content_delta":
        print(event.delta, end="", flush=True)
    elif event.event == "done":
        print("\n[完成]")
print()

client.close()
```

### 消息 role 角色说明

`messages` 中的每条消息由 `role` + `content` 构成，按数组顺序拼接为完整上下文。三种角色的作用：

| role | 作用 | 说明 |
|------|------|------|
| `system` | 系统提示 | 设定模型身份、行为规范、输出格式与全局约束，**放在最前面，优先级最高**。 |
| `user` | 用户输入 | 调用方/终端用户本轮要模型回答的内容。 |
| `assistant` | 助手历史回复 | 携带多轮对话上下文（前一问一答），让模型在上文基础上继续；也可注入示范回答（few-shot）引导风格。 |

**样例：system 设定身份与约束**

```python
client.llm_chat(messages=[
    {"role": "system", "content": "你是一个严谨的财务分析师。回答必须基于数据推理，禁止编造数字。"},
    {"role": "user", "content": "如何评估一家公司的短期偿债能力？"},
])
```

**样例：assistant 携带多轮上下文（多轮对话）**

```python
client.llm_chat(messages=[
    {"role": "system", "content": "你是一个只讲冷笑话的助手。"},
    {"role": "user", "content": "给我讲一个关于程序员的笑话。"},
    {"role": "assistant", "content": "程序员最讨厌的两件事：一是别人不写注释，二是让自己写注释。"},
    {"role": "user", "content": "再讲一个关于产品经理的笑话。"},   # 本轮提问，模型结合上文回答
])
```

> 注意：`assistant` 角色不带记忆——直连接口无持久化，每次调用必须**自己把需要的上下文放进 `messages`**；省略历史轮次则模型只基于本轮输入回答。role 值非 `system/user/assistant` 时按 `user` 处理。

### 3. AI 测评

```python
from mateclaw_dataagent import DataAgentClient, EvaluationRunner

# 初始化客户端
client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1
)
client.login(username="admin", password="admin123")

# 准备测评数据
test_cases = [
    {"question": "2024 年 Q1 的销售额是多少？", "expected_type": "kpi"},
    {"question": "各产品线的利润对比", "expected_type": "chart"},
    {"question": "分析用户增长趋势", "expected_type": "analysis"},
]

# 运行测评（默认使用名称为「数据分析助手」的 Agent）
runner = EvaluationRunner(client)
agent = client.get_agent_by_name("数据分析助手")
results = runner.run_evaluation(
    agent_id=agent.id,
    test_cases=test_cases,
    output_file="evaluation_results.json",
    use_stream=True  # 使用流式对话
)

# 输出测评报告
print(f"总计：{results.total} 个用例")
print(f"成功：{results.success} 个")
print(f"失败：{results.failed} 个")
print(f"成功率：{results.success_rate:.2%}")
print(f"平均延迟：{results.avg_latency_ms:.0f} ms")

client.close()
```

## 示例脚本

项目提供了多个示例脚本：

| 脚本 | 说明 |
|------|------|
| `examples/quickstart.py` | 快速入门（5 分钟上手） |
| `examples/chat_demo.py` | 对话功能演示 |
| `examples/llm_direct_demo.py` | 大模型直连（无持久化）演示 |
| `examples/evaluation_demo.py` | AI 测评演示 |
| `examples/verify_evaluation.py` | 测评结果验证 |

运行示例：

```bash
# 快速入门
python examples/quickstart.py

# AI 测评
python examples/evaluation_demo.py

# 大模型直连（无持久化）
python examples/llm_direct_demo.py

# 测评验证（默认使用「数据分析助手」Agent）
python examples/verify_evaluation.py --cases examples/test_cases.json
```

## API 参考

### DataAgentClient 主要方法

#### 构造方法

```python
client = DataAgentClient(
    base_url: str,        # API 基础路径，如 "http://localhost:18089/dataagent/api"
    workspace_id: int,    # 工作区 ID
    timeout: int = 60,    # 请求超时时间（秒）
)
```

#### 认证
```python
client.login(username, password)        # 用户登录，成功后自动保存 JWT
client.get_current_user()               # 获取当前用户信息（未登录返回 None）
client.logout()                         # 退出登录（清除本地 token 与已存凭据）
```

客户端会保存 `login()` 传入的登录凭据。当请求返回 401（token 过期，默认有效期 24 小时）时，会自动重新登录并**重试一次**；登录接口本身以及未保存凭据的请求不会触发自动重登。

#### 对话
```python
client.chat(agent_id, message, conversation_id, ...)           # 同步对话（返回纯文本）
client.stream_chat(agent_id, message, conversation_id, ...)    # 流式对话（SSE，生成器）
client.stop_stream(conversation_id)                             # 停止流式生成
```

> `stream_chat` 额外支持 `reconnect`（断线重连）与 `last_event_id`（最后收到的事件 ID）参数，用于中断后恢复流式输出。

#### 大模型直连（无持久化）
```python
client.llm_chat(messages, provider=None, model=None, temperature=None, max_tokens=None)
    # 同步直连：返回 LlmChatResponse；不做任何持久化，类似 HTTP 直连大模型。
    # messages 元素可为 dict（{"role","content"}）或 LlmChatMessage。

client.llm_chat_stream(messages, provider=None, model=None, temperature=None, max_tokens=None)
    # 流式直连：SSE 生成器，产出 SseEvent（content_delta / done / error）。
```

> 大模型直连接口不经过 Agent / 会话体系，服务端不创建会话、不保存消息、不写库；`provider` / `model` 缺省时使用默认模型。

#### 会话管理
```python
client.list_conversations()              # 获取会话列表
client.get_messages(conversation_id)     # 获取会话消息
client.delete_conversation(conversation_id)  # 删除会话
client.rename_conversation(conversation_id, title)  # 重命名会话
```

#### Agent 管理
```python
client.list_agents(enabled=None)         # 获取 Agent 列表（enabled=None 表示全部）
client.get_agent(agent_id)               # 获取 Agent 详情（不存在返回 None）
client.get_agent_by_name(name, enabled=None)  # 按名称查找 Agent（返回首个名称匹配）
```

> 所有示例脚本默认使用名称为「**数据分析助手**」的 Agent：显式指定 `AGENT_ID`（脚本内变量）或 `--agent-id`（verify_evaluation.py）时优先使用传入值，否则按名称自动查找。

#### 生命周期
```python
client.close()   # 关闭底层 HTTP 会话

# 同时支持上下文管理器用法：
with DataAgentClient(base_url, workspace_id=1) as client:
    ...
```

### SSE 事件类型

| 事件类型 | 说明 | 数据格式 |
|---------|------|---------|
| `session` | 会话开始 | `{"conversationId": "...", "agentId": 1}` |
| `message_start` | 消息开始 | `{"role": "assistant"}` |
| `stream_started` | 流开始 | `{"conversationId": "...", "timestamp": 123}` |
| `thinking_delta` | 思考内容增量 | `{"delta": "..."}` |
| `content_delta` | 回答内容增量 | `{"delta": "..."}` |
| `tool_call_started` | 工具调用开始 | `{"toolCallId": "...", "toolName": "...", "arguments": "..."}` |
| `tool_call_completed` | 工具调用完成 | `{"toolCallId": "...", "toolName": "...", "result": "...", "success": true}` |
| `message_complete` | 消息完成 | `{"status": "completed", "hasThinking": true, "hasContent": true}` |
| `recommended_questions` | 推荐问题 | `{"questions": ["...", "..."]}` |
| `done` | 对话完成 | `{"conversationId": "...", "status": "completed", "promptTokens": 100, "completionTokens": 50}` |
| `heartbeat` | 心跳保活 | `{}` |
| `error` | 错误 | `{"message": "..."}` |

> 使用 `event.event` 判断事件类型，通过 `event.delta` / `event.data` 读取事件内容。

### 数据模型

所有模型均为 `dataclass`，字段通过**属性**访问（而非字典下标）。

| 模型 | 关键字段 |
|------|---------|
| `LoginResponse` | `id`, `token`, `username`, `nickname`, `role`, `workspaces: list[Workspace]` |
| `ChatResponse` | `content`, `thinking`, `tool_calls`, `prompt_tokens`, `completion_tokens`, `runtime_model`, `runtime_provider`, `status`, `metadata` |
| `LlmChatMessage` | `role`, `content` |
| `LlmChatResponse` | `content`, `model`, `provider`, `prompt_tokens`, `completion_tokens`, `status` |
| `SseEvent` | `event`, `data`（dict 或 str）, `id`；便捷属性 `delta` / `content` / `thinking` |
| `Conversation` | `id`, `conversation_id`, `title`, `agent_id`, `message_count`, `last_message`, `last_active_time`, `pinned`, `model_provider`, `model_name`, `stream_status` |
| `Message` | `id`, `conversation_id`, `role`, `content`, `tool_name`, `status`, `metadata`, `prompt_tokens`, `completion_tokens`, `runtime_model`, `runtime_provider` |
| `Agent` | `id`, `name`, `description`, `agent_type`, `system_prompt`, `model_name`, `max_iterations`, `enabled`, `icon`, `tags`, `workspace_id`, `default_thinking_level` |
| `Workspace` | `id`, `name`, `slug`, `description`, `member_role`, `role_level` |
| `ApiError` | `code`, `message`, `response_data` |

## 错误处理

所有请求失败（网络错误、HTTP 状态码异常、业务码异常）统一抛出 `ApiError`：

```python
from mateclaw_dataagent import DataAgentClient, ApiError

client = DataAgentClient("http://localhost:18089/dataagent/api", workspace_id=1)

try:
    client.login("admin", "wrong_password")
except ApiError as e:
    print(f"登录失败：{e.code} - {e.message}")
```

`ApiError` 提供 `code`、`message` 和 `response_data`（原始响应文本）。客户端统一校验 HTTP 状态码与服务端 `R` 封装业务码（`{code, msg, data}`，业务失败以 HTTP 200 + `code != 200` 返回），因此所有异常都以 `ApiError` 形式抛出。

## 测评验证规则

使用 `verify_evaluation.py` 脚本时，可以配置以下验证规则：

| 规则类型 | 说明 | 配置参数 |
|---------|------|---------|
| `has_answer` | 必须有回答内容 | - |
| `has_tool_call` | 必须调用指定工具 | `tool_name`: 工具名称 |
| `has_keyword` | 回答必须包含关键词 | `keywords`: 关键词列表 |
| `token_limit` | Token 使用限制 | `max_prompt`, `max_completion` |
| `latency_limit` | 延迟限制 | `max_ms`: 最大延迟（毫秒） |
| `answer_type` | 回答类型匹配 | `expected_type`: 期望类型 |

### 测评用例格式示例

```json
[
  {
    "question": "2024 年 Q1 的销售额是多少？",
    "expected_type": "kpi",
    "conversation_id": "eval-001",
    "rules": [
      {"type": "has_answer"},
      {"type": "has_keyword", "keywords": ["销售额", "Q1"]},
      {"type": "token_limit", "max_prompt": 8000, "max_completion": 2000},
      {"type": "latency_limit", "max_ms": 30000}
    ]
  }
]
```

### 编写用例注意事项

1. **会话管理**：测评框架**不会自动清理会话**，请按场景自行决定：
   - 需要用例互相隔离时，可在每次运行前调用 `client.delete_conversation(conversation_id)` 清理旧会话，避免历史消息污染；
   - 需要验证多轮上下文延续时，可复用同一 `conversation_id`（会话不会被自动删除）。
2. **问题自包含**：`question` 应单独可理解、可回答，避免依赖上文指代（如"它""上面那个"）。
3. **期望类型**：`expected_type` 用于与回答类型推断结果比对，可选 `kpi` / `chart` / `analysis` / `sql_query` / `schema_search` / `file_read` / `general`；不确定时可省略。
4. **默认 Agent**：示例脚本默认使用名称为「数据分析助手」的 Agent；可通过 `AGENT_ID`（脚本内变量）或 `--agent-id`（verify_evaluation.py）显式指定其他 Agent。
5. **规则配置**：`rules` 可按需组合；`token_limit` / `latency_limit` 参数缺省为 0，表示不限制。

## 运行测试

```bash
# 使用 unittest
python -m unittest tests/test_client.py -v

# 或使用 pytest
pip install pytest
python -m pytest tests/test_client.py -v
```

## 环境要求

- Python 3.10+
- requests >= 2.28.0

> **线程安全说明**：`DataAgentClient` 持有可变的 `token`/`session` 状态，非线程安全。批量并行测评时请为每个线程创建独立的客户端实例，或在多线程间加锁复用。

## 项目结构

```
mateclaw-dataagent-client-python/
├── mateclaw_dataagent/       # SDK 源代码
│   ├── __init__.py           # 包入口
│   ├── client.py             # HTTP 客户端
│   ├── models.py             # 数据模型
│   └── evaluation.py         # 测评模块
├── examples/                 # 示例脚本
│   ├── quickstart.py         # 快速入门
│   ├── chat_demo.py          # 对话示例
│   ├── llm_direct_demo.py    # 大模型直连（无持久化）示例
│   ├── evaluation_demo.py    # AI 测评示例
│   ├── verify_evaluation.py  # 测评验证脚本
│   └── test_cases.json       # 测评用例示例
├── tests/                    # 单元测试
│   └── test_client.py
├── README.md                 # 英文文档（详细版）
├── README_CN.md              # 中文文档（本文件，详细版）
└── setup.py                  # 安装脚本
```

## 常见问题

### 连接失败
- 确保 MateClaw DataAgent 服务已启动（默认 http://localhost:18089）
- 检查 `base_url` 配置是否正确

### 认证失败
- 检查用户名密码是否正确（默认 admin/admin123）
- 确保工作区 ID 正确

### 没有 Agent
- 在 MateClaw DataAgent UI 中创建一个 Agent
- 确保 Agent 已启用

## 许可证

MIT
