package org.forest.chatollama.service;

import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.model.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.forest.chatollama.dto.CustomChatResponse;
import org.forest.chatollama.model.MetaData;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 居森林
 * @since 2025-03-21 15:25:53
 */
public interface IChatMessageService extends IService<ChatMessage> {

    Flux<CustomChatResponse> generateStream(ChatMessageRequest request);

    Flux<CustomChatResponse> reconnectStream(String sessionId);

    void stopStream(String recordId);

    Flux<CustomChatResponse> simpleGenerateStreamCustom(String message);

    default List<ChatMessage> selectBySessionId(String sessionId) {
        return selectBySessionId(sessionId, true);
    }

    List<ChatMessage> selectBySessionId(String sessionId, boolean isAsc);

    //构建多轮对话
    List<Message> buildMessageList(List<ChatMessage> chatMessageList, boolean includeToolInfo);
    
    default List<Message> buildMessageList(List<ChatMessage> chatMessageList) {
        return buildMessageList(chatMessageList, true);
    }

    /**
     * 多轮对话转换为多查询变体 再查询知识库
     */
    List<Document> multiQuerySimilaritySearch(String sessionId, String currentQuestion);

    void deleteBySessionId(String sessionId);

    ChatMessage selectById(Long id);

    /**
     * 根据对话ID查询并合并本轮工具调用信息。
     *
     * @param recordId 对话ID
     * @return 工具调用信息列表
     */
    List<MetaData.ToolCallMeta> selectToolCallsByRecordId(String recordId);


    /**
     * 查询sessionId 有没有正在进行的聊天
     */
    boolean isSessionIdHasRunningChat(String sessionId);

}
