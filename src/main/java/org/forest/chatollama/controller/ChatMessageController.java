package org.forest.chatollama.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.dto.CustomChatResponse;
import org.forest.chatollama.dto.Result;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.MetaData;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;

@RestController
@RequestMapping(value = "/ChatMessage")
@Validated
@Tag(name = "AiChat消息")
public class ChatMessageController {


    @Autowired
    private IChatMessageService chatMessageService;

    @Autowired
    private SpringAiRagUtils springAiRagUtils;

    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送会话消息")
    public Flux<CustomChatResponse> generateStream(@RequestBody @Validated ChatMessageRequest request) {
        return chatMessageService.generateStream(request);
    }

    @GetMapping(value = "/simpleMessage", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<CustomChatResponse> simpleGenerateStream(@NotBlank String message) {
        return chatMessageService.simpleGenerateStreamCustom(message);
    }

    @GetMapping(value = "/selectBySessionId")
    public Result<List<ChatMessage>> selectBySessionId(@NotBlank String sessionId) {
        var chatMessages = chatMessageService.selectBySessionId(sessionId);
        return Result.succ(chatMessages);
    }

    @GetMapping(value = "/selectById")
    public Result<ChatMessage> selectById(@NotNull Long id) {
        var chatMessage = chatMessageService.selectById(id);
        return Result.succ(chatMessage);
    }

    /**
     * 根据对话ID查询本轮工具调用详情。
     *
     * @param recordId 对话ID
     * @return 工具调用详情
     */
    @GetMapping(value = "/selectToolCallsByRecordId")
    public Result<List<MetaData.ToolCallMeta>> selectToolCallsByRecordId(@NotBlank String recordId) {
        var toolCalls = chatMessageService.selectToolCallsByRecordId(recordId);
        return Result.succ(toolCalls);
    }

    /**
     * 问题查询知识库
     * @param sessionId
     * @param currentQuestion
     * @return
     */
    @PostMapping(value = "/multiQuerySimilaritySearch")
    public Result<List<Document>> multiQuerySimilaritySearch(String sessionId, @NotBlank String currentQuestion) {
        var documents = chatMessageService.multiQuerySimilaritySearch(sessionId, currentQuestion);
        return Result.succ(documents);
    }
}
