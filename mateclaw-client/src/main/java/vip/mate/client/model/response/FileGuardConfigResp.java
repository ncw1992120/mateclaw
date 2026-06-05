package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文件防护配置
 */
@Data
public class FileGuardConfigResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 文件防护开关 */
    private Boolean fileGuardEnabled;

    /** 敏感路径列表 */
    private List<String> sensitivePaths;
}
