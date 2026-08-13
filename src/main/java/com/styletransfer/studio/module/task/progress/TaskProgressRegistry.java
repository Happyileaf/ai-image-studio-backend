package com.styletransfer.studio.module.task.progress;

import com.styletransfer.studio.module.task.vo.TaskProgressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务进度注册表（SSE 推送中心）
 *
 * <p>维护 taskId → SseEmitter 列表的多端订阅。Worker 通过 {@link #sendProgress} 推送进度，
 * 通过 {@link #sendDone} 推送终态并 complete 所有 emitter。</p>
 */
@Slf4j
@Component
public class TaskProgressRegistry {

    private final ConcurrentHashMap<Long, List<SseEmitter>> registry = new ConcurrentHashMap<>();

    /**
     * 注册订阅
     *
     * @param taskId    任务 ID
     * @param timeoutMs 超时时间（毫秒）
     * @return SSE emitter
     */
    public SseEmitter register(Long taskId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        List<SseEmitter> emitters = registry.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> {
            log.debug("[ProgressRegistry] emitter 超时 taskId={}", taskId);
            removeEmitter(taskId, emitter);
        });
        emitter.onError(e -> {
            log.debug("[ProgressRegistry] emitter 异常 taskId={}: {}", taskId, e.getMessage());
            removeEmitter(taskId, emitter);
        });
        return emitter;
    }

    /**
     * 推送进度事件（event name = "progress"）
     */
    public void sendProgress(Long taskId, TaskProgressVO progressData) {
        send(taskId, "progress", progressData, false);
    }

    /**
     * 推送终态事件（event name = "done"）并 complete 所有 emitter
     */
    public void sendDone(Long taskId, TaskProgressVO doneData) {
        send(taskId, "done", doneData, true);
    }

    private void send(Long taskId, String eventName, Object data, boolean completeAfter) {
        List<SseEmitter> emitters = registry.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                if (completeAfter) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.debug("[ProgressRegistry] send 失败移除 emitter taskId={}: {}", taskId, e.getMessage());
                removeEmitter(taskId, emitter);
            }
        }
        if (completeAfter) {
            registry.remove(taskId);
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = registry.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                registry.remove(taskId, emitters);
            }
        }
    }
}
