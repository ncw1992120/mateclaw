package vip.mate.dataagent.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AloudataTimeResolver} 单元测试。
 * <p>
 * 固定锚点 today=2026-09-03（星期四），期望日期均为手工推算：
 * 本周一=2026-08-31，上周一=2026-08-24，上周五=2026-08-28，上周日=2026-08-30。
 * 另设跨年锚点 today=2026-01-05（星期一）验证跨年/跨月边界。
 */
class AloudataTimeResolverTest {

    /** 常规锚点：2026-09-03（星期四） */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    /** 跨年锚点：2026-01-05（星期一） */
    private static final LocalDate CROSS_YEAR_TODAY = LocalDate.of(2026, 1, 5);

    private AloudataTimeResolver.ResolvedRange resolve(String message) {
        return AloudataTimeResolver.resolveRelativeTime(message, TODAY);
    }

    @Test
    @DisplayName("昨日返回前一天单点")
    void yesterdayResolvesToPreviousDay() {
        AloudataTimeResolver.ResolvedRange range = resolve("查询昨日的销售额");
        assertEquals(LocalDate.of(2026, 9, 2), range.start());
        assertEquals(LocalDate.of(2026, 9, 2), range.end());
        assertEquals("昨日", range.label());
    }

    @Test
    @DisplayName("昨日形容词变体（昨天/昨日）")
    void yesterdayVariants() {
        assertEquals(LocalDate.of(2026, 9, 2), resolve("昨天数据").start());
        assertEquals(LocalDate.of(2026, 9, 2), resolve("查询昨日情况").start());
    }

    @Test
    @DisplayName("今日返回当天单点")
    void todayResolvesToCurrentDay() {
        AloudataTimeResolver.ResolvedRange range = resolve("今天客户数");
        assertEquals(LocalDate.of(2026, 9, 3), range.start());
        assertEquals(LocalDate.of(2026, 9, 3), range.end());
        assertEquals("今日", range.label());
    }

    @Test
    @DisplayName("上周五定位到上周对应日期")
    void lastFridayResolvesToCorrectDate() {
        AloudataTimeResolver.ResolvedRange range = resolve("查询上周五的当月亏损客户数_营业部");
        assertEquals(LocalDate.of(2026, 8, 28), range.start());
        assertEquals(LocalDate.of(2026, 8, 28), range.end());
        assertEquals("上周五", range.label());
    }

    @Test
    @DisplayName("上周各星期与周日变体")
    void lastWeekWeekdayVariants() {
        assertEquals(LocalDate.of(2026, 8, 24), resolve("上周一").start());
        assertEquals(LocalDate.of(2026, 8, 26), resolve("上周三").start());
        // "天" 归一化为 "日"（周日）
        assertEquals(LocalDate.of(2026, 8, 30), resolve("上周天").start());
    }

    @Test
    @DisplayName("上周整周返回周一到周日")
    void lastWeekResolvesToFullWeek() {
        AloudataTimeResolver.ResolvedRange range = resolve("上周的销售额");
        assertEquals(LocalDate.of(2026, 8, 24), range.start());
        assertEquals(LocalDate.of(2026, 8, 30), range.end());
        assertEquals("上周", range.label());
    }

    @Test
    @DisplayName("本周与这周变体")
    void thisWeekWeekdayVariants() {
        assertEquals(LocalDate.of(2026, 9, 4), resolve("本周五").start());
        assertEquals(LocalDate.of(2026, 9, 1), resolve("这周二").start());
    }

    @Test
    @DisplayName("上月返回整月且月末正确")
    void lastMonthResolvesToFullMonth() {
        AloudataTimeResolver.ResolvedRange range = resolve("上月的亏损客户数");
        assertEquals(LocalDate.of(2026, 8, 1), range.start());
        assertEquals(LocalDate.of(2026, 8, 31), range.end());
        assertEquals("上月", range.label());
    }

    @Test
    @DisplayName("本月返回月初到今日")
    void thisMonthResolvesToMonthStartUntilToday() {
        AloudataTimeResolver.ResolvedRange range = resolve("本月新增客户");
        assertEquals(LocalDate.of(2026, 9, 1), range.start());
        assertEquals(LocalDate.of(2026, 9, 3), range.end());
        assertEquals("本月", range.label());
    }

    @Test
    @DisplayName("近N天区间")
    void recentDaysResolvesToRange() {
        AloudataTimeResolver.ResolvedRange range = resolve("近7天的销售额");
        assertEquals(LocalDate.of(2026, 8, 28), range.start());
        assertEquals(LocalDate.of(2026, 9, 3), range.end());
        assertEquals("近7天", range.label());
    }

    @Test
    @DisplayName("最近N日与近30天变体")
    void recentDaysVariants() {
        assertEquals(LocalDate.of(2026, 8, 5), resolve("近30天").start());
        // "最近2日" = 昨天 ~ 今天
        AloudataTimeResolver.ResolvedRange range = resolve("最近2日");
        assertEquals(LocalDate.of(2026, 9, 2), range.start());
        assertEquals(LocalDate.of(2026, 9, 3), range.end());
    }

    @Test
    @DisplayName("近0天兜底为1天")
    void zeroDaysFallsBackToOneDay() {
        AloudataTimeResolver.ResolvedRange range = resolve("近0天");
        assertEquals(LocalDate.of(2026, 9, 3), range.start());
        assertEquals(LocalDate.of(2026, 9, 3), range.end());
    }

    @Test
    @DisplayName("去年同期返回去年同月")
    void lastYearSamePeriodResolvesToLastYearSameMonth() {
        AloudataTimeResolver.ResolvedRange range = resolve("和去年同期对比");
        assertEquals(LocalDate.of(2025, 9, 1), range.start());
        assertEquals(LocalDate.of(2025, 9, 30), range.end());
        assertEquals("去年同期", range.label());
    }

    @Test
    @DisplayName("去年今日返回去年当天（优先于内部'今日'子串）")
    void lastYearTodayResolvesToLastYearSameDay() {
        assertEquals(LocalDate.of(2025, 9, 3), resolve("去年今日").start());
    }

    @Test
    @DisplayName("去年兜底返回去年全年")
    void lastYearResolvesToFullLastYear() {
        AloudataTimeResolver.ResolvedRange range = resolve("去年的销售额");
        assertEquals(LocalDate.of(2025, 1, 1), range.start());
        assertEquals(LocalDate.of(2025, 12, 31), range.end());
        assertEquals("去年", range.label());
    }

    @Test
    @DisplayName("多个时间表述取原话中最先出现者")
    void multipleExpressionsTakeEarliestPosition() {
        AloudataTimeResolver.ResolvedRange range = resolve("上周五和昨日对比");
        assertEquals(LocalDate.of(2026, 8, 28), range.start());
        assertEquals("上周五", range.label());
    }

    @Test
    @DisplayName("跨年场景上周五")
    void lastFridayAcrossYearBoundary() {
        // today=2026-01-05（周一），上周五=2026-01-02
        AloudataTimeResolver.ResolvedRange range =
                AloudataTimeResolver.resolveRelativeTime("上周五", CROSS_YEAR_TODAY);
        assertEquals(LocalDate.of(2026, 1, 2), range.start());
    }

    @Test
    @DisplayName("跨年场景上周整周")
    void lastWeekAcrossYearBoundary() {
        // 上周跨年：2025-12-29（周一）~ 2026-01-04（周日）
        AloudataTimeResolver.ResolvedRange range =
                AloudataTimeResolver.resolveRelativeTime("上周", CROSS_YEAR_TODAY);
        assertEquals(LocalDate.of(2025, 12, 29), range.start());
        assertEquals(LocalDate.of(2026, 1, 4), range.end());
    }

    @Test
    @DisplayName("跨年场景上月")
    void lastMonthAcrossYearBoundary() {
        AloudataTimeResolver.ResolvedRange range =
                AloudataTimeResolver.resolveRelativeTime("上月", CROSS_YEAR_TODAY);
        assertEquals(LocalDate.of(2025, 12, 1), range.start());
        assertEquals(LocalDate.of(2025, 12, 31), range.end());
    }

    @Test
    @DisplayName("无相对时间表述返回null")
    void noTimeExpressionReturnsNull() {
        assertNull(resolve("查询销售额"));
        assertNull(resolve("各营业部客户数"));
    }

    @Test
    @DisplayName("null与空白消息返回null")
    void nullOrBlankMessageReturnsNull() {
        assertNull(AloudataTimeResolver.resolveRelativeTime(null, TODAY));
        assertNull(AloudataTimeResolver.resolveRelativeTime("", TODAY));
        assertNull(AloudataTimeResolver.resolveRelativeTime("   ", TODAY));
    }

    @Test
    @DisplayName("多指标提问不误锁（含'周'但非相对时间）")
    void nonTimeExpressionsDoNotLock() {
        // 指标名含"周"但不构成相对时间表述，不应锁定
        assertNull(resolve("查询周报数据和月报数据"));
    }
}
