package vip.mate.sdk.service;

import vip.mate.sdk.service.agent.AgentRuntime;
import vip.mate.sdk.service.datasource.DatasourceRuntime;
import vip.mate.sdk.service.model.ModelRuntime;
import vip.mate.sdk.service.skill.SkillRuntime;
import vip.mate.sdk.service.tool.ToolRuntime;
import vip.mate.sdk.service.workspace.WorkspaceRuntime;

/**
 * MateClaw 嵌入式运行时接口
 * <p>
 * 提供对 MateClaw 核心业务能力的编程式访问，是 SDK 的主入口接口。
 * 继承各领域子接口，宿主应用通过注入此接口即可使用 MateClaw 的全部能力，
 * 无需直接依赖内部服务实现。
 * <p>
 * 如需按领域注入，可直接使用各子接口：
 * <ul>
 *   <li>{@link AgentRuntime} — Agent 对话、CRUD、模板应用</li>
 *   <li>{@link DatasourceRuntime} — 数据源 CRUD、连接测试、启停</li>
 *   <li>{@link ToolRuntime} — 工具注册、禁用、查询、Agent 能力绑定</li>
 *   <li>{@link ModelRuntime} — Provider/模型管理、发现、测试</li>
 *   <li>{@link SkillRuntime} — 技能 CRUD、导入安装</li>
 *   <li>{@link WorkspaceRuntime} — 工作区管理、权限、成员</li>
 * </ul>
 */
public interface MateClawRuntime extends AgentRuntime, DatasourceRuntime, ToolRuntime,
        ModelRuntime, SkillRuntime, WorkspaceRuntime {
}
