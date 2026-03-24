package org.forest.chatollama.controller;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.dto.CustomChatResponse;
import org.forest.chatollama.dto.Result;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.util.SpringAiRagUtils;
import org.springframework.ai.chat.model.ChatResponse;
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

    /*
     * 生成教学设计
     */
    @PostMapping(value = "/generateTeachingDesign", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateTeachingDesign(@NotBlank String message, String designTemplate) {
        StringBuilder prompt = new StringBuilder();
        if (StrUtil.isNotBlank(designTemplate)) {
            prompt.append("我选择了一篇文档如下，请参考该文档。").append("\r\n\r\n").append(designTemplate).append("\r\n\r\n");
        }
        prompt.append("我现在需要你生成html标签格式的文档，仅输出<body>标签内的部分，不要<!DOCTYPE html>、<html>、<head>等标签。");
        prompt.append("\r\n\r\n");
        prompt.append("文档内容要求如下：").append(message);
        return chatMessageService.simpleGenerateStream(prompt.toString());
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
