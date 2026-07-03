package org.forest.chatollama.service.ai;

import lombok.Getter;
import org.forest.chatollama.dto.CustomChatResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 后端托管的聊天流任务。
 */
@Getter
public class ChatStreamTask {

    /**
     * 会话ID。
     */
    private final String sessionId;

    /**
     * 当前轮对话ID。
     */
    private final String recordId;

    /**
     * 可回放的SSE消息推送器，前端重连时会先收到历史chunk。
     */
    private final Sinks.Many<CustomChatResponse> sink = Sinks.many().replay().all();

    /**
     * 当前轮AI已生成的完整内容。
     */
    private final StringBuffer fullContent = new StringBuffer();

    /**
     * 当前轮AI回复的token使用数。
     */
    private final AtomicReference<Integer> tokenCount = new AtomicReference<>();

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
     */
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
     * 获取可重复订阅的流。
     *
     * @return 流式响应
     */
    public Flux<CustomChatResponse> asFlux() {
        return sink.asFlux();
    }

    /**
     * 绑定模型生成订阅句柄。
     *
     * @param disposable 订阅句柄
     */
    public void setDisposable(Disposable disposable) {
        this.disposable = disposable;
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
}
