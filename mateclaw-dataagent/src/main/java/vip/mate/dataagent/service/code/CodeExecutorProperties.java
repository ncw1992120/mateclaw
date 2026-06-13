package vip.mate.dataagent.service.code;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python 代码执行器配置属性
 */
@Getter
@Setter
@ConfigurationProperties(prefix = CodeExecutorProperties.CONFIG_PREFIX)
public class CodeExecutorProperties {

    public static final String CONFIG_PREFIX = "mateclaw.code-executor";

    /** Python 解释器命令，默认自动检测系统 PATH 中的 Python */
    private String pythonCommand = null;

    /** pip 命令，默认自动检测系统 PATH 中的 pip */
    private String pipCommand = null;

    /** Python 代码执行超时时间（秒） */
    private long codeTimeoutSeconds = 60;

    /** pip 安装依赖超时时间（秒） */
    private long pipTimeoutSeconds = 120;

    /** 是否启用 Python 执行器 */
    private boolean enabled = true;

    /** 临时工作目录前缀 */
    private String workDirPrefix = "mateclaw-python-";

    /** Python 执行最大重试次数 */
    private int maxRetries = 3;

    /** 标准输出最大长度（字符数），超过则截断 */
    private int maxStdOutLength = 50000;

    /** 标准错误最大长度（字符数），超过则截断 */
    private int maxStdErrLength = 10000;
}
