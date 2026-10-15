package vip.mate.dataagent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 存为样例模板请求
 * <p>
 * 将已有仪表盘派生为团队共享样例模板（visibility=template）。
 * 模板副本保留原仪表盘的数据集绑定与 Schema，带示例数据可直接使用。
 */
@Data
public class InsightDashboardSaveAsTemplateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板名称（可选，为空时取源仪表盘名称） */
    private String name;

    /** 模板描述（可选，为空时取源仪表盘描述） */
    private String description;

    /** 标签（可选） */
    private List<String> tags;

    /** 分类（可选，前端「先占位图」阶段用于分类色块） */
    private String category;
}
