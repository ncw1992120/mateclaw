package vip.mate.dataagent.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.service.code.CodeExecutorProperties;
import vip.mate.dataagent.service.code.CodeExecutorService;
import vip.mate.dataagent.service.code.impl.LocalCodeExecutorService;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

/**
 * Python 数据分析工具
 * <p>
 * 以 Plugin Tool 方式注册到 MateClaw ToolRegistry，
 * 供 Agent 调用 Python 进行复杂数据分析。
 * <p>
 * 借鉴 DataAgent 项目的 Python 分析经验：
 * <ul>
 *   <li>上下文过大会导致模型准确性下降，应由 Python 来分析更合适</li>
 *   <li>数据查询负责结构化数据获取（数据库 SQL 或指标平台查询），Python 负责复杂数据分析和计算</li>
 *   <li>查询结果作为 Python 的标准输入，避免大上下文问题</li>
 * </ul>
 * <p>
 * 暴露两个动作：
 * <ul>
 *   <li>execute_python — 执行 Python 代码进行数据分析</li>
 *   <li>check_environment — 检查 Python 执行环境是否可用</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAnalysisTool {

    private final MateClawRuntime mateClawRuntime;
    private final CodeExecutorProperties codeExecutorProperties;

    private CodeExecutorService codeExecutorService;

    private static final String TOOL_NAME = "python_analysis";

    private static final String TOOL_DESCRIPTION = """
            执行 Python 代码进行数据分析。适用于以下场景：
            1. 复杂数学计算和统计分析（相关性分析、假设检验、回归分析等）
            2. 数据清洗和预处理（缺失值处理、异常值检测、数据转换等）
            3. 机器学习分析（聚类、分类、预测等）
            4. 高级数据可视化（matplotlib/seaborn/plotly 图表）
            5. 时序分析、数据透视、多表关联计算等 SQL 难以完成的复杂分析

            核心设计：数据查询负责结构化数据获取，Python 负责复杂数据分析和计算。
            数据来源包括：
            - 数据库（通过 data_query 的 execute_sql 执行 SQL 查询获取结构化数据）
            - 指标平台（通过 aloudata_metrics_query 查询指标数据，或 aloudata_search_semantic 检索指标/维度元数据）
            - 业务术语（通过 search_business_term 查询术语定义和同义词，辅助理解业务含义）
            查询结果通过 input 参数传入 Python，避免大上下文导致模型准确性下降。

            典型工作流：
            1. 先通过数据查询工具（data_query / aloudata_metrics_query）获取所需数据
            2. 再通过 python_analysis 执行 Python 代码进行复杂分析
            3. 若用户提问涉及业务术语，先通过 search_business_term 查询术语含义

            支持两种动作：
            1. action='execute_python' — 执行 Python 代码（需要 code，可选 input 和 requirement）
            2. action='check_environment' — 检查 Python 执行环境是否可用（无需其他参数）

            Python 代码约定：
            - 使用 sys.stdin.read() 读取 input 数据（JSON 格式）
            - 使用 print() 输出分析结果
            - 输出结果应为可读的文本或 JSON 格式
            - 推荐使用 pandas 进行数据处理，使用 json.loads() 解析输入
            """;

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "description": "动作：execute_python / check_environment",
                  "enum": ["execute_python", "check_environment"]
                },
                "code": {
                  "type": "string",
                  "description": "要执行的 Python 代码（execute_python 时必填）。使用 sys.stdin.read() 读取 input 数据，使用 print() 输出结果。"
                },
                "input": {
                  "type": "string",
                  "description": "Python 代码的标准输入数据，通常是 JSON 格式的查询结果（来自 data_query 的 SQL 查询或 aloudata_metrics_query 的指标数据，execute_python 时可选）"
                },
                "requirement": {
                  "type": "string",
                  "description": "pip 依赖声明，每行一个包名（execute_python 时可选，默认已包含 pandas/numpy/json/sys）"
                }
              },
              "required": ["action"]
            }
            """;

    private static final String ACTION_EXECUTE_PYTHON = "execute_python";
    private static final String ACTION_CHECK_ENVIRONMENT = "check_environment";

    /** Python 代码模板：引导 LLM 生成规范的 Python 分析代码 */
    private static final String CODE_TEMPLATE_HINT = """
            # 推荐的 Python 分析代码模板：
            import sys
            import json
            import pandas as pd

            # 读取标准输入数据（SQL 查询结果的 JSON）
            input_data = sys.stdin.read()
            if input_data:
                data = json.loads(input_data)
                df = pd.DataFrame(data)

            # 在此编写分析逻辑
            # ...

            # 输出分析结果
            print(result)
            """;

    @PostConstruct
    public void register() {
        // 初始化 Python 执行器
        this.codeExecutorService = new LocalCodeExecutorService(codeExecutorProperties);

        SkillScopedToolCallback toolCallback = new SkillScopedToolCallback(
                TOOL_NAME,
                TOOL_DESCRIPTION,
                INPUT_SCHEMA,
                this::handleToolCall
        );
        mateClawRuntime.registerTool(toolCallback);
        log.info("PythonAnalysisTool registered as plugin tool: {}, available: {}",
                TOOL_NAME, codeExecutorService.isAvailable());
    }

    private String handleToolCall(String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            String action = input.getStr("action", "");
            return switch (action) {
                case ACTION_EXECUTE_PYTHON -> executePython(input);
                case ACTION_CHECK_ENVIRONMENT -> checkEnvironment();
                default -> error("未知动作: " + action + "，支持: execute_python / check_environment");
            };
        } catch (Exception e) {
            log.error("Python 分析失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 执行 Python 代码
     */
    private String executePython(JSONObject input) {
        String code = input.getStr("code");
        if (code == null || code.isBlank()) {
            return error("execute_python 需要 code 参数。\n\n推荐代码模板:\n" + CODE_TEMPLATE_HINT);
        }

        if (!codeExecutorService.isAvailable()) {
            return error("Python 执行环境不可用，请先使用 check_environment 检查环境配置。"
                    + " 确保系统已安装 Python 并配置在 PATH 中。");
        }

        String inputJson = input.getStr("input", "");
        String requirement = input.getStr("requirement", "");

        log.info("执行 Python 分析代码, 输入数据长度: {}, 依赖: {}",
                inputJson.length(), requirement.isBlank() ? "无" : requirement);

        // 支持重试机制
        CodeExecutorService.TaskResponse response = null;
        int maxRetries = codeExecutorProperties.getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            response = codeExecutorService.execute(
                    new CodeExecutorService.TaskRequest(code, inputJson, requirement)
            );

            if (response.isSuccess()) {
                break;
            }

            // 如果是执行异常（非代码错误），直接返回，不需要重试
            if (!response.executionSuccessButResultFailed()) {
                break;
            }

            // 代码执行失败，记录日志
            log.warn("Python 执行失败 (第 {}/{} 次), stderr: {}",
                    attempt, maxRetries, response.stdErr());

            // 最后一次尝试失败，不再重试
            if (attempt == maxRetries) {
                break;
            }
        }

        return formatResponse(response);
    }

    /**
     * 格式化 Python 执行结果
     */
    private String formatResponse(CodeExecutorService.TaskResponse response) {
        StringBuilder sb = new StringBuilder();

        if (response.isSuccess()) {
            sb.append("**Python 分析结果**:\n\n");
            if (response.stdOut() != null && !response.stdOut().isBlank()) {
                sb.append(response.stdOut());
            } else {
                sb.append("（无输出）");
            }
        } else if (response.executionSuccessButResultFailed()) {
            sb.append("**Python 执行失败**:\n\n");
            if (response.stdOut() != null && !response.stdOut().isBlank()) {
                sb.append("标准输出:\n").append(response.stdOut()).append("\n\n");
            }
            if (response.stdErr() != null && !response.stdErr().isBlank()) {
                sb.append("错误信息:\n").append(response.stdErr()).append("\n\n");
            }
            sb.append("请根据错误信息修正 Python 代码后重新执行。");
        } else {
            sb.append("**Python 执行异常**:\n\n");
            sb.append(response.exceptionMsg());
        }

        return sb.toString();
    }

    /**
     * 检查 Python 执行环境
     */
    private String checkEnvironment() {
        JSONObject result = new JSONObject();
        result.set("available", codeExecutorService.isAvailable());
        result.set("enabled", codeExecutorProperties.isEnabled());
        result.set("codeTimeoutSeconds", codeExecutorProperties.getCodeTimeoutSeconds());
        result.set("maxRetries", codeExecutorProperties.getMaxRetries());

        if (codeExecutorService.isAvailable()) {
            result.set("status", "Python 执行环境可用");
        } else if (!codeExecutorProperties.isEnabled()) {
            result.set("status", "Python 执行器已禁用（mateclaw.code-executor.enabled=false）");
        } else {
            result.set("status", "未检测到可用的 Python 解释器，请确保系统已安装 Python 并配置在 PATH 中");
        }

        return result.toStringPretty();
    }

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }
}
