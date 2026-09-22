package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.dto.QueryContextDTO;

/**
 * 查询计划器：合并 Schema 静态查询配置（queryConfig / 旧 scriptFilterBindings）与运行时
 * {@link QueryContextDTO}，产出单数据集的 {@link DatasetQueryPlanDTO}。
 * <p>
 * 验收约束：相同 QueryContext 在所有数据源类型上生成相同的字段、筛选、排序和分页语义；
 * 任何未授权字段在 Planner 阶段失败，不进入 Adapter。
 */
public interface QueryPlanner {

    /**
     * @param component 组件节点（pages[].components[]），用于跨输入收集全部参数定义与校验绑定归属
     * @param input     组件绑定的单个数据集输入节点（config.datasetPipeline.datasetInputs[]）
     * @param runtime   页面运行时 QueryContext
     * @param hasPython 该组件是否带有 Python 脚本（可能改变行数/粒度/顺序）
     */
    DatasetQueryPlanDTO plan(JsonNode component, JsonNode input, QueryContextDTO runtime, boolean hasPython);
}
