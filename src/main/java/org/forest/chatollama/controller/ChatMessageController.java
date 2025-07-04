package org.forest.chatollama.controller;

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
    public Flux<ChatResponse> simpleGenerateStream(String message) {
        return chatMessageService.simpleGenerateStream(message);
    }

    @GetMapping(value = "/selectBySessionId")
    public Result<List<ChatMessage>> selectBySessionId(@NotBlank String sessionId) {
        var chatMessages = chatMessageService.selectBySessionId(sessionId);
        return Result.succ(chatMessages);
    }

}
