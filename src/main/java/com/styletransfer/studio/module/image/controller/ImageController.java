package com.styletransfer.studio.module.image.controller;

import com.styletransfer.studio.common.annotation.RateLimit;
import com.styletransfer.studio.common.result.Result;
import com.styletransfer.studio.module.image.service.ImageService;
import com.styletransfer.studio.module.image.vo.UploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片接口：仅上传（隐私原则：不提供原图列表/详情/下载）
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 上传原图（用户级频控：60s 内 20 次）
     */
    @PostMapping("/upload")
    @RateLimit(key = "upload", limit = 20, window = 60)
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(imageService.upload(file));
    }
}
