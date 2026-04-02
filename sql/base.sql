CREATE TABLE `chat_session` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `session_id` varchar(64) NOT NULL COMMENT '会话ID',
                                `title` varchar(255) DEFAULT NULL COMMENT '会话标题',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';
CREATE TABLE `chat_message` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `type` smallint NOT NULL DEFAULT '0' COMMENT '消息类型：0-系统, 1-用户, 2-AI, 3-工具',
                                `session_id` varchar(64) NOT NULL COMMENT '会话ID',
                                `record_id` varchar(64) NOT NULL COMMENT '对话ID',
                                `content` text COMMENT '对话内容',
                                `meta_json` JSON DEFAULT NULL COMMENT '消息扩展元数据',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息记录表';
