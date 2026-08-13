package com.styletransfer.studio.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.styletransfer.studio.module.task.entity.TaskItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务项 Mapper
 */
@Mapper
public interface TaskItemMapper extends BaseMapper<TaskItem> {

}
