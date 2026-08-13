package com.styletransfer.studio.module.history.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.styletransfer.studio.common.result.Result;
import com.styletransfer.studio.module.history.service.HistoryService;
import com.styletransfer.studio.module.history.vo.HistoryImageVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史记录接口：列表 / 删除 / 批量删除 / ZIP 下载
 */
@RestController
@RequestMapping("/api/v1/history/images")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 分页查询当前用户历史结果图（仅保留期内）
     */
    @GetMapping
    public Result<Page<HistoryImageVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(historyService.list(page, size));
    }

    /**
     * 删除单条历史图片（软删 DB + 删 MinIO）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        historyService.delete(id);
        return Result.success();
    }

    /**
     * 批量删除历史图片
     */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        historyService.batchDelete(ids);
        return Result.success();
    }

    /**
     * ZIP 流式下载（ids 为空时下载当前用户保留期内全部）
     */
    @GetMapping("/download")
    public void download(@RequestParam(required = false) List<Long> ids, HttpServletResponse response) {
        historyService.downloadZip(ids, response);
    }
}
