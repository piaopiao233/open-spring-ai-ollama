package org.forest.chatollama.service.ai;

import cn.hutool.core.collection.CollUtil;
import org.forest.chatollama.common.exception.Const;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.MetaData;
import org.forest.chatollama.service.IChatMessageService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录工具调用
 */
@SuppressWarnings("NullableProblems")
public class LoggingToolCallAdvisor extends ToolCallAdvisor {

    private final String sessionId;
    private final String recordId;

    private final IChatMessageService chatMessageService;

    /**
     * 每次请求 new 一个，传入业务参数
     */
    public LoggingToolCallAdvisor(ToolCallingManager toolCallingManager,
                                  String sessionId,
                                  String recordId,
                                  IChatMessageService chatMessageService) {
        super(toolCallingManager, BaseAdvisor.HIGHEST_PRECEDENCE + 300);
        this.sessionId = sessionId;
        this.recordId = recordId;
        this.chatMessageService = chatMessageService;
    }


    /**
     * 记录模型刚产出的工具调用请求，保存一条 ASSISTANT 消息。
     * @param chatClientResponse
     */
    private void logToolCallBefore(ChatClientResponse chatClientResponse) {
        if (!chatClientResponse.chatResponse().hasToolCalls()) {
            return;
        }

        AssistantMessage assistantMessage = chatClientResponse.chatResponse().getResult().getOutput();
        if (assistantMessage == null || CollUtil.isEmpty(assistantMessage.getToolCalls())) {
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
        ChatMessage assistantToolCallMessage = new ChatMessage(
                Const.ChatMessageType.ASSISTANT,
                sessionId,
                recordId,
                assistantMessage.getText(),
                new MetaData(toolCalls)
        );
        chatMessageService.save(assistantToolCallMessage);

    }

    @Override
    protected List<Message> doGetNextInstructionsForToolCallStream(ChatClientRequest chatClientRequest,
                                                                   ChatClientResponse chatClientResponse,
                                                                   ToolExecutionResult toolExecutionResult) {
        // 工具调用后时会走到这里
        logToolCallBefore(chatClientResponse);
        logToolCallAfter(chatClientResponse, toolExecutionResult);
        return super.doGetNextInstructionsForToolCallStream(chatClientRequest, chatClientResponse, toolExecutionResult);
    }


    /**
     * 记录工具执行结果，保存一条 TOOL 消息。
     * @param chatClientResponse
     */
    private void logToolCallAfter(ChatClientResponse chatClientResponse, ToolExecutionResult toolExecutionResult) {
        if (!chatClientResponse.chatResponse().hasToolCalls()) {
            return;
        }

        List<MetaData.ToolCallMeta> toolCalls = extractToolCallResults(toolExecutionResult);
        if (CollUtil.isEmpty(toolCalls)) {
            return;
        }
        ChatMessage toolResultMessage = new ChatMessage(
                Const.ChatMessageType.TOOL,
                sessionId,
                recordId,
                null,
                new MetaData(toolCalls)
        );
        chatMessageService.save(toolResultMessage);

    }

    private List<MetaData.ToolCallMeta> extractToolCallResults(ToolExecutionResult toolExecutionResult) {
        if (toolExecutionResult == null || CollUtil.isEmpty(toolExecutionResult.conversationHistory())) {
            return List.of();
        }
        // 最新消息就是当前这轮工具执行后的 ToolResponseMessage。
        Message message = toolExecutionResult.conversationHistory().get(toolExecutionResult.conversationHistory().size() - 1);
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

}
