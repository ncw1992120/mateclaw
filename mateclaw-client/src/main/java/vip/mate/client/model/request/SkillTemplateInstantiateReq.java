package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 技能模板实例化请求
 * <p>
 * 模板变量键值对，结构取决于具体 SkillTemplate 定义
 */
@Data
public class SkillTemplateInstantiateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 模板变量键值对 */
    private Map<String, Object> values;
}