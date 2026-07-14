package org.forest.chatollama.service.ai;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.Setter;
import org.forest.chatollama.dto.CustomChatResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后端托管的聊天流任务。
 */
public class ChatStreamTask {

    /**
     * 会话ID。
     */
    @Getter
    private final String sessionId;

    /**
     * 当前轮对话ID。
     */
    @Getter
    private final String recordId;

    /**
     * 可回放的SSE消息推送器，前端重连时会先收到历史chunk。
     */
    private final Sinks.Many<CustomChatResponse> sink = Sinks.many().replay().all();

    /**
     * 当前轮AI已生成的完整内容。
     */
    private final StringBuilder fullContent = new StringBuilder();

    /**
     * 当前轮AI已生成的思考内容。
     */
    private final StringBuilder thinkingContent = new StringBuilder();

    /**
     * 当前轮AI回复的token使用数。
     */
    private Integer tokenCount;

    /**
     * 当前轮AI回复的提示词token使用数。
     */
    private Integer promptTokenCount;

    /**
     * 当前轮AI回复的生成内容token使用数。
     */
    private Integer completionTokenCount;

    /**
     * 流式任务是否已经结束。
     */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * 最终助手消息是否已经落库。
     */
    private final AtomicBoolean saved = new AtomicBoolean(false);

    /**
     * 模型流订阅句柄，用于用户主动停止生成。
     * -- SETTER --
     *  绑定模型生成订阅句柄。
     *
     * @param disposable 订阅句柄

     */
    @Setter
    private volatile Disposable disposable;

    /**
     * 创建聊天流任务。
     *
     * @param sessionId 会话ID
     * @param recordId 对话ID
     */
    public ChatStreamTask(String sessionId, String recordId) {
        this.sessionId = sessionId;
        this.recordId = recordId;
    }

    /**
     * 获取当前累计结果的不可变快照。
     *
     * @return 当前响应快照
     */
    public synchronized ResponseSnapshot snapshot() {
        return new ResponseSnapshot(
                fullContent.toString(),
                thinkingContent.toString(),
                tokenCount,
                promptTokenCount,
                completionTokenCount
        );
    }

    /**
     * 获取可重复订阅的流。
     *
     * @return 流式响应
     */
    public Flux<CustomChatResponse> asFlux() {
        return sink.asFlux();
    }

    /**
     * 推送一段流式响应。
     *
     * @param response 流式响应
     */
    public void emit(CustomChatResponse response) {
        sink.tryEmitNext(response);
    }

    /**
     * 累计并推送一段模型流式响应。
     *
     * @param response 模型流式响应
     */
    public void emitModelResponse(CustomChatResponse response) {
        if (ObjectUtil.isNull(response)) {
            return;
        }
        synchronized (this) {
            String content = StrUtil.nullToDefault(response.getContent(), "");
            if (Boolean.TRUE.equals(response.getIsThinking())) {
                thinkingContent.append(content);
            } else {
                fullContent.append(content);
            }
            if (ObjectUtil.isNotNull(response.getTokenCount())) {
                tokenCount = response.getTokenCount();
            }
            if (ObjectUtil.isNotNull(response.getPromptTokenCount())) {
                promptTokenCount = response.getPromptTokenCount();
            }
            if (ObjectUtil.isNotNull(response.getCompletionTokenCount())) {
                completionTokenCount = response.getCompletionTokenCount();
            }
        }
        emit(response);
    }

    /**
     * 完成当前流。
     */
    public void complete() {
        if (finished.compareAndSet(false, true)) {
            sink.tryEmitComplete();
        }
    }

    /**
     * 异常结束当前流。
     *
     * @param error 异常
     */
    public void error(Throwable error) {
        if (finished.compareAndSet(false, true)) {
            sink.tryEmitError(error);
        }
    }

    /**
     * 取消模型生成。
     */
    public void cancelModelStream() {
        Disposable currentDisposable = disposable;
        if (currentDisposable != null && !currentDisposable.isDisposed()) {
            currentDisposable.dispose();
        }
    }

    /**
     * 标记最终助手消息已落库。
     *
     * @return 是否首次标记成功
     */
    public boolean markSaved() {
        return saved.compareAndSet(false, true);
    }

    /**
     * 当前流式任务累计结果的不可变快照。
     *
     * @param fullContent 完整正文
     * @param thinkingContent 完整思考内容
     * @param tokenCount token使用数
     * @param promptTokenCount 提示词token使用数
     * @param completionTokenCount 生成内容token使用数
     */
    public record ResponseSnapshot(String fullContent,
                                   String thinkingContent,
                                   Integer tokenCount,
                                   Integer promptTokenCount,
                                   Integer completionTokenCount) {
    }
}
