package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Data
@TableName("t_recharge_order")
public class RechargeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;         // 平台充值单号，如 R20260929001
    private String requestNo;       // ★ 客户端幂等号，对应 uk_request_no
    private String studentNo;
    private String cardNo;
    private Long amount;            // 充值金额，单位：分
    private String payMethod;       // WECHAT / ALIPAY / UNIONPAY
    private Integer status;         // 0待支付 1支付中 2支付成功 3支付失败 4已关闭
    private String channelOrderNo;  // 渠道单号
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime closeTime;
    private LocalDateTime expireTime;
    private LocalDate billDate;     // 账单日期
    private String remark;

}
