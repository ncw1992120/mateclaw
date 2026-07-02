package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 帮助中心批量排序请求对象
 */
@Data
public class HelpReorderRequest {

    /** 按目标顺序排列的 ID 列表 */
    private List<String> ids;
}
