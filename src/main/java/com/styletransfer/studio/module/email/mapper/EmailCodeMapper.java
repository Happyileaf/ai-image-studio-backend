package com.styletransfer.studio.module.email.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.styletransfer.studio.module.email.entity.EmailCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮箱验证码 Mapper
 */
@Mapper
public interface EmailCodeMapper extends BaseMapper<EmailCode> {

}
