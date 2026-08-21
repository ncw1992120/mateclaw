"""
示例脚本 - 大模型直连（无持久化）

演示 DataAgent 的大模型直连接口：不经过 Agent / 会话体系，
服务端不产生任何会话/消息记录，等价于通过 HTTP 直接调用大模型。

同时演示消息 role 的三种角色作用：
- system    : 系统提示。设定模型身份/行为/输出约束，放最前面，优先级最高。
- user      : 用户输入。本次要回答的内容。
- assistant : 助手历史回复。携带多轮上下文，或注入示范回答（few-shot）。
消息按数组顺序拼接成完整上下文。
"""

import sys
from pathlib import Path

# 添加父目录到路径，以便导入 mateclaw_dataagent
sys.path.insert(0, str(Path(__file__).parent.parent))

from mateclaw_dataagent import DataAgentClient, LlmChatMessage


def main():
    """运行大模型直连示例"""

    # ==================== 配置 ====================
    BASE_URL = "http://localhost:18089/dataagent/api"
    WORKSPACE_ID = 1
    USERNAME = "admin"
    PASSWORD = "admin123"
    # 可选：指定 provider / model；None 时使用服务端默认模型
    PROVIDER = None
    MODEL = None
    # ===========================================

    print("=" * 60)
    print("MateClaw DataAgent 大模型直连示例（无持久化）")
    print("=" * 60)

    with DataAgentClient(
        base_url=BASE_URL,
        workspace_id=WORKSPACE_ID,
    ) as client:

        # 登录
        print(f"\n[1/4] 登录...")
        try:
            login_response = client.login(username=USERNAME, password=PASSWORD)
            print(f"      登录成功！用户：{login_response.username}")
        except Exception as e:
            print(f"      登录失败：{e}")
            return 1

        # ========== 2. system 角色：设定身份与行为约束 ==========
        print(f"\n[2/4] role=system 设定助手身份与约束\n")
        try:
            response = client.llm_chat(
                messages=[
                    {"role": "system", "content": "你是一个严谨的财务分析师。"
                                                  "回答必须基于数据推理，禁止编造数字；"
                                                  "每条结论控制在 50 字内。"},
                    {"role": "user", "content": "如何评估一家公司的短期偿债能力？"},
                ],
                provider=PROVIDER,
                model=MODEL,
                temperature=0.3,
            )
            print(f"用户：如何评估一家公司的短期偿债能力？")
            print(f"回答：{response.content}")
            print(f"模型：{response.model}（{response.provider}）")
        except Exception as e:
            print(f"错误：{e}")
            return 1

        # ========== 3. assistant 角色：多轮上下文延续 ==========
        #    第一轮：user/assistant 构成历史；第二轮：真正的 user 提问
        print(f"\n[3/4] role=assistant 携带多轮上下文（多轮对话）\n")
        messages = [
            LlmChatMessage(role="system", content="你是一个只讲冷笑话的助手。"),
            LlmChatMessage(role="user", content="给我讲一个关于程序员的笑话。"),
            LlmChatMessage(role="assistant",
                           content="程序员最讨厌的两件事：一是别人不写注释，二是让自己写注释。"),
            # 以上为历史轮次；以下为本轮新提问，模型需结合上文继续回答
            LlmChatMessage(role="user", content="再讲一个关于产品经理的笑话。"),
        ]
        try:
            response = client.llm_chat(
                messages=messages,
                provider=PROVIDER,
                model=MODEL,
            )
            print(f"用户（第 2 轮）：再讲一个关于产品经理的笑话。")
            print(f"回答：{response.content}")
            print(f"提示：模型是基于上文 <system/assistant 历史> + 本轮 user 消息 生成的")
        except Exception as e:
            print(f"错误：{e}")
            return 1

        # ========== 4. 流式直连对话（演示 user 单条消息） ==========
        print(f"\n[4/4] 大模型直连 - 流式\n")
        print("回答：", end="", flush=True)
        try:
            for event in client.llm_chat_stream(
                messages=[
                    {"role": "user", "content": "讲一个关于数据的小故事，不超过三句话"},
                ],
                provider=PROVIDER,
                model=MODEL,
            ):
                if event.event == "content_delta":
                    print(event.delta, end="", flush=True)
                elif event.event == "done":
                    print("\n[完成]", flush=True)
        except Exception as e:
            print(f"\n错误：{e}")
            return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
