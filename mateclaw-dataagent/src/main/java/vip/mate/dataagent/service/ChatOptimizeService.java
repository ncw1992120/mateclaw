package vip.mate.dataagent.service;

/**
 * 对话输入优化服务接口
 */
public interface ChatOptimizeService {

    /**
     * 优化用户输入的文本，使其更清晰、更专业、更有条理
     *
     * @param input 用户原始输入文本
     * @return 优化后的文本内容
     */
    String optimizePrompt(String input);
}
