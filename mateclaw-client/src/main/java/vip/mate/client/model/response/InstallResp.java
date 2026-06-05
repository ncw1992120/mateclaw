package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能安装结果
 */
@Data
public class InstallResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private boolean enabled;
    private String sourceUrl;
    private String sourceType;
}
