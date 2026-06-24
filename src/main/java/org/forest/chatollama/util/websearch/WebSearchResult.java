package org.forest.chatollama.util.websearch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网络搜索结果类
 * 封装搜索引擎返回的单条结果数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSearchResult {

    /**
     * 状态码：0-成功，1-失败
     */
    private Integer code;

    /**
     * 状态消息
     */
    private String message;

    /**
     * 搜索结果的URL链接
     */
    private String url;

    /**
     * 搜索结果的标题
     */
    private String title;

    /**
     * 搜索结果的内容摘要
     */
    private String content;

    /**
     * 创建成功结果
     *
     * @param url     URL链接
     * @param title   标题
     * @param content 内容
     * @return 成功的搜索结果
     */
    public static WebSearchResult success(String url, String title, String content) {
        return new WebSearchResult(0, "success", url, title, content);
    }

    /**
     * 创建失败结果
     *
     * @param message 错误信息
     * @return 失败的搜索结果
     */
    public static WebSearchResult fail(String message) {
        return new WebSearchResult(1, message, null, null, null);
    }
}
