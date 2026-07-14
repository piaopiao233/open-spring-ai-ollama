package org.forest.chatollama.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.dto.CustomChatResponse;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.MetaData;
import org.forest.chatollama.service.IChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 装饰 {@link ToolCallingManager}，在工具执行前后落库：
 * <ul>
 *     <li>执行前：保存一条 ASSISTANT 消息，记录模型产出的工具调用请求。</li>
 *     <li>执行后：保存一条 TOOL 消息，记录工具执行结果。</li>
 * </ul>
 * 替代 1.x 中重写 {@code ToolCallAdvisor} 的方案，不依赖 protected 钩子，
 * 是 2.0 推荐的切面式扩展点。
 */
@Slf4j
public class LoggingToolCallingManager implements ToolCallingManager {

    private static final String TOOL_CONTEXT_SESSION_ID = "sessionId";
    private static final String TOOL_CONTEXT_RECORD_ID = "recordId";

    private final ToolCallingManager delegate;
    private final ObjectProvider<IChatMessageService> chatMessageServiceProvider;
    private final ObjectProvider<ChatStreamTaskManager> chatStreamTaskManagerProvider;

    /**
     * 构建无请求状态的工具调用落库管理器。
     *
     * @param delegate 被装饰的默认实现（通常是框架自动装配的 DefaultToolCallingManager）
     * @param chatMessageServiceProvider 聊天消息服务延迟提供器
     * @param chatStreamTaskManagerProvider 流式任务管理器延迟提供器
     */
    public LoggingToolCallingManager(ToolCallingManager delegate,
                                     ObjectProvider<IChatMessageService> chatMessageServiceProvider,
                                     ObjectProvider<ChatStreamTaskManager> chatStreamTaskManagerProvider) {
        this.delegate = delegate;
        this.chatMessageServiceProvider = chatMessageServiceProvider;
        this.chatStreamTaskManagerProvider = chatStreamTaskManagerProvider;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        ToolLogContext toolLogContext = getToolLogContext(prompt);
        //推送工具开始执行的流式事件
        emitToolCallBefore(chatResponse, toolLogContext);
        // 执行前：记录模型产出的工具调用请求。
        logToolCallBefore(chatResponse, toolLogContext);

        ToolExecutionResult result;
        try {
            result = delegate.executeToolCalls(prompt, chatResponse);
        } catch (RuntimeException e) {
            // 工具执行链路抛异常时构造失败工具响应，交给模型生成面向用户的最终回复。
            log.warn("工具调用执行失败。", e);
            result = buildFailedToolExecutionResult(prompt, chatResponse, e);
        }

        // 执行后：记录工具执行结果。
        logToolCallAfter(result, toolLogContext);
        return result;
    }

    /**
     * 从 Prompt 的工具上下文中提取本轮业务标识。
     *
     * @param prompt 模型请求
     * @return 工具调用落库上下文
     */
    private ToolLogContext getToolLogContext(Prompt prompt) {
        Map<String, Object> toolContext = Map.of();
        if (prompt != null
                && prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions
                && CollUtil.isNotEmpty(toolCallingChatOptions.getToolContext())) {
            toolContext = toolCallingChatOptions.getToolContext();
        }

        return new ToolLogContext(
                StrUtil.toStringOrNull(toolContext.get(TOOL_CONTEXT_SESSION_ID)),
                StrUtil.toStringOrNull(toolContext.get(TOOL_CONTEXT_RECORD_ID))
        );
    }


    /**
     * 记录模型刚产出的工具调用请求，保存一条 ASSISTANT 消息。
     *
     * @param chatResponse 模型响应
     * @param toolLogContext 工具调用落库上下文
     */
    private void logToolCallBefore(ChatResponse chatResponse, ToolLogContext toolLogContext) {
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return;
        }
        Generation result = chatResponse.getResult();
        if (result == null){
            return;
        }
        AssistantMessage assistantMessage = result.getOutput();
        if (CollUtil.isEmpty(assistantMessage.getToolCalls())) {
            return;
        }
        if (toolLogContext.sessionId() ==  null || toolLogContext.recordId() == null) {
            return;
        }
        List<MetaData.ToolCallMeta> toolCalls = assistantMessage.getToolCalls().stream()
                .map(toolCall -> new MetaData.ToolCallMeta(
                        toolCall.id(),
                        toolCall.type(),
                        toolCall.name(),
                        toolCall.arguments(),
                        null
                )).toList();
        ChatStreamTask streamTask = chatStreamTaskManagerProvider.getObject()
                .getByRecordId(toolLogContext.recordId());
        ChatStreamTask.ResponseSnapshot responseSnapshot = ObjectUtil.isNull(streamTask)
                ? null
                : streamTask.drainCurrentResponse();
        MetaData metaData = new MetaData(toolCalls);
        if (ObjectUtil.isNotNull(responseSnapshot) && StrUtil.isNotEmpty(responseSnapshot.thinkingContent())) {
            // 工具阶段的思考内容随对应assistant保存，避免清空当前轮次时丢失。
            metaData.setThinking(responseSnapshot.thinkingContent());
        }
        ChatMessage assistantToolCallMessage = new ChatMessage(
                Const.ChatMessageType.ASSISTANT,
                toolLogContext.sessionId(),
                toolLogContext.recordId(),
                assistantMessage.getText(),
                metaData
        );
        TokenUsage tokenUsage = extractTokenUsage(chatResponse);
        assistantToolCallMessage.setTokenCount(tokenUsage.totalTokens());
        assistantToolCallMessage.setPromptTokenCount(tokenUsage.promptTokens());
        assistantToolCallMessage.setCompletionTokenCount(tokenUsage.completionTokens());
        getChatMessageService().save(assistantToolCallMessage);
    }

    /**
     * 提取工具调用对应模型响应的token用量。
     *
     * @param chatResponse 模型响应
     * @return token用量
     */
    private TokenUsage extractTokenUsage(ChatResponse chatResponse) {
        if (ObjectUtil.isNull(chatResponse) || ObjectUtil.isNull(chatResponse.getMetadata())
                || ObjectUtil.isNull(chatResponse.getMetadata().getUsage())) {
            return new TokenUsage(null, null, null);
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        Integer totalTokens = usage.getTotalTokens();
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        return new TokenUsage(
                normalizeTokenCount(totalTokens),
                normalizeTokenCount(promptTokens),
                normalizeTokenCount(completionTokens)
        );
    }

    /**
     * 将无效的零token值转换为空，避免把没有统计数据误存为有效用量。
     *
     * @param tokenCount token数量
     * @return 有效token数量
     */
    private Integer normalizeTokenCount(Integer tokenCount) {
        return ObjectUtil.isNull(tokenCount) || tokenCount == 0 ? null : tokenCount;
    }

    /**
     * 记录工具执行结果，保存一条 TOOL 消息。
     *
     * @param toolExecutionResult 工具执行结果
     * @param toolLogContext 工具调用落库上下文
     */
    private void logToolCallAfter(ToolExecutionResult toolExecutionResult, ToolLogContext toolLogContext) {
        if (toolExecutionResult == null || CollUtil.isEmpty(toolExecutionResult.conversationHistory())) {
            return;
        }
        List<MetaData.ToolCallMeta> toolCalls = extractToolCallResults(toolExecutionResult);
        if (CollUtil.isEmpty(toolCalls)) {
            return;
        }
        if (toolLogContext.sessionId() ==  null || toolLogContext.recordId() == null) {
            return;
        }
        ChatMessage toolResultMessage = new ChatMessage(
                Const.ChatMessageType.TOOL,
                toolLogContext.sessionId(),
                toolLogContext.recordId(),
                null,
                new MetaData(toolCalls)
        );
        getChatMessageService().save(toolResultMessage);
    }

    /**
     * 推送工具开始执行的流式事件。
     *
     * @param chatResponse 模型响应
     * @param toolLogContext 工具调用上下文
     */
    private void emitToolCallBefore(ChatResponse chatResponse, ToolLogContext toolLogContext) {
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return;
        }
        Generation result = chatResponse.getResult();
        if (result == null) {
            return;
        }
        AssistantMessage assistantMessage = result.getOutput();
        if (CollUtil.isEmpty(assistantMessage.getToolCalls())) {
            return;
        }
        List<CustomChatResponse.ToolCallInfo> toolCallInfos = assistantMessage.getToolCalls().stream()
                .map(toolCall -> CustomChatResponse.ToolCallInfo.of(
                        toolCall.name(),
                        toolCall.arguments()
                ))
                .toList();
        emitToolCallEvent(toolLogContext, toolCallInfos);
    }

    /**
     * 向当前会话推送工具调用事件。
     *
     * @param toolLogContext 工具调用上下文
     * @param toolCallInfos 工具调用信息
     */
    private void emitToolCallEvent(ToolLogContext toolLogContext, List<CustomChatResponse.ToolCallInfo> toolCallInfos) {
        if (toolLogContext.sessionId() == null || toolLogContext.recordId() == null || CollUtil.isEmpty(toolCallInfos)) {
            return;
        }
        ChatStreamTask streamTask = chatStreamTaskManagerProvider.getObject().getByRecordId(toolLogContext.recordId());
        if (ObjectUtil.isNull(streamTask)) {
            return;
        }
        // 工具事件只用于前端展示中间状态，不参与最终 assistant 文本累计。
        streamTask.emit(new CustomChatResponse(
                "",
                false,
                toolLogContext.sessionId(),
                toolLogContext.recordId(),
                null,
                toolCallInfos
        ));
    }

    /**
     * 构建工具执行失败结果，让模型继续基于失败信息生成最终回复。
     *
     * @param prompt 模型请求
     * @param chatResponse 模型响应
     * @param exception 工具执行异常
     * @return 工具执行失败结果
     */
    private ToolExecutionResult buildFailedToolExecutionResult(Prompt prompt,
                                                               ChatResponse chatResponse,
                                                               RuntimeException exception) {
        if (prompt == null || chatResponse == null || !chatResponse.hasToolCalls()) {
            throw exception;
        }
        Generation result = chatResponse.getResult();
        if (result == null){
            throw exception;
        }
        AssistantMessage assistantMessage = result.getOutput();
        if (CollUtil.isEmpty(assistantMessage.getToolCalls())) {
            throw exception;
        }
        String errorMessage = StrUtil.blankToDefault(exception.getMessage(), exception.getClass().getSimpleName());
        List<ToolResponseMessage.ToolResponse> toolResponses = assistantMessage.getToolCalls().stream()
                .map(toolCall -> new ToolResponseMessage.ToolResponse(
                        toolCall.id(),
                        toolCall.name(),
                        "工具执行失败：" + errorMessage
                ))
                .toList();
        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        conversationHistory.add(assistantMessage);
        conversationHistory.add(ToolResponseMessage.builder()
                .responses(toolResponses)
                .build());
        return ToolExecutionResult.builder()
                .conversationHistory(conversationHistory)
                .returnDirect(false)
                .build();
    }

    /**
     * 提取工具执行结果中的工具响应元数据。
     *
     * @param toolExecutionResult 工具执行结果
     * @return 工具响应元数据列表
     */
    private List<MetaData.ToolCallMeta> extractToolCallResults(ToolExecutionResult toolExecutionResult) {
        // 最新消息就是当前这轮工具执行后的 ToolResponseMessage。
        Message message = toolExecutionResult.conversationHistory().getLast();
        if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
            return List.of();
        }
        List<ToolResponseMessage.ToolResponse> currentRoundResponses = toolResponseMessage.getResponses();
        if (CollUtil.isEmpty(currentRoundResponses)) {
            return List.of();
        }
        List<MetaData.ToolCallMeta> toolCalls = new ArrayList<>(currentRoundResponses.size());
        for (ToolResponseMessage.ToolResponse toolResponse : currentRoundResponses) {
            toolCalls.add(new MetaData.ToolCallMeta(
                    toolResponse.id(),
                    null,
                    toolResponse.name(),
                    null,
                    toolResponse.responseData()
            ));
        }
        return toolCalls;
    }

    /**
     * 获取聊天消息服务。
     *
     * @return 聊天消息服务
     */
    private IChatMessageService getChatMessageService() {
        return chatMessageServiceProvider.getObject();
    }

    private record ToolLogContext(String sessionId, String recordId) {
    }

    /**
     * 工具调用对应的模型token用量。
     *
     * @param totalTokens 总token数
     * @param promptTokens 输入token数
     * @param completionTokens 输出token数
     */
    private record TokenUsage(Integer totalTokens, Integer promptTokens, Integer completionTokens) {
    }
}
