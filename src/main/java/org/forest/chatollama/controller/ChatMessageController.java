package org.forest.chatollama.controller;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotBlank;
import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.model.ChatMessage;
import org.forest.chatollama.model.Result;
import org.forest.chatollama.service.IChatMessageService;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;

@RestController
@RequestMapping(value = "/ChatMessage")
@Validated
public class ChatMessageController {


    @Autowired
    private IChatMessageService chatMessageService;


    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(@RequestBody @Validated ChatMessageRequest request) {
        return chatMessageService.generateStream(request);
    }

    @GetMapping(value = "/simpleMessage", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> simpleGenerateStream(@NotBlank String message) {
        return chatMessageService.simpleGenerateStream(message);
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
}
