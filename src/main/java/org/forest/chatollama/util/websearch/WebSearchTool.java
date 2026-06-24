package org.forest.chatollama.util.websearch;

import java.util.List;

/**
 * 网络搜索工具接口
 * 定义统一的网络搜索能力，可由不同搜索引擎实现（如Tavily、Bing等）
 */
public interface WebSearchTool {

    /**
     * 执行网络搜索
     *
     * @param query 搜索关键词
     * @return 搜索结果列表
     */
    List<WebSearchResult> search(String query);

    /**
     * 执行网络搜索（指定返回结果数量）
     *
     * @param query      搜索关键词
     * @param maxResults 最大返回结果数
     * @return 搜索结果列表
     */
    List<WebSearchResult> search(String query, Integer maxResults);
}
