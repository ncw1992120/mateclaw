package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话级「成功查询基座」结构化状态实体（P0-2）。
 * <p>
 * 持久化每轮成功 {@code aloudata_metrics_query} 的结构化参数，作为多轮追问的
 * 确定性基座：下一轮读取此状态注入 prompt，使追问与"会话历史是否被压缩"解耦，
 * 避免历史丢失后全量重新检索、选错指标导致的跨轮自相矛盾。
 * <p>
 * 每条 {@code (conversationId, datasourceId)} 保存最新一次成功查询（upsert 覆盖）。
 */
@Data
@TableName("dataagent_query_state")
public class QueryStateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID */
    private String conversationId;

    /** 数据源 ID */
    private Long datasourceId;

    /** 指标英文名列表（JSON 数组字符串） */
    private String metrics;

    /** 维度英文名列表（JSON 数组字符串） */
    private String dimensions;

    /** 时间约束表达式 */
    private String timeConstraint;

    /** 全局筛选条件（JSON 数组字符串） */
    private String filters;

    /** 排序定义（JSON 数组字符串） */
    private String orders;

    /** 指标英文名 → {displayName, caliber} 映射（JSON 对象字符串） */
    private String metricDisplayMap;

    /** 成功请求的完整参数 JSON（审计/追踪） */
    private String requestJson;

    /** 该基座被复用的次数（注入到下一轮 prompt 时递增） */
    private Integer queryCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
