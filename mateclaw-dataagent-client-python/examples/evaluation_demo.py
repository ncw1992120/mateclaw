"""
示例脚本 - AI 测评
"""

import sys
from pathlib import Path

# 添加父目录到路径，以便导入 mateclaw_dataagent
sys.path.insert(0, str(Path(__file__).parent.parent))

from mateclaw_dataagent import DataAgentClient, EvaluationRunner


def main():
    """运行 AI 测评示例"""

    # ==================== 配置 ====================
    BASE_URL = "http://localhost:18089/dataagent/api"
    WORKSPACE_ID = 1
    USERNAME = "admin"
    PASSWORD = "admin123"
    # 显式指定 Agent ID 时优先使用；None 时按 AGENT_NAME 名称查找
    AGENT_ID = None
    AGENT_NAME = "数据分析助手"  # 默认测评 Agent 名称
    OUTPUT_FILE = "evaluation_results.json"
    # ===========================================

    print("=" * 60)
    print("MateClaw DataAgent AI 测评")
    print("=" * 60)

    # 初始化客户端
    client = DataAgentClient(
        base_url=BASE_URL,
        workspace_id=WORKSPACE_ID,
    )

    try:
        # 登录
        print(f"\n[1/4] 登录到 {BASE_URL}...")
        login_response = client.login(username=USERNAME, password=PASSWORD)
        print(f"      登录成功！用户：{login_response.username} ({login_response.nickname})")
        print(f"      工作区：{[w.name for w in login_response.workspaces]}")

        # 获取 Agent 列表
        print(f"\n[2/4] 获取 Agent 列表...")
        agents = client.list_agents()
        print(f"      共有 {len(agents)} 个 Agent:")
        for agent in agents:
            status = "✓" if agent.enabled else "✗"
            print(f"        {status} [{agent.id}] {agent.name} ({agent.agent_type})")

        # 解析目标 Agent：显式 AGENT_ID 优先，否则按默认名称查找
        if AGENT_ID is None:
            target = client.get_agent_by_name(AGENT_NAME)
            if target is None:
                print(f"      未找到名称为「{AGENT_NAME}」的 Agent，请显式设置 AGENT_ID")
                return 1
            AGENT_ID = target.id
            print(f"      使用默认 Agent [{AGENT_ID}] {target.name}")

        # 准备测评用例
        test_cases = [
            {
                "question": "2024 年 Q1 的销售额是多少？",
                "expected_type": "kpi",
            },
            {
                "question": "各产品线的利润对比分析",
                "expected_type": "chart",
            },
            {
                "question": "用户增长趋势如何？",
                "expected_type": "analysis",
            },
            {
                "question": "上个月的销售数据",
                "expected_type": "sql_query",
            },
        ]

        print(f"\n[3/4] 准备测评 {len(test_cases)} 个用例...")
        for i, case in enumerate(test_cases, 1):
            print(f"      {i}. {case['question']} (期望类型：{case['expected_type']})")

        # 运行测评
        print(f"\n[4/4] 开始测评...")
        runner = EvaluationRunner(client)
        results = runner.run_evaluation(
            agent_id=AGENT_ID,
            test_cases=test_cases,
            output_file=OUTPUT_FILE,
            use_stream=True,  # 使用流式对话
        )

        # 输出结果
        print("\n" + "=" * 60)
        print("测评结果")
        print("=" * 60)
        print(f"  总计：{results.total} 个用例")
        print(f"  成功：{results.success} 个")
        print(f"  失败：{results.failed} 个")
        print(f"  成功率：{results.success_rate:.2%}")
        print(f"  平均延迟：{results.avg_latency_ms:.0f} ms")
        print(f"  平均 Token: {results.avg_tokens[0]:.0f} + {results.avg_tokens[1]:.0f}")

        print("\n详细结果:")
        for i, result in enumerate(results.results, 1):
            status = "✓" if result.success else "✗"
            print(f"\n  {i}. {result.question}")
            print(f"     状态：{status} {result.actual_type} (期望：{result.expected_type})")
            print(f"     延迟：{result.latency_ms} ms")
            print(f"     Token: {result.tokens[0]} + {result.tokens[1]}")
            if result.error:
                print(f"     错误：{result.error}")
            if result.tool_calls:
                print(f"     工具调用：{[tc.get('name') for tc in result.tool_calls]}")

        print(f"\n测评报告已保存到：{OUTPUT_FILE}")

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
