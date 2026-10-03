package com.campus.card.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description 充值单 —— /api/recharge/order 系列接口的统一返回体
 * @Author u
 * @Date 2026/10/2
 */
@Data
public class RechargeOrderVO {

    private String orderNo;         // 平台充值单号，如 R20261001001A7K3
    private String requestNo;       // 客户端幂等号
    private String studentNo;
    private String studentName;     // ← 来自 t_user.name
    private String cardNo;
    private Long amount;            // 单位：分
    private String payMethod;       // WECHAT / ALIPAY / UNIONPAY
    private Integer status;         // 0待支付 1支付中 2支付成功 3支付失败 4已关闭
    private String channelOrderNo;  // 渠道单号

    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime closeTime;
    private LocalDateTime expireTime;

    private Integer notifyStatus;   // 0待通知 1成功 2失败（Day 7 填真值）
    private Integer notifyTimes;
    private LocalDateTime nextRetryTime;

    private LocalDate billDate;     // 输出 "2026-10-01"
    private String remark;
}