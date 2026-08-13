package com.styletransfer.studio.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.styletransfer.studio.module.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

}
