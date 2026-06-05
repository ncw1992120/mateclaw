package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 安全扫描发现
 */
@Data
public class SecurityFindingResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String ruleId;
    private String severity;
    private String category;
    private String title;
    private String description;
    private String filePath;
    private Integer lineNumber;
    private String snippet;
    private String remediation;
}
