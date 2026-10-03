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
@TableName("t_account_flow")
public class AccountFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String flowNo;       // 流水号
    private String cardNo;       // 卡号
    private String studentNo;    // 学号
    private String orderNo;      // 关联充值单号，消费流水填 "-"
    private String flowType;     // RECHARGE / CONSUME / REFUND / ADJUST //	充值入账 / 消费扣款 / 退款出账 / 人工调账
    private Long amount;         // 有符号：充值正、消费负
    private Long balanceAfter;   // 入账后余额，对账要核这个
    private LocalDateTime createTime;
    private String remark;


}
