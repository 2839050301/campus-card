package com.campus.card.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @Description
 * @Author u
 * @Date 2026/10/2
 */
@Data
public class RechargeCreateReq {

    @NotBlank(message = "没有请求号")
    private String requestNo;

    // 充值金额 单位分
    @NotNull(message = "充值金额不能为空")
    private Long amount;

    /** WECHAT / ALIPAY / UNIONPAY */
    @NotBlank(message = "缺少支付方式")
    private String payMethod;
}
