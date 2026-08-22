package vip.mate.dataagent.support;

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
}