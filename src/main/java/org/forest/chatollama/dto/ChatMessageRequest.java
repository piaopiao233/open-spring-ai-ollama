package org.forest.chatollama.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {


    @NotBlank
    @Schema(description ="消息内容",  requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    /**
     * 会话id
     */
    @Schema(description ="会话id")
    private String sessionId;

}
