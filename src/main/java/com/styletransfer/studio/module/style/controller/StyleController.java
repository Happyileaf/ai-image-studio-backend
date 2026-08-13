package com.styletransfer.studio.module.style.controller;

import com.styletransfer.studio.common.result.Result;
import com.styletransfer.studio.module.style.service.StyleService;
import com.styletransfer.studio.module.style.vo.StyleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风格库前台接口：上架风格列表 / 风格详情
 */
@RestController
@RequestMapping("/api/v1/styles")
@RequiredArgsConstructor
public class StyleController {

    private final StyleService styleService;

    /**
     * 上架风格列表（可按分类筛选）
     */
    @GetMapping
    public Result<List<StyleVO>> list(@RequestParam(required = false) String category) {
        return Result.success(styleService.listOnShelf(category));
    }

    /**
     * 风格详情
     */
    @GetMapping("/{id}")
    public Result<StyleVO> detail(@PathVariable Long id) {
        return Result.success(styleService.getDetail(id));
    }
}
