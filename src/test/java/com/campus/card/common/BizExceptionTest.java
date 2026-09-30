package com.campus.card.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BizExceptionTest {

    @Test
    @DisplayName("只传错误码时，用枚举自带的默认文案")
    void shouldUseDefaultMsgWhenOnlyErrCodeGiven() {
        BizException e = new BizException(ErrCode.UNAUTHORIZED);

        assertEquals("UNAUTHORIZED", e.getCode());
        assertEquals("登录已过期，请重新登录", e.getMessage());
    }

    @Test
    @DisplayName("传了自定义文案时，覆盖默认值")
    void shouldOverrideMsgWhenMsgGiven() {
        BizException e = new BizException(ErrCode.BIZ_ERROR, "余额不足");

        assertEquals("BIZ_ERROR", e.getCode());
        assertEquals("余额不足", e.getMessage());
    }
}
