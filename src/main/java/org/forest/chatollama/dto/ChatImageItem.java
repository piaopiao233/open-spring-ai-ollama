package org.forest.chatollama.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 图片输入项。
 */
@Data
public class ChatImageItem {

    @NotBlank
    @Schema(description = "图片地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "图片MIME类型，例如 image/jpeg、image/png")
    private String mimeType;
}
