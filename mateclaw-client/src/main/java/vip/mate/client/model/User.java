package vip.mate.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    /** BCrypt 加密，仅写入（序列化时不输出） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    /** admin / user */
    private String role;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
