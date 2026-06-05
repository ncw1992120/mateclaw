package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 应用发现的模型请求
 */
@Data
public class ApplyDiscoveredModelsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 待应用的模型 ID 列表 */
    private List<String> modelIds;
}
