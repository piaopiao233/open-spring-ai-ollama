package org.forest.chatollama.util.websearch;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Tavily搜索引擎实现类
 * 基于Tavily API实现网络搜索功能
 */
@Component
public class TavilyWebSearchTool implements WebSearchTool {

    /**
     * Tavily API搜索接口地址
     */
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    /**
     * Tavily API密钥，从配置文件读取
     */
    @Value("${tavily.api-key}")
    private String apiKey;

    /**
     * 默认最大返回结果数，从配置文件读取，默认值为10
     */
    @Value("${tavily.max-results:10}")
    private Integer maxResults;

    /**
     * 执行网络搜索，使用配置文件中默认的maxResults
     *
     * @param query 搜索关键词
     * @return 搜索结果列表
     */
    @Override
    public List<WebSearchResult> search(String query) {
        return search(query, maxResults);
    }

    /**
     * 执行网络搜索（指定返回结果数量）
     *
     * @param query      搜索关键词
     * @param maxResults 最大返回结果数
     * @return 搜索结果列表，包含URL、标题和内容
     * @throws IllegalArgumentException 当API请求失败时抛出异常
     */
    @Override
    public List<WebSearchResult> search(String query, Integer maxResults) {
        // 构建请求体JSON
        JSONObject requestBody = JSONUtil.createObj()
                .set("query", query)
                .set("search_depth", "advanced")
                .set("max_results", maxResults);

        // 发送POST请求到Tavily API
        HttpResponse response = HttpRequest.post(TAVILY_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .execute();

        // 使用Assert校验响应状态
        Assert.isTrue(response.isOk(), "Tavily API请求失败，状态码: " + response.getStatus());

        // 解析响应JSON
        JSONObject responseBody = JSONUtil.parseObj(response.body());
        JSONArray results = responseBody.getJSONArray("results");

        // 将JSON数组转换为WebSearchResult列表
        List<WebSearchResult> searchResults = new ArrayList<>();

        Assert.isTrue(CollUtil.isNotEmpty(results), "未找到相关信息");
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            WebSearchResult result = WebSearchResult.success(
                    item.getStr("url"),
                    item.getStr("title"),
                    item.getStr("content")
            );
            searchResults.add(result);
        }
        return searchResults;
    }
}
