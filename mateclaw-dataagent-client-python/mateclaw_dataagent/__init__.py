"""
MateClaw DataAgent Python Client SDK
=====================================

用于 AI 测评的 Python 客户端 SDK，支持通过 HTTP 调用 MateClaw DataAgent 的 REST API。

主要功能:
- JWT 认证登录
- 同步/流式对话
- 会话管理
- Agent 管理
- 结构化 SSE 事件解析
- 测评结果导出

使用示例:
    from mateclaw_dataagent import DataAgentClient

    client = DataAgentClient(
        base_url="http://localhost:18089/dataagent/api",
        workspace_id=1
    )
    client.login(username="admin", password="admin123")

    # 按名称获取 Agent（未指定时通常使用"数据分析助手"）
    agent = client.get_agent_by_name("数据分析助手")

    # 同步对话
    response = client.chat(
        agent_id=agent.id,
        message="请分析销售数据",
        conversation_id="test-001"
    )
    print(f"Answer: {response.content}")

    client.close()
"""

from .client import DataAgentClient
from .models import (
    Agent,
    ApiError,
    ChatResponse,
    Conversation,
    EvaluationReport,
    EvaluationResult,
    LoginResponse,
    Message,
    SseEvent,
    Workspace,
)
from .evaluation import EvaluationChecker, EvaluationRunner

__version__ = "0.1.0"
__all__ = [
    "DataAgentClient",
    "ChatResponse",
    "SseEvent",
    "LoginResponse",
    "Conversation",
    "Message",
    "Agent",
    "Workspace",
    "ApiError",
    "EvaluationRunner",
    "EvaluationChecker",
    "EvaluationResult",
    "EvaluationReport",
]
