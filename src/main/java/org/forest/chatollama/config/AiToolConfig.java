package org.forest.chatollama.config;

import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.service.ai.ChatStreamTaskManager;
import org.forest.chatollama.service.ai.LoggingToolCallingManager;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 工具调用配置。
 */
@Configuration
public class AiToolConfig {

    /**
     * 构建带工具调用落库能力的 ToolCallingAdvisor。
     *
     * @param toolCallingManager Spring AI 默认工具调用管理器
     * @param chatMessageServiceProvider 聊天消息服务延迟提供器，用于避免和 ChatMessageServiceImpl 构造注入形成循环依赖
     * @param chatStreamTaskManagerProvider 流式任务管理器延迟提供器，用于推送工具调用中间状态
     * @return 工具调用 Advisor
     */
    @Bean
    public ToolCallingAdvisor toolCallingAdvisor(ToolCallingManager toolCallingManager,
                                                 ObjectProvider<IChatMessageService> chatMessageServiceProvider,
                                                 ObjectProvider<ChatStreamTaskManager> chatStreamTaskManagerProvider) {
        LoggingToolCallingManager loggingToolCallingManager = buildLoggingToolCallingManager(
                toolCallingManager,
                chatMessageServiceProvider,
                chatStreamTaskManagerProvider
        );
        return ToolCallingAdvisor.builder()
                .toolCallingManager(loggingToolCallingManager)
                .build();
    }

    /**
     * 构建无请求状态的工具调用落库管理器。
     *
     * @param toolCallingManager Spring AI 默认工具调用管理器
     * @param chatMessageServiceProvider 聊天消息服务延迟提供器
     * @param chatStreamTaskManagerProvider 流式任务管理器延迟提供器
     * @return 工具调用落库管理器
     */
    private LoggingToolCallingManager buildLoggingToolCallingManager(ToolCallingManager toolCallingManager,
                                                                     ObjectProvider<IChatMessageService> chatMessageServiceProvider,
                                                                     ObjectProvider<ChatStreamTaskManager> chatStreamTaskManagerProvider) {
        return new LoggingToolCallingManager(
                toolCallingManager,
                chatMessageServiceProvider,
                chatStreamTaskManagerProvider
        );
    }
}
