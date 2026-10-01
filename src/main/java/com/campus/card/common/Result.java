package com.campus.card.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private Boolean success;
    private String errCode;
    private String errMsg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String errCode, String errMsg) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setErrCode(errCode);
        result.setErrMsg(errMsg);
        return result;
    }

    public static <T> Result<T> fail(ErrCode errCode, String errMsg) {
        return fail(errCode.name(), errMsg);
    }

}
