package org.forest.chatollama.service;

import cn.hutool.core.date.LocalDateTimeUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ToolCalling {

    @Tool(name = "get_current_time", description = "获取当前或者今天时间")
    public String getCurrentTime() {
        String string = LocalDateTimeUtil.formatNormal(LocalDateTime.now());
        System.out.println("ToolCalling触发 当前时间：" + string);
        return string;
    }

}
