package com.styletransfer.studio.module.task.controller;

import com.styletransfer.studio.common.enums.TaskStatus;
import com.styletransfer.studio.common.result.Result;
import com.styletransfer.studio.module.task.dto.CreateTaskDTO;
import com.styletransfer.studio.module.task.progress.TaskProgressRegistry;
import com.styletransfer.studio.module.task.service.TaskResultService;
import com.styletransfer.studio.module.task.service.TaskService;
import com.styletransfer.studio.module.task.vo.ResultImageVO;
import com.styletransfer.studio.module.task.vo.TaskDetailVO;
import com.styletransfer.studio.module.task.vo.TaskProgressVO;
import com.styletransfer.studio.module.task.vo.TaskVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 任务接口：创建 / 当前任务 / 详情 / 取消 / SSE 进度
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    /** SSE 连接超时：5 分钟 */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final TaskService taskService;
    private final TaskResultService taskResultService;
    private final TaskProgressRegistry taskProgressRegistry;

    /**
     * 创建任务
     */
    @PostMapping
    public Result<TaskVO> create(@Valid @RequestBody CreateTaskDTO dto) {
        return Result.success(taskService.createTask(dto));
    }

    /**
     * 当前用户进行中任务（可能为 null）
     */
    @GetMapping("/current")
    public Result<TaskVO> current() {
        return Result.success(taskService.getCurrentTask());
    }

    /**
     * 任务详情（含 items，不含 fileKey）
     */
    @GetMapping("/{id}")
    public Result<TaskDetailVO> detail(@PathVariable Long id) {
        return Result.success(taskService.getTaskDetail(id));
    }

    /**
     * 取消任务
     */
    @PostMapping("/{id}/cancel")
    public Result<TaskVO> cancel(@PathVariable Long id) {
        return Result.success(taskService.cancelTask(id));
    }

    /**
     * 任务进度 SSE 流
     *
     * <p>立即返回 emitter，后续 Worker 通过 {@link TaskProgressRegistry} 推送 progress / done 事件。
     * 若任务已是终态，立即推送 done 并 complete。</p>
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        // 校验归属并取状态（不存在或不归属当前用户抛 TASK_NOT_FOUND）
        TaskDetailVO detail = taskService.getTaskDetail(id);
        SseEmitter emitter = taskProgressRegistry.register(id, SSE_TIMEOUT_MS);
        if (isTerminal(detail.getStatus())) {
            TaskProgressVO done = TaskProgressVO.builder()
                    .status(detail.getStatus())
                    .total(detail.getImageCount())
                    .success(detail.getSuccessCount())
                    .failed(detail.getFailCount())
                    .currentItem(0)
                    .stage(detail.getStatus())
                    .progress(100)
                    .build();
            taskProgressRegistry.sendDone(id, done);
        }
        return emitter;
    }

    /**
     * 任务结果图列表（仅成功结果图，预签名 URL 1h）
     */
    @GetMapping("/{id}/results")
    public Result<List<ResultImageVO>> results(@PathVariable Long id) {
        return Result.success(taskResultService.listResults(id));
    }

    /**
     * 任务结果图 ZIP 流式下载
     */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        taskResultService.downloadZip(id, response);
    }

    private boolean isTerminal(String status) {
        return TaskStatus.SUCCESS.name().equals(status)
                || TaskStatus.FAILED.name().equals(status)
                || TaskStatus.CANCELED.name().equals(status);
    }
}
