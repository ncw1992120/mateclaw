package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 浏览器健康检查响应
 */
@Data
public class BrowserHealthResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 总体状态 */
    private String overall;

    /** 诊断发现列表 */
    private List<BrowserFindingResp> findings;

    /** 修复建议列表 */
    private List<String> advice;
}
