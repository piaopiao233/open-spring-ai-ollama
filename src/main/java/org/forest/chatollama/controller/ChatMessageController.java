package org.forest.chatollama.controller;

import org.forest.chatollama.dto.ChatMessageRequest;
import org.forest.chatollama.service.IChatMessageService;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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


}
