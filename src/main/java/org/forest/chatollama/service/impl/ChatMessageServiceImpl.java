package org.forest.chatollama.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.dto.CustomChatResponse;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.mapper.ChatMessageMapper;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.ChatSession;
import org.forest.chatollama.model.MetaData;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.service.IChatSessionService;
import org.forest.chatollama.service.ToolCalling;
import org.forest.chatollama.service.ai.LoggingToolCallAdvisor;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
@Slf4j
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    private final OllamaChatModel chatModel;
    private final SpringAiRagUtils springAiRagUtils;
    private final IChatSessionService chatSessionService;
    private final ToolCalling toolCalling;
    private final ToolCallingManager toolCallingManager;

    @Override
    public Flux<CustomChatResponse> generateStream(ChatMessageRequest request) {
        String recordId = IdUtil.fastSimpleUUID();
        String userMessage = request.getMessage();
        String sessionId = prepareSession(request.getSessionId(), userMessage);
        // 保存用户消息
        saveUserMessage(sessionId, recordId, userMessage);
        //构建上下文
        List<Message> messages = buildMessageList(selectBySessionId(sessionId, true));
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ChatOptions chatOptions = buildChatOptions();
        StringBuffer fullContent = new StringBuffer();
        //创建工具顾问，在工具执行后 将工具信息落库
        LoggingToolCallAdvisor toolCallAdvisor = new LoggingToolCallAdvisor(toolCallingManager, sessionId, recordId, this);
        return chatClient.prompt().messages(messages).advisors(toolCallAdvisor).tools(toolCalling).options(chatOptions).stream()
                .chatResponse()
                .map(chatResponse -> buildStreamResponse(chatResponse, fullContent, sessionId, recordId))
                .doOnComplete(() -> finishAssistantMessage(sessionId, recordId, fullContent))
                .doOnError(err -> log.error("流异常: ", err));
    }

    @Override
    public Flux<CustomChatResponse> simpleGenerateStreamCustom(String message) {
        ChatOptions chatOptions = OllamaChatOptions.builder()
                // .toolCallbacks(ToolCalling.toolCallbacks)
                .disableThinking() //关闭思考
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
    public List<Message> buildMessageList(List<ChatMessage> chatMessageList, boolean includeToolInfo) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessageList) {
            Short type = chatMessage.getType();
            if (Const.ChatMessageType.USER.equals(type)) {
                messages.add(new UserMessage(chatMessage.getContent()));
            } else if (Const.ChatMessageType.ASSISTANT.equals(type)) {
                if (includeToolInfo) {
                    messages.add(buildAssistantMessage(chatMessage));
                } else {
                    messages.add(new AssistantMessage(chatMessage.getContent()));
                }
            } else if (Const.ChatMessageType.SYSTEM.equals(type)) {
                messages.add(new SystemMessage(chatMessage.getContent()));
            } else if (Const.ChatMessageType.TOOL.equals(type)) {
                if (includeToolInfo) {
                    messages.add(buildToolResponseMessage(chatMessage));
                }
            }
        }
        return messages;
    }

    /**
     * 创建或校验会话
     */
    private String prepareSession(String sessionId, String userMessage) {
        if (StrUtil.isBlank(sessionId)) {
            String newSessionId = IdUtil.fastSimpleUUID();
            String title = userMessage.length() > 20 ? userMessage.substring(0, 20) : userMessage;
            chatSessionService.save(new ChatSession(newSessionId, title));
            return newSessionId;
        }

        ChatSession chatSession = chatSessionService.getBySessionId(sessionId);
        if (chatSession == null) {
            Assert.isTrue(false,"会话不存在");
        }
        return sessionId;
    }

    /**
     * 用户消息先落库，再把整段历史组装给模型。
     */
    private void saveUserMessage(String sessionId, String recordId, String userMessage) {
        save(new ChatMessage(Const.ChatMessageType.USER, sessionId, recordId, userMessage));
    }

    private ChatOptions buildChatOptions() {
        return OllamaChatOptions.builder()
                .disableThinking()//关闭思考
                .build();
    }


    /**
     * 处理流式返回：累计文本、记录工具调用、抽取 token。
     */
    private CustomChatResponse buildStreamResponse(ChatResponse chatResponse,
                                                   StringBuffer fullContent,
                                                   String sessionId,
                                                   String recordId) {
        AssistantMessage output = chatResponse.getResult().getOutput();;
        //每个轮的 token
        String delta = StrUtil.nullToDefault(output.getText(), "");
        fullContent.append(delta);

        String thinkingPart = chatResponse.getResult().getMetadata().get("thinking");
        boolean isThinking = StrUtil.isNotBlank(thinkingPart);
        //累计token
        Integer tokens = extractTotalTokens(chatResponse);
        return new CustomChatResponse(delta, isThinking, sessionId, recordId, tokens);
    }


    /**
     * 抽取模型返回的 token 数量。
     * @param chatResponse
     * @return
     */
    private Integer extractTotalTokens(ChatResponse chatResponse) {
        if (chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return null;
        }
        return chatResponse.getMetadata().getUsage().getTotalTokens();
    }

    /**
     * 流式响应结束后，一次性保存助手最终回答
     */
    private void finishAssistantMessage(String sessionId, String recordId, StringBuffer fullContent) {
        //保存助手消息
        ChatMessage assistantChat = new ChatMessage(
                Const.ChatMessageType.ASSISTANT,
                sessionId,
                recordId,
                fullContent.toString()
        );
        save(assistantChat);
        //刷新会话时间
        chatSessionService.touchSession(sessionId);
    }
    


    private AssistantMessage buildAssistantMessage(ChatMessage chatMessage) {
        MetaData metaData = chatMessage.getMetaJson();
        if (metaData == null || CollUtil.isEmpty(metaData.getToolCalls())) {
            return new AssistantMessage(chatMessage.getContent());
        }

        List<AssistantMessage.ToolCall> toolCalls = metaData.getToolCalls().stream()
                .map(toolCallMeta -> new AssistantMessage.ToolCall(
                        toolCallMeta.getId(),
                        toolCallMeta.getType(),
                        toolCallMeta.getName(),
                        toolCallMeta.getArguments()
                ))
                .toList();
        return AssistantMessage.builder()
                .content(StrUtil.nullToDefault(chatMessage.getContent(), ""))
                .toolCalls(toolCalls)
                .build();
    }

    private ToolResponseMessage buildToolResponseMessage(ChatMessage chatMessage) {
        MetaData metaData = chatMessage.getMetaJson();
        if (metaData == null || CollUtil.isEmpty(metaData.getToolCalls())) {
            String toolCallId = chatMessage.getId() == null ? IdUtil.fastSimpleUUID() : chatMessage.getId().toString();
            ToolResponseMessage.ToolResponse toolResp = new ToolResponseMessage.ToolResponse(
                    toolCallId,
                    "unknown_tool",
                    StrUtil.nullToDefault(chatMessage.getContent(), "")
            );
            return ToolResponseMessage.builder()
                    .responses(List.of(toolResp))
                    .build();
        }

        // TOOL 消息只回放工具执行结果，结果优先取 metaJson 中记录的 result。
        List<ToolResponseMessage.ToolResponse> responses = metaData.getToolCalls().stream()
                .map(toolCallMeta -> new ToolResponseMessage.ToolResponse(
                        toolCallMeta.getId(),
                        toolCallMeta.getName(),
                        toolCallMeta.getResult()
                )).toList();
        return ToolResponseMessage.builder().responses(responses).build();
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
