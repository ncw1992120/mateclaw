package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 活动流记录行
 */
@Data
public class ActivityRowResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private String id;

    /** 来源（audit/approval） */
    private String source;

    /** 时间戳 */
    private LocalDateTime time;

    /** 用户名 */
    private String username;

    /** 操作 */
    private String action;

    /** 资源类型 */
    private String resourceType;

    /** 资源名称 */
    private String resourceName;

    /** IP地址 */
    private String ipAddress;

    /** 详情 */
    private Map<String, Object> detail;
}
