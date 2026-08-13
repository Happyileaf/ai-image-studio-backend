package com.styletransfer.studio.module.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.styletransfer.studio.common.result.Result;
import com.styletransfer.studio.module.admin.dto.AdminStyleEditDTO;
import com.styletransfer.studio.module.admin.dto.QuotaAdjustDTO;
import com.styletransfer.studio.module.admin.dto.StatusDTO;
import com.styletransfer.studio.module.admin.dto.SysConfigBatchDTO;
import com.styletransfer.studio.module.admin.service.AdminService;
import com.styletransfer.studio.module.admin.vo.AdminStyleVO;
import com.styletransfer.studio.module.admin.vo.AdminTaskDetailVO;
import com.styletransfer.studio.module.admin.vo.AdminTaskVO;
import com.styletransfer.studio.module.admin.vo.AdminUserVO;
import com.styletransfer.studio.module.admin.vo.DashboardVO;
import com.styletransfer.studio.module.admin.vo.SysConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台管理接口：仪表盘 / 用户 / 风格 / 任务 / 系统配置
 *
 * <p>类级别 {@code @PreAuthorize("hasRole('ADMIN')")} 统一鉴权；
 * SecurityConfig 已配 {@code /api/v1/admin/** hasRole('ADMIN')}。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ===================== 1. 仪表盘 =====================

    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.success(adminService.dashboard());
    }

    // ===================== 2. 用户管理 =====================

    @GetMapping("/users")
    public Result<IPage<AdminUserVO>> pageUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminService.pageUsers(page, size, email, status, startDate, endDate));
    }

    @PutMapping("/users/{id}/quota")
    public Result<Map<String, Object>> adjustQuota(@PathVariable Long id,
                                                   @Valid @RequestBody QuotaAdjustDTO dto) {
        int balance = adminService.adjustQuota(id, dto);
        return Result.success(Map.of("balance", balance));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                         @Valid @RequestBody StatusDTO dto) {
        adminService.updateUserStatus(id, dto.getStatus());
        return Result.success();
    }

    // ===================== 3. 风格管理 =====================

    @PostMapping(value = "/styles", consumes = "multipart/form-data")
    public Result<AdminStyleVO> createStyle(
            @Valid @RequestPart("dto") AdminStyleEditDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile coverFile) {
        return Result.success(adminService.createStyle(dto, coverFile));
    }

    @PutMapping(value = "/styles/{id}", consumes = "multipart/form-data")
    public Result<AdminStyleVO> updateStyle(
            @PathVariable Long id,
            @Valid @RequestPart("dto") AdminStyleEditDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile coverFile) {
        return Result.success(adminService.updateStyle(id, dto, coverFile));
    }

    @PutMapping("/styles/{id}/status")
    public Result<Void> updateStyleStatus(@PathVariable Long id,
                                          @Valid @RequestBody StatusDTO dto) {
        adminService.updateStyleStatus(id, dto.getStatus());
        return Result.success();
    }

    // ===================== 4. 任务监控 =====================

    @GetMapping("/tasks")
    public Result<IPage<AdminTaskVO>> pageTasks(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long styleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminService.pageTasks(page, size, taskId, email, status, styleId, startDate, endDate));
    }

    /**
     * 任务详情：含子项明细（不含 fileKey，隐私红线）+ 额度流水
     */
    @GetMapping("/tasks/{id}")
    public Result<AdminTaskDetailVO> taskDetail(@PathVariable Long id) {
        return Result.success(adminService.getTaskDetail(id));
    }

    @PostMapping("/tasks/{id}/retry")
    public Result<Void> retryTask(@PathVariable Long id) {
        adminService.retryTask(id);
        return Result.success();
    }

    // ===================== 5. 系统配置 =====================

    @GetMapping("/config")
    public Result<List<SysConfigVO>> listConfigs() {
        return Result.success(adminService.listConfigs());
    }

    @PutMapping("/config")
    public Result<List<SysConfigVO>> updateConfigs(@Valid @RequestBody SysConfigBatchDTO dto) {
        return Result.success(adminService.updateConfigs(dto));
    }
}
