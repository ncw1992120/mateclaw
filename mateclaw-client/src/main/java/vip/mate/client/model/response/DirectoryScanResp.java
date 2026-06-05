package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 目录扫描结果
 */
@Data
public class DirectoryScanResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已扫描数量 */
    private int scanned;

    /** 已添加数量 */
    private int added;

    /** 已跳过数量 */
    private int skipped;

    /** 错误信息列表 */
    private List<String> errors;
}
