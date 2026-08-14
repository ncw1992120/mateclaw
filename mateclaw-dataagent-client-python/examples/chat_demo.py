"""
示例脚本 - 基础对话
"""

import sys
from pathlib import Path

# 添加父目录到路径，以便导入 mateclaw_dataagent
sys.path.insert(0, str(Path(__file__).parent.parent))

from mateclaw_dataagent import DataAgentClient


def main():
    """运行基础对话示例"""

    # ==================== 配置 ====================
    BASE_URL = "http://localhost:18089/dataagent/api"
    WORKSPACE_ID = 1
    USERNAME = "admin"
    PASSWORD = "admin123"
    # 显式指定 Agent ID 时优先使用；None 时按 AGENT_NAME 名称查找
    AGENT_ID = None
    AGENT_NAME = "数据分析助手"  # 默认对话 Agent 名称
    # ===========================================

    print("=" * 60)
    print("MateClaw DataAgent 基础对话示例")
    print("=" * 60)

    # 使用上下文管理器自动关闭连接
    with DataAgentClient(
        base_url=BASE_URL,
        workspace_id=WORKSPACE_ID,
    ) as client:

        # 登录
        print(f"\n[1/3] 登录...")
        try:
            login_response = client.login(username=USERNAME, password=PASSWORD)
            print(f"      登录成功！用户：{login_response.username}")
        except Exception as e:
            print(f"      登录失败：{e}")
            return 1

        # 解析目标 Agent：显式 AGENT_ID 优先，否则按默认名称查找
        print(f"\n[2/3] 获取 Agent...")
        if AGENT_ID is None:
            agent = client.get_agent_by_name(AGENT_NAME)
            if not agent:
                print(f"      未找到名称为「{AGENT_NAME}」的 Agent，请显式设置 AGENT_ID")
                return 1
            AGENT_ID = agent.id
        else:
            agent = client.get_agent(AGENT_ID)
            if not agent:
                print(f"      Agent [{AGENT_ID}] 不存在")
                return 1
        print(f"      名称：{agent.name}")
        print(f"      类型：{agent.agent_type}")
        print(f"      模型：{agent.model_name}")

        # 对话示例
        print(f"\n[3/3] 开始对话...\n")

        questions = [
            "你好，请介绍一下你自己",
            "2024 年的销售数据如何？",
        ]

        for question in questions:
            print(f"用户：{question}")
            print("-" * 40)

            try:
                # 同步对话
                response = client.chat(
                    agent_id=AGENT_ID,
                    message=question,
                    conversation_id=f"demo-{id(question)}",
                )
                print(f"AI: {response.content[:200]}..." if len(response.content) > 200 else f"AI: {response.content}")
                print(f"    (Token: {response.prompt_tokens} + {response.completion_tokens})")

            except Exception as e:
                print(f"错误：{e}")

            print()

        # 流式对话示例
        print("\n" + "=" * 60)
        print("流式对话示例")
        print("=" * 60 + "\n")

        question = "请分析销售趋势"
        print(f"用户：{question}")
        print("-" * 40)
        print("AI: ", end="", flush=True)

        try:
            for event in client.stream_chat(
                agent_id=AGENT_ID,
                message=question,
                conversation_id="demo-stream-001",
            ):
                if event.event == "thinking_delta":
                    print(f"[思考] {event.delta}", end="", flush=True)
                elif event.event == "content_delta":
                    print(event.delta, end="", flush=True)
                elif event.event == "tool_call_started":
                    print(f"\n[工具调用] {event.data.get('toolName')}...", flush=True)
                elif event.event == "done":
                    print(f"\n\n[完成] Token: {event.data.get('promptTokens', 0)} + {event.data.get('completionTokens', 0)}")

        except Exception as e:
            print(f"\n错误：{e}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
