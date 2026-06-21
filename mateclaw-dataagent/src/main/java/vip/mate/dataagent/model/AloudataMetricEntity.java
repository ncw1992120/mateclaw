package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Aloudata 指标元数据实体
 * <p>
 * 存储从 Aloudata 指标平台同步的指标级语义信息，
 * 包含指标名、展示名、业务口径、同义词、类目、单位等。
 * 支持 ES 混合检索（关键词 + 向量语义 + RRF 融合）。
 */
@Data
@TableName("dataagent_aloudata_metric")
public class AloudataMetricEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联数据源 ID */
    private Long datasourceId;

    /** 指标编码（系统内部唯一标识） */
    private String metricCode;

    /** 指标英文名（Aloudata 唯一标识） */
    private String metricName;

    /** 指标展示名（业务别名） */
    private String metricDisplayName;

    /** 指标版本号 */
    private Integer version;

    /** 指标类型：ATOMIC/DERIVED/COMPOSITE */
    private String type;

    /** 指标终态：ONLINE/OFFLINE */
    private String status;

    /** 发布状态：DRAFT/PUBLISHED */
    private String publishStatus;

    /** 显示状态：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE */
    private String displayStatus;

    /** 业务口径描述 */
    private String businessCaliber;

    /** 指标负责人 */
    private String owner;

    /** 业务负责人 */
    private String businessOwner;

    /** 类目 ID */
    private String metricCategoryId;

    /** 类目名称 */
    private String metricCategoryName;

    /** 指标单位 */
    private String unit;

    /** 中文指标单位 */
    private String cnUnit;

    /** 指标查询次数 */
    private Integer metricViewCount;

    /** 时间粒度（数据统计的时间单位） */
    private String timeGranularity;

    /** 是否有日期限制：0-否，1-是 */
    private Boolean hasDateLimit;

    /** 是否有衍生方法：0-否，1-是 */
    private Boolean hasDerivationMethod;

    /** 指标时间数据类型：DATE_TIME */
    private String metricTimeDataType;

    /** 是否允许编辑：0-否，1-是 */
    private Boolean canEdit;

    /** 是否允许删除：0-否，1-是 */
    private Boolean canDelete;

    /** 是否允许使用：0-否，1-是 */
    private Boolean canUsage;

    /** 是否允许授权：0-否，1-是 */
    private Boolean canAuth;

    /** 是否允许转移：0-否，1-是 */
    private Boolean canTransfer;

    /** 指标属性JSON（MANAGE/BUSINESS/TECHNOLOGY/BASE） */
    private String properties;

    /** 创建时间（Aloudata原始格式） */
    private String gmtCreate;

    /** 修改时间（Aloudata原始格式） */
    private String gmtUpdate;

    /** 同义词（逗号分隔） */
    private String synonyms;

    /** 嵌入文本（用于生成向量） */
    private String embeddingText;

    /** 向量数据（float32 小端序序列化） */
    private byte[] embedding;

    /** 嵌入模型 ID */
    private Long embeddingModelId;

    /** 同步版本号（每次全量同步递增） */
    private Integer syncVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 构建用于 Prompt 的指标语义描述
     */
    public String getPromptInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(metricName);
        if (metricDisplayName != null && !metricDisplayName.isBlank()) {
            sb.append("(").append(metricDisplayName).append(")");
        }
        if (type != null && !type.isBlank()) {
            sb.append(" [").append(type).append("]");
        }
        if (businessCaliber != null && !businessCaliber.isBlank()) {
            sb.append(" - ").append(businessCaliber);
        }
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append(", 同义词: ").append(synonyms);
        }
        if (unit != null && !unit.isBlank()) {
            sb.append(", 单位: ").append(unit);
        }
        if (metricCategoryName != null && !metricCategoryName.isBlank()) {
            sb.append(", 类目: ").append(metricCategoryName);
        }
        return sb.toString();
    }

    /**
     * 构建 ES 索引用的嵌入文本
     * <p>
     * 格式: "metricName metricDisplayName | 类型: type, 口径: businessCaliber, 同义词: synonyms, 类目: categoryName, 单位: unit"
     */
    public String buildEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        sb.append(metricName != null ? metricName : "");
        if (metricDisplayName != null && !metricDisplayName.isBlank()) {
            sb.append(" ").append(metricDisplayName);
        }
        sb.append(" | ");
        if (type != null && !type.isBlank()) {
            sb.append("类型: ").append(type).append(", ");
        }
        if (businessCaliber != null && !businessCaliber.isBlank()) {
            sb.append("口径: ").append(businessCaliber).append(", ");
        }
        if (synonyms != null && !synonyms.isBlank()) {
            sb.append("同义词: ").append(synonyms).append(", ");
        }
        if (metricCategoryName != null && !metricCategoryName.isBlank()) {
            sb.append("类目: ").append(metricCategoryName).append(", ");
        }
        if (unit != null && !unit.isBlank()) {
            sb.append("单位: ").append(unit);
        }
        return sb.toString().trim();
    }
}
