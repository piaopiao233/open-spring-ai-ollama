package org.forest.chatollama.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.forest.chatollama.common.context.UserContext;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.mapper.ChatMessageMapper;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.ChatSession;
import org.forest.chatollama.dto.CustomChatResponse;
import org.forest.chatollama.dto.User;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.service.IChatSessionService;
import org.forest.chatollama.service.ToolCalling;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 居森林
 * @since 2025-03-21 15:25:53
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    private final OllamaChatModel chatModel;
    private final SpringAiRagUtils springAiRagUtils;
    private final IChatSessionService chatSessionService;

    @Override
    public Flux<CustomChatResponse> generateStream(ChatMessageRequest request) {
        User user = UserContext.getUser();
        Assert.isTrue(user != null, "用户未登录");
        Long userId = user.getId();
        Long schoolId = user.getSchoolId();
        String sessionId = request.getSessionId();
        String recordId = IdUtil.fastSimpleUUID();
        String userMessage = request.getMessage();
        if (StrUtil.isBlank(sessionId)) {
            sessionId = IdUtil.fastSimpleUUID();
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) : userMessage;
            ChatSession session = new ChatSession(sessionId, title, userId, schoolId);
            chatSessionService.save(session);
        } else {
            ChatSession chatSession = chatSessionService.getBySessionId(sessionId);
            Assert.isTrue(chatSession != null, "会话不存在");
            Assert.isTrue(Objects.equals(chatSession.getUserId(), userId), "用户无权限访问会话");
        }
        ChatMessage userChat = new ChatMessage(schoolId, userId, Const.ChatMessageType.USER, sessionId, recordId, userMessage);
        save(userChat);
        List<ChatMessage> historyMessages = selectBySessionId(sessionId, true);
        List<Message> messages = buildMessageList(historyMessages);

        ChatOptions chatOptions = OllamaChatOptions.builder()
                .disableThinking()
                .toolCallbacks(ToolCalling.toolCallbacks)
                .build();
        Prompt prompt = new Prompt(messages, chatOptions);
        StringBuffer fullContent = new StringBuffer();
        String finalSessionId = sessionId;
        return chatModel.stream(prompt)
                .map(chatResponse -> {
                    String delta = chatResponse.getResult().getOutput().getText();
                    String thinkingPart = chatResponse.getResult().getMetadata().get("thinking");
                    boolean isThinking = StrUtil.isNotBlank(thinkingPart);
                    Integer tokens = null;
                    if (chatResponse.getMetadata() != null
                            && chatResponse.getMetadata().getUsage() != null) {
                        tokens = chatResponse.getMetadata().getUsage().getTotalTokens();
                    }
                    fullContent.append(delta);
                    return new CustomChatResponse(delta, isThinking, finalSessionId, recordId, tokens);
                })
                .doOnComplete(() -> {
                    ChatMessage assistantChat = new ChatMessage(schoolId, userId, Const.ChatMessageType.ASSISTANT, finalSessionId, recordId, fullContent.toString());
                    save(assistantChat);
                    chatSessionService.lambdaUpdate()
                            .eq(ChatSession::getSessionId, finalSessionId)
                            .set(ChatSession::getUpdateTime, LocalDateTime.now())
                            .update();
                })
                .doOnError(err -> log.error("流异常: ", err));
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
                .doOnNext(content -> log.info("完整响应: {}", content)).subscribe();   // 触发收集
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
        for (ChatMessage chatMessage : chatMessageList) {
            Short type = chatMessage.getType();
            String content = chatMessage.getContent();
            if (Const.ChatMessageType.USER.equals(type)) {
                messages.add(new UserMessage(content));
            } else if (Const.ChatMessageType.ASSISTANT.equals(type)) {
                messages.add(new AssistantMessage(content));
            } else if (Const.ChatMessageType.SYSTEM.equals(type)) {
                messages.add(new SystemMessage(content));
            } else if (Const.ChatMessageType.TOOL.equals(type)) {

            }
        }
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
