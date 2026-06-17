package org.forest.chatollama.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.forest.chatollama.dto.PageResult;
import org.forest.chatollama.mapper.ChatSessionMapper;
import org.forest.chatollama.model.ChatSession;
import org.forest.chatollama.service.IChatMessageService;
import org.forest.chatollama.service.IChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements IChatSessionService {

    private IChatMessageService chatMessageService;

    @Lazy
    @Autowired
    public void setChatMessageService(IChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @Override
    public ChatSession getBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return getOne(wrapper);
    }

    /**
     * 分页查询会话列表。
     *
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 会话分页结果
     */
    @Override
    public PageResult<ChatSession> pageSessions(Long pageNum, Long pageSize) {
        Page<ChatSession> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChatSession::getUpdateTime)
                .orderByDesc(ChatSession::getId);
        Page<ChatSession> result = page(page, wrapper);
        return new PageResult<>(
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getRecords()
        );
    }

    @Override
    public void touchSession(String sessionId) {
        lambdaUpdate()
                .eq(ChatSession::getSessionId, sessionId)
                .set(ChatSession::getUpdateTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional
    public void deleteSession(Long id) {
        ChatSession chatSession = getById(id);
        if (ObjectUtil.isNull(chatSession)) {
            return;
        }
        boolean removed = removeById(id);
        if (!removed) {
            return;
        }
        chatMessageService.deleteBySessionId(chatSession.getSessionId());
    }

    @Override
    public void updateSessionTitle(Long id, String title) {
        lambdaUpdate()
                .eq(ChatSession::getId, id)
                .set(ChatSession::getTitle, title)
                .set(ChatSession::getUpdateTime, LocalDateTime.now())
                .update();
    }
}
