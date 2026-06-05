package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 梦境报告分页数据
 */
@Data
public class DreamReportPageResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 报告记录列表 */
    private List<DreamReportResp> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private long page;

    /** 每页大小 */
    private long size;
}
