package org.forest.chatollama.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("chat_session")
public class ChatSession implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @NotNull
    private Long id;

    private String sessionId;

    @NotBlank
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public ChatSession(String sessionId, String title) {
        this.sessionId = sessionId;
        this.title = title;
    }

    public ChatSession() {
    }
}