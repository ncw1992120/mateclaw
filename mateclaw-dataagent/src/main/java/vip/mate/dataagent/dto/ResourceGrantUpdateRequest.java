package vip.mate.dataagent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源授权更新请求 DTO
 */
@Data
public class ResourceGrantUpdateRequest {

    /** 权限：view / use / edit */
    private String permission;

    /** 过期时间（NULL 表示永久） */
    private LocalDateTime expireTime;
}
