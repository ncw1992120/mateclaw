package vip.mate.tool.builtin;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 内置工具：日期时间
 *
 * @author MateClaw Team
 */
@Component
public class DateTimeTool {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Tool(description = "Get current date and time in yyyy-MM-dd HH:mm:ss format, with day of week in Chinese (e.g. 星期四) appended")
    public String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + dayOfWeekZh(now);
    }

    @Tool(description = "Get current date in yyyy-MM-dd format, with day of week in Chinese (e.g. 星期四) appended")
    public String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + dayOfWeekZh(now);
    }

    @Tool(description = "Get current time in HH:mm:ss format")
    public String getCurrentTime() {
        return LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * 返回中文星期几，如 "星期四"。
     */
    private static String dayOfWeekZh(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        return day.getDisplayName(TextStyle.FULL, Locale.CHINA);
    }
}
