package org.forest.chatollama.common.exception;

public interface Const {

    interface ChatMessageType {

        Short SYSTEM =  0; // 系统消息

        Short USER = 1; // 用户消息

        Short ASSISTANT = 2; // 助手消息

        Short TOOL = 3; // 工具消息
    }

}
