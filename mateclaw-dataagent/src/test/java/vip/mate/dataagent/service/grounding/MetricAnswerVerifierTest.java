package vip.mate.dataagent.service.grounding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MetricAnswerVerifier} 单测（P0-1）。
 * <p>
 * 纯 JUnit 单测，无 Spring 上下文。覆盖：证据缺失跳过、无显著声明、全命中、
 * 全未命中（编造）、部分命中（衍生换算）、单位换算、千分位、独立年份过滤。
 */
class MetricAnswerVerifierTest {

    private final MetricAnswerVerifier verifier = new MetricAnswerVerifier();

    private MetricQueryEvidence evidence(Object... numbers) {
        MetricQueryEvidence.Builder b = MetricQueryEvidence.builder(1L);
        for (Object n : numbers) {
            b.addNumber(n.toString());
        }
        return b.build();
    }

    @Test
    void noEvidence_whenNoQueryExecuted() {
        GroundingResult r = verifier.verify("销售额 1.2亿元", List.of());
        assertEquals(GroundingResult.Status.NO_EVIDENCE, r.getStatus());
    }

    @Test
    void noEvidence_whenEvidenceHasNoNumbers() {
        GroundingResult r = verifier.verify("销售额 1.2亿元", List.of(evidence()));
        assertEquals(GroundingResult.Status.NO_EVIDENCE, r.getStatus());
    }

    @Test
    void inconclusive_whenAnswerHasNoSignificantNumbers() {
        GroundingResult r = verifier.verify("已按月度汇总完成，详见下方表格。", List.of(evidence("120000000")));
        assertEquals(GroundingResult.Status.INCONCLUSIVE, r.getStatus());
    }

    @Test
    void verified_whenAllSignificantNumbersMatch() {
        GroundingResult r = verifier.verify(
                "本季度销售额 1.2亿元，同比增长 20%。",
                List.of(evidence("120000000", "20")));
        assertEquals(GroundingResult.Status.VERIFIED, r.getStatus());
        assertTrue(r.getUnsupportedNumbers().isEmpty());
    }

    @Test
    void failed_whenNumberFabricated() {
        GroundingResult r = verifier.verify(
                "本季度销售额 99.9亿元，环比增长 30%。",
                List.of(evidence("120000000", "20")));
        assertEquals(GroundingResult.Status.FAILED, r.getStatus());
        assertEquals(2, r.getUnsupportedNumbers().size());
        assertTrue(r.getUnsupportedNumbers().contains("99.9亿元"));
        assertTrue(r.needsCaveat());
    }

    @Test
    void partial_whenSomeNumbersDerived() {
        GroundingResult r = verifier.verify(
                "本季度销售额 1.2亿元，华东占 87%。",
                List.of(evidence("120000000", "15")));
        assertEquals(GroundingResult.Status.PARTIAL, r.getStatus());
        assertTrue(r.getUnsupportedNumbers().contains("87%"));
        assertTrue(r.needsCaveat());
    }

    @Test
    void unitScaling_3_2Yi_matches_320000000() {
        GroundingResult r = verifier.verify("本季度销售额为 3.2亿。", List.of(evidence("320000000")));
        assertEquals(GroundingResult.Status.VERIFIED, r.getStatus());
    }

    @Test
    void thousandsSeparator_matchesPlain() {
        GroundingResult r = verifier.verify("订单量为 123,456 单。", List.of(evidence("123456")));
        assertEquals(GroundingResult.Status.VERIFIED, r.getStatus());
    }

    @Test
    void standaloneYear_isNotFlagged() {
        // "2024" 为独立年份，不产生显著声明，不应出现在 unsupported 中
        GroundingResult r = verifier.verify("截至2024年，销售额 1.2亿元。", List.of(evidence("120000000")));
        assertEquals(GroundingResult.Status.VERIFIED, r.getStatus());
        assertTrue(r.getUnsupportedNumbers().isEmpty());
    }

    @Test
    void smallNoUnitNumber_notFlagged() {
        // "3家"：无单位且不满足大数阈值 → 不产生显著声明
        GroundingResult r = verifier.verify("新增了3家门店。", List.of(evidence("120000000")));
        assertEquals(GroundingResult.Status.INCONCLUSIVE, r.getStatus());
    }

    @Test
    void partial_caveatText_providesGuidance() {
        GroundingResult r = verifier.verify("销售额 1.2亿元，华东占 87%。", List.of(evidence("120000000", "15")));
        assertTrue(r.caveatText() != null && !r.caveatText().isBlank());
    }
}
