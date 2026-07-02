package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.*;

import java.util.List;

/**
 * 帮助中心服务接口
 */
public interface HelpCenterService {

    /**
     * 获取分类树
     *
     * @return 分类树列表
     */
    List<HelpCategoryVO> listCategoryTree();

    /**
     * 创建分类
     *
     * @param request 分类请求
     * @return 分类视图对象
     */
    HelpCategoryVO createCategory(HelpCategoryRequest request);

    /**
     * 更新分类
     *
     * @param id      分类 ID
     * @param request 分类请求
     * @return 分类视图对象
     */
    HelpCategoryVO updateCategory(Long id, HelpCategoryRequest request);

    /**
     * 删除分类
     *
     * @param id 分类 ID
     */
    void deleteCategory(Long id);

    /**
     * 批量排序分类
     *
     * @param ids 按目标顺序排列的分类 ID 列表
     */
    void reorderCategories(List<String> ids);

    /**
     * 获取分类下的文档列表
     *
     * @param categoryId 分类 ID
     * @return 文档列表
     */
    List<HelpDocumentVO> listDocuments(Long categoryId);

    /**
     * 获取文档详情
     *
     * @param id 文档 ID
     * @return 文档视图对象
     */
    HelpDocumentVO getDocument(Long id);

    /**
     * 创建文档
     *
     * @param request 文档请求
     * @return 文档视图对象
     */
    HelpDocumentVO createDocument(HelpDocumentRequest request);

    /**
     * 更新文档
     *
     * @param id      文档 ID
     * @param request 文档请求
     * @return 文档视图对象
     */
    HelpDocumentVO updateDocument(Long id, HelpDocumentRequest request);

    /**
     * 删除文档
     *
     * @param id 文档 ID
     */
    void deleteDocument(Long id);

    /**
     * 批量排序分类下的文档
     *
     * @param categoryId    分类 ID
     * @param documentIds 按目标顺序排列的文档 ID 列表
     */
    void reorderDocuments(Long categoryId, List<String> documentIds);

    /**
     * 发布文档
     *
     * @param id 文档 ID
     * @return 文档视图对象
     */
    HelpDocumentVO publishDocument(Long id);

    /**
     * 取消发布文档
     *
     * @param id 文档 ID
     * @return 文档视图对象
     */
    HelpDocumentVO unpublishDocument(Long id);

    /**
     * 搜索文档
     *
     * @param keyword 搜索关键字
     * @param limit   返回条数
     * @return 搜索结果列表
     */
    List<HelpSearchResultVO> searchDocuments(String keyword, Integer limit);

    /**
     * 获取相关文档推荐
     *
     * @param documentId 当前文档 ID
     * @param limit      返回条数
     * @return 相关文档列表
     */
    List<HelpDocumentVO> getRelatedDocuments(Long documentId, Integer limit);

    /**
     * 提交文档反馈
     *
     * @param documentId 文档 ID
     * @param request    反馈请求
     * @return 反馈视图对象
     */
    HelpFeedbackVO submitFeedback(Long documentId, HelpFeedbackRequest request);

    /**
     * 获取文档反馈汇总
     *
     * @param documentId 文档 ID
     * @return 反馈汇总视图对象
     */
    HelpFeedbackSummaryVO getFeedbackSummary(Long documentId);
}
