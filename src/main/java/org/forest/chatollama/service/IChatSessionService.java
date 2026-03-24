package org.forest.chatollama.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.forest.chatollama.model.ChatSession;

public interface IChatSessionService extends IService<ChatSession> {
    ChatSession getBySessionId(String sessionId);
}