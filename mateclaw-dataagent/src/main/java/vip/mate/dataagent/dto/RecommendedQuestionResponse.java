package vip.mate.dataagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 推荐问题响应
 */
@Data
public class RecommendedQuestionResponse {
    /** 推荐问题列表 */
    private List<String> questions;

    /**
     * 根据推荐问题列表构建响应对象
     *
     * @param questions 推荐问题列表
     * @return 推荐问题响应
     */
    public static RecommendedQuestionResponse of(List<String> questions) {
        RecommendedQuestionResponse response = new RecommendedQuestionResponse();
        response.setQuestions(questions);
        return response;
    }
}
