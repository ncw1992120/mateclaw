# MateClaw DataAgent Python Client SDK

A Python client SDK for AI evaluation. It talks to the MateClaw DataAgent REST API over HTTP, covering authentication, synchronous/streaming chat, conversation management, agent management, and a built-in evaluation framework.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [1. Basic Chat](#1-basic-chat)
  - [2. Streaming Chat](#2-streaming-chat)
  - [3. AI Evaluation](#3-ai-evaluation)
- [Example Scripts](#example-scripts)
- [API Reference](#api-reference)
  - [DataAgentClient](#dataagentclient)
  - [Data Models](#data-models)
  - [SSE Event Types](#sse-event-types)
- [AI Evaluation](#ai-evaluation)
  - [Test Case Format](#test-case-format)
  - [Test Case Authoring Notes](#test-case-authoring-notes)
  - [Verification Rules](#verification-rules)
  - [Evaluation Report](#evaluation-report)
- [Error Handling](#error-handling)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [FAQ](#faq)
- [License](#license)

## Features

- JWT authentication (login / current user / logout)
- Synchronous chat
- Streaming chat over SSE, with reconnect support
- Conversation management (list, messages, delete, rename, stop streaming)
- Agent management (list, detail)
- Structured SSE event parsing
- AI evaluation framework (`EvaluationRunner`)
- Evaluation rule verification (`EvaluationChecker`)
- Evaluation report export (JSON)

## Requirements

- Python 3.10+
- requests >= 2.28.0

> **Thread safety**: `DataAgentClient` holds mutable `token`/`session` state and is not thread-safe. For concurrent batch evaluation, create a separate client instance per thread or guard shared usage with a lock.`

## Installation

```bash
# Install the only runtime dependency
pip install requests

# Or install this package locally (editable mode)
pip install -e .
```

## Quick Start

### 1. Basic Chat

```python
from mateclaw_dataagent import DataAgentClient

# Initialize the client
client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1,
)

# Login
client.login(username="admin", password="admin123")

# List available agents
agents = client.list_agents()
print(f"Available agents: {[a.name for a in agents]}")

# Synchronous chat
response = client.chat(
    agent_id=agents[0].id,
    message="Please analyze the sales data",
    conversation_id="test-001",
)
print(f"Answer: {response.content}")

# Close the client
client.close()
```

### 2. Streaming Chat

```python
from mateclaw_dataagent import DataAgentClient

client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1,
)
client.login(username="admin", password="admin123")

# Streaming chat: process SSE events as they arrive
print("AI: ", end="", flush=True)

# Default agents are resolved by name "数据分析助手" (see below)
agent = client.get_agent_by_name("数据分析助手")
for event in client.stream_chat(
    agent_id=agent.id,
    message="Please analyze the sales data",
    conversation_id="test-002",
):
    if event.event == "content_delta":
        print(event.delta, end="", flush=True)
    elif event.event == "thinking_delta":
        print(f"[thinking] {event.delta}", end="", flush=True)
    elif event.event == "done":
        print(f"\n\nDone! Tokens: {event.data.get('promptTokens', 0)} + {event.data.get('completionTokens', 0)}")

client.close()
```

### 3. AI Evaluation

```python
from mateclaw_dataagent import DataAgentClient, EvaluationRunner

client = DataAgentClient(
    base_url="http://localhost:18089/dataagent/api",
    workspace_id=1,
)
client.login(username="admin", password="admin123")

# Prepare test cases
test_cases = [
    {"question": "What is the sales amount for Q1 2024?", "expected_type": "kpi"},
    {"question": "Compare profit across product lines", "expected_type": "chart"},
    {"question": "Analyze user growth trends", "expected_type": "analysis"},
]

# Run the evaluation
runner = EvaluationRunner(client)
agent = client.get_agent_by_name("数据分析助手")  # default agent by name
report = runner.run_evaluation(
    agent_id=agent.id,
    test_cases=test_cases,
    output_file="evaluation_results.json",
    use_stream=True,  # use streaming chat
)

# Print the report
print(f"Total: {report.total}")
print(f"Success: {report.success}")
print(f"Failed: {report.failed}")
print(f"Success rate: {report.success_rate:.2%}")
print(f"Average latency: {report.avg_latency_ms:.0f} ms")

client.close()
```

## Example Scripts

The project ships several runnable examples:

| Script | Description |
|--------|-------------|
| `examples/quickstart.py` | Quick start (5-minute onboarding) |
| `examples/chat_demo.py` | Chat feature demo |
| `examples/evaluation_demo.py` | AI evaluation demo |
| `examples/verify_evaluation.py` | Evaluation verification with custom rules |
| `examples/test_cases.json` | Sample test cases (input for `verify_evaluation.py`) |

Run them:

```bash
# Quick start
python examples/quickstart.py

# AI evaluation
python examples/evaluation_demo.py

# Verification with custom cases (defaults to the "数据分析助手" agent)
python examples/verify_evaluation.py --cases examples/test_cases.json
```

> All example scripts resolve the target agent as follows: an explicit `AGENT_ID` (script variable) or `--agent-id` (`verify_evaluation.py`) wins; otherwise the agent named "**数据分析助手**" is looked up by name.

## API Reference

### DataAgentClient

#### Constructor

```python
client = DataAgentClient(
    base_url: str,        # Base path of the DataAgent API, e.g. "http://localhost:18089/dataagent/api"
    workspace_id: int,    # Workspace ID
    timeout: int = 60,    # Request timeout in seconds
)
```

#### Authentication

```python
client.login(username: str, password: str) -> LoginResponse   # Login; stores the JWT token
client.get_current_user() -> LoginResponse | None             # Current user info; None if not logged in
client.logout()                                               # Clear the local token & saved credentials
```

The client keeps the credentials passed to `login()`. When a request fails with `401` (token expired, validity 24h by default), it automatically re-logins and retries the request **once**; the login endpoint itself and requests made without saved credentials are never auto-retried.

#### Chat

```python
# Synchronous chat (the server returns plain text only)
client.chat(
    agent_id: int,
    message: str,
    conversation_id: str,
    model_provider: str = None,
    model_name: str = None,
    datasource_ids: list = None,
) -> ChatResponse

# Streaming chat (SSE); yields SseEvent objects
client.stream_chat(
    agent_id: int,
    message: str,
    conversation_id: str,
    model_provider: str = None,
    model_name: str = None,
    datasource_ids: list = None,
    reconnect: bool = False,     # resume an interrupted stream
    last_event_id: int = None,   # last received event ID (used with reconnect)
) -> Generator[SseEvent, None, None]

# Stop an ongoing stream
client.stop_stream(conversation_id: str) -> bool
```

#### Conversation Management

```python
client.list_conversations()                          # -> list[Conversation]
client.get_messages(conversation_id: str)            # -> list[Message]
client.delete_conversation(conversation_id: str)     # -> bool
client.rename_conversation(conversation_id: str, title: str) -> bool
```

#### Agent Management

```python
client.list_agents(enabled: bool = None) -> list[Agent]          # None = all agents
client.get_agent(agent_id: int) -> Agent | None                  # None if not found
client.get_agent_by_name(name: str, enabled: bool = None) -> Agent | None  # first match by name
```

#### Lifecycle

```python
client.close()   # Close the underlying HTTP session

# The client also supports the context manager protocol:
with DataAgentClient(base_url, workspace_id=1) as client:
    ...
```

### Data Models

| Model | Key fields |
|-------|-----------|
| `LoginResponse` | `id`, `token`, `username`, `nickname`, `role`, `workspaces: list[Workspace]` |
| `ChatResponse` | `content`, `thinking`, `tool_calls`, `prompt_tokens`, `completion_tokens`, `runtime_model`, `runtime_provider`, `status`, `metadata` |
| `SseEvent` | `event`, `data` (dict or str), `id`; convenience properties: `delta`, `content`, `thinking` |
| `Conversation` | `id`, `conversation_id`, `title`, `agent_id`, `message_count`, `last_message`, `last_active_time`, `pinned`, `model_provider`, `model_name`, `stream_status` |
| `Message` | `id`, `conversation_id`, `role`, `content`, `tool_name`, `status`, `metadata`, `prompt_tokens`, `completion_tokens`, `runtime_model`, `runtime_provider` |
| `Agent` | `id`, `name`, `description`, `agent_type`, `system_prompt`, `model_name`, `max_iterations`, `enabled`, `icon`, `tags`, `workspace_id`, `default_thinking_level` |
| `Workspace` | `id`, `name`, `slug`, `description`, `member_role`, `role_level` |
| `ApiError` | `code`, `message`, `response_data` |

All models are plain `dataclass`es; fields are accessed as attributes, not dict keys.

### SSE Event Types

| Event | Description | Data format |
|-------|-------------|-------------|
| `session` | Conversation started | `{"conversationId": "...", "agentId": 1}` |
| `message_start` | Message started | `{"role": "assistant"}` |
| `stream_started` | Stream started | `{"conversationId": "...", "timestamp": 123}` |
| `thinking_delta` | Incremental thinking content | `{"delta": "..."}` |
| `content_delta` | Incremental answer content | `{"delta": "..."}` |
| `tool_call_started` | Tool call started | `{"toolCallId": "...", "toolName": "...", "arguments": "..."}` |
| `tool_call_completed` | Tool call completed | `{"toolCallId": "...", "toolName": "...", "result": "...", "success": true}` |
| `message_complete` | Message completed | `{"status": "completed", "hasThinking": true, "hasContent": true}` |
| `recommended_questions` | Recommended follow-ups | `{"questions": ["...", "..."]}` |
| `done` | Chat finished | `{"conversationId": "...", "status": "completed", "promptTokens": 100, "completionTokens": 50}` |
| `heartbeat` | Keep-alive heartbeat | `{}` |
| `error` | Error | `{"message": "..."}` |

Use `event.event` to branch on the type and `event.delta` / `event.data` to read payloads.

## AI Evaluation

### Test Case Format

```json
[
  {
    "question": "What is the sales amount for Q1 2024?",
    "expected_type": "kpi",
    "conversation_id": "eval-001",
    "rules": [
      {"type": "has_answer"},
      {"type": "has_keyword", "keywords": ["sales", "Q1"]},
      {"type": "token_limit", "max_prompt": 8000, "max_completion": 2000},
      {"type": "latency_limit", "max_ms": 30000}
    ]
  }
]
```

### Test Case Authoring Notes

1. **Conversation management**: the framework does **not** delete conversations automatically; decide per scenario:
   - For isolated cases, call `client.delete_conversation(conversation_id)` before each run to avoid context leaking between cases.
   - Reusing the same `conversation_id` keeps context across turns (it is never auto-deleted), which is useful for multi-turn tests.
2. **Self-contained questions**: each `question` should be understandable and answerable on its own; avoid references to earlier turns (e.g. "it", "the one above").
3. **Expected type**: `expected_type` is compared against the inferred answer type; allowed values are `kpi` / `chart` / `analysis` / `sql_query` / `schema_search` / `file_read` / `general`. You may omit it if unsure.
4. **Default agent**: the examples default to the agent named "数据分析助手"; pass an explicit `AGENT_ID` or `--agent-id` to use another agent.
5. **Rule tuning**: `rules` can be combined freely; `token_limit` / `latency_limit` parameters default to `0`, meaning "no limit".

### Verification Rules

| Rule | Description | Parameters |
|------|-------------|------------|
| `has_answer` | Answer content must exist | - |
| `has_tool_call` | A specific tool must be called | `tool_name` |
| `has_keyword` | Answer must contain keywords | `keywords` |
| `token_limit` | Token usage limits | `max_prompt`, `max_completion` |
| `latency_limit` | Latency limit | `max_ms` |
| `answer_type` | Answer type must match | `expected_type` |

### Evaluation Report

`EvaluationRunner.run_evaluation(...)` returns an `EvaluationReport` with:

| Field | Description |
|-------|-------------|
| `total` | Total number of test cases |
| `success` | Passed cases |
| `failed` | Failed cases |
| `success_rate` | `success / total` (property) |
| `avg_latency_ms` | Average latency across cases |
| `avg_tokens` | Average `(prompt, completion)` token usage |
| `results` | Per-case `EvaluationResult` list (`question`, `answer`, `thinking`, `actual_type`, `tool_calls`, `tokens`, `latency_ms`, `error`) |
| `to_dict()` | Serialize the whole report to a JSON-able dict |

## Error Handling

```python
from mateclaw_dataagent import DataAgentClient, ApiError

client = DataAgentClient("http://localhost:18089/dataagent/api", workspace_id=1)

try:
    client.login("admin", "wrong_password")
except ApiError as e:
    print(f"Login failed: {e.code} - {e.message}")
```

`ApiError` exposes `code`, `message` and `response_data` (raw response text). The client uniformly validates both HTTP status codes and the server-side `R` wrapper business codes (`{code, msg, data}`, business failures return HTTP 200 with `code != 200`), so all failures surface as `ApiError`.

## Running Tests

```bash
# With unittest
python -m unittest tests/test_client.py -v

# Or with pytest
pip install pytest
python -m pytest tests/test_client.py -v
```

## Project Structure

```
mateclaw-dataagent-client-python/
├── mateclaw_dataagent/       # SDK source
│   ├── __init__.py           # Package entry & public API exports
│   ├── client.py             # HTTP client (DataAgentClient)
│   ├── models.py             # Data models (dataclasses)
│   └── evaluation.py         # Evaluation framework
├── examples/                 # Runnable example scripts
│   ├── quickstart.py         # Quick start
│   ├── chat_demo.py          # Chat demo
│   ├── evaluation_demo.py    # AI evaluation demo
│   ├── verify_evaluation.py  # Verification script
│   └── test_cases.json       # Sample test cases
├── tests/                    # Unit tests
│   └── test_client.py
├── README.md                 # English documentation (this file)
├── README_CN.md              # Chinese documentation
└── setup.py                  # Packaging script
```

## FAQ

### Connection failures
- Make sure the MateClaw DataAgent service is running (default `http://localhost:18089`).
- Check the `base_url` configuration.

### Authentication failures
- Check the username/password (default `admin` / `admin123`).
- Make sure the workspace ID is correct.

### No agents available
- Create an Agent in the MateClaw DataAgent UI.
- Make sure the Agent is enabled.

## License

MIT
