"""
AI 测评验证脚本

用于验证 MateClaw DataAgent 的对话接口返回结果是否符合预期。
支持自定义验证规则，输出详细的验证报告。

使用方法:
    python verify_evaluation.py --agent-id 1 --cases cases.json --output results.json
"""

import argparse
import json
import sys
import time
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

# 添加父目录到路径，以便导入 mateclaw_dataagent
sys.path.insert(0, str(Path(__file__).parent.parent))

from mateclaw_dataagent import DataAgentClient, EvaluationRunner
from mateclaw_dataagent.models import EvaluationChecker, EvaluationResult


def check_rule(rule_type: str, config: dict, result: EvaluationResult) -> tuple[bool, str]:
    """
    校验单条规则（判断逻辑复用 EvaluationChecker）

    Args:
        rule_type: 规则类型
        config: 规则配置
        result: 测评结果

    Returns:
        tuple[bool, str]: (是否通过，原因)
    """
    if rule_type == "has_answer":
        passed = EvaluationChecker.check_has_answer(result)
        return passed, "有回答内容" if passed else "没有回答内容"

    if rule_type == "has_tool_call":
        tool_name = config.get("tool_name", "")
        passed = EvaluationChecker.check_has_tool_call(result, tool_name)
        return passed, f"调用了工具 {tool_name}" if passed else f"未调用工具 {tool_name}"

    if rule_type == "has_keyword":
        keywords = config.get("keywords", [])
        passed = EvaluationChecker.check_has_keyword(result, keywords)
        found = [kw for kw in keywords if kw.lower() in (result.answer or "").lower()]
        return passed, f"包含关键词：{found}" if passed else f"未包含关键词：{keywords}"

    if rule_type == "token_limit":
        max_prompt = config.get("max_prompt", 0)
        max_completion = config.get("max_completion", 0)
        passed = EvaluationChecker.check_token_limit(result, max_prompt, max_completion)
        reasons = []
        if max_prompt > 0 and result.tokens[0] > max_prompt:
            reasons.append(f"Prompt Token 超限：{result.tokens[0]} > {max_prompt}")
        if max_completion > 0 and result.tokens[1] > max_completion:
            reasons.append(f"Completion Token 超限：{result.tokens[1]} > {max_completion}")
        return passed, "; ".join(reasons) if reasons else "Token 使用在限制内"

    if rule_type == "latency_limit":
        max_ms = config.get("max_ms", 0)
        passed = EvaluationChecker.check_latency_limit(result, max_ms)
        return passed, f"延迟 {result.latency_ms}ms 在限制内" if passed else f"延迟超限：{result.latency_ms}ms > {max_ms}ms"

    if rule_type == "answer_type":
        expected_type = config.get("expected_type", "")
        passed = EvaluationChecker.check_answer_type(result, expected_type)
        return passed, f"回答类型匹配：{result.actual_type}" if passed else f"回答类型不匹配：{result.actual_type} != {expected_type}"

    return False, f"未知的规则类型 {rule_type}"


def load_test_cases(cases_file: str) -> list[dict]:
    """
    加载测评用例

    用例格式 (JSON):
    [
        {
            "question": "2024 年 Q1 的销售额是多少？",
            "expected_type": "kpi",
            "rules": [
                {"type": "has_answer"},
                {"type": "has_keyword", "keywords": ["销售额", "Q1"]},
                {"type": "token_limit", "max_prompt": 4000, "max_completion": 1000},
                {"type": "latency_limit", "max_ms": 10000}
            ]
        }
    ]
    """
    with open(cases_file, "r", encoding="utf-8") as f:
        return json.load(f)


_KNOWN_RULES = frozenset({
    "has_answer", "has_tool_call", "has_keyword",
    "token_limit", "latency_limit", "answer_type",
})


def create_rules(rule_configs: list[dict]) -> list[dict]:
    """根据配置筛选出合法的验证规则（判断逻辑由 check_rule 复用 EvaluationChecker）"""
    rules = []
    for config in rule_configs or []:
        rule_type = config.get("type")
        if rule_type not in _KNOWN_RULES:
            print(f"警告：未知的规则类型 {rule_type}")
            continue
        rules.append(config)
    return rules


def run_verification(
    client: DataAgentClient,
    agent_id: int,
    test_cases: list[dict],
    output_file: str,
    use_stream: bool = True,
) -> dict:
    """
    运行测评验证

    Args:
        client: DataAgent 客户端
        agent_id: Agent ID
        test_cases: 测评用例
        output_file: 输出文件路径
        use_stream: 是否使用流式对话

    Returns:
        dict 验证报告
    """
    report = {
        "generated_at": datetime.now().isoformat(),
        "agent_id": agent_id,
        "total": 0,
        "passed": 0,
        "failed": 0,
        "results": [],
    }

    runner = EvaluationRunner(client)

    for i, case in enumerate(test_cases):
        question = case.get("question", "")
        expected_type = case.get("expected_type")
        rule_configs = case.get("rules", [])
        conversation_id = case.get("conversation_id", f"verify-{int(time.time() * 1000)}-{i}")

        print(f"\n[{i + 1}/{len(test_cases)}] {question}")

        # 运行测评
        result = runner.run_single_test(
            agent_id=agent_id,
            question=question,
            conversation_id=conversation_id,
            expected_type=expected_type,
            use_stream=use_stream,
        )

        # 应用验证规则（判断逻辑复用 EvaluationChecker）
        rules = create_rules(rule_configs)
        rule_results = []

        for config in rules:
            passed, reason = check_rule(config.get("type"), config, result)
            rule_results.append({
                "rule_type": config.get("type"),
                "passed": passed,
                "reason": reason,
            })

        # 判断是否通过
        all_passed = all(r["passed"] for r in rule_results)

        report["results"].append({
            "question": question,
            "expected_type": expected_type,
            "actual_type": result.actual_type,
            "answer": result.answer[:500] if result.answer else "",  # 截断避免过长
            "answer_preview": result.answer[:100] + "..." if result.answer and len(result.answer) > 100 else result.answer,
            "tool_calls": result.tool_calls,
            "tokens": result.tokens,
            "latency_ms": result.latency_ms,
            "rule_results": rule_results,
            "passed": all_passed,
            "error": result.error,
        })

        report["total"] += 1
        if all_passed:
            report["passed"] += 1
            print(f"      ✓ 通过 ({len(rules)} 条规则)")
        else:
            report["failed"] += 1
            failed_rules = [r for r in rule_results if not r["passed"]]
            print(f"      ✗ 失败：{failed_rules[0]['reason'] if failed_rules else '未知'}")

    # 兜底一致性：以 passed 反推 failed（与循环内累计保持一致）
    report["failed"] = report["total"] - report["passed"]

    # 计算汇总统计
    if report["total"] > 0:
        report["pass_rate"] = report["passed"] / report["total"]
        report["avg_latency_ms"] = sum(r["latency_ms"] for r in report["results"]) / report["total"]
        report["avg_prompt_tokens"] = sum(r["tokens"][0] for r in report["results"]) / report["total"]
        report["avg_completion_tokens"] = sum(r["tokens"][1] for r in report["results"]) / report["total"]

    # 保存报告
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    return report


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="AI 测评验证脚本")
    parser.add_argument("--base-url", default="http://localhost:18089/dataagent/api",
                        help="DataAgent API 地址")
    parser.add_argument("--workspace-id", type=int, default=1,
                        help="工作区 ID")
    parser.add_argument("--username", default="admin",
                        help="用户名")
    parser.add_argument("--password", default="admin123",
                        help="密码")
    parser.add_argument("--agent-id", type=int, default=None,
                        help="Agent ID（默认按 --agent-name 名称查找）")
    parser.add_argument("--agent-name", default="数据分析助手",
                        help="默认 Agent 名称（未指定 --agent-id 时按此名称查找）")
    parser.add_argument("--cases", required=True,
                        help="测评用例文件路径 (JSON)")
    parser.add_argument("--output", default="verification_results.json",
                        help="输出文件路径")
    parser.add_argument("--no-stream", action="store_true",
                        help="不使用流式对话")

    args = parser.parse_args()

    print("=" * 60)
    print("MateClaw DataAgent AI 测评验证")
    print("=" * 60)

    # 初始化客户端
    client = DataAgentClient(
        base_url=args.base_url,
        workspace_id=args.workspace_id,
    )

    try:
        # 登录
        print(f"\n[1/4] 登录到 {args.base_url}...")
        login_response = client.login(username=args.username, password=args.password)
        print(f"      登录成功！用户：{login_response.username} ({login_response.nickname})")

        # 解析目标 Agent：显式 --agent-id 优先，否则按名称查找
        agent_id = args.agent_id
        if agent_id is None:
            agent = client.get_agent_by_name(args.agent_name)
            if not agent:
                print(f"      未找到名称为「{args.agent_name}」的 Agent，请使用 --agent-id 显式指定")
                return 1
            agent_id = agent.id
            print(f"      使用默认 Agent [{agent_id}] {agent.name}")

        # 加载测评用例
        print(f"\n[2/4] 加载测评用例 from {args.cases}...")
        test_cases = load_test_cases(args.cases)
        print(f"      共 {len(test_cases)} 个用例")

        # 运行验证
        print(f"\n[3/4] 开始验证 Agent [{agent_id}]...")
        report = run_verification(
            client=client,
            agent_id=agent_id,
            test_cases=test_cases,
            output_file=args.output,
            use_stream=not args.no_stream,
        )

        # 输出汇总
        print(f"\n[4/4] 验证完成！")
        print("\n" + "=" * 60)
        print("验证结果汇总")
        print("=" * 60)
        print(f"  总计：{report['total']} 个用例")
        print(f"  通过：{report['passed']} 个")
        print(f"  失败：{report['failed']} 个")
        print(f"  通过率：{report['pass_rate']:.2%}")
        print(f"  平均延迟：{report['avg_latency_ms']:.0f} ms")
        print(f"  平均 Token: {report['avg_prompt_tokens']:.0f} + {report['avg_completion_tokens']:.0f}")
        print(f"\n验证报告已保存到：{args.output}")

    except Exception as e:
        print(f"\n错误：{e}")
        import traceback
        traceback.print_exc()
        return 1

    finally:
        client.close()
        print("\n已关闭连接")

    return 0


if __name__ == "__main__":
    sys.exit(main())
