package org.forest.chatollama.service;

import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.model.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 居森林
 * @since 2025-03-21 15:25:53
 */
public interface IChatMessageService extends IService<ChatMessage> {

    Flux<ChatResponse> generateStream(ChatMessageRequest request);


    List<ChatMessage> selectBySessionId(String sessionId,  boolean isAsc);

    //构建多轮对话
    List<Message> buildMessageList(List<ChatMessage> chatMessageList);
}
