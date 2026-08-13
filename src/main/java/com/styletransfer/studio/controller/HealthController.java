package com.styletransfer.studio.controller;

import com.styletransfer.studio.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 根路径 / 健康检查与 API 信息
 *
 * <p>用于容器健康检查与接口可达性验证。业务 API 统一挂在 /api/v1 下。</p>
 */
@RestController
@RequestMapping
public class HealthController {

    @GetMapping("/")
    public Result<Map<String, Object>> index() {
        return Result.success(Map.of(
                "name", "AI Image Style Transfer Studio - Backend",
                "version", "1.0.0-SNAPSHOT",
                "time", LocalDateTime.now().toString(),
                "status", "UP",
                "docs", Map.of(
                        "swagger-ui", "/swagger-ui.html",
                        "openapi", "/v3/api-docs",
                        "actuator-health", "/actuator/health"
                )
        ));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }
}
