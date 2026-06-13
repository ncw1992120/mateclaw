package vip.mate.dataagent.service.code;

/**
 * Python 代码执行服务接口
 * <p>
 * 借鉴 DataAgent 项目的 Python 分析经验，将数据分析逻辑从 LLM 上下文中剥离，
 * 由 Python 执行引擎完成复杂数据计算，避免大上下文导致模型准确性下降。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>SQL 负责结构化数据查询，Python 负责复杂数据分析和计算</li>
 *   <li>SQL 查询结果作为 Python 的标准输入，由 Python 在隔离环境中处理</li>
 *   <li>LLM 仅负责生成 Python 代码和解读分析结果，不直接处理大量数据</li>
 * </ul>
 */
public interface CodeExecutorService {

    /**
     * 执行 Python 代码任务
     *
     * @param request 任务请求
     * @return 任务响应
     */
    TaskResponse execute(TaskRequest request);

    /**
     * 检查 Python 执行环境是否可用
     *
     * @return true 表示环境可用
     */
    boolean isAvailable();

    /**
     * Python 执行任务请求
     *
     * @param code        Python 代码
     * @param input       标准输入数据（通常是 JSON 格式的 SQL 查询结果）
     * @param requirement pip 依赖声明（每行一个包名，如 pandas==2.0.0）
     */
    record TaskRequest(String code, String input, String requirement) {
    }

    /**
     * Python 执行任务响应
     *
     * @param isSuccess                    是否完全成功
     * @param executionSuccessButResultFailed 代码执行成功但结果异常（exitCode != 0）
     * @param stdOut                       标准输出
     * @param stdErr                       标准错误
     * @param exceptionMsg                 异常信息
     */
    record TaskResponse(boolean isSuccess, boolean executionSuccessButResultFailed,
                        String stdOut, String stdErr, String exceptionMsg) {

        /**
         * 执行任务时发生异常
         */
        public static TaskResponse exception(String msg) {
            return new TaskResponse(false, false, null, null,
                    "Python 执行异常: " + msg);
        }

        /**
         * 执行成功，代码正常返回
         */
        public static TaskResponse success(String stdOut) {
            return new TaskResponse(true, false, stdOut, null, null);
        }

        /**
         * 代码执行完成但返回非零退出码
         */
        public static TaskResponse failure(String stdOut, String stdErr) {
            return new TaskResponse(false, true, stdOut, stdErr,
                    "Python 执行失败, stderr: " + stdErr);
        }
    }
}
