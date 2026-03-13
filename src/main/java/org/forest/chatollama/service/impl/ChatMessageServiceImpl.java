package org.forest.chatollama.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.mapper.ChatMessageMapper;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.CustomChatResponse;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 居森林
 * @since 2025-03-21 15:25:53
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {


    @Autowired
    private OllamaChatModel chatModel;

    @Autowired
    private SpringAiRagUtils springAiRagUtils;

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
        ChatOptions chatOptions = OllamaChatOptions.builder()
                .disableThinking()
                // .toolCallbacks(ToolCalling.toolCallbacks)
                .build();
        Prompt prompt = new Prompt(messages, chatOptions);
        Flux<ChatResponse> chatResponseFlux = chatModel.stream(prompt);
        // 使用 share() 或 cache() 让多个订阅者共享同一份流（非常重要！）
        Flux<ChatResponse> sharedFlux = chatResponseFlux.share();   // 或 .cache() 如果你确定只有一个订阅者
        // 异步收集完整内容并保存（不阻塞主流程）
        sharedFlux.map(resp -> resp.getResult().getOutput().getText())
                .reduce("", String::concat)           // 拼接所有 token
                .doOnNext(content -> {
                    ChatMessage chatMessageAssistant = BeanUtil.copyProperties(chatMessage, ChatMessage.class);
                    chatMessageAssistant.setId(null);
                    chatMessageAssistant.setType(Const.ChatMessageType.ASSISTANT);
                    chatMessageAssistant.setContent(content);
                    chatMessageAssistant.setCreateTime(LocalDateTime.now());
                    save(chatMessageAssistant);
                }).subscribe();
        return sharedFlux;
    }

    @Override
    public Flux<CustomChatResponse> simpleGenerateStreamCustom(String message) {
        ChatOptions chatOptions = OllamaChatOptions.builder()
                // .toolCallbacks(ToolCalling.toolCallbacks)
                .disableThinking()
                .build();
        Prompt prompt = new Prompt(message, chatOptions);
        // 用于收集完整内容（日志/保存用）
        StringBuffer fullContent = new StringBuffer();
        return chatModel.stream(prompt)
                .map(chatResponse -> {
                    // 1. 获取当前 chunk 文本
                    String delta = chatResponse.getResult().getOutput().getText();
                    // 2. 判断是否思考过程（Spring AI Ollama 官方方式）
                    String thinkingPart = chatResponse.getResult().getMetadata().get("thinking");
                    boolean isThinking = StrUtil.isNotBlank(thinkingPart);
                    // 3. token 数量（仅最后一块才有，非流式 usage 会在最后一 chunk 返回）
                    Integer tokens = null;
                    if (chatResponse.getMetadata() != null
                            && chatResponse.getMetadata().getUsage() != null) {
                        tokens = chatResponse.getMetadata().getUsage().getTotalTokens();
                    }
                    // 4. 累积完整内容（日志用）
                    fullContent.append(delta);
                    return new CustomChatResponse(
                            delta,
                            isThinking,
                            null,
                            tokens
                    );
                })
                .doOnComplete(() -> {
                    System.out.println("完整响应: " + fullContent);
                }).doOnCancel(() -> {
                    System.out.println("用户取消，部分内容: " + fullContent);
                }).doOnError(err -> System.err.println("流异常: " + err));
    }

    @Override
    public Flux<ChatResponse> simpleGenerateStream(String message) {
        ChatOptions chatOptions = OllamaChatOptions.builder()
                .disableThinking()
                // .toolCallbacks(ToolCalling.toolCallbacks)
                .build();
        Prompt prompt = new Prompt(message, chatOptions);
        Flux<ChatResponse> flux = chatModel.stream(prompt);
        // 使用 share() 或 cache() 让多个订阅者共享同一份流（非常重要！）
        Flux<ChatResponse> sharedFlux = flux.share();   // 或 .cache() 如果你确定只有一个订阅者
        // 异步收集完整内容并保存（不阻塞主流程）
        sharedFlux.map(resp -> resp.getResult().getOutput().getText())
                .reduce("", String::concat)           // 拼接所有 token
                .doOnNext(System.out::println).subscribe();   // 触发收集
        // 返回给调用方的是原始流
        return sharedFlux;
    }

    @Override
    public List<ChatMessage> selectBySessionId(String sessionId, boolean isAsc) {
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
            } else if (Const.ChatMessageType.USER.equals(chatMessage.getType())) {
                messages.add(new UserMessage(chatMessage.getContent()));
            } else {
                messages.add(new AssistantMessage(chatMessage.getContent()));
            }
        });
        return messages;
    }

    @Override
    public List<Document> multiQuerySimilaritySearch(String sessionId, String currentQuestion) {
        List<Message> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(sessionId)) {
            //查询所有的对话
            List<ChatMessage> chatMessages = selectBySessionId(sessionId, true);
            //构建多轮对话
            messages = buildMessageList(chatMessages);
        }
        //获取上下文的查询变体
        List<String> queries = springAiRagUtils.generateMultiCondensedQueries(messages, currentQuestion);
        System.out.printf("查询变体有：%s%n", queries);
        return springAiRagUtils.multiQuerySimilaritySearch(queries);
    }
}
