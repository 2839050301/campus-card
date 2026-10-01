package com.campus.card.vo;

import lombok.Data;

/**
 * @Description 账户信息 —— GET /api/card/account 的返回体
 * @Author u
 * @Date 2026/10/1
 */
@Data
public class AccountVO {

    private String cardNo;
    private String studentNo;
    private String name;
    private String college;
    private Long balance;         // 余额，单位：分
    private Integer status;       // 1 正常 / 0 冻结
    private Long singleLimit;     // 单笔限额（分）
    private Long dailyLimit;      // 单日限额（分）
    private Long todayRecharged;  // 今日已充（分）
}
