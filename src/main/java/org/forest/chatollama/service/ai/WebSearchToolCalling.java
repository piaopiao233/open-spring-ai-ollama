package org.forest.chatollama.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.forest.chatollama.util.websearch.WebSearchResult;
import org.forest.chatollama.util.websearch.WebSearchTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网络搜索工具调用类
 * 提供基于ToolCalling的网络搜索能力，供AI模型调用
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSearchToolCalling {

    private final WebSearchTool webSearchTool;

    /**
     * 执行网络搜索
     *
     * @param query 搜索关键词
     * @return 搜索结果列表，包含状态、URL、标题和内容
     */
    @Tool(name = "web_search", description = "搜索互联网获取实时信息，当需要查询最新资讯、新闻或事实时使用")
    public List<WebSearchResult> webSearch(@ToolParam(description = "搜索关键词") String query) {
        try {
            log.info("web_search开始搜索: " + query);
            return webSearchTool.search(query);
        } catch (Exception e) {
            log.error("搜索失败: " + e.getMessage(), e);
            return List.of(WebSearchResult.fail("搜索失败: " + e.getMessage()));
        }
    }
}
