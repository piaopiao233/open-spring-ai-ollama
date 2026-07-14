package org.forest.chatollama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.forest.chatollama.service.ai.ToolLabelConfig;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomChatResponse {

    /**
     * 工具调用信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallInfo {
        /**
         * 工具名称
         */
        private String toolName;
        /**
         * 工具参数
         */
        private String toolArguments;
        /**
         * 用户显示名称
         */
        private String label;

        /**
         * 创建工具调用信息
         *
         * @param toolName      工具名称
         * @param toolArguments 工具参数
         * @return 工具调用信息
         */
        public static ToolCallInfo of(String toolName, String toolArguments) {
            return new ToolCallInfo(toolName, toolArguments, ToolLabelConfig.getLabel(toolName));
        }
    }

    /**
     * 当前 chunk 的文本（delta）
     */
    private String content;

    /**
     * 是否属于思考过程（thinking）
     */
    private Boolean isThinking;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 对话 ID
     */
    private String recordId;

    /**
     * token 数量（仅最后一块有值，其他为 0 或 null）
     */
    private Integer tokenCount;

    /**
     * 提示词token数量（仅最后一块有值，其他为null）
     */
    private Integer promptTokenCount;

    /**
     * 生成内容token数量（仅最后一块有值，其他为null）
     */
    private Integer completionTokenCount;

    /**
     * 工具调用信息
     */
    private List<ToolCallInfo> toolCalls;

    public CustomChatResponse(String content, Boolean isThinking, String sessionId, String recordId, Integer tokenCount) {
        this(content, isThinking, sessionId, recordId, tokenCount, null, null, null);
    }

    /**
     * 创建不包含细分token用量的流式响应。
     *
     * @param content 响应内容
     * @param isThinking 是否为思考内容
     * @param sessionId 会话ID
     * @param recordId 对话ID
     * @param tokenCount 总token数
     * @param toolCalls 工具调用信息
     */
    public CustomChatResponse(String content,
                              Boolean isThinking,
                              String sessionId,
                              String recordId,
                              Integer tokenCount,
                              List<ToolCallInfo> toolCalls) {
        this(content, isThinking, sessionId, recordId, tokenCount, null, null, toolCalls);
    }
}
