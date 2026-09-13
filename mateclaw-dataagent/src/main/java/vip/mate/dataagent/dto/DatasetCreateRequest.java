package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建数据集请求
 */
@Data
public class DatasetCreateRequest {

    private String name;

    private String description;

    private String datasourceId;

    private List<String> tableIds;

    /** 新版类型化来源定义；为空时沿用旧 JDBC_TABLE 请求。 */
    private DatasetSourceDefinition sourceDefinition;
}
