package org.forest.chatollama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class ChatMessageRequest {


    @NotNull
    @NotBlank
    private String message;


}
