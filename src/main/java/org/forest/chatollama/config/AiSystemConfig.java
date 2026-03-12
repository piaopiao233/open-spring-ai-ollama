package org.forest.chatollama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.NotNull;

/**
 * AI系统配置属性类
 * 用于管理和注入AI相关的配置参数
 */
@Component
@ConfigurationProperties(prefix = "system.ai")
@Validated
@Data
public class AiSystemConfig {

    /**
     * 每个RAG查询变体检索的文档数量
     * 控制每个查询变体返回的文档数量
     */
    @NotNull(message = "topKPerQuery不能为空")
    @Min(value = 1, message = "topKPerQuery必须大于等于1")
    private Integer topKPerQuery;

    /**
     * 所有RAG查询变体检索的文档总数量
     * 控制所有查询变体返回的文档总数
     */
    @NotNull(message = "allTopK不能为空")
    @Min(value = 1, message = "allTopK必须大于等于1")
    private Integer allTopK;

    /**
     * RAG查询变体数量
     * 控制生成的查询变体个数
     */
    @NotNull(message = "numVariants不能为空")
    @Min(value = 1, message = "numVariants必须大于等于1")
    private Integer numVariants;


    /**
     * rag查询相似度阈值
     */
    @NotNull(message = "similarityThreshold不能为空")
    @Min(value = 0, message = "similarityThreshold必须大于等于0")
    public Double similarityThreshold;
}