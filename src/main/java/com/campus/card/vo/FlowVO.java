package com.campus.card.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description 账户流水列表项 —— GET /api/card/flow 里 records 的每一项
 * @Author u
 * @Date 2026/10/1
 */
@Data
public class FlowVO {
    private Long id;
    private String cardNo;
    private String studentNo;
    private String orderNo;       // 消费流水是 "-"
    private String flowType;      // RECHARGE / CONSUME / REFUND / ADJUST
    private Long amount;          // 有符号：充值正、消费负
    private Long balanceAfter;    // 入账后余额
    private LocalDateTime createTime;
    private String remark;

}
