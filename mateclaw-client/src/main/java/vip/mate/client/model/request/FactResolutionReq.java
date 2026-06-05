package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 事实矛盾解决请求
 */
@Data
public class FactResolutionReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 解决方式（KEEP_A / KEEP_B / MERGE / IGNORE） */
    private String resolution;
}