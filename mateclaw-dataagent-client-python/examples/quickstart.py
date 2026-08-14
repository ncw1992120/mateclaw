"""
快速入门示例 - 5 分钟上手 MateClaw DataAgent Python SDK

运行此脚本前，请确保:
1. MateClaw DataAgent 服务已启动 (默认 http://localhost:18089)
2. 已安装依赖：pip install requests

使用方法:
    python quickstart.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from mateclaw_dataagent import DataAgentClient


def main():
    # ==================== 1. 初始化客户端 ====================
    print("=" * 60)
    print("MateClaw DataAgent Python SDK 快速入门")
    print("=" * 60)

    client = DataAgentClient(
        base_url="http://localhost:18089/dataagent/api",
        workspace_id=1,
        timeout=60,
    )

    # ==================== 2. 登录 ====================
    print("\n[步骤 1] 登录...")
    try:
        client.login(username="admin", password="admin123")
        print("✓ 登录成功！")
    except Exception as e:
        print(f"✗ 登录失败：{e}")
        print("  请确保 DataAgent 服务已启动，且用户名密码正确")
        return 1

    # ==================== 3. 获取 Agent 列表 ====================
    print("\n[步骤 2] 获取 Agent 列表...")
    agents = client.list_agents()
    if not agents:
        print("✗ 没有找到 Agent")
        return 1

    print(f"✓ 找到 {len(agents)} 个 Agent:")
    for i, agent in enumerate(agents[:5], 1):  # 只显示前 5 个
        status = "✓" if agent.enabled else "✗"
        print(f"  {i}. {status} [{agent.id}] {agent.name}")

    if len(agents) > 5:
        print(f"  ... 还有 {len(agents) - 5} 个 Agent")

    # 解析目标 Agent：显式 AGENT_ID 优先，否则使用名称为「数据分析助手」的 Agent
    AGENT_ID = None  # 显式指定时优先；None 时按 AGENT_NAME 查找
    AGENT_NAME = "数据分析助手"
    if AGENT_ID is not None:
        target_agent = client.get_agent(AGENT_ID)
    else:
        target_agent = next((a for a in agents if a.name == AGENT_NAME), None)
    if not target_agent:
        print(f"✗ 未找到 Agent（显式 AGENT_ID 或名称为「{AGENT_NAME}」）")
        return 1

    print(f"\n→ 使用 Agent: [{target_agent.id}] {target_agent.name}")

    # ==================== 4. 同步对话 ====================
    print("\n[步骤 3] 同步对话测试...")
    question = "你好，请介绍一下你自己"
    print(f"用户：{question}")

    try:
        response = client.chat(
            agent_id=target_agent.id,
            message=question,
            conversation_id="quickstart-sync-001",
        )
        print(f"AI: {response.content[:200]}..." if len(response.content) > 200 else f"AI: {response.content}")
        print(f"✓ 同步对话成功！Token: {response.prompt_tokens} + {response.completion_tokens}")
    except Exception as e:
        print(f"✗ 对话失败：{e}")

    # ==================== 5. 流式对话 ====================
    print("\n[步骤 4] 流式对话测试...")
    question = "用一句话总结你的功能"
    print(f"用户：{question}")
    print("AI: ", end="", flush=True)

    try:
        content_parts = []
        for event in client.stream_chat(
            agent_id=target_agent.id,
            message=question,
            conversation_id="quickstart-stream-001",
        ):
            if event.event == "content_delta":
                print(event.delta, end="", flush=True)
                content_parts.append(event.delta)
            elif event.event == "thinking_delta":
                print(f"[思考]{event.delta}", end="", flush=True)
            elif event.event == "done":
                tokens = event.data.get("promptTokens", 0) + event.data.get("completionTokens", 0)
                print(f"\n✓ 流式对话成功！Token: {tokens}")
    except Exception as e:
        print(f"\n✗ 流式对话失败：{e}")

    # ==================== 6. 会话管理 ====================
    print("\n[步骤 5] 会话管理测试...")

    # 获取会话列表
    conversations = client.list_conversations()
    print(f"✓ 当前有 {len(conversations)} 个会话")

    # 重命名会话
    if conversations:
        conv = conversations[0]
        print(f"  重命名会话 '{conv.title}' 为 '快速入门测试'...")
        client.rename_conversation(conv.conversation_id, "快速入门测试")
        print("  ✓ 重命名成功")

    # ==================== 7. 完成 ====================
    print("\n" + "=" * 60)
    print("快速入门完成！")
    print("=" * 60)
    print("""
接下来你可以:
1. 查看 examples/ 目录中的更多示例
2. 运行 python examples/evaluation_demo.py 进行 AI 测评
3. 运行 python examples/verify_evaluation.py 进行结果验证
4. 阅读 README.md 了解完整 API 文档

常见问题:
- 连接失败：检查 DataAgent 服务是否启动
- 认证失败：检查用户名密码是否正确
- 没有 Agent: 在 DataAgent UI 中创建一个 Agent
""")

    client.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
