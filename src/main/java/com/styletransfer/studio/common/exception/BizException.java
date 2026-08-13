package com.styletransfer.studio.common.exception;

import com.styletransfer.studio.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.resultCode = rc;
    }

    public BizException(ResultCode rc, String message) {
        super(message);
        this.resultCode = rc;
    }
}
