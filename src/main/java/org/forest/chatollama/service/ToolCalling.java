package org.forest.chatollama.service;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ToolCalling {

   public static final ToolCallback[] toolCallbacks = ToolCallbacks.from(new ToolCalling());

    @Tool(description = "获取当前或者今天时间")
    public String getCurrentTime() {
        String string = LocalDateTimeUtil.formatNormal(LocalDateTime.now());
        System.out.println("ToolCalling触发 当前时间：" + string);
        return string;
    }

    @Tool(name = "get_student_attendance_by_date", description = "获取某个学生某天的考勤")
    public String getStudentAttendance(@ToolParam(required = true, description = "姓名") String name,
                                       @ToolParam(required = false, description = "日期， 可选，格式 yyyy-MM-dd（默认为空，今天也传空值") String date) {
        if (StrUtil.isBlank(name)) {
            return "请填写姓名";
        }
        if (StrUtil.isBlank(date)) {
            date = LocalDateTimeUtil.formatNormal(LocalDate.now());
        }
        try {
            LocalDateTimeUtil.parseDate(date);
        } catch (Exception e) {
            return "请填写正确的日期格式yyyy-mm-dd";
        }
        String string = StrUtil.format("{}的考勤信息如下：一班的{}已签到，二班的{}没有查询到考勤信息", date, name, name);
        System.out.println("ToolCalling触发 getStudentAttendance：" + string);
        return string;
    }

}
