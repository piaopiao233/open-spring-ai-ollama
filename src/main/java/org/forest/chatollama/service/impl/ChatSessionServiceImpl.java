package org.forest.chatollama.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.forest.chatollama.mapper.ChatSessionMapper;
import org.forest.chatollama.model.ChatSession;
import org.forest.chatollama.service.IChatSessionService;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    @Override
    public ChatSession getBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return getOne(wrapper);
    }
}