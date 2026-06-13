package vip.mate.dataagent.aloudata;

import lombok.Data;

/**
 * API 参数规范定义
 * <p>
 * 用于描述接口的请求参数或响应参数，包括参数名称、数据类型、是否必填、
 * 默认值、参数说明及传递方式。所有参数定义符合项目 API 设计规范，
 * 提高接口的可理解性和可维护性。
 */
@Data
public class ApiParam {

    /** 参数名称 */
    private String name;

    /** 数据类型：String / Integer / Long / Boolean / Array / Map / Object */
    private String type;

    /** 是否必填 */
    private Boolean required;

    /** 默认值（字符串表示） */
    private String defaultValue;

    /** 参数说明 */
    private String description;

    /**
     * 传递方式（仅请求参数适用）：
     * <ul>
     *   <li>HEADER — HTTP 请求头</li>
     *   <li>PATH — URL 路径参数</li>
     *   <li>QUERY — URL 查询参数</li>
     *   <li>BODY — 请求体参数</li>
     * </ul>
     */
    private String paramLocation;

    /**
     * 可选取值范围（枚举值），用逗号分隔。
     * 例如："UID,TOKEN,ACCOUNT,APIKEY"
     */
    private String enumValues;

    public ApiParam() {
    }

    public ApiParam(String name, String type, Boolean required, String defaultValue,
                    String description, String paramLocation) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
        this.paramLocation = paramLocation;
    }

    public ApiParam(String name, String type, Boolean required, String defaultValue,
                    String description, String paramLocation, String enumValues) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
        this.paramLocation = paramLocation;
        this.enumValues = enumValues;
    }
}
