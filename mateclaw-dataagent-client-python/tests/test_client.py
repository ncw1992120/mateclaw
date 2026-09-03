"""
MateClaw DataAgent Python Client SDK 单元测试
"""

import base64
import time
import unittest
from unittest.mock import Mock, patch

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from mateclaw_dataagent import DataAgentClient, ApiError
from mateclaw_dataagent.models import SseEvent, ChatResponse, LlmChatMessage, LlmChatResponse


class TestDataAgentClient(unittest.TestCase):
    """DataAgentClient 测试"""

    def setUp(self):
        """测试前准备"""
        self.client = DataAgentClient(
            base_url="http://localhost:18089/dataagent/api",
            workspace_id=1,
        )

    def tearDown(self):
        """测试后清理"""
        self.client.close()

    def test_initialization(self):
        """测试初始化"""
        self.assertEqual(self.client.base_url, "http://localhost:18089/dataagent/api")
        self.assertEqual(self.client.workspace_id, 1)
        self.assertEqual(self.client.timeout, 60)
        self.assertIsNone(self.client.token)

    def test_url_building(self):
        """测试 URL 构建"""
        self.assertEqual(
            self.client._url("/v1/auth/login"),
            "http://localhost:18089/dataagent/api/v1/auth/login",
        )

    def test_parse_sse_buffer(self):
        """测试 SSE 缓冲解析"""
        buffer = """event: content_delta
data: {"delta": "Hello"}

event: done
data: {"status": "completed"}"""

        events = self.client._parse_sse_buffer(buffer)

        self.assertEqual(len(events), 2)
        self.assertEqual(events[0].event, "content_delta")
        self.assertEqual(events[0].data, {"delta": "Hello"})
        self.assertEqual(events[1].event, "done")
        self.assertEqual(events[1].data, {"status": "completed"})

    def test_sse_event_properties(self):
        """测试 SSE 事件属性"""
        event = SseEvent(event="content_delta", data={"delta": "Hello"})

        self.assertEqual(event.delta, "Hello")
        self.assertEqual(event.content, "Hello")
        self.assertIsNone(event.thinking)

        thinking_event = SseEvent(event="thinking_delta", data={"delta": "Thinking..."})
        self.assertEqual(thinking_event.thinking, "Thinking...")

    # ==================== 大模型直连 ====================

    def test_build_llm_payload_minimal(self):
        """仅传 messages 时请求体最小"""
        payload = self.client._build_llm_payload([{"role": "user", "content": "hi"}])
        self.assertEqual(payload, {"messages": [{"role": "user", "content": "hi"}]})

    def test_build_llm_payload_full(self):
        """全部可选参数时请求体包含所有字段"""
        payload = self.client._build_llm_payload(
            [LlmChatMessage(role="user", content="hi")],
            provider="dashscope",
            model="qwen-max",
            temperature=0.5,
            max_tokens=1024,
        )
        self.assertEqual(payload, {
            "messages": [{"role": "user", "content": "hi"}],
            "provider": "dashscope",
            "model": "qwen-max",
            "temperature": 0.5,
            "maxTokens": 1024,
        })

    def test_llm_chat_maps_response(self):
        """同步大模型直连解析 R 封装响应"""
        response = Mock()
        response.json.return_value = {
            "code": 200,
            "data": {
                "content": "你好！",
                "model": "qwen-max",
                "provider": "dashscope",
                "promptTokens": 12,
                "completionTokens": 20,
            },
        }

        with patch.object(self.client, "_request", return_value=response) as mock_request:
            result = self.client.llm_chat(
                messages=[{"role": "user", "content": "你好"}],
                provider="dashscope",
                model="qwen-max",
            )

        mock_request.assert_called_once_with(
            "POST",
            "/v1/llm/chat",
            json_data={
                "messages": [{"role": "user", "content": "你好"}],
                "provider": "dashscope",
                "model": "qwen-max",
            },
        )
        self.assertIsInstance(result, LlmChatResponse)
        self.assertEqual(result.content, "你好！")
        self.assertEqual(result.model, "qwen-max")
        self.assertEqual(result.provider, "dashscope")
        self.assertEqual(result.prompt_tokens, 12)
        self.assertEqual(result.completion_tokens, 20)

    def test_llm_chat_stream_yields_sse_events(self):
        """流式大模型直连解析 SSE 事件"""
        client = self.client

        class FakeResponse:
            """模拟 requests.Response 流式对象"""

            status_code = 200

            def __init__(self):
                self.headers = {"Content-Type": "text/event-stream"}

            def iter_lines(self, decode_unicode=False):
                lines = [
                    "event: content_delta",
                    'data: {"delta": "Hello"}',
                    "",
                    "event: done",
                    'data: {"status": "completed"}',
                    "",
                ]
                return iter(lines)

        with patch.object(client.session, "request", return_value=FakeResponse()):
            events = list(client.llm_chat_stream(messages=[{"role": "user", "content": "hi"}]))

        self.assertEqual(len(events), 2)
        self.assertEqual(events[0].event, "content_delta")
        self.assertEqual(events[0].data, {"delta": "Hello"})
        self.assertEqual(events[1].event, "done")
        self.assertEqual(events[1].data, {"status": "completed"})

    def test_request_auto_relogin_on_401(self):
        """token 过期（401）时自动重新登录并重试一次"""
        client = self.client
        client._username = "admin"
        client._password = "admin123"

        def fake_login(username, password):
            # 模拟重新登录成功后更新 token
            client.token = "relogin-token"

        def fake_request_once(method, path, json_data=None, params=None, stream=False):
            # 未重新登录前返回 401，重登后返回正常响应
            if client.token != "relogin-token":
                raise ApiError(401, "Token expired or invalid")
            response = Mock()
            response.status_code = 200
            response.json.return_value = {"code": 200, "data": {}}
            return response

        with patch.object(client, "login", side_effect=fake_login), \
             patch.object(client, "_request_once", side_effect=fake_request_once):
            response = client._request("GET", "/v1/agents")

        self.assertIsNotNone(response)
        self.assertEqual(client.token, "relogin-token")

    def test_request_no_relogin_without_credentials(self):
        """未保存登录凭据时，401 不自动重登"""
        client = self.client  # _username / _password 均为 None

        def fake_request_once(method, path, json_data=None, params=None, stream=False):
            raise ApiError(401, "Token expired or invalid")

        with patch.object(client, "_request_once", side_effect=fake_request_once), \
             patch.object(client, "login") as mock_login:
            with self.assertRaises(ApiError) as ctx:
                client._request("GET", "/v1/agents")

        self.assertEqual(ctx.exception.code, 401)
        mock_login.assert_not_called()

    # ==================== 敏感字段传输加密 ====================

    def test_encrypt_sensitive_field_envelope(self):
        """加密信封可被对应私钥解出 "毫秒时间戳:明文" 结构"""
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        public_pem = private_key.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode("utf-8")

        response = Mock()
        response.json.return_value = {
            "code": 200,
            "data": {"publicKey": public_pem, "algorithm": "RSA-OAEP"},
        }

        with patch.object(self.client, "_request", return_value=response) as mock_request:
            envelope = self.client._encrypt_sensitive_field("p@ss中文123")

        mock_request.assert_called_once_with("GET", "/v1/auth/pubkey")
        inner = private_key.decrypt(
            base64.b64decode(envelope),
            padding.OAEP(
                mgf=padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None,
            ),
        )
        plain = base64.b64decode(inner).decode("utf-8")
        ts, _, value = plain.partition(":")
        self.assertTrue(ts.isdigit())
        self.assertLessEqual(abs(int(time.time() * 1000) - int(ts)), 60_000)
        self.assertEqual(value, "p@ss中文123")

    def test_encrypt_sensitive_field_missing_pubkey(self):
        """公钥响应缺失 publicKey 时抛出 ApiError"""
        response = Mock()
        response.json.return_value = {"code": 200, "data": {}}

        with patch.object(self.client, "_request", return_value=response):
            with self.assertRaises(ApiError):
                self.client._encrypt_sensitive_field("secret")

    def test_login_encrypts_password(self):
        """login 的 password 经加密信封传输"""
        response = Mock()
        response.json.return_value = {"code": 200, "data": {"token": "tok", "username": "admin"}}

        with patch.object(self.client, "_encrypt_sensitive_field", return_value="ENVELOPE") as mock_enc, \
             patch.object(self.client, "_request", return_value=response) as mock_request:
            result = self.client.login("admin", "admin123")

        mock_enc.assert_called_once_with("admin123")
        mock_request.assert_called_once_with(
            "POST",
            "/v1/auth/login",
            json_data={"username": "admin", "password": "ENVELOPE"},
        )
        self.assertEqual(result.token, "tok")

    def test_request_no_relogin_on_login_failure(self):
        """登录接口本身的 401 不触发自动重登（避免密码错误时无限循环）"""
        client = self.client
        client._username = "admin"
        client._password = "wrong-password"

        def fake_request_once(method, path, json_data=None, params=None, stream=False):
            raise ApiError(401, "用户名或密码错误")

        with patch.object(client, "_request_once", side_effect=fake_request_once), \
             patch.object(client, "login") as mock_login:
            with self.assertRaises(ApiError) as ctx:
                client._request("POST", "/v1/auth/login",
                                json_data={"username": "admin", "password": "wrong-password"})

        self.assertEqual(ctx.exception.code, 401)
        mock_login.assert_not_called()


class TestEvaluationResult(unittest.TestCase):
    """测评结果测试"""

    def test_evaluation_report_success_rate(self):
        """测试成功率计算"""
        from mateclaw_dataagent.models import EvaluationReport, EvaluationResult

        report = EvaluationReport(
            total=10,
            success=8,
            failed=2,
        )

        self.assertEqual(report.success_rate, 0.8)

    def test_evaluation_report_to_dict(self):
        """测试转换为字典"""
        from mateclaw_dataagent.models import EvaluationReport, EvaluationResult

        report = EvaluationReport(
            total=2,
            success=1,
            failed=1,
            results=[
                EvaluationResult(
                    question="Test question",
                    answer="Test answer",
                    success=True,
                )
            ],
        )

        data = report.to_dict()

        self.assertEqual(data["summary"]["total"], 2)
        self.assertEqual(data["summary"]["success"], 1)
        self.assertEqual(data["summary"]["success_rate"], 0.5)
        self.assertEqual(len(data["results"]), 1)

    def test_checker_has_answer(self):
        """测试 EvaluationChecker.check_has_answer"""
        from mateclaw_dataagent import EvaluationChecker
        from mateclaw_dataagent.models import EvaluationResult

        self.assertTrue(EvaluationChecker.check_has_answer(EvaluationResult(question="q", answer="回答内容")))
        self.assertFalse(EvaluationChecker.check_has_answer(EvaluationResult(question="q")))

    def test_checker_answer_type(self):
        """测试 EvaluationChecker.check_answer_type"""
        from mateclaw_dataagent import EvaluationChecker
        from mateclaw_dataagent.models import EvaluationResult

        result = EvaluationResult(question="q", answer="分析显示销售额增长", actual_type="analysis")
        self.assertTrue(EvaluationChecker.check_answer_type(result, "analysis"))
        self.assertFalse(EvaluationChecker.check_answer_type(result, "kpi"))


if __name__ == "__main__":
    unittest.main()
