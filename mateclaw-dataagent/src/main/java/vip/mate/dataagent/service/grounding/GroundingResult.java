package vip.mate.dataagent.service.grounding;

import java.io.Serializable;
import java.util.List;

/**
 * 最终答案的数字对齐校验结果（P0-1）。
 * <p>
 * 见 {@link MetricAnswerVerifier#verify} 的判定语义。
 */
public final class GroundingResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        /** 本轮未执行指标查询，无证据可校验 → 跳过 */
        NO_EVIDENCE,
        /** 有证据，但答案中没有需要校验的显著数值声明 → 无法判定，不打扰 */
        INCONCLUSIVE,
        /** 答案中的显著数值全部能在查询结果中找到 → 通过 */
        VERIFIED,
        /** 部分显著数值未能在查询结果中找到（可能为衍生计算/单位换算）→ 提示 */
        PARTIAL,
        /** 答案中的显著数值均未能在查询结果中找到 → 强警告 */
        FAILED
    }

    private final Status status;
    private final List<String> unsupportedNumbers;
    private final int totalClaims;
    private final int verifiedClaims;

    public GroundingResult(Status status, List<String> unsupportedNumbers, int totalClaims, int verifiedClaims) {
        this.status = status;
        this.unsupportedNumbers = unsupportedNumbers == null ? List.of() : List.copyOf(unsupportedNumbers);
        this.totalClaims = totalClaims;
        this.verifiedClaims = verifiedClaims;
    }

    public static GroundingResult noEvidence() {
        return new GroundingResult(Status.NO_EVIDENCE, List.of(), 0, 0);
    }

    public static GroundingResult inconclusive() {
        return new GroundingResult(Status.INCONCLUSIVE, List.of(), 0, 0);
    }

    public static GroundingResult verified(int totalClaims) {
        return new GroundingResult(Status.VERIFIED, List.of(), totalClaims, totalClaims);
    }

    public static GroundingResult partial(List<String> unsupported, int totalClaims, int verifiedClaims) {
        return new GroundingResult(Status.PARTIAL, unsupported, totalClaims, verifiedClaims);
    }

    public static GroundingResult failed(List<String> unsupported, int totalClaims) {
        return new GroundingResult(Status.FAILED, unsupported, totalClaims, 0);
    }

    public Status getStatus() {
        return status;
    }

    public List<String> getUnsupportedNumbers() {
        return unsupportedNumbers;
    }

    public int getTotalClaims() {
        return totalClaims;
    }

    public int getVerifiedClaims() {
        return verifiedClaims;
    }

    /** 是否需要触发显式声明（仅 PARTIAL / FAILED） */
    public boolean needsCaveat() {
        return status == Status.PARTIAL || status == Status.FAILED;
    }

    /** 生成的用户可见提示文案（追加到答案末尾；无需要时为 null） */
    public String caveatText() {
        if (status == Status.FAILED) {
            return "⚠️ 数据校验提示：本条回答中的显著数值（"
                    + String.join("、", unsupportedNumbers) + "）未能在本次查询结果中核对到，"
                    + "可能与查询口径不符或为遗漏数据，请以查询返回的原始结果为准，谨慎参考。";
        }
        if (status == Status.PARTIAL) {
            return "※ 提示：本条回答中包含基于查询结果的衍生/换算数值，未逐项与原始结果核对，"
                    + "如需精确数值请以查询返回结果为准。";
        }
        return null;
    }
}
