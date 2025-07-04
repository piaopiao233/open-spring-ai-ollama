CREATE TABLE `chat_message` (
                                `id` bigint(20) NOT NULL AUTO_INCREMENT,
                                `type` smallint(6) NOT NULL COMMENT '0：系统 1：用户  2：AI',
                                `session_id` varchar(255) NOT NULL,
                                `record_id` varchar(255) NOT NULL,
                                `content` text NOT NULL,
                                `create_time` datetime NOT NULL,
                                PRIMARY KEY (`id`),
                                KEY `index_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='ai对话记录';