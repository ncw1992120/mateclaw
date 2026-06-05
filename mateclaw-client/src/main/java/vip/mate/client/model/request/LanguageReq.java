package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统语言保存请求
 */
@Data
public class LanguageReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 系统语言 */
    private String language;
}
