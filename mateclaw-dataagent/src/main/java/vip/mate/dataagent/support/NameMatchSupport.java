package vip.mate.dataagent.support;

import java.util.HashSet;
import java.util.Set;

/**
 * 名称匹配的标点/空白不敏感工具。
 * <p>
 * 背景：用户问句常带全角括号、半角括号、连接符（-、_）、空格等标点，LLM 生成检索 keyword
 * 时可能截断 / 改写 / 转换全角半角（如「客户-收入」被格式化成「客户 - 收入」），导致
 * 「按关键词匹配指标/术语名称」失配。本工具提供两种防护形态：
 * <ul>
 *   <li>{@link #normalizeKey(String)}：归一化后做**完全相等**判定（两边都去标点再比），
 *       用于指标精确匹配（如 AloudataCallTool.resolveNormalizedMetricMatch）；</li>
 *   <li>{@link #likePattern(String)}：拆段 {@code %} 连接的 LIKE 模式（「客户 - 收入」
 *       → {@code %客户%收入%}），用于 MySQL LIKE 降级检索（业务术语 / 指标 / 维度），
 *       覆盖字面 LIKE 对空格的敏感性。</li>
 * </ul>
 * 两者共用同一套字符归一化规则：全角 ASCII 字母数字转半角、统一小写、只保留
 * 字母 / 数字 / 汉字，丢弃空白、标点、括号、连接符、斜杠等分隔符。
 *
 * @author mateclaw
 */
public final class NameMatchSupport {

    private NameMatchSupport() {
    }

    /**
     * 归一化字符串用于「标点/空白不敏感」的匹配比较。
     * <p>
     * 例：{@code "销售金额（含税）"} → {@code "销售金额含税"}；
     * {@code "sales_amount"} → {@code "salesamount"}；
     * {@code "metric_time__month"} → {@code "metrictimemonth"}。
     * 只用于「是否匹配」的判定；判定命中后仍使用原始名称构造查询，不改写查询值。
     *
     * @param text 原始文本，可为 null
     * @return 归一化形态，null 输入返回空串
     */
    public static String normalizeKey(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            char cc = c;
            // 全角 ASCII（U+FF01~U+FF5E）转半角
            if (cc >= 0xFF01 && cc <= 0xFF5E) {
                cc = (char) (cc - 0xFEE0);
            }
            // 全角空格转半角
            if (cc == 0x3000) {
                cc = ' ';
            }
            char lower = Character.toLowerCase(cc);
            if (Character.isLetterOrDigit(lower)) {
                sb.append(lower);
            }
        }
        return sb.toString();
    }

    /**
     * 标点不敏感 LIKE 模式。
     * <p>
     * 将文本拆分为连续「字母/数字/汉字」段，段间用 LIKE 通配符 {@code %} 连接：
     * 「客户 - 收入」→「%客户%收入%」，可命中库内「客户-收入」「客户(收入)」「客户收入」等写法。
     * 仅用于 MySQL LIKE 降级路径的核心名称字段（termName/synonyms、metricName/displayName 等），
     * 避免 LLM 格式化（加空格/改标点/全角半角）导致字面 LIKE 召回落空；ES 主路径走 ik 分词，
     * 拆词后天然免疫，无需此处理。
     *
     * @param text 原始查询文本
     * @return LIKE 模式；无有效段（如纯标点查询）或输入为空白时返回 null，由调用方跳过该模式
     */
    public static String likePattern(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        StringBuilder pattern = new StringBuilder();
        StringBuilder current = new StringBuilder();
        for (char c : text.trim().toCharArray()) {
            char cc = c;
            // 全角 ASCII（U+FF01~U+FF5E）转半角
            if (cc >= 0xFF01 && cc <= 0xFF5E) {
                cc = (char) (cc - 0xFEE0);
            }
            // 全角空格转半角
            if (cc == 0x3000) {
                cc = ' ';
            }
            if (Character.isLetterOrDigit(cc)) {
                current.append(Character.toLowerCase(cc));
            } else if (current.length() > 0) {
                pattern.append('%').append(current);
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            pattern.append('%').append(current);
        }
        return pattern.length() == 0 ? null : pattern.toString();
    }

    /**
     * 提取字符串中的所有中文字符集合。
     * <p>
     * 原实现位于 AloudataCallTool（私有方法），抽取至此供检索层维度相关性打分等
     * 多处复用，避免重复实现。
     *
     * @param text 原始文本，可为 null
     * @return 中文字符集合（单字符字符串），null 输入返回空集合
     */
    public static Set<String> extractChineseChars(String text) {
        Set<String> chars = new HashSet<>();
        if (text == null) {
            return chars;
        }
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chars.add(String.valueOf(c));
            }
        }
        return chars;
    }

    /**
     * 提取字符串中的英文单词集合（按下划线和非字母数字分隔，统一小写）。
     * <p>
     * 原实现位于 AloudataCallTool（私有方法），抽取至此供检索层维度相关性打分等
     * 多处复用，避免重复实现。
     *
     * @param text 原始文本，可为 null
     * @return 英文单词集合（小写），null 输入返回空集合
     */
    public static Set<String> extractEnglishWords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) {
            return words;
        }
        String[] parts = text.split("[^a-zA-Z0-9]+");
        for (String part : parts) {
            if (!part.isEmpty() && part.matches(".*[a-zA-Z].*")) {
                words.add(part.toLowerCase());
            }
        }
        return words;
    }

    /**
     * 计算两个中文字符集合的重叠度 = 交集大小 / targetChars 大小。
     * <p>
     * 语义：目标名称中有多少比例的字符出现在源文本中。
     * 值域 [0, 1]，1 表示目标名称的每个字符都在源文本中出现。
     * 原实现位于 AloudataCallTool（私有方法 computeCharOverlap），抽取至此供
     * 检索层维度相关性打分等多处复用，避免重复实现。
     *
     * @param srcChars    源文本（如用户原话）的中文字符集合
     * @param targetChars 目标名称（如维度展示名）的中文字符集合
     * @return 重叠度；目标集合为空时返回 0
     */
    public static double charOverlapRatio(Set<String> srcChars, Set<String> targetChars) {
        if (targetChars == null || targetChars.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(targetChars);
        intersection.retainAll(srcChars);
        return (double) intersection.size() / targetChars.size();
    }
}