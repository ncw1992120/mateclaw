package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 浏览器诊断发现项
 */
@Data
public class BrowserFindingResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 检查项 ID */
    private String id;

    /** 检查状态 */
    private String status;

    /** 描述 */
    private String message;

    /** 附加数据 */
    private Map<String, Object> data;

    /** 修复建议 */
    private String advice;
}
