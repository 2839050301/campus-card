package com.campus.card.common;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
public enum ErrCode {
    /*UNAUTHORIZED,
    FORBIDDEN,
    PARAM_ERROR,
    BIZ_ERROR,
    SYSTEM_ERROR*/
    UNAUTHORIZED("UNAUTHORIZED", "登录已过期，请重新登录"),
    FORBIDDEN   ("FORBIDDEN",    "权限不足"),
    PARAM_ERROR ("PARAM_ERROR",  "参数不合法"),
    BIZ_ERROR   ("BIZ_ERROR",    "业务处理失败"),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统繁忙，请稍后重试");

    private final String code;
    private final String defaultMsg;
    ErrCode(String code, String defaultMsg) { this.code = code; this.defaultMsg = defaultMsg; }

    public String getCode() { return code; }
    public String getDefaultMsg() { return defaultMsg; }
}
