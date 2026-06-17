package org.forest.chatollama.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.forest.chatollama.dto.PageResult;
import org.forest.chatollama.model.ChatSession;

public interface IChatSessionService extends IService<ChatSession> {
    ChatSession getBySessionId(String sessionId);

    /**
     * 分页查询会话列表。
     *
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 会话分页结果
     */
    PageResult<ChatSession> pageSessions(Long pageNum, Long pageSize);

    void touchSession(String sessionId);

    void deleteSession(Long id);

    void updateSessionTitle(Long id, String title);
}
