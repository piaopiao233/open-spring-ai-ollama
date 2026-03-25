package org.forest.chatollama.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话消息扩展信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaData {

    /**
     * 本条助手消息中涉及到的工具调用记录。
     */
    private List<ToolCallMeta> toolCalls;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallMeta {

        /**
         * Spring AI 生成的工具调用唯一标识。
         */
        private String id;

        /**
         * 工具调用类型，通常为 function。
         */
        private String type;

        /**
         * 工具名称。
         */
        private String name;

        /**
         * 模型传递给工具的参数 JSON。
         */
        private String arguments;

        /**
         * 工具执行结果。
         */
        private String result;
    }
}
