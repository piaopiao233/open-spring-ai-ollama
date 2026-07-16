package org.forest.chatollama.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import org.forest.chatollama.service.ai.ChatStreamTask;
import org.forest.chatollama.service.ai.ChatStreamTaskManager;
import org.forest.chatollama.service.ai.WebSearchToolCalling;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 网络搜索工具名称常量
     */
    private static final String WEB_SEARCH_TOOL_NAME = "web_search";

    private final OllamaChatModel chatModel;
    private final SpringAiRagUtils springAiRagUtils;
    private final ToolCalling toolCalling;
    private final WebSearchToolCalling webSearchToolCalling;
    private final ToolCallingAdvisor toolCallingAdvisor;
    private final ChatStreamTaskManager chatStreamTaskManager;
    private IChatSessionService chatSessionService;

    @Autowired
    @Lazy
    public void setChatSessionService(IChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

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
        Assert.isTrue(ObjectUtil.isNull(chatStreamTaskManager.getBySessionId(sessionId)), "当前会话正在生成中，请稍后再试");
        // 先把当前轮用户消息落库，图片地址会一并写入 meta_json。
        saveUserMessage(sessionId, recordId, request);
        // 再把完整上下文回放给模型，确保多轮场景下历史图片也能被带上。
        List<Message> messages = buildMessageList(selectBySessionId(sessionId, true));
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ChatOptions.Builder<?> chatOptionsBuilder = buildChatOptions(sessionId, recordId, request.getEnableThinking());
        ChatStreamTask streamTask = chatStreamTaskManager.createTask(sessionId, recordId);
        // 根据是否启用网络搜索决定使用的工具列表
        Object[] tools = Boolean.TRUE.equals(request.getEnableWebSearch()) 
                ? new Object[]{toolCalling, webSearchToolCalling}
                : new Object[]{toolCalling};
        // 先推一个空 chunk，保证新会话前端能马上拿到 sessionId 和 recordId。
        streamTask.emit(new CustomChatResponse("", false, sessionId, recordId, null));
        Flux<CustomChatResponse> modelStream = chatClient.prompt()
                .messages(messages)
                .advisors(toolCallingAdvisor)
                .tools(tools)
                .options(chatOptionsBuilder)
                .stream()
                .chatResponse()
                .map(this::buildStreamResponse);
        Disposable disposable = modelStream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        streamTask::emitModelResponse,
                        err -> finishStreamTaskWithError(streamTask, err),
                        () -> finishStreamTask(streamTask)
                );
        streamTask.setDisposable(disposable);
        return streamTask.asFlux();
    }

    /**
     * 重连会话中正在生成的流。
     *
     * @param sessionId 会话ID
     * @return 流式响应
     */
    @Override
    public Flux<CustomChatResponse> reconnectStream(String sessionId) {
        ChatStreamTask streamTask = chatStreamTaskManager.getBySessionId(sessionId);
        if (ObjectUtil.isNull(streamTask)) {
            return Flux.empty();
        }
        log.info("重新连接会话{}，对话{}的流式生成", sessionId, streamTask.getRecordId());
        return streamTask.asFlux();
    }

    /**
     * 停止指定对话的流式生成。
     *
     * @param recordId 对话ID
     */
    @Override
    public void stopStream(String recordId) {
        ChatStreamTask streamTask = chatStreamTaskManager.getByRecordId(recordId);
        if (ObjectUtil.isNull(streamTask)) {
            return;
        }
        log.info("停止会话{}，对话{}的流式生成", streamTask.getSessionId(), recordId);
        streamTask.cancelModelStream();
        finishStreamTask(streamTask);
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
                    TokenUsage tokenUsage = extractTokenUsage(chatResponse);
                    fullContent.append(delta);
                    return new CustomChatResponse(
                            delta,
                            isThinking,
                            null,
                            null,
                            tokenUsage.totalTokens(),
                            tokenUsage.promptTokens(),
                            tokenUsage.completionTokens(),
                            null
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
        List<ChatMessage> chatMessages = list(queryWrapper);
        // 格式化工具信息，清除工具消息的content
        for (ChatMessage chatMessage : chatMessages) {
            MetaData metaJson = chatMessage.getMetaJson();
            if (metaJson != null && metaJson.getToolCalls() != null) {
                for (MetaData.ToolCallMeta toolCall : metaJson.getToolCalls()) {
                    toolCall.setResult(null);
                }
            }
        }
        return chatMessages;
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
     * @param sessionId 会话id
     * @param recordId 记录id
     * @param enableThinking 是否启用深度思考
     * @return 对话参数
     */
    private ChatOptions.Builder<?> buildChatOptions(String sessionId, String recordId, Boolean enableThinking) {
        var builder = OllamaChatOptions.builder()
                .toolContext(Map.of("sessionId", sessionId, "recordId", recordId));
        if (Boolean.TRUE.equals(enableThinking)) {
            return builder.enableThinking();
        }
        return builder.disableThinking();
    }

    /**
     * 构建流式响应片段。
     *
     * @param chatResponse 模型响应
     * @return 自定义流式响应
     */
    private CustomChatResponse buildStreamResponse(ChatResponse chatResponse) {
        Generation result = chatResponse.getResult();
        if (ObjectUtil.isNull(result)) {
            return new CustomChatResponse("", false, null, null, null);
        }
        AssistantMessage output = result.getOutput();
        String delta = ObjectUtil.isNull(output) ? "" : StrUtil.nullToDefault(output.getText(), "");
        String thinkingPart = ObjectUtil.isNull(result.getMetadata())
                ? null
                : StrUtil.toStringOrNull(result.getMetadata().get("thinking"));
        boolean isThinking = StrUtil.isNotEmpty(thinkingPart);
        TokenUsage tokenUsage = extractTokenUsage(chatResponse);
        return new CustomChatResponse(
                isThinking ? thinkingPart : delta,
                isThinking,
                null,
                null,
                tokenUsage.totalTokens(),
                tokenUsage.promptTokens(),
                tokenUsage.completionTokens(),
                null
        );
    }

    /**
     * 提取模型响应的token用量。
     *
     * @param chatResponse 模型响应
     * @return token用量
     */
    private TokenUsage extractTokenUsage(ChatResponse chatResponse) {
        if (ObjectUtil.isNull(chatResponse.getMetadata())
                || ObjectUtil.isNull(chatResponse.getMetadata().getUsage())) {
            return new TokenUsage(null, null, null);
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        Integer totalTokens = usage.getTotalTokens();
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        return new TokenUsage(
                ObjectUtil.isNull(totalTokens) || totalTokens == 0 ? null : totalTokens,
                ObjectUtil.isNull(promptTokens) || promptTokens == 0 ? null : promptTokens,
                ObjectUtil.isNull(completionTokens) || completionTokens == 0 ? null : completionTokens
        );
    }

    /**
     * 模型token用量。
     *
     * @param totalTokens 总token数
     * @param promptTokens 提示词token数
     * @param completionTokens 生成内容token数
     */
    private record TokenUsage(Integer totalTokens, Integer promptTokens, Integer completionTokens) {
    }

    /**
     * 流式响应结束后保存助手消息。
     *
     * @param sessionId 会话id
     * @param recordId 记录id
     * @param fullContent 完整响应
     * @param thinkingContent 思考内容
     * @param tokenCount token 使用数
     * @param promptTokenCount 输入token使用数
     * @param completionTokenCount 输出token使用数
     */
    private void finishAssistantMessage(String sessionId,
                                        String recordId,
                                        String fullContent,
                                        String thinkingContent,
                                        Integer tokenCount,
                                        Integer promptTokenCount,
                                        Integer completionTokenCount) {
        if (fullContent.isEmpty() && thinkingContent.isEmpty()){
            fullContent = "异常终止";
        }
        ChatMessage assistantChat = new ChatMessage(
                Const.ChatMessageType.ASSISTANT,
                sessionId,
                recordId,
                fullContent,
                buildAssistantMessageMetaData(thinkingContent)
        );
        assistantChat.setTokenCount(tokenCount);
        assistantChat.setPromptTokenCount(promptTokenCount);
        assistantChat.setCompletionTokenCount(completionTokenCount);
        save(assistantChat);
        chatSessionService.touchSession(sessionId);
    }

    /**
     * 构建助手消息元数据。
     *
     * @param thinkingContent 思考内容
     * @return 助手消息元数据
     */
    private MetaData buildAssistantMessageMetaData(String thinkingContent) {
        if (thinkingContent.isEmpty()) {
            return null;
        }
        MetaData metaData = new MetaData();
        metaData.setThinking(thinkingContent);
        return metaData;
    }

    /**
     * 完成流式任务并保存最终助手消息。
     *
     * @param streamTask 流式任务
     */
    private void finishStreamTask(ChatStreamTask streamTask) {
        log.info("流式任务完成: 会话id：{} 对话id：{}" , streamTask.getSessionId(), streamTask.getRecordId());
        if (streamTask.markSaved()) {
            ChatStreamTask.ResponseSnapshot snapshot = streamTask.snapshot();
            finishAssistantMessage(
                    streamTask.getSessionId(),
                    streamTask.getRecordId(),
                    snapshot.fullContent(),
                    snapshot.thinkingContent(),
                    snapshot.tokenCount(),
                    snapshot.promptTokenCount(),
                    snapshot.completionTokenCount()
            );
        }
        streamTask.complete();
        chatStreamTaskManager.removeTask(streamTask);
    }

    /**
     * 异常结束流式任务。
     *
     * @param streamTask 流式任务
     * @param err 异常
     */
    private void finishStreamTaskWithError(ChatStreamTask streamTask, Throwable err) {
        log.error("流异常: ", err);
        if (streamTask.markSaved()) {
            ChatStreamTask.ResponseSnapshot snapshot = streamTask.snapshot();
            finishAssistantMessage(
                    streamTask.getSessionId(),
                    streamTask.getRecordId(),
                    snapshot.fullContent(),
                    snapshot.thinkingContent(),
                    snapshot.tokenCount(),
                    snapshot.promptTokenCount(),
                    snapshot.completionTokenCount()
            );
        }
        streamTask.error(err);
        chatStreamTaskManager.removeTask(streamTask);
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
     * 根据会话ID删除消息。
     *
     * @param sessionId 会话ID
     */
    @Override
    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        remove(wrapper);
    }

    /**
     * 根据ID查询消息。
     *
     * @param id 消息ID
     * @return 消息
     */
    @Override
    public ChatMessage selectById(Long id) {
        return getById(id);
    }

    /**
     * 根据对话ID查询并合并本轮工具调用信息。
     *
     * @param recordId 对话ID
     * @return 工具调用信息列表
     */
    @Override
    public List<MetaData.ToolCallMeta> selectToolCallsByRecordId(String recordId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getRecordId, recordId)
                .orderByAsc(ChatMessage::getId);
        List<ChatMessage> chatMessages = list(wrapper);
        Map<String, MetaData.ToolCallMeta> toolCallMap = new LinkedHashMap<>();

        for (ChatMessage chatMessage : chatMessages) {
            MetaData metaJson = chatMessage.getMetaJson();
            if (metaJson == null || CollUtil.isEmpty(metaJson.getToolCalls())) {
                continue;
            }
            for (int i = 0; i < metaJson.getToolCalls().size(); i++) {
                MetaData.ToolCallMeta toolCall = metaJson.getToolCalls().get(i);
                if (ObjectUtil.isNull(toolCall)) {
                    continue;
                }
                // 工具调用和工具结果分别是两条消息，这里按工具调用ID合并成一条给前端展示。
                String toolKey = StrUtil.blankToDefault(toolCall.getId(), StrUtil.format("{}-{}", toolCall.getName(), i));
                MetaData.ToolCallMeta mergedToolCall = toolCallMap.computeIfAbsent(toolKey, key -> new MetaData.ToolCallMeta());
                mergeToolCallMeta(mergedToolCall, toolCall);
            }
        }
        return new ArrayList<>(toolCallMap.values());
    }

    @Override
    public boolean isSessionIdHasRunningChat(String sessionId) {
       return chatStreamTaskManager.getBySessionId(sessionId) != null;
    }

    /**
     * 合并工具调用元数据。
     *
     * @param target 合并后的工具调用
     * @param source 当前消息里的工具调用
     */
    private void mergeToolCallMeta(MetaData.ToolCallMeta target, MetaData.ToolCallMeta source) {
        if (StrUtil.isNotBlank(source.getId())) {
            target.setId(source.getId());
        }
        if (StrUtil.isNotBlank(source.getType())) {
            target.setType(source.getType());
        }
        if (StrUtil.isNotBlank(source.getName())) {
            target.setName(source.getName());
        }
        if (StrUtil.isNotBlank(source.getArguments())) {
            target.setArguments(source.getArguments());
        }
        if (StrUtil.isNotBlank(source.getResult())) {
            target.setResult(source.getResult());
        }
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
