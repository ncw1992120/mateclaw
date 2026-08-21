"""
数据模型定义
"""

from dataclasses import dataclass, field
from typing import Any, Optional


@dataclass
class LoginResponse:
    """登录响应"""
    id: int
    token: str
    username: str
    nickname: str
    role: str
    workspaces: list = field(default_factory=list)


@dataclass
class ChatResponse:
    """同步对话响应"""
    content: str
    thinking: Optional[str] = None
    tool_calls: list = field(default_factory=list)
    prompt_tokens: int = 0
    completion_tokens: int = 0
    runtime_model: Optional[str] = None
    runtime_provider: Optional[str] = None
    status: str = "completed"
    metadata: dict = field(default_factory=dict)


@dataclass
class LlmChatMessage:
    """大模型直连对话消息"""
    role: str
    content: str

    def to_dict(self) -> dict:
        return {"role": self.role, "content": self.content}


@dataclass
class LlmChatResponse:
    """大模型直连对话响应"""
    content: str
    model: Optional[str] = None
    provider: Optional[str] = None
    prompt_tokens: int = 0
    completion_tokens: int = 0
    status: str = "completed"


@dataclass
class SseEvent:
    """SSE 流式事件"""
    event: str
    data: dict | str
    id: Optional[str] = None

    @property
    def delta(self) -> Optional[str]:
        """获取增量内容（针对 thinking_delta / content_delta）"""
        if isinstance(self.data, dict):
            return self.data.get("delta")
        return None

    @property
    def content(self) -> Optional[str]:
        """获取内容（针对 content_delta）"""
        if self.event == "content_delta":
            return self.delta
        return None

    @property
    def thinking(self) -> Optional[str]:
        """获取思考内容（针对 thinking_delta）"""
        if self.event == "thinking_delta":
            return self.delta
        return None


@dataclass
class Conversation:
    """会话"""
    id: str
    conversation_id: str
    title: str
    agent_id: int
    message_count: int
    last_message: str
    last_active_time: str
    pinned: bool = False
    model_provider: Optional[str] = None
    model_name: Optional[str] = None
    stream_status: str = "idle"
    create_time: Optional[str] = None
    update_time: Optional[str] = None


@dataclass
class Message:
    """会话消息"""
    id: int
    conversation_id: str
    role: str
    content: str
    tool_name: Optional[str] = None
    status: Optional[str] = None
    metadata: Optional[dict] = None
    prompt_tokens: Optional[int] = None
    completion_tokens: Optional[int] = None
    runtime_model: Optional[str] = None
    runtime_provider: Optional[str] = None
    create_time: Optional[str] = None


@dataclass
class Agent:
    """Agent 实体"""
    id: int
    name: str
    description: str
    agent_type: str
    system_prompt: str
    model_name: str
    max_iterations: int
    enabled: bool
    icon: str
    tags: str
    workspace_id: int
    default_thinking_level: str
    create_time: Optional[str] = None
    update_time: Optional[str] = None


@dataclass
class Workspace:
    """工作区"""
    id: int
    name: str
    slug: str
    description: str
    member_role: Optional[str] = None
    role_level: int = 0


class ApiError(Exception):
    """API 错误异常"""

    def __init__(self, code: int, message: str, response_data: Any = None):
        self.code = code
        self.message = message
        self.response_data = response_data
        super().__init__(f"API Error {code}: {message}")


@dataclass
class EvaluationResult:
    """测评结果"""
    question: str
    answer: str = ""
    thinking: str = ""
    expected_type: Optional[str] = None
    actual_type: Optional[str] = None
    success: bool = False
    tool_calls: list = field(default_factory=list)
    tokens: tuple = (0, 0)
    latency_ms: int = 0
    error: Optional[str] = None


@dataclass
class EvaluationReport:
    """测评报告"""
    total: int = 0
    success: int = 0
    failed: int = 0
    results: list = field(default_factory=list)
    avg_latency_ms: float = 0.0
    avg_tokens: tuple = (0, 0)

    @property
    def success_rate(self) -> float:
        """成功率"""
        if self.total == 0:
            return 0.0
        return self.success / self.total

    def to_dict(self) -> dict:
        """转换为字典"""
        return {
            "summary": {
                "total": self.total,
                "success": self.success,
                "failed": self.failed,
                "success_rate": self.success_rate,
                "avg_latency_ms": self.avg_latency_ms,
                "avg_prompt_tokens": self.avg_tokens[0],
                "avg_completion_tokens": self.avg_tokens[1],
            },
            "results": [
                {
                    "question": r.question,
                    "answer": r.answer,
                    "thinking": r.thinking,
                    "expected_type": r.expected_type,
                    "actual_type": r.actual_type,
                    "success": r.success,
                    "tool_calls": r.tool_calls,
                    "tokens": list(r.tokens),
                    "latency_ms": r.latency_ms,
                    "error": r.error,
                }
                for r in self.results
            ],
        }
