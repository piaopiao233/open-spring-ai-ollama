package org.forest.chatollama.service.ai;

import java.util.Map;

/**
 * 工具标签配置
 * 维护工具名称到用户友好显示名称的映射
 */
public class ToolLabelConfig {

    /**
     * 工具名称 -> 用户显示名称
     */
    private static final Map<String, String> LABEL_MAP = Map.of(
            "get_current_time", "正在获取当前时间",
            "web_search", "正在进行网络搜索"
    );

    /**
     * 获取工具的用户显示名称
     * 如果没有配置则返回工具原名称
     *
     * @param toolName 工具名称
     * @return 用户显示名称
     */
    public static String getLabel(String toolName) {
        return LABEL_MAP.getOrDefault(toolName, toolName);
    }
}
