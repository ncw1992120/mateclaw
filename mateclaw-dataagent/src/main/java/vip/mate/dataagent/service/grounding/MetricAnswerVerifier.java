package vip.mate.dataagent.service.grounding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指标问数「最终答案 → 查询结果」数字对齐校验器（P0-1）。
 * <p>
 * 目的：拦截 LLM 在最终回答中**编造/抄错**结果的显著数值。查询工具在 RAW 结果
 * （spill/截断前）上抽取证据（{@link MetricQueryEvidence}），本校验器在会话收尾时
 * 把最终答案中的"显著数值声明"与证据集合对齐：
 * <ul>
 *   <li>显著 = 带单位（万/亿/元/%）的数值，或绝对值 ≥ 10000 的大数值；</li>
 *   <li>年份（1900–2100 独立小整数）、低价 + 无单位的小数（可能为衍生/换算）不列为强声明；</li>
 *   <li>全部命中 → VERIFIED；部分命中 → PARTIAL（提示衍生数值仅供参考）；全部未命中 → FAILED（强警告）。</li>
 * </ul>
 * <p>
 * 这是一道**非阻断**的护栏（v1）：不重写答案，只在需要时追加显式声明并产出
 * {status, unsupportedNumbers} 供持久化/事件消费。数字对齐是启发式，未来应以
 * eval 回归校准阈值与单位换算策略。
 */
@Slf4j
@Component
public class MetricAnswerVerifier {

    /** 数字串：支持千分位逗号；数字后允许跟随单位（长单位优先） */
    private static final Pattern NUMBER_CLAIM =
            Pattern.compile("(?<![\\w.])(-?[\\d,]+(?:\\.\\d+)?)\\s*(亿元|万元|亿|万|%|％|元)?");

    /** 千分位逗号 */
    private static final Pattern THOUSAND_SEP = Pattern.compile(",");

    /** 单位 → 乘数 */
    private static final BigDecimal BILLION = new BigDecimal("100000000");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /** 显著值下限：无单位数值达到该绝对值才被视为强声明（避免把衍生小数/序号算进去） */
    private static final BigDecimal LARGE_THRESHOLD = new BigDecimal("10000");

    /** 断言用的答案截取长度上限（防超长答案 OCR 式误报） */
    private static final int ANSWER_SCAN_LIMIT = 6000;

    /** 单条答案最多计入的显著声明数（超出不限，用于总量统计） */
    private static final int MAX_CLAIMS_TRACKED = 200;

    /** 一个待校验的数值声明的规范化结果 */
    private record NormalizedClaim(String label, BigDecimal value, boolean hasUnit, boolean percent) {
    }

    /**
     * 校验最终答案中的显著数值是否都能在本次查询结果（证据集合）中找到。
     *
     * @param answerText 最终答案文本
     * @param evidences  本轮成功指标查询的证据列表（可为空）
     * @return 校验结果
     */
    public GroundingResult verify(String answerText, List<MetricQueryEvidence> evidences) {
        if (!StringUtils.hasText(answerText)) {
            return GroundingResult.noEvidence();
        }
        if (evidences == null || evidences.isEmpty()) {
            return GroundingResult.noEvidence();
        }

        // 1) 合并所有证据的规范化数值集合（含 total）
        Set<String> evidenceNumbers = new LinkedHashSet<>();
        for (MetricQueryEvidence ev : evidences) {
            for (String raw : ev.getNumberTokens()) {
                String norm = normalizeEvidence(raw);
                if (norm != null) {
                    evidenceNumbers.add(norm);
                }
            }
        }
        if (evidenceNumbers.isEmpty()) {
            return GroundingResult.noEvidence();
        }

        // 2) 抽取答案中的显著数值声明
        List<NormalizedClaim> claims = extractClaims(answerText);
        if (claims.isEmpty()) {
            return GroundingResult.inconclusive();
        }

        // 3) 逐条对齐
        List<String> unsupported = new ArrayList<>();
        int verified = 0;
        for (NormalizedClaim claim : claims) {
            String norm = canonical(claim.value());
            if (evidenceNumbers.contains(norm)) {
                verified++;
            } else {
                unsupported.add(claim.label());
            }
        }
        int total = claims.size();

        if (total == 0) {
            return GroundingResult.inconclusive();
        }
        if (unsupported.isEmpty()) {
            return GroundingResult.verified(total);
        }
        if (verified == 0) {
            return GroundingResult.failed(cap(unsupported), total);
        }
        return GroundingResult.partial(cap(unsupported), total, verified);
    }

    /**
     * 从答案文本中抽取"显著数值声明"。只保留有利于检测编造的高信号数值：
     * 带单位（万/亿/元/%）的数值，或绝对值 ≥ {@link #LARGE_THRESHOLD} 的大数值。
     */
    private List<NormalizedClaim> extractClaims(String answer) {
        String scan = answer.length() > ANSWER_SCAN_LIMIT
                ? answer.substring(0, ANSWER_SCAN_LIMIT) : answer;
        List<NormalizedClaim> claims = new ArrayList<>();
        Matcher m = NUMBER_CLAIM.matcher(scan);
        int guard = 0;
        while (m.find() && guard++ < MAX_CLAIMS_TRACKED) {
            String rawNum = m.group(1);
            String unit = m.group(2);
            if (rawNum == null) {
                continue;
            }
            BigDecimal value = parseDecimal(rawNum);
            if (value == null) {
                continue;
            }
            boolean hasUnit = unit != null && !unit.isBlank();
            boolean percent = unit != null && ("%".equals(unit) || "％".equals(unit));
            boolean billion = unit != null && (unit.contains("亿"));
            boolean tenThousand = unit != null && (unit.contains("万"));

            if (billion) {
                value = value.multiply(BILLION);
            } else if (tenThousand) {
                value = value.multiply(TEN_THOUSAND);
            }

            // 显著判断
            boolean largeEnough = value.abs().compareTo(LARGE_THRESHOLD) >= 0;
            if (!largeEnough && !hasUnit) {
                continue;
            }
            // 过滤独立年份（如"截至 2024 年"，非数据声明）
            if (!hasUnit && isYearLike(value)) {
                continue;
            }
            claims.add(new NormalizedClaim(rawNumRaw(rawNum, unit), value, hasUnit, percent));
        }
        return claims;
    }

    /** 展示用的原始标签（如 "123,456" 或 "3.2亿"） */
    private String rawNumRaw(String rawNum, String unit) {
        String s = THOUSAND_SEP.matcher(rawNum).replaceAll("");
        return unit == null || unit.isBlank() ? s : (s + unit);
    }

    /** 解析数字串为 BigDecimal（去除千分位），失败返回 null */
    private BigDecimal parseDecimal(String raw) {
        try {
            String clean = THOUSAND_SEP.matcher(raw).replaceAll("");
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 证据值的规范化：转绝对值无关的规范字符串。
     * <ul>
     *   <li>去千分位、去尾部无关 0（stripTrailingZeros + toPlainString）</li>
     *   <li>证据值一般来自查询结果单元格（原始数值，无单位），因此只做数值归一</li>
     * </ul>
     */
    private String normalizeEvidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        // 兜底去掉可能出现的百分比符号后尝试解析
        BigDecimal v = parseDecimal(t.replace("%", "").replace("％", ""));
        if (v == null) {
            return null;
        }
        return canonical(v);
    }

    /** 数值规范形式：去尾部零、无科学计数法、保留负号 */
    private String canonical(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** 是否形如独立年份（1900–2100），用于过滤非数据声明 */
    private boolean isYearLike(BigDecimal value) {
        if (value.scale() != 0 || value.stripTrailingZeros().scale() != 0) {
            return false;
        }
        try {
            long l = value.longValueExact();
            return l >= 1900 && l <= 2100;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> cap(List<String> list) {
        if (list.size() <= 10) {
            return list;
        }
        return new ArrayList<>(list.subList(0, 10));
    }
}
