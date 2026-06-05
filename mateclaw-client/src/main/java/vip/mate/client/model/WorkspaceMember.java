package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作区成员
 */
@Data
public class WorkspaceMember implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private Long userId;
    private String role;
    private String username;
    private String nickname;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
