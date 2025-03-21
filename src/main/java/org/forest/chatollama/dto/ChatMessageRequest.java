package org.forest.chatollama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {


    @NotBlank
    private String message;

    /**
     * 会话id
     */
    @NotBlank
    private String sessionId;

    /**
     * 对话id
     */
    @NotBlank
    private String recordId;

}
