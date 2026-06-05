package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 技能策展器状态
 */
@Data
public class CuratorStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 配置信息 */
    private Map<String, Object> config;

    /** 控制信息 */
    private Map<String, Object> control;

    /** 计数信息 */
    private Map<String, Object> counts;

    /** 最近报告 */
    private Map<String, Object> lastReport;
}
