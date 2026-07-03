package org.forest.chatollama.service.ai;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理正在生成中的聊天流任务。
 */
@Service
public class ChatStreamTaskManager {

    private final Map<String, ChatStreamTask> recordTaskMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRecordMap = new ConcurrentHashMap<>();

    /**
     * 创建流式任务。
     *
     * @param sessionId 会话ID
     * @param recordId 对话ID
     * @return 流式任务
     */
    public ChatStreamTask createTask(String sessionId, String recordId) {
        ChatStreamTask task = new ChatStreamTask(sessionId, recordId);
        recordTaskMap.put(recordId, task);
        sessionRecordMap.put(sessionId, recordId);
        return task;
    }

    /**
     * 根据会话ID获取任务。
     *
     * @param sessionId 会话ID
     * @return 流式任务
     */
    public ChatStreamTask getBySessionId(String sessionId) {
        String recordId = sessionRecordMap.get(sessionId);
        if (ObjectUtil.isNull(recordId)) {
            return null;
        }
        return recordTaskMap.get(recordId);
    }

    /**
     * 根据对话ID获取任务。
     *
     * @param recordId 对话ID
     * @return 流式任务
     */
    public ChatStreamTask getByRecordId(String recordId) {
        return recordTaskMap.get(recordId);
    }

    /**
     * 移除任务。
     *
     * @param task 流式任务
     */
    public void removeTask(ChatStreamTask task) {
        if (ObjectUtil.isNull(task)) {
            return;
        }
        recordTaskMap.remove(task.getRecordId(), task);
        sessionRecordMap.remove(task.getSessionId(), task.getRecordId());
    }
}
