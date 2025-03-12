package org.forest.chatollama.controller;


import org.forest.chatollama.dto.ChatMessageRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping(value = "/Chat")
@Validated
public class ChatController {


    @Autowired
    private OllamaChatModel chatModel;


    @PostMapping(value = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(@RequestBody @Validated ChatMessageRequest request) {
        return chatModel.stream(new Prompt(new UserMessage(request.getMessage()))).cache();
    }


}
