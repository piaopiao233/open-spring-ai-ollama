package org.forest.chatollama.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
/**
 * <p>
 * 
 * </p>
 *
 * @author 居森林
 * @since 2025-03-21 15:25:53
 */
@Getter
@Setter
@ToString
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;


    /**
     * 0：系统  1：用户 2：AI  3：工具
     */
    private Short type;

    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 对话id
     */
    private String recordId;

    /**
     * 对话内容
     */
    private String content;

    /**
     * 消息扩展元数据(JSON)
     */
    @TableField(value = "meta_json", typeHandler = JacksonTypeHandler.class)
    private MetaData metaJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;


    public ChatMessage(Short type, String sessionId, String recordId, String content) {
        this(type, sessionId, recordId, content, null);
    }

    public ChatMessage(Short type, String sessionId, String recordId, String content, MetaData metaJson) {
        this.type = type;
        this.sessionId = sessionId;
        this.recordId = recordId;
        this.content = content;
        this.metaJson = metaJson;
    }

    public ChatMessage() {
    }
}
