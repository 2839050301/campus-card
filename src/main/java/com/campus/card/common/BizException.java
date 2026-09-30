package com.campus.card.common;

import lombok.Getter;


/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */

@Getter
public class BizException extends RuntimeException{
    private final String code;

    public BizException(String message){
        this(ErrCode.BIZ_ERROR.name(),message);
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrCode errCode,String message){
        this(errCode.name(),message);
    }
    public BizException(ErrCode errCode) {
        this(errCode.getCode(), errCode.getDefaultMsg());
    }

}
