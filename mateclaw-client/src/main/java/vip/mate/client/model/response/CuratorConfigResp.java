package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 策展器配置
 */
@Data
public class CuratorConfigResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 多少天后标记为 stale */
    private int staleAfterDays;

    /** 多少天后归档 */
    private int archiveAfterDays;

    /** 作用域 */
    private String scope;
}
