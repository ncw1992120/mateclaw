"""
MateClaw DataAgent HTTP 客户端
"""

import json
from typing import Generator, Optional
from urllib.parse import urljoin

import requests

from .models import (
    ApiError,
    Agent,
    ChatResponse,
    Conversation,
    LlmChatMessage,
    LlmChatResponse,
    LoginResponse,
    Message,
    SseEvent,
    Workspace,
)


def _parse_workspaces(items) -> list:
    """将服务端工作区数据解析为 Workspace 对象列表"""
    return [
        Workspace(
            id=w.get("id"),
            name=w.get("name", ""),
            slug=w.get("slug", ""),
            description=w.get("description", ""),
            member_role=w.get("memberRole"),
            role_level=w.get("roleLevel", 0),
        )
        for w in (items or [])
    ]


class DataAgentClient:
    """
    MateClaw DataAgent HTTP 客户端

    用于通过 REST API 与 DataAgent 交互，支持认证、对话、会话管理等功能。

    Attributes:
        base_url: API 基础路径
        workspace_id: 工作区 ID
        timeout: 请求超时时间（秒）
        token: JWT 令牌（登录后自动设置）
    """

    def __init__(
        self,
        base_url: str,
        workspace_id: int,
        timeout: int = 60,
    ):
        """
        初始化客户端

        Args:
            base_url: API 基础路径，如 "http://localhost:18089/dataagent/api"
            workspace_id: 工作区 ID
            timeout: 请求超时时间（秒）
        """
        self.base_url = base_url.rstrip("/")
        self.workspace_id = workspace_id
        self.timeout = timeout
        self.token: Optional[str] = None
        # 保存登录凭据，用于 token 过期（401）时自动重新登录（与 login 同为明文内存驻留）
        self._username: Optional[str] = None
        self._password: Optional[str] = None
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json",
            "X-Workspace-Id": str(workspace_id),
        })

    def _url(self, path: str) -> str:
        """构建完整 URL"""
        return urljoin(self.base_url + "/", path.lstrip("/"))

    def _request(
        self,
        method: str,
        path: str,
        json_data: Optional[dict] = None,
        params: Optional[dict] = None,
        stream: bool = False,
    ) -> requests.Response:
        """
        发送 HTTP 请求

        Args:
            method: HTTP 方法
            path: API 路径
            json_data: JSON 请求体
            params: 查询参数
            stream: 是否流式接收

        Returns:
            requests.Response 对象

        Raises:
            ApiError: API 错误
        """
        # token 过期（401）时基于已保存的凭据自动重新登录并重试一次；
        # 登录接口本身及未保存凭据的场景不触发自动重登
        for attempt in (0, 1):
            try:
                return self._request_once(method, path, json_data, params, stream)
            except ApiError as e:
                if e.code == 401 and attempt == 0 and path != "/v1/auth/login" and self._username:
                    self.login(self._username, self._password)
                    continue
                raise

    def _request_once(
        self,
        method: str,
        path: str,
        json_data: Optional[dict] = None,
        params: Optional[dict] = None,
        stream: bool = False,
    ) -> requests.Response:
        """
        发送单次 HTTP 请求

        Args:
            method: HTTP 方法
            path: API 路径
            json_data: JSON 请求体
            params: 查询参数
            stream: 是否流式接收

        Returns:
            requests.Response 对象

        Raises:
            ApiError: API 错误
        """
        url = self._url(path)
        headers = {}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        try:
            response = self.session.request(
                method,
                url,
                json=json_data,
                params=params,
                stream=stream,
                headers=headers,
                timeout=self.timeout,
            )
        except requests.exceptions.Timeout:
            raise ApiError(408, "Request timeout")
        except requests.exceptions.ConnectionError as e:
            raise ApiError(503, f"Connection error: {e}")

        # 处理错误响应
        if response.status_code >= 400:
            try:
                error_data = response.json()
                code = error_data.get("code", response.status_code)
                message = error_data.get("msg", error_data.get("message", "Unknown error"))
            except (json.JSONDecodeError, ValueError):
                code = response.status_code
                message = response.text or "Unknown error"
            raise ApiError(code, message, response.text)

        # 业务错误码校验：服务端统一响应 R 封装（含 code/msg/data），
        # 业务失败（如 R.fail）以 HTTP 200 + code != 200 返回，此处统一拦截
        try:
            body = response.json()
            if isinstance(body, dict) and "code" in body and "data" in body and body.get("code") != 200:
                message = body.get("msg", body.get("message", "Unknown error"))
                raise ApiError(body.get("code"), message, response.text)
        except (json.JSONDecodeError, ValueError):
            # 非 JSON 响应（如同步对话返回纯文本）无需业务码校验
            pass

        return response

    # ==================== 认证 ====================

    def login(self, username: str, password: str) -> LoginResponse:
        """
        用户登录

        Args:
            username: 用户名
            password: 密码

        Returns:
            LoginResponse 登录响应

        Raises:
            ApiError: 登录失败
        """
        response = self._request(
            "POST",
            "/v1/auth/login",
            json_data={"username": username, "password": password},
        )
        data = response.json().get("data") or {}
        if not data or not data.get("token"):
            raise ApiError(401, "登录失败：响应中未包含有效的 token")
        self.token = data.get("token")
        # 保存凭据供 token 过期时自动重登（仅在登录成功后写入，避免错误密码被缓存）
        self._username = username
        self._password = password
        self.session.headers["Authorization"] = f"Bearer {self.token}"
        return LoginResponse(
            id=data.get("id"),
            token=data.get("token"),
            username=data.get("username"),
            nickname=data.get("nickname"),
            role=data.get("role"),
            workspaces=_parse_workspaces(data.get("workspaces")),
        )

    def get_current_user(self) -> Optional[LoginResponse]:
        """
        获取当前用户信息

        Returns:
            LoginResponse 用户信息，未登录返回 None
        """
        try:
            response = self._request("GET", "/v1/auth/me")
        except ApiError as e:
            if e.code != 401:
                raise
            # 仅未登录 / token 失效返回 None，网络错误等服务异常需上抛以便调用方区分
            return None
        data = response.json().get("data")
        if not data:
            return None
        return LoginResponse(
            id=data.get("id"),
            token=data.get("token"),
            username=data.get("username"),
            nickname=data.get("nickname"),
            role=data.get("role"),
            workspaces=_parse_workspaces(data.get("workspaces")),
        )

    def logout(self):
        """退出登录"""
        self.token = None
        self._username = None
        self._password = None
        if "Authorization" in self.session.headers:
            del self.session.headers["Authorization"]

    # ==================== 对话 ====================

    def chat(
        self,
        agent_id: int,
        message: str,
        conversation_id: str,
        model_provider: Optional[str] = None,
        model_name: Optional[str] = None,
        datasource_ids: Optional[list] = None,
    ) -> ChatResponse:
        """
        同步对话

        Args:
            agent_id: Agent ID
            message: 用户消息
            conversation_id: 会话 ID
            model_provider: 模型 Provider（可选）
            model_name: 模型名称（可选）
            datasource_ids: 数据源 ID 白名单（可选）

        Returns:
            ChatResponse 对话响应
        """
        payload = {
            "agentId": agent_id,
            "message": message,
            "conversationId": conversation_id,
        }
        if model_provider:
            payload["modelProvider"] = model_provider
        if model_name:
            payload["modelName"] = model_name
        if datasource_ids:
            payload["datasourceIds"] = datasource_ids

        response = self._request("POST", "/v1/chat", json_data=payload)

        # 服务端同步接口仅返回纯文本内容，token/工具调用等结构化信息需经消息历史回读
        return ChatResponse(
            content=response.text,
            status="completed",
        )

    # ==================== 大模型直连 ====================

    def _build_llm_payload(
        self,
        messages,
        provider: Optional[str] = None,
        model: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
    ) -> dict:
        """
        构建大模型直连请求体

        Args:
            messages: 消息列表，元素可为 dict（{"role","content"}）或 LlmChatMessage
            provider: Provider ID（可选）
            model: 模型名称（可选）
            temperature: 采样温度（可选）
            max_tokens: 最大输出 token 数（可选）

        Returns:
            dict 请求体
        """
        payload = {
            "messages": [
                m.to_dict() if isinstance(m, LlmChatMessage) else dict(m)
                for m in messages
            ]
        }
        if provider:
            payload["provider"] = provider
        if model:
            payload["model"] = model
        if temperature is not None:
            payload["temperature"] = temperature
        if max_tokens is not None:
            payload["maxTokens"] = max_tokens
        return payload

    def llm_chat(
        self,
        messages,
        provider: Optional[str] = None,
        model: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
    ) -> LlmChatResponse:
        """
        大模型直连同步对话（无持久化）

        直接调用大模型并返回完整回答，服务端不产生任何会话/消息记录。

        Args:
            messages: 消息列表，元素可为 dict（{"role","content"}）或 LlmChatMessage
            provider: Provider ID（可选，缺省用默认模型）
            model: 模型名称（可选，缺省用默认模型）
            temperature: 采样温度（可选）
            max_tokens: 最大输出 token 数（可选）

        Returns:
            LlmChatResponse 大模型直连响应
        """
        payload = self._build_llm_payload(messages, provider, model, temperature, max_tokens)
        response = self._request("POST", "/v1/llm/chat", json_data=payload)
        data = response.json().get("data") or {}
        return LlmChatResponse(
            content=data.get("content", ""),
            model=data.get("model"),
            provider=data.get("provider"),
            prompt_tokens=data.get("promptTokens", 0),
            completion_tokens=data.get("completionTokens", 0),
        )

    def llm_chat_stream(
        self,
        messages,
        provider: Optional[str] = None,
        model: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
    ) -> Generator[SseEvent, None, None]:
        """
        大模型直连流式对话（无持久化）

        通过 SSE 接收大模型内容增量（content_delta / done），服务端不产生任何会话/消息记录。

        Args:
            messages: 消息列表，元素可为 dict（{"role","content"}）或 LlmChatMessage
            provider: Provider ID（可选，缺省用默认模型）
            model: 模型名称（可选，缺省用默认模型）
            temperature: 采样温度（可选）
            max_tokens: 最大输出 token 数（可选）

        Yields:
            SseEvent SSE 事件（content_delta / done / error）

        Raises:
            ApiError: 请求失败
        """
        payload = self._build_llm_payload(messages, provider, model, temperature, max_tokens)
        url = self._url("/v1/llm/chat/stream")

        # token 过期（401）时基于已保存的凭据自动重新登录并重试一次
        response = None
        for attempt in (0, 1):
            headers = {"Accept": "text/event-stream"}
            if self.token:
                headers["Authorization"] = f"Bearer {self.token}"

            try:
                response = self.session.request(
                    "POST",
                    url,
                    json=payload,
                    headers=headers,
                    stream=True,
                    timeout=self.timeout,
                )
            except requests.exceptions.Timeout:
                raise ApiError(408, "Request timeout")
            except requests.exceptions.ConnectionError as e:
                raise ApiError(503, f"Connection error: {e}")

            if response.status_code >= 400:
                try:
                    error_data = response.json()
                    code = error_data.get("code", response.status_code)
                    message = error_data.get("msg", "Unknown error")
                except (json.JSONDecodeError, ValueError):
                    code = response.status_code
                    message = response.text or "Unknown error"
                if code == 401 and attempt == 0 and self._username:
                    # 关闭旧响应连接，避免流式响应未消费导致的连接泄漏
                    response.close()
                    self.login(self._username, self._password)
                    continue
                raise ApiError(code, message)

            # 防御性校验：服务端若在 SSE 建立前返回 R 封装错误（HTTP 200 + JSON），
            # 按 JSON 解析并抛 ApiError，避免将错误体当作 SSE 流解析产生伪事件
            content_type = response.headers.get("Content-Type", "")
            if content_type and "text/event-stream" not in content_type:
                try:
                    body = response.json()
                    code = body.get("code", 200)
                    message = body.get("msg", body.get("message", "Unknown error"))
                    if code != 200:
                        raise ApiError(code, message)
                except (json.JSONDecodeError, ValueError):
                    pass

            break

        # 解析 SSE 流
        buffer = ""
        for line in response.iter_lines(decode_unicode=True):
            if line is None:
                continue

            buffer += line + "\n"

            # SSE 消息以空行分隔
            if line.strip() == "":
                events = self._parse_sse_buffer(buffer)
                buffer = ""
                for event in events:
                    yield event

        # 处理剩余缓冲
        if buffer.strip():
            for event in self._parse_sse_buffer(buffer):
                yield event

    def stream_chat(
        self,
        agent_id: int,
        message: str,
        conversation_id: str,
        model_provider: Optional[str] = None,
        model_name: Optional[str] = None,
        datasource_ids: Optional[list] = None,
        reconnect: bool = False,
        last_event_id: Optional[int] = None,
    ) -> Generator[SseEvent, None, None]:
        """
        流式对话（SSE）

        Args:
            agent_id: Agent ID
            message: 用户消息
            conversation_id: 会话 ID
            model_provider: 模型 Provider（可选）
            model_name: 模型名称（可选）
            datasource_ids: 数据源 ID 白名单（可选）
            reconnect: 是否重连
            last_event_id: 最后接收的事件 ID（重连时使用）

        Yields:
            SseEvent SSE 事件

        Raises:
            ApiError: 请求失败
        """
        payload = {
            "agentId": agent_id,
            "message": message,
            "conversationId": conversation_id,
        }
        if model_provider:
            payload["modelProvider"] = model_provider
        if model_name:
            payload["modelName"] = model_name
        if datasource_ids:
            payload["datasourceIds"] = datasource_ids
        if reconnect:
            payload["reconnect"] = True
            if last_event_id is not None:
                payload["lastEventId"] = last_event_id

        url = self._url("/v1/chat/stream")

        # token 过期（401）时基于已保存的凭据自动重新登录并重试一次
        response = None
        for attempt in (0, 1):
            # 仅补充流式专用头，session 上已有的 Content-Type / X-Workspace-Id / Authorization 会一并带上；
            # 每次重试前重新读取 self.token，确保使用自动重登后的新令牌
            headers = {"Accept": "text/event-stream"}
            if self.token:
                headers["Authorization"] = f"Bearer {self.token}"

            try:
                response = self.session.request(
                    "POST",
                    url,
                    json=payload,
                    headers=headers,
                    stream=True,
                    timeout=self.timeout,
                )
            except requests.exceptions.Timeout:
                raise ApiError(408, "Request timeout")
            except requests.exceptions.ConnectionError as e:
                raise ApiError(503, f"Connection error: {e}")

            if response.status_code >= 400:
                try:
                    error_data = response.json()
                    code = error_data.get("code", response.status_code)
                    message = error_data.get("msg", "Unknown error")
                except (json.JSONDecodeError, ValueError):
                    code = response.status_code
                    message = response.text or "Unknown error"
                if code == 401 and attempt == 0 and self._username:
                    # 关闭旧响应连接，避免流式响应未消费导致的连接泄漏
                    response.close()
                    self.login(self._username, self._password)
                    continue
                raise ApiError(code, message)

            # 防御性校验：服务端若在 SSE 建立前返回 R 封装错误（HTTP 200 + JSON），
            # 按 JSON 解析并抛 ApiError，避免将错误体当作 SSE 流解析产生伪事件
            content_type = response.headers.get("Content-Type", "")
            if content_type and "text/event-stream" not in content_type:
                try:
                    body = response.json()
                    code = body.get("code", 200)
                    message = body.get("msg", body.get("message", "Unknown error"))
                    if code != 200:
                        raise ApiError(code, message)
                except (json.JSONDecodeError, ValueError):
                    pass

            break

        # 解析 SSE 流
        buffer = ""
        for line in response.iter_lines(decode_unicode=True):
            if line is None:
                continue

            buffer += line + "\n"

            # SSE 消息以空行分隔
            if line.strip() == "":
                events = self._parse_sse_buffer(buffer)
                buffer = ""
                for event in events:
                    yield event

        # 处理剩余缓冲
        if buffer.strip():
            for event in self._parse_sse_buffer(buffer):
                yield event

    def _parse_sse_buffer(self, buffer: str) -> list:
        """
        解析 SSE 缓冲

        Args:
            buffer: SSE 原始文本

        Returns:
            list[SseEvent] 事件列表
        """
        events = []

        # 按空行分割多个 SSE 消息
        message_blocks = buffer.strip().split("\n\n")

        for block in message_blocks:
            if not block.strip():
                continue

            event_type = "message"
            event_data = ""
            event_id = None

            for line in block.strip().split("\n"):
                if not line.strip():
                    continue

                if line.startswith("event:"):
                    event_type = line[6:].strip()
                elif line.startswith("id:"):
                    event_id = line[3:].strip()
                elif line.startswith("data:"):
                    # 按 SSE 规范，同一事件内的多条 data 行以换行拼接
                    data_line = line[5:].strip()
                    if event_data:
                        event_data += "\n" + data_line
                    else:
                        event_data = data_line

            if event_data:
                try:
                    data = json.loads(event_data)
                except json.JSONDecodeError:
                    data = event_data

                events.append(SseEvent(event=event_type, data=data, id=event_id))

        return events

    def stop_stream(self, conversation_id: str) -> bool:
        """
        停止流式生成

        Args:
            conversation_id: 会话 ID

        Returns:
            bool 是否成功停止
        """
        response = self._request("DELETE", f"/v1/chat/stream/{conversation_id}")
        data = response.json().get("data", {})
        return data.get("stopped", False)

    # ==================== 会话管理 ====================

    def list_conversations(self) -> list:
        """
        获取会话列表

        Returns:
            list[Conversation] 会话列表
        """
        response = self._request("GET", "/v1/conversations")
        data = response.json().get("data", [])
        return [
            Conversation(
                id=str(c.get("id", "")),
                conversation_id=c.get("conversationId", ""),
                title=c.get("title", ""),
                agent_id=c.get("agentId", 0),
                message_count=c.get("messageCount", 0),
                last_message=c.get("lastMessage", ""),
                last_active_time=c.get("lastActiveTime", ""),
                pinned=bool(c.get("pinned", False)),
                model_provider=c.get("modelProvider"),
                model_name=c.get("modelName"),
                stream_status=c.get("streamStatus", "idle"),
                create_time=c.get("createTime"),
                update_time=c.get("updateTime"),
            )
            for c in data
        ]

    def get_messages(self, conversation_id: str) -> list:
        """
        获取会话消息历史

        Args:
            conversation_id: 会话 ID

        Returns:
            list[Message] 消息列表
        """
        response = self._request("GET", f"/v1/conversations/{conversation_id}/messages")
        data = response.json().get("data", [])
        return [
            Message(
                id=m.get("id"),
                conversation_id=m.get("conversationId", ""),
                role=m.get("role", ""),
                content=m.get("content", ""),
                tool_name=m.get("toolName"),
                status=m.get("status"),
                metadata=m.get("metadata"),
                prompt_tokens=m.get("promptTokens"),
                completion_tokens=m.get("completionTokens"),
                runtime_model=m.get("runtimeModel"),
                runtime_provider=m.get("runtimeProvider"),
                create_time=m.get("createTime"),
            )
            for m in data
        ]

    def delete_conversation(self, conversation_id: str) -> bool:
        """
        删除会话

        Args:
            conversation_id: 会话 ID

        Returns:
            bool 是否成功删除
        """
        # _request 已统一校验 HTTP 状态码与业务码，未抛异常即视为成功
        self._request("DELETE", f"/v1/conversations/{conversation_id}")
        return True

    def rename_conversation(self, conversation_id: str, title: str) -> bool:
        """
        重命名会话

        Args:
            conversation_id: 会话 ID
            title: 新标题

        Returns:
            bool 是否成功
        """
        self._request(
            "PUT",
            f"/v1/conversations/{conversation_id}/title",
            json_data={"title": title},
        )
        return True

    # ==================== Agent 管理 ====================

    def list_agents(self, enabled: Optional[bool] = None) -> list:
        """
        获取 Agent 列表

        Args:
            enabled: 是否仅获取启用的 Agent

        Returns:
            list[Agent] Agent 列表
        """
        params = {}
        if enabled is not None:
            params["enabled"] = enabled

        response = self._request("GET", "/v1/agents", params=params)
        data = response.json().get("data", [])
        return [
            Agent(
                id=a.get("id"),
                name=a.get("name", ""),
                description=a.get("description", ""),
                agent_type=a.get("agentType", ""),
                system_prompt=a.get("systemPrompt", ""),
                model_name=a.get("modelName", ""),
                max_iterations=a.get("maxIterations", 0),
                enabled=a.get("enabled", False),
                icon=a.get("icon", ""),
                tags=a.get("tags", ""),
                workspace_id=a.get("workspaceId", 0),
                default_thinking_level=a.get("defaultThinkingLevel", ""),
                create_time=a.get("createTime"),
                update_time=a.get("updateTime"),
            )
            for a in data
        ]

    def get_agent(self, agent_id: int) -> Optional[Agent]:
        """
        获取 Agent 详情

        Args:
            agent_id: Agent ID

        Returns:
            Agent 详情，不存在返回 None
        """
        response = self._request("GET", f"/v1/agents/{agent_id}")
        data = response.json().get("data")
        if not data:
            return None
        return Agent(
            id=data.get("id"),
            name=data.get("name", ""),
            description=data.get("description", ""),
            agent_type=data.get("agentType", ""),
            system_prompt=data.get("systemPrompt", ""),
            model_name=data.get("modelName", ""),
            max_iterations=data.get("maxIterations", 0),
            enabled=data.get("enabled", False),
            icon=data.get("icon", ""),
            tags=data.get("tags", ""),
            workspace_id=data.get("workspaceId", 0),
            default_thinking_level=data.get("defaultThinkingLevel", ""),
            create_time=data.get("createTime"),
            update_time=data.get("updateTime"),
        )

    def get_agent_by_name(self, name: str, enabled: Optional[bool] = None) -> Optional[Agent]:
        """
        按名称查找 Agent（返回首个名称匹配的 Agent）

        Args:
            name: Agent 名称
            enabled: 是否仅匹配指定启用状态（None 表示不限）

        Returns:
            Agent 详情，未找到返回 None
        """
        for a in self.list_agents(enabled=enabled):
            if a.name == name:
                return a
        return None

    def close(self):
        """关闭客户端"""
        self.session.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
