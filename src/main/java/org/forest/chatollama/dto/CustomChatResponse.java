package org.forest.chatollama.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.forest.chatollama.service.ai.ToolLabelConfig;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomChatResponse {

    /**
     * 工具调用信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
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
     * 工具调用信息
     */
    private List<ToolCallInfo> toolCalls;

    public CustomChatResponse(String content, Boolean isThinking, String sessionId, String recordId, Integer tokenCount) {
        this(content, isThinking, sessionId, recordId, tokenCount, null);
    }
}
