package org.forest.chatollama.service.impl;

import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.mapper.ChatMessageMapper;
import org.forest.chatollama.service.IChatMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 居森林
 * @since 2025-03-21 15:25:53
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {


    @Autowired
    private OllamaChatModel chatModel;


    @Override
    public Flux<ChatResponse> generateStream(ChatMessageRequest request) {
        UserMessage userMessage = new UserMessage(request.getMessage());
        List<Message> messages = List.of(userMessage);
        Prompt prompt = new Prompt(messages);
        return chatModel.stream(prompt).cache();
    }
}
