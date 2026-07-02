package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.HelpCategoryEntity;
import vip.mate.dataagent.model.HelpDocumentEntity;
import vip.mate.dataagent.model.HelpFeedbackEntity;
import vip.mate.dataagent.repository.HelpCategoryMapper;
import vip.mate.dataagent.repository.HelpDocumentMapper;
import vip.mate.dataagent.repository.HelpFeedbackMapper;
import vip.mate.dataagent.service.HelpCenterService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 帮助中心服务实现
 */
@Service
@RequiredArgsConstructor
public class HelpCenterServiceImpl implements HelpCenterService {

    private final HelpCategoryMapper categoryMapper;

    private final HelpDocumentMapper documentMapper;

    private final HelpFeedbackMapper feedbackMapper;

    @Override
    public List<HelpCategoryVO> listCategoryTree() {
        LambdaQueryWrapper<HelpCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HelpCategoryEntity::getDeleted, 0);
        wrapper.orderByAsc(HelpCategoryEntity::getSortOrder);
        List<HelpCategoryEntity> entities = categoryMapper.selectList(wrapper);

        // 统计每个分类下的文档数量
        List<HelpDocumentEntity> allDocs = documentMapper.selectList(
                new LambdaQueryWrapper<HelpDocumentEntity>()
                        .eq(HelpDocumentEntity::getDeleted, 0)
                        .eq(HelpDocumentEntity::getStatus, DataAgentConstants.HELP_DOC_STATUS_PUBLISHED));
        Map<Long, Long> docCountMap = allDocs.stream()
                .collect(Collectors.groupingBy(HelpDocumentEntity::getCategoryId, Collectors.counting()));

        List<HelpCategoryVO> voList = entities.stream().map(entity -> {
            HelpCategoryVO vo = toCategoryVO(entity);
            vo.setDocumentCount(docCountMap.getOrDefault(entity.getId(), 0L).intValue());
            return vo;
        }).toList();

        return buildTree(voList, DataAgentConstants.HELP_CATEGORY_ROOT_PARENT_ID);
    }

    @Override
    public HelpCategoryVO createCategory(HelpCategoryRequest request) {
        HelpCategoryEntity entity = new HelpCategoryEntity();
        entity.setName(request.getName());
        entity.setParentId(request.getParentId() != null ? Long.parseLong(request.getParentId()) : DataAgentConstants.HELP_CATEGORY_ROOT_PARENT_ID);
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setIcon(request.getIcon());
        entity.setDescription(request.getDescription());
        entity.setDeleted(0);
        categoryMapper.insert(entity);
        return toCategoryVO(entity);
    }

    @Override
    public HelpCategoryVO updateCategory(Long id, HelpCategoryRequest request) {
        HelpCategoryEntity entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("分类不存在");
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getParentId() != null) {
            entity.setParentId(Long.parseLong(request.getParentId()));
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (request.getIcon() != null) {
            entity.setIcon(request.getIcon());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        categoryMapper.updateById(entity);
        return toCategoryVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        HelpCategoryEntity entity = categoryMapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setDeleted(1);
        categoryMapper.updateById(entity);

        // 递归删除子分类
        List<HelpCategoryEntity> children = categoryMapper.selectList(
                new LambdaQueryWrapper<HelpCategoryEntity>()
                        .eq(HelpCategoryEntity::getParentId, id)
                        .eq(HelpCategoryEntity::getDeleted, 0));
        for (HelpCategoryEntity child : children) {
            deleteCategory(child.getId());
        }

        // 删除该分类下的文档
        LambdaQueryWrapper<HelpDocumentEntity> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(HelpDocumentEntity::getCategoryId, id);
        docWrapper.eq(HelpDocumentEntity::getDeleted, 0);
        List<HelpDocumentEntity> docs = documentMapper.selectList(docWrapper);
        for (HelpDocumentEntity doc : docs) {
            doc.setDeleted(1);
            documentMapper.updateById(doc);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderCategories(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            Long id = Long.parseLong(ids.get(i));
            HelpCategoryEntity entity = categoryMapper.selectById(id);
            if (entity == null || entity.getDeleted() == 1) {
                continue;
            }
            entity.setSortOrder(i);
            categoryMapper.updateById(entity);
        }
    }

    @Override
    public List<HelpDocumentVO> listDocuments(Long categoryId) {
        LambdaQueryWrapper<HelpDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HelpDocumentEntity::getCategoryId, categoryId);
        wrapper.eq(HelpDocumentEntity::getDeleted, 0);
        wrapper.orderByAsc(HelpDocumentEntity::getSortOrder);
        List<HelpDocumentEntity> entities = documentMapper.selectList(wrapper);

        HelpCategoryEntity category = categoryMapper.selectById(categoryId);
        String categoryName = category != null ? category.getName() : "";

        return entities.stream().map(entity -> {
            HelpDocumentVO vo = toDocumentVO(entity);
            vo.setCategoryName(categoryName);
            return vo;
        }).toList();
    }

    @Override
    public HelpDocumentVO getDocument(Long id) {
        HelpDocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        // 增加浏览次数
        entity.setViewCount(entity.getViewCount() != null ? entity.getViewCount() + 1 : 1);
        documentMapper.updateById(entity);

        HelpDocumentVO vo = toDocumentVO(entity);
        HelpCategoryEntity category = categoryMapper.selectById(entity.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }
        return vo;
    }

    @Override
    public HelpDocumentVO createDocument(HelpDocumentRequest request) {
        HelpDocumentEntity entity = new HelpDocumentEntity();
        entity.setCategoryId(Long.parseLong(request.getCategoryId()));
        entity.setTitle(request.getTitle());
        entity.setContent(request.getContent());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(request.getStatus() != null ? request.getStatus() : DataAgentConstants.HELP_DOC_STATUS_DRAFT);
        entity.setAuthor(request.getAuthor());
        entity.setTags(request.getTags());
        entity.setSummary(request.getSummary());
        entity.setViewCount(0);
        entity.setDeleted(0);
        documentMapper.insert(entity);
        return toDocumentVO(entity);
    }

    @Override
    public HelpDocumentVO updateDocument(Long id, HelpDocumentRequest request) {
        HelpDocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("文档不存在");
        }
        if (request.getCategoryId() != null) {
            entity.setCategoryId(Long.parseLong(request.getCategoryId()));
        }
        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            entity.setContent(request.getContent());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getAuthor() != null) {
            entity.setAuthor(request.getAuthor());
        }
        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }
        if (request.getSummary() != null) {
            entity.setSummary(request.getSummary());
        }
        documentMapper.updateById(entity);
        return toDocumentVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        HelpDocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            return;
        }
        entity.setDeleted(1);
        documentMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderDocuments(Long categoryId, List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty() || categoryId == null) {
            return;
        }
        for (int i = 0; i < documentIds.size(); i++) {
            Long id = Long.parseLong(documentIds.get(i));
            HelpDocumentEntity entity = documentMapper.selectById(id);
            if (entity == null || entity.getDeleted() == 1 || !categoryId.equals(entity.getCategoryId())) {
                continue;
            }
            entity.setSortOrder(i);
            documentMapper.updateById(entity);
        }
    }

    @Override
    public HelpDocumentVO publishDocument(Long id) {
        HelpDocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("文档不存在");
        }
        entity.setStatus(DataAgentConstants.HELP_DOC_STATUS_PUBLISHED);
        documentMapper.updateById(entity);
        return toDocumentVO(entity);
    }

    @Override
    public HelpDocumentVO unpublishDocument(Long id) {
        HelpDocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("文档不存在");
        }
        entity.setStatus(DataAgentConstants.HELP_DOC_STATUS_DRAFT);
        documentMapper.updateById(entity);
        return toDocumentVO(entity);
    }

    @Override
    public List<HelpSearchResultVO> searchDocuments(String keyword, Integer limit) {
        if (keyword == null || keyword.trim().length() < DataAgentConstants.HELP_SEARCH_MIN_KEYWORD_LENGTH) {
            return List.of();
        }
        if (limit == null || limit <= 0) {
            limit = DataAgentConstants.HELP_SEARCH_DEFAULT_LIMIT;
        }
        String kw = keyword.trim();
        LambdaQueryWrapper<HelpDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HelpDocumentEntity::getDeleted, 0);
        wrapper.eq(HelpDocumentEntity::getStatus, DataAgentConstants.HELP_DOC_STATUS_PUBLISHED);
        wrapper.and(w -> w.like(HelpDocumentEntity::getTitle, kw).or().like(HelpDocumentEntity::getContent, kw));
        wrapper.orderByDesc(HelpDocumentEntity::getViewCount);
        wrapper.last("LIMIT " + limit);
        List<HelpDocumentEntity> entities = documentMapper.selectList(wrapper);

        return entities.stream().map(entity -> {
            HelpSearchResultVO vo = new HelpSearchResultVO();
            vo.setId(String.valueOf(entity.getId()));
            vo.setCategoryId(String.valueOf(entity.getCategoryId()));
            vo.setTitle(entity.getTitle());
            vo.setStatus(entity.getStatus());
            vo.setAuthor(entity.getAuthor());
            vo.setViewCount(entity.getViewCount());
            if (entity.getUpdateTime() != null) {
                vo.setUpdateTime(entity.getUpdateTime().toString());
            }
            // 生成高亮摘要
            String content = entity.getContent() != null ? entity.getContent() : "";
            String plainText = stripMarkdownForSearch(content);
            vo.setHighlightContent(buildHighlightSnippet(plainText, kw));
            // 填充分类名称
            HelpCategoryEntity category = categoryMapper.selectById(entity.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
            return vo;
        }).toList();
    }

    @Override
    public List<HelpDocumentVO> getRelatedDocuments(Long documentId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = DataAgentConstants.HELP_RELATED_DOCS_DEFAULT_LIMIT;
        }
        HelpDocumentEntity currentDoc = documentMapper.selectById(documentId);
        if (currentDoc == null) {
            return List.of();
        }
        // 基于同分类下的其他文档推荐
        LambdaQueryWrapper<HelpDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HelpDocumentEntity::getDeleted, 0);
        wrapper.eq(HelpDocumentEntity::getStatus, DataAgentConstants.HELP_DOC_STATUS_PUBLISHED);
        wrapper.eq(HelpDocumentEntity::getCategoryId, currentDoc.getCategoryId());
        wrapper.ne(HelpDocumentEntity::getId, documentId);
        wrapper.orderByDesc(HelpDocumentEntity::getViewCount);
        wrapper.last("LIMIT " + limit);
        List<HelpDocumentEntity> entities = documentMapper.selectList(wrapper);

        // 如果同分类文档不足，补充其他分类的热门文档
        if (entities.size() < limit) {
            List<Long> excludeIds = entities.stream().map(HelpDocumentEntity::getId).collect(Collectors.toList());
            excludeIds.add(documentId);
            LambdaQueryWrapper<HelpDocumentEntity> extraWrapper = new LambdaQueryWrapper<>();
            extraWrapper.eq(HelpDocumentEntity::getDeleted, 0);
            extraWrapper.eq(HelpDocumentEntity::getStatus, DataAgentConstants.HELP_DOC_STATUS_PUBLISHED);
            extraWrapper.notIn(HelpDocumentEntity::getId, excludeIds);
            extraWrapper.orderByDesc(HelpDocumentEntity::getViewCount);
            extraWrapper.last("LIMIT " + (limit - entities.size()));
            entities.addAll(documentMapper.selectList(extraWrapper));
        }

        HelpCategoryEntity category = categoryMapper.selectById(currentDoc.getCategoryId());
        String categoryName = category != null ? category.getName() : "";

        return entities.stream().map(entity -> {
            HelpDocumentVO vo = toDocumentVO(entity);
            vo.setCategoryName(categoryName);
            return vo;
        }).toList();
    }

    @Override
    public HelpFeedbackVO submitFeedback(Long documentId, HelpFeedbackRequest request) {
        if (request.getRating() != null && (request.getRating() < DataAgentConstants.HELP_FEEDBACK_MIN_RATING
                || request.getRating() > DataAgentConstants.HELP_FEEDBACK_MAX_RATING)) {
            throw new RuntimeException("评分必须在1-5之间");
        }
        HelpFeedbackEntity entity = new HelpFeedbackEntity();
        entity.setDocumentId(documentId);
        entity.setRating(request.getRating());
        entity.setSuggestion(request.getSuggestion());
        entity.setUserId(request.getUserId());
        entity.setDeleted(0);
        feedbackMapper.insert(entity);
        return toFeedbackVO(entity);
    }

    @Override
    public HelpFeedbackSummaryVO getFeedbackSummary(Long documentId) {
        LambdaQueryWrapper<HelpFeedbackEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HelpFeedbackEntity::getDocumentId, documentId);
        wrapper.eq(HelpFeedbackEntity::getDeleted, 0);
        List<HelpFeedbackEntity> feedbacks = feedbackMapper.selectList(wrapper);

        HelpFeedbackSummaryVO summary = new HelpFeedbackSummaryVO();
        summary.setDocumentId(String.valueOf(documentId));
        summary.setTotalFeedbacks(feedbacks.size());

        if (feedbacks.isEmpty()) {
            summary.setAverageRating(0.0);
            summary.setStar5Count(0);
            summary.setStar4Count(0);
            summary.setStar3Count(0);
            summary.setStar2Count(0);
            summary.setStar1Count(0);
            return summary;
        }

        double avg = feedbacks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(HelpFeedbackEntity::getRating)
                .average().orElse(0.0);
        summary.setAverageRating(Math.round(avg * 10) / 10.0);

        summary.setStar5Count((int) feedbacks.stream().filter(f -> f.getRating() != null && f.getRating() == 5).count());
        summary.setStar4Count((int) feedbacks.stream().filter(f -> f.getRating() != null && f.getRating() == 4).count());
        summary.setStar3Count((int) feedbacks.stream().filter(f -> f.getRating() != null && f.getRating() == 3).count());
        summary.setStar2Count((int) feedbacks.stream().filter(f -> f.getRating() != null && f.getRating() == 2).count());
        summary.setStar1Count((int) feedbacks.stream().filter(f -> f.getRating() != null && f.getRating() == 1).count());

        return summary;
    }

    /**
     * 去除 Markdown 标记，提取纯文本用于搜索
     */
    private String stripMarkdownForSearch(String md) {
        return md
                .replace("```", "")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1")
                .replaceAll("[#>*_~\\-]", "")
                .replaceAll("\\n+", " ")
                .trim();
    }

    /**
     * 构建搜索高亮摘要
     */
    private String buildHighlightSnippet(String plainText, String keyword) {
        int maxLen = 200;
        int idx = plainText.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return plainText.length() > maxLen ? plainText.substring(0, maxLen) + "..." : plainText;
        }
        int start = Math.max(0, idx - 60);
        int end = Math.min(plainText.length(), idx + keyword.length() + 140);
        String snippet = (start > 0 ? "..." : "") + plainText.substring(start, end) + (end < plainText.length() ? "..." : "");
        // 高亮关键字
        return snippet.replaceAll("(?i)(" + Pattern.quote(keyword) + ")", "<mark>$1</mark>");
    }

    private HelpFeedbackVO toFeedbackVO(HelpFeedbackEntity entity) {
        HelpFeedbackVO vo = new HelpFeedbackVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setDocumentId(String.valueOf(entity.getDocumentId()));
        vo.setRating(entity.getRating());
        vo.setSuggestion(entity.getSuggestion());
        vo.setUserId(entity.getUserId());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }

    /**
     * 构建分类树
     */
    private List<HelpCategoryVO> buildTree(List<HelpCategoryVO> allCategories, Long parentId) {
        List<HelpCategoryVO> tree = new ArrayList<>();
        for (HelpCategoryVO category : allCategories) {
            String parentStr = parentId.equals(DataAgentConstants.HELP_CATEGORY_ROOT_PARENT_ID) ? "0" : String.valueOf(parentId);
            if (parentStr.equals(category.getParentId())) {
                category.setChildren(buildTree(allCategories, Long.parseLong(category.getId())));
                tree.add(category);
            }
        }
        return tree;
    }

    private HelpCategoryVO toCategoryVO(HelpCategoryEntity entity) {
        HelpCategoryVO vo = new HelpCategoryVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setName(entity.getName());
        vo.setParentId(String.valueOf(entity.getParentId()));
        vo.setSortOrder(entity.getSortOrder());
        vo.setIcon(entity.getIcon());
        vo.setDescription(entity.getDescription());
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        return vo;
    }

    private HelpDocumentVO toDocumentVO(HelpDocumentEntity entity) {
        HelpDocumentVO vo = new HelpDocumentVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setId(String.valueOf(entity.getId()));
        vo.setCategoryId(String.valueOf(entity.getCategoryId()));
        if (entity.getCreateTime() != null) {
            vo.setCreateTime(entity.getCreateTime().toString());
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdateTime(entity.getUpdateTime().toString());
        }
        vo.setTags(entity.getTags());
        vo.setSummary(entity.getSummary());
        return vo;
    }
}
