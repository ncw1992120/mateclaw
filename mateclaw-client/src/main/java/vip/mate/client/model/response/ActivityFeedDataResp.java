package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 活动流分页数据
 */
@Data
public class ActivityFeedDataResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页码 */
    private int page;

    /** 每页大小 */
    private int size;

    /** 总记录数 */
    private long total;

    /** 活动记录列表 */
    private List<ActivityRowResp> records;
}
