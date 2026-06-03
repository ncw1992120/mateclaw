package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.DatasetCreateRequest;
import vip.mate.dataagent.dto.DatasetDataVO;
import vip.mate.dataagent.dto.DatasetFieldVO;
import vip.mate.dataagent.dto.DatasetRowCreateRequest;
import vip.mate.dataagent.dto.DatasetRowUpdateRequest;
import vip.mate.dataagent.dto.DatasetUpdateRequest;
import vip.mate.dataagent.dto.DatasetVO;

import java.util.List;
import java.util.Map;

/**
 * 数据集管理服务接口
 */
public interface DatasetManageService {

    /**
     * 获取所有数据集
     *
     * @return 数据集列表
     */
    List<DatasetVO> listDatasets();

    /**
     * 根据 ID 获取数据集
     *
     * @param id 数据集 ID
     * @return 数据集视图对象
     */
    DatasetVO getDataset(Long id);

    /**
     * 创建数据集（关联数据源表并自动提取字段）
     *
     * @param request 创建请求
     * @return 创建后的数据集视图对象
     */
    DatasetVO createDataset(DatasetCreateRequest request);

    /**
     * 更新数据集基本信息
     *
     * @param id      数据集 ID
     * @param request 更新请求
     * @return 更新后的数据集视图对象
     */
    DatasetVO updateDataset(Long id, DatasetUpdateRequest request);

    /**
     * 删除数据集
     *
     * @param id 数据集 ID
     */
    void deleteDataset(Long id);

    /**
     * 获取数据集字段列表
     *
     * @param datasetId 数据集 ID
     * @return 字段列表
     */
    List<DatasetFieldVO> listFields(Long datasetId);

    /**
     * 获取数据集数据（分页）
     *
     * @param datasetId 数据集 ID
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @return 数据集数据视图对象
     */
    DatasetDataVO getDatasetData(Long datasetId, int page, int size);

    /**
     * 更新数据集行数据
     *
     * @param datasetId 数据集 ID
     * @param request   行更新请求
     */
    void updateRow(Long datasetId, DatasetRowUpdateRequest request);

    /**
     * 新增数据集行
     *
     * @param datasetId 数据集 ID
     * @param request   新增行请求
     */
    void addRow(Long datasetId, DatasetRowCreateRequest request);

    /**
     * 删除数据集行
     *
     * @param datasetId 数据集 ID
     * @param rowKey    行标识值
     */
    void deleteRow(Long datasetId, Map<String, Object> rowKey);

    /**
     * 更新字段分类
     *
     * @param fieldId       字段 ID
     * @param fieldCategory 字段分类
     * @return 更新后的字段视图对象
     */
    DatasetFieldVO updateFieldCategory(Long fieldId, String fieldCategory);

    /**
     * 同步数据集数据（从源表拉取数据并落库到本地业务数据表）
     *
     * @param datasetId 数据集 ID
     * @return 更新后的数据集视图对象
     */
    DatasetVO syncDatasetData(Long datasetId);
}
