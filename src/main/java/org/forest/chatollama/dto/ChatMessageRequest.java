package org.forest.chatollama.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 发送消息请求。
 */
@Data
public class ChatMessageRequest {

    @NotBlank
    @Schema(description ="消息内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    /**
     * 会话id
     */
    @Schema(description ="会话id")
    private String sessionId;

    /**
     * 当前轮图片列表
     */
    @Valid
    @Schema(description = "当前轮附带的图片列表")
    private List<ChatImageItem> imageList;
}
