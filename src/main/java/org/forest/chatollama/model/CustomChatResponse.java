package org.forest.chatollama.model;

public record CustomChatResponse(String content,         // 当前 chunk 的文本（delta）
                                 Boolean isThinking,     // 是否属于思考过程（thinking）
                                 String sessionId,       // 会话 ID
                                 Integer tokenCount      // token 数量（仅最后一块有值，其他为 0 或 null）
){}
