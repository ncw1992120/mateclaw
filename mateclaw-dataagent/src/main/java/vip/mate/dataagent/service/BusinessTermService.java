package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.BusinessTermCreateRequest;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.BusinessTermUpdateRequest;
import vip.mate.dataagent.dto.BusinessTermVO;

import java.util.List;

/**
 * 业务术语服务接口
 * <p>
 * 提供术语的 CRUD、关键词搜索、按租户/类目查询等能力。
 */
public interface BusinessTermService {

    /**
     * 列出所有已存在术语数据的租户编码（去重）
     *
     * @return 租户编码列表
     */
    List<String> listTenantCodes();

    /**
     * 按租户查询所有启用的术语
     *
     * @param tenantCode 租户编码
     * @return 术语列表
     */
    List<BusinessTermVO> listByTenantCode(String tenantCode);

    /**
     * 按租户和类目查询启用的术语
     *
     * @param tenantCode 租户编码
     * @param category   类目
     * @return 术语列表
     */
    List<BusinessTermVO> listByTenantCodeAndCategory(String tenantCode, String category);

    /**
     * 根据 ID 获取术语
     *
     * @param id 术语 ID
     * @return 术语视图对象
     */
    BusinessTermVO getById(Long id);

    /**
     * 创建术语
     *
     * @param request 创建请求
     * @return 创建后的术语视图对象
     */
    BusinessTermVO create(BusinessTermCreateRequest request);

    /**
     * 更新术语
     *
     * @param id      术语 ID
     * @param request 更新请求
     * @return 更新后的术语视图对象
     */
    BusinessTermVO update(Long id, BusinessTermUpdateRequest request);

    /**
     * 删除术语
     *
     * @param id 术语 ID
     */
    void delete(Long id);

    /**
     * 启用术语
     *
     * @param id 术语 ID
     */
    void enable(Long id);

    /**
     * 停用术语
     *
     * @param id 术语 ID
     */
    void disable(Long id);

    /**
     * 关键词搜索术语
     *
     * @param tenantCode 租户编码
     * @param keyword    关键词
     * @return 匹配的术语列表
     */
    List<BusinessTermVO> searchByKeyword(String tenantCode, String keyword);

    /**
     * 为租户下的所有术语生成嵌入向量并写入 ES 索引
     *
     * @param tenantCode 租户编码
     * @return 新增或更新的嵌入数量
     */
    int embedAndIndexAll(String tenantCode);

    /**
     * 从 MySQL 已同步数据重新向量化并写入 ES（不重新从 API 拉取）
     *
     * @param tenantCode 租户编码
     * @return 重建的嵌入数量
     */
    int rebuildEsIndex(String tenantCode);

    /**
     * 语义混合检索术语（跨所有租户）
     * <p>
     * 使用 ES 关键词检索 + 向量语义检索（RRF 融合），
     * ES 不可用时降级为 MySQL LIKE 查询。
     *
     * @param query      搜索关键词
     * @param topK       返回结果数量上限
     * @param threshold  向量语义检索相似度阈值
     * @return 检索结果
     */
    BusinessTermSearchResult semanticSearch(String query, int topK, double threshold);
}
