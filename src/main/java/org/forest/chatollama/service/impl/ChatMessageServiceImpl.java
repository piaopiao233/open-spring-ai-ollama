package org.forest.chatollama.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.dto.ChatImageItem;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.dto.CustomChatResponse;
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
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * 发送流式会话消息。
     *
     * @param request 会话请求
     * @return 流式响应
     */
    @Override
    public Flux<CustomChatResponse> generateStream(ChatMessageRequest request) {
        String recordId = IdUtil.fastSimpleUUID();
        String userMessage = request.getMessage();
        String sessionId = prepareSession(request.getSessionId(), userMessage);
        // 先把当前轮用户消息落库，图片地址会一并写入 meta_json。
        saveUserMessage(sessionId, recordId, request);
        // 再把完整上下文回放给模型，确保多轮场景下历史图片也能被带上。
        List<Message> messages = buildMessageList(selectBySessionId(sessionId, true));
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ChatOptions chatOptions = buildChatOptions();
        StringBuffer fullContent = new StringBuffer();
        AtomicReference<Integer> tokenCount = new AtomicReference<>();
        LoggingToolCallAdvisor toolCallAdvisor = new LoggingToolCallAdvisor(toolCallingManager, sessionId, recordId, this);
        return chatClient.prompt()
                .messages(messages)
                .advisors(toolCallAdvisor)
                .tools(toolCalling)
                .options(chatOptions)
                .stream()
                .chatResponse()
                .map(chatResponse -> buildStreamResponse(chatResponse, fullContent, tokenCount, sessionId, recordId))
                .doOnComplete(() -> finishAssistantMessage(sessionId, recordId, fullContent, tokenCount.get()))
                .doOnCancel(() -> finishAssistantMessage(sessionId, recordId, fullContent, tokenCount.get()))
                .doOnError(err -> log.error("流异常: ", err));
    }

    /**
     * 发送简单流式消息并转换为自定义响应。
     *
     * @param message 用户消息
     * @return 自定义流式响应
     */
    @Override
    public Flux<CustomChatResponse> simpleGenerateStreamCustom(String message) {
        ChatOptions chatOptions = OllamaChatOptions.builder()
                .disableThinking()
                .build();
        Prompt prompt = new Prompt(message, chatOptions);
        StringBuffer fullContent = new StringBuffer();
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
                    return new CustomChatResponse(
                            delta,
                            isThinking,
                            null,
                            null,
                            tokens
                    );
                })
                .doOnComplete(() -> System.out.println("完整响应: " + fullContent))
                .doOnCancel(() -> System.out.println("用户取消，部分内容: " + fullContent))
                .doOnError(err -> System.err.println("流异常: " + err));
    }


    /**
     * 按会话查询消息列表。
     *
     * @param sessionId 会话id
     * @param isAsc 是否升序
     * @return 消息列表
     */
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

    /**
     * 构建发送给模型的消息列表。
     *
     * @param chatMessageList 历史消息列表
     * @param includeToolInfo 是否包含工具消息
     * @return 消息列表
     */
    @Override
    public List<Message> buildMessageList(List<ChatMessage> chatMessageList, boolean includeToolInfo) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessageList) {
            Short type = chatMessage.getType();
            if (Const.ChatMessageType.USER.equals(type)) {
                messages.add(buildUserMessage(chatMessage));
            } else if (Const.ChatMessageType.ASSISTANT.equals(type)) {
                if (includeToolInfo) {
                    messages.add(buildAssistantMessage(chatMessage));
                } else {
                    messages.add(new AssistantMessage(chatMessage.getContent()));
                }
            } else if (Const.ChatMessageType.SYSTEM.equals(type)) {
                messages.add(new SystemMessage(chatMessage.getContent()));
            } else if (Const.ChatMessageType.TOOL.equals(type) && includeToolInfo) {
                messages.add(buildToolResponseMessage(chatMessage));
            }
        }
        return messages;
    }

    /**
     * 创建或校验会话。
     *
     * @param sessionId 会话id
     * @param userMessage 用户消息
     * @return 可用会话id
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
            Assert.isTrue(false, "会话不存在");
        }
        return sessionId;
    }

    /**
     * 保存用户消息。
     *
     * @param sessionId 会话id
     * @param recordId 记录id
     * @param request 用户请求
     */
    private void saveUserMessage(String sessionId, String recordId, ChatMessageRequest request) {
        MetaData metaData = buildUserMessageMetaData(request.getImageList());
        save(new ChatMessage(Const.ChatMessageType.USER, sessionId, recordId, request.getMessage(), metaData));
    }

    /**
     * 构建对话参数。
     *
     * @return 对话参数
     */
    private ChatOptions buildChatOptions() {
        return OllamaChatOptions.builder()
                .disableThinking()
                .build();
    }

    /**
     * 构建流式响应片段。
     *
     * @param chatResponse 模型响应
     * @param fullContent 完整响应缓冲区
     * @param tokenCount token 使用数
     * @param sessionId 会话id
     * @param recordId 记录id
     * @return 自定义流式响应
     */
    private CustomChatResponse buildStreamResponse(ChatResponse chatResponse,
                                                   StringBuffer fullContent,
                                                   AtomicReference<Integer> tokenCount,
                                                   String sessionId,
                                                   String recordId) {
        AssistantMessage output = chatResponse.getResult().getOutput();
        String delta = StrUtil.nullToDefault(output.getText(), "");
        fullContent.append(delta);
        String thinkingPart = chatResponse.getResult().getMetadata().get("thinking");
        boolean isThinking = StrUtil.isNotBlank(thinkingPart);
        Integer tokens = extractTotalTokens(chatResponse);
        if (ObjectUtil.isNotNull(tokens)) {
            tokenCount.set(tokens);
        }
        return new CustomChatResponse(delta, isThinking, sessionId, recordId, tokens);
    }

    /**
     * 提取总 token 数。
     *
     * @param chatResponse 模型响应
     * @return token 数
     */
    private Integer extractTotalTokens(ChatResponse chatResponse) {
        if (chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return null;
        }
        return chatResponse.getMetadata().getUsage().getTotalTokens();
    }

    /**
     * 流式响应结束后保存助手消息。
     *
     * @param sessionId 会话id
     * @param recordId 记录id
     * @param fullContent 完整响应
     * @param tokenCount token 使用数
     */
    private void finishAssistantMessage(String sessionId, String recordId, StringBuffer fullContent, Integer tokenCount) {
        if (fullContent.isEmpty()){
            return;
        }
        ChatMessage assistantChat = new ChatMessage(
                Const.ChatMessageType.ASSISTANT,
                sessionId,
                recordId,
                fullContent.toString()
        );
        assistantChat.setTokenCount(tokenCount);
        save(assistantChat);
        chatSessionService.touchSession(sessionId);
    }

    /**
     * 构建用户消息。
     *
     * @param chatMessage 用户消息实体
     * @return Spring AI 用户消息
     */
    private UserMessage buildUserMessage(ChatMessage chatMessage) {
        List<Media> mediaList = buildMediaList(chatMessage.getMetaJson());
        if (CollUtil.isEmpty(mediaList)) {
            return new UserMessage(chatMessage.getContent());
        }
        return UserMessage.builder()
                .text(chatMessage.getContent())
                .media(mediaList)
                .build();
    }

    /**
     * 构建助手消息。
     *
     * @param chatMessage 助手消息实体
     * @return Spring AI 助手消息
     */
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

    /**
     * 构建工具响应消息。
     *
     * @param chatMessage 工具消息实体
     * @return Spring AI 工具消息
     */
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
        List<ToolResponseMessage.ToolResponse> responses = metaData.getToolCalls().stream()
                .map(toolCallMeta -> new ToolResponseMessage.ToolResponse(
                        toolCallMeta.getId(),
                        toolCallMeta.getName(),
                        toolCallMeta.getResult()
                ))
                .toList();
        return ToolResponseMessage.builder()
                .responses(responses)
                .build();
    }

    /**
     * 多轮对话生成多查询检索结果。
     *
     * @param sessionId 会话id
     * @param currentQuestion 当前问题
     * @return 文档列表
     */
    @Override
    public List<Document> multiQuerySimilaritySearch(String sessionId, String currentQuestion) {
        List<Message> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(sessionId)) {
            List<ChatMessage> chatMessages = selectBySessionId(sessionId, true);
            messages = buildMessageList(chatMessages);
        }
        List<String> queries = springAiRagUtils.generateMultiCondensedQueries(messages, currentQuestion);
        System.out.printf("查询变体有：%s%n", queries);
        return springAiRagUtils.multiQuerySimilaritySearch(queries);
    }

    /**
     * 构建用户消息元数据。
     *
     * @param imageList 图片列表
     * @return 元数据
     */
    private MetaData buildUserMessageMetaData(List<ChatImageItem> imageList) {
        if (CollUtil.isEmpty(imageList)) {
            return null;
        }
        List<MetaData.ImageMeta> images = imageList.stream()
                .filter(ObjectUtil::isNotNull)
                .filter(item -> StrUtil.isNotBlank(item.getUrl()))
                .map(item -> new MetaData.ImageMeta(item.getUrl(), item.getMimeType()))
                .toList();
        if (CollUtil.isEmpty(images)) {
            return null;
        }
        MetaData metaData = new MetaData();
        metaData.setImages(images);
        return metaData;
    }

    /**
     * 从消息元数据中构建图片媒体列表。
     *
     * @param metaData 元数据
     * @return 图片媒体列表
     */
    private List<Media> buildMediaList(MetaData metaData) {
        if (metaData == null || CollUtil.isEmpty(metaData.getImages())) {
            return List.of();
        }
        // 历史多模态消息在回放时需要重新挂载图片，并将远程图片下载为字节内容再发给 Ollama。
        return metaData.getImages().stream()
                .filter(ObjectUtil::isNotNull)
                .filter(imageMeta -> StrUtil.isNotBlank(imageMeta.getUrl()))
                .map(this::buildImageMedia)
                .toList();
    }

    /**
     * 构建单张图片媒体对象。
     *
     * @param imageMeta 图片元数据
     * @return Spring AI 图片媒体对象
     */
    private Media buildImageMedia(MetaData.ImageMeta imageMeta) {
        byte[] bytes = HttpUtil.downloadBytes(imageMeta.getUrl());
        Resource resource = new ByteArrayResource(bytes);
        MimeType mimeType =  MimeTypeUtils.parseMimeType(imageMeta.getMimeType());
        return new Media(mimeType, resource);
    }

}
