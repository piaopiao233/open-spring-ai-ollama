package org.forest.chatollama.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.mapper.ChatMessageMapper;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.service.ToolCalling;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        //保存用户消息
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(request.getSessionId());
        chatMessage.setRecordId(request.getRecordId());
        chatMessage.setContent(request.getMessage());
        chatMessage.setType(Const.ChatMessageType.USER);
        chatMessage.setCreateTime(LocalDateTime.now());
        save(chatMessage);
        //查询所有的对话
        List<ChatMessage> chatMessages = selectBySessionId(request.getSessionId(), true);
        //构建多轮对话
        List<Message> messages = buildMessageList(chatMessages);
        Flux<ChatResponse> chatResponseFlux = chatModel.stream(new Prompt(messages)).cache();
        chatResponseFlux.collectList().doOnNext(chatResponseList -> {
            String collect = chatResponseList.stream()
                    .map(r -> r.getResult().getOutput().getText())
                    .collect(Collectors.joining());
            ChatMessage chatMessageAssistant = BeanUtil.copyProperties(chatMessage, ChatMessage.class);
            chatMessageAssistant.setId(null);
            chatMessageAssistant.setType(Const.ChatMessageType.ASSISTANT);
            chatMessageAssistant.setContent(collect);
            chatMessageAssistant.setCreateTime(LocalDateTime.now());
            save(chatMessageAssistant);
        }).subscribe();
        return chatResponseFlux;
    }

    @Override
    public Flux<ChatResponse> simpleGenerateStream(String message) {
        ChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(ToolCalling.toolCallbacks).build();
        Prompt prompt = new Prompt(message);
        Flux<ChatResponse> chatResponseFlux = chatModel.stream(prompt).cache();
        chatResponseFlux.collectList().doOnNext(chatResponseList -> {
            String collect = chatResponseList.stream()
                    .map(r -> r.getResult().getOutput().getText())
                    .collect(Collectors.joining());
            System.out.println("AI回答：" + collect);
        }).subscribe();
        return chatResponseFlux;
    }

    @Override
    public List<ChatMessage> selectBySessionId(String sessionId,  boolean isAsc) {
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getSessionId, sessionId);
        if (isAsc) {
            queryWrapper.orderByAsc(ChatMessage::getId);
        } else {
            queryWrapper.orderByDesc(ChatMessage::getId);
        }
        return list(queryWrapper);
    }

    @Override
    public List<Message> buildMessageList(List<ChatMessage> chatMessageList) {
        List<Message> messages = new ArrayList<>();
        chatMessageList.forEach(chatMessage -> {
            if (Const.ChatMessageType.SYSTEM.equals(chatMessage.getType())) {
                messages.add(new SystemMessage(chatMessage.getContent()));
            } else if (Const.ChatMessageType.USER.equals(chatMessage.getType())){
                messages.add(new UserMessage(chatMessage.getContent()));
            }else {
                messages.add(new AssistantMessage(chatMessage.getContent()));
            }
        });
        return messages;
    }
}
