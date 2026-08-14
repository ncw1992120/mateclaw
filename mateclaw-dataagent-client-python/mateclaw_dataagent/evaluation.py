"""
AI 测评模块
"""

import json
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

from .client import DataAgentClient
from .models import ApiError, EvaluationReport, EvaluationResult, SseEvent

# 回答类型推断关键词（依据回答内容特征）
_KPI_KEYWORDS = ("销售额", "利润", "收入", "同比", "环比", "增长率")
_CHART_KEYWORDS = ("图表", "趋势图", "柱状图", "饼图", "折线图")
_ANALYSIS_KEYWORDS = ("分析", "趋势", "原因", "建议", "总结")


class EvaluationRunner:
    """
    AI 测评运行器

    用于批量运行测评用例，收集结果并生成报告。

    使用示例:
        client = DataAgentClient("http://localhost:18089/dataagent/api", workspace_id=1)
        client.login("admin", "admin123")

        runner = EvaluationRunner(client)
        results = runner.run_evaluation(
            agent_id=1,
            test_cases=[
                {"question": "2024 年 Q1 的销售额是多少？", "expected_type": "kpi"},
                {"question": "各产品线的利润对比", "expected_type": "chart"},
            ],
            output_file="evaluation_results.json"
        )

        print(f"成功率：{results.success_rate:.2%}")
        client.close()
    """

    def __init__(self, client: DataAgentClient):
        """
        初始化测评运行器

        Args:
            client: DataAgent 客户端
        """
        self.client = client

    def run_evaluation(
        self,
        agent_id: int,
        test_cases: list,
        output_file: Optional[str] = None,
        use_stream: bool = False,
    ) -> EvaluationReport:
        """
        运行测评

        Args:
            agent_id: Agent ID
            test_cases: 测评用例列表，每个用例包含:
                - question: 问题
                - expected_type: 期望的回答类型（可选）
                - conversation_id: 会话 ID（可选，默认自动生成）
            output_file: 输出文件路径（可选）
            use_stream: 是否使用流式对话

        Returns:
            EvaluationReport 测评报告
        """
        report = EvaluationReport()
        results = []

        for i, case in enumerate(test_cases):
            question = case.get("question", "")
            expected_type = case.get("expected_type")
            conversation_id = case.get("conversation_id", f"eval-{int(time.time() * 1000)}-{i}")

            result = self.run_single_test(
                agent_id=agent_id,
                question=question,
                conversation_id=conversation_id,
                expected_type=expected_type,
                use_stream=use_stream,
            )
            results.append(result)

        report.results = results
        report.total = len(results)
        report.success = sum(1 for r in results if r.success)
        report.failed = report.total - report.success

        # 计算平均值
        if results:
            report.avg_latency_ms = sum(r.latency_ms for r in results) / len(results)
            total_prompt = sum(r.tokens[0] for r in results)
            total_completion = sum(r.tokens[1] for r in results)
            report.avg_tokens = (
                total_prompt / len(results),
                total_completion / len(results),
            )

        # 输出文件
        if output_file:
            self._save_report(report, output_file)

        return report

    def run_single_test(
        self,
        agent_id: int,
        question: str,
        conversation_id: str,
        expected_type: Optional[str] = None,
        use_stream: bool = False,
    ) -> EvaluationResult:
        """
        运行单个测评用例

        Args:
            agent_id: Agent ID
            question: 问题
            conversation_id: 会话 ID
            expected_type: 期望的回答类型
            use_stream: 是否使用流式对话

        Returns:
            EvaluationResult 测评结果
        """
        start_time = time.time()
        result = EvaluationResult(
            question=question,
            expected_type=expected_type,
        )

        try:
            if use_stream:
                # 流式对话：分别累积思考与回答内容
                thinking_parts = []
                content_parts = []
                tool_calls = []
                prompt_tokens = 0
                completion_tokens = 0

                for event in self.client.stream_chat(
                    agent_id=agent_id,
                    message=question,
                    conversation_id=conversation_id,
                ):
                    if event.event == "thinking_delta":
                        thinking_parts.append(event.delta)
                    elif event.event == "content_delta":
                        content_parts.append(event.delta)
                    elif event.event == "tool_call_started":
                        tool_calls.append({
                            "name": event.data.get("toolName", ""),
                            "status": "started",
                        })
                    elif event.event == "tool_call_completed":
                        tool_calls.append({
                            "name": event.data.get("toolName", ""),
                            "status": "completed",
                            "result": event.data.get("result", ""),
                        })
                    elif event.event == "done":
                        prompt_tokens = event.data.get("promptTokens", 0)
                        completion_tokens = event.data.get("completionTokens", 0)

                result.answer = "".join(content_parts)
                result.thinking = "".join(thinking_parts)
                result.tool_calls = tool_calls
                result.tokens = (prompt_tokens, completion_tokens)

                # 判断回答类型
                result.actual_type = self._infer_answer_type(
                    content=result.answer,
                    tool_calls=tool_calls,
                )

            else:
                # 同步对话：接口仅返回纯文本，从消息历史回读 token 与工具调用信息
                response = self.client.chat(
                    agent_id=agent_id,
                    message=question,
                    conversation_id=conversation_id,
                )
                result.answer = response.content
                result.tool_calls = response.tool_calls
                result.tokens = (response.prompt_tokens, response.completion_tokens)
                self._enrich_from_history(conversation_id, result)
                result.actual_type = self._infer_answer_type(
                    content=result.answer,
                    tool_calls=result.tool_calls,
                )

            result.success = self._evaluate_result(result, expected_type)
            result.latency_ms = int((time.time() - start_time) * 1000)

        except Exception as e:
            result.error = str(e)
            result.success = False
            result.latency_ms = int((time.time() - start_time) * 1000)

        return result

    def _infer_answer_type(
        self,
        content: str,
        tool_calls: list,
    ) -> str:
        """
        推断回答类型

        Args:
            content: 回答内容
            tool_calls: 工具调用列表

        Returns:
            str 回答类型
        """
        # 检查是否有工具调用
        if tool_calls:
            tool_names = [tc.get("name", "") for tc in tool_calls]
            if "execute_sql" in tool_names:
                return "sql_query"
            if "search_schema" in tool_names:
                return "schema_search"
            if "readFile" in tool_names:
                return "file_read"

        # 检查内容特征
        content_lower = content.lower() if content else ""

        # 检查是否是 KPI/指标回答
        if any(kw in content_lower for kw in _KPI_KEYWORDS):
            if any(char.isdigit() for char in content):
                return "kpi"

        # 检查是否是图表
        if any(kw in content_lower for kw in _CHART_KEYWORDS):
            return "chart"

        # 检查是否是分析
        if any(kw in content_lower for kw in _ANALYSIS_KEYWORDS):
            return "analysis"

        # 默认类型
        return "general"

    def _evaluate_result(
        self,
        result: EvaluationResult,
        expected_type: Optional[str] = None,
    ) -> bool:
        """
        评估测评结果

        Args:
            result: 测评结果
            expected_type: 期望的回答类型

        Returns:
            bool 是否成功
        """
        # 如果没有期望类型，只要有回答就认为成功
        if expected_type is None:
            return bool(result.answer) and result.error is None

        # 检查回答类型是否匹配
        return result.actual_type == expected_type

    def _enrich_from_history(self, conversation_id: str, result: EvaluationResult):
        """
        同步接口仅返回纯文本，通过消息历史补齐 token 与工具调用信息

        Args:
            conversation_id: 会话 ID
            result: 待补充的测评结果
        """
        try:
            messages = self.client.get_messages(conversation_id)
        except ApiError:
            return

        assistant_messages = [m for m in messages if m.role == "assistant"]
        if not assistant_messages:
            return

        last = assistant_messages[-1]
        if last.prompt_tokens is not None or last.completion_tokens is not None:
            result.tokens = (last.prompt_tokens or 0, last.completion_tokens or 0)

        metadata = last.metadata or {}
        tool_calls = metadata.get("toolCalls") or []
        if tool_calls:
            result.tool_calls = [
                {
                    "name": tc.get("name", ""),
                    "status": tc.get("status", ""),
                    "result": tc.get("result", ""),
                }
                for tc in tool_calls
            ]

    def _save_report(self, report: EvaluationReport, output_file: str):
        """
        保存测评报告

        Args:
            report: 测评报告
            output_file: 输出文件路径
        """
        output_path = Path(output_file)
        output_path.parent.mkdir(parents=True, exist_ok=True)

        report_data = report.to_dict()
        report_data["generated_at"] = datetime.now().isoformat()

        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(report_data, f, ensure_ascii=False, indent=2)

    def run_stream_evaluation(
        self,
        agent_id: int,
        test_cases: list,
        output_file: Optional[str] = None,
    ) -> EvaluationReport:
        """
        使用流式对话运行测评

        Args:
            agent_id: Agent ID
            test_cases: 测评用例列表
            output_file: 输出文件路径

        Returns:
            EvaluationReport 测评报告
        """
        return self.run_evaluation(
            agent_id=agent_id,
            test_cases=test_cases,
            output_file=output_file,
            use_stream=True,
        )


class EvaluationChecker:
    """
    测评结果验证器

    用于验证测评结果是否符合预期。
    """

    @staticmethod
    def check_has_answer(result: EvaluationResult) -> bool:
        """检查是否包含回答内容"""
        return bool(result.answer)

    @staticmethod
    def check_answer_type(result: EvaluationResult, expected_type: str) -> bool:
        """检查回答类型是否与期望一致"""
        return result.actual_type == expected_type

    @staticmethod
    def check_has_tool_call(result: EvaluationResult, tool_name: str) -> bool:
        """检查是否调用了指定工具"""
        return any(tc.get("name") == tool_name for tc in result.tool_calls)

    @staticmethod
    def check_has_keyword(result: EvaluationResult, keywords: list) -> bool:
        """检查回答是否包含关键词"""
        content = result.answer.lower()
        return any(kw.lower() in content for kw in keywords)

    @staticmethod
    def check_token_limit(
        result: EvaluationResult,
        max_prompt: int = 0,
        max_completion: int = 0,
    ) -> bool:
        """检查 Token 使用是否在限制内"""
        if max_prompt > 0 and result.tokens[0] > max_prompt:
            return False
        if max_completion > 0 and result.tokens[1] > max_completion:
            return False
        return True

    @staticmethod
    def check_latency_limit(result: EvaluationResult, max_ms: int) -> bool:
        """检查延迟是否在限制内"""
        return result.latency_ms <= max_ms
