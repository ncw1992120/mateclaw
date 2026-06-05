package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Provider 模型发现结果
 */
@Data
public class DiscoverResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 远端发现的所有模型 */
    private List<ModelInfoResp> discoveredModels;

    /** 新增的模型 */
    private List<ModelInfoResp> newModels;

    /** 发现的模型总数 */
    private int totalDiscovered;

    /** 新增模型数量 */
    private int newCount;
}
