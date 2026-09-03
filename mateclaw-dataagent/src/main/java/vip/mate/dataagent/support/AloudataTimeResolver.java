package vip.mate.dataagent.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 相对时间表述解析器（系统解析优先）。
 * <p>
 * 背景：LLM 构造 timeConstraint 时对"上周五/近N天/上月"等相对时间常自行推算星期与日期导致算错。
 * 本解析器用确定性规则从用户原话解析相对时间，命中即返回绝对日期区间，由调用方锁定 timeConstraint，
 * LLM 无需（也不允许）再推算日期；解析不到（无相对时间表述或无法识别）时返回 null，
 * 由调用方维持原有 LLM 构造行为。
 * <p>
 * 多条相对时间并存时（如"上周五和昨日对比"）按<b>原话中出现位置</b>取最先者，
 * 保证"去年今日"优先于其内部命中的"今日"等子串级误匹配。
 */
public final class AloudataTimeResolver {

    /** 周对照标签：下标 i 对应 ISO 星期值 i+1（一=0 … 日=6） */
    private static final String WEEKDAY_LABELS = "一二三四五六日";

    /** 昨天/昨日 */
    private static final Pattern P_YESTERDAY = Pattern.compile("昨[天日]");

    /** 今天/今日 */
    private static final Pattern P_TODAY = Pattern.compile("今[天日]");

    /** 上周X（X 为一~日/天），如"上周五" */
    private static final Pattern P_LAST_WEEKDAY = Pattern.compile("上周([一二三四五六日天])");

    /** 本周X/这周X，如"本周五" */
    private static final Pattern P_THIS_WEEKDAY = Pattern.compile("(?:本|这)周([一二三四五六日天])");

    /** 上周（整周，未跟具体星期） */
    private static final Pattern P_LAST_WEEK = Pattern.compile("上周(?![一二三四五六日天])");

    /** 本月/这个月 */
    private static final Pattern P_THIS_MONTH = Pattern.compile("(?:这个月|本月)");

    /** 上月/上个月 */
    private static final Pattern P_LAST_MONTH = Pattern.compile("(?:上个月|上月)");

    /** 近N天/最近N天/近N日/最近N日 */
    private static final Pattern P_RECENT_DAYS = Pattern.compile("(?:最近|近)(\\d+)[天日]");

    /** 去年同期/去年同月（对比区间） */
    private static final Pattern P_LAST_YEAR_SAME = Pattern.compile("(?:去年同期|去年同月)");

    /** 去年今日/去年今天 */
    private static final Pattern P_LAST_YEAR_TODAY = Pattern.compile("去年今[日天]");

    /** 去年（全年，兜底） */
    private static final Pattern P_LAST_YEAR = Pattern.compile("去年");

    private AloudataTimeResolver() {
    }

    /**
     * 从用户原话解析相对时间表述，返回绝对日期区间。
     * <p>
     * 全部候选按其在原话中的起始位置排序，取最早出现者；无相对时间表述时返回 null。
     *
     * @param message 用户原话，可为 null
     * @param today   当前日期（调用方传入确定性锚点，如 Asia/Shanghai 时区）
     * @return 绝对日期区间；未命中返回 null
     */
    public static ResolvedRange resolveRelativeTime(String message, LocalDate today) {
        if (message == null || message.isBlank()) {
            return null;
        }
        List<Candidate> candidates = new ArrayList<>();
        LocalDate yesterday = today.minusDays(1);

        // 昨日/今日
        addCandidate(P_YESTERDAY.matcher(message),
                new ResolvedRange(yesterday, yesterday, "昨日"), candidates);
        addCandidate(P_TODAY.matcher(message),
                new ResolvedRange(today, today, "今日"), candidates);

        // 上周X（如"上周五"）
        Matcher matcher = P_LAST_WEEKDAY.matcher(message);
        if (matcher.find()) {
            LocalDate monday = thisMonday(today).minusWeeks(1);
            LocalDate date = monday.plusDays(weekdayIndex(matcher.group(1)));
            candidates.add(new Candidate(matcher.start(),
                    new ResolvedRange(date, date, "上周" + matcher.group(1))));
        }

        // 本周X/这周X
        matcher = P_THIS_WEEKDAY.matcher(message);
        if (matcher.find()) {
            LocalDate monday = thisMonday(today);
            LocalDate date = monday.plusDays(weekdayIndex(matcher.group(1)));
            candidates.add(new Candidate(matcher.start(),
                    new ResolvedRange(date, date, "本周" + matcher.group(1))));
        }

        // 上周（整周）
        matcher = P_LAST_WEEK.matcher(message);
        if (matcher.find()) {
            LocalDate monday = thisMonday(today).minusWeeks(1);
            candidates.add(new Candidate(matcher.start(),
                    new ResolvedRange(monday, monday.plusDays(6), "上周")));
        }

        // 上月/本月
        matcher = P_LAST_MONTH.matcher(message);
        if (matcher.find()) {
            LocalDate lastMonth = today.minusMonths(1);
            candidates.add(new Candidate(matcher.start(), new ResolvedRange(
                    lastMonth.withDayOfMonth(1),
                    lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()), "上月")));
        }
        matcher = P_THIS_MONTH.matcher(message);
        if (matcher.find()) {
            candidates.add(new Candidate(matcher.start(),
                    new ResolvedRange(today.withDayOfMonth(1), today, "本月")));
        }

        // 近N天
        matcher = P_RECENT_DAYS.matcher(message);
        if (matcher.find()) {
            int days = parseIntGroup(matcher.group(1));
            candidates.add(new Candidate(matcher.start(),
                    new ResolvedRange(today.minusDays(days - 1L), today, "近" + days + "天")));
        }

        // 去年同期/去年今日/去年
        matcher = P_LAST_YEAR_SAME.matcher(message);
        if (matcher.find()) {
            LocalDate lastYear = today.minusYears(1);
            candidates.add(new Candidate(matcher.start(), new ResolvedRange(
                    LocalDate.of(lastYear.getYear(), lastYear.getMonthValue(), 1),
                    LocalDate.of(lastYear.getYear(), lastYear.getMonthValue(),
                            lastYear.lengthOfMonth()), "去年同期")));
        }
        matcher = P_LAST_YEAR_TODAY.matcher(message);
        if (matcher.find()) {
            LocalDate lastYear = today.minusYears(1);
            candidates.add(new Candidate(matcher.start(),
                    new ResolvedRange(lastYear, lastYear, "去年今日")));
        }
        matcher = P_LAST_YEAR.matcher(message);
        if (matcher.find()) {
            int lastYear = today.minusYears(1).getYear();
            candidates.add(new Candidate(matcher.start(), new ResolvedRange(
                    LocalDate.of(lastYear, 1, 1), LocalDate.of(lastYear, 12, 31), "去年")));
        }

        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingInt(Candidate::start));
        return candidates.getFirst().range();
    }

    /**
     * 匹配命中时登记候选：{@code matcher.find()} 为 true 则记录其起始位置与解析结果。
     */
    private static void addCandidate(Matcher matcher, ResolvedRange range, List<Candidate> candidates) {
        if (matcher.find()) {
            candidates.add(new Candidate(matcher.start(), range));
        }
    }

    /**
     * 本周一。
     */
    private static LocalDate thisMonday(LocalDate today) {
        return today.with(DayOfWeek.MONDAY);
    }

    /**
     * 星期汉字（一~日/天）到下标（0~6）的映射。
     */
    private static int weekdayIndex(String weekdayChar) {
        return WEEKDAY_LABELS.indexOf(weekdayChar.replace('天', '日'));
    }

    /**
     * 解析正则数字分组；非数字（正则已保证为 \d+）时兜底为 1。
     */
    private static int parseIntGroup(String group) {
        int days = 1;
        try {
            days = Integer.parseInt(group);
        } catch (NumberFormatException e) {
            // 正则保证纯数字，此处仅防御
        }
        return Math.max(days, 1);
    }

    /**
     * 原话中出现位置 + 对应解析结果，用于多候选按位置取最先者。
     */
    private record Candidate(int start, ResolvedRange range) {
    }

    /**
     * 解析出的绝对日期区间。
     *
     * @param start 区间起始日期（含）
     * @param end   区间结束日期（含）
     * @param label 相对时间的人类可读标签（如"上周五"）
     */
    public record ResolvedRange(LocalDate start, LocalDate end, String label) {
    }
}