package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 能力信息
 */
@Data
public class AgentCapabilitiesResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long agentId;
    private String modelName;
    private String providerId;
    private List<String> modalities;
    private Long defaultVisionModelId;
    private String defaultVisionModelLabel;
    private Long defaultVideoModelId;
    private String defaultVideoModelLabel;
}
