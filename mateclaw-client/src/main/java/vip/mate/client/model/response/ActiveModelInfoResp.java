package vip.mate.client.model.response;

import lombok.Data;
import vip.mate.client.model.request.ModelSlotConfigReq;

import java.io.Serializable;

/**
 * 活跃模型信息
 */
@Data
public class ActiveModelInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前活跃 LLM 配置 */
    private ModelSlotConfigReq activeLlm;
}
