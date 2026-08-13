package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 后台仪表盘统计视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO implements Serializable {

    /** 今日新增用户数 */
    private Long todayNewUsers;

    /** 今日任务总数 */
    private Long todayTaskTotal;

    /** 今日任务成功率（百分比，0~100） */
    private Double todaySuccessRate;

    /** 当前进行中任务数 */
    private Long inProgressTasks;
}
