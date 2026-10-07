package com.campus.card.vo;

import com.campus.card.entity.AccountFlow;
import lombok.Data;

/**
 * @Description 刷卡消费结果
 * @Author u
 * @Date 2026/10/7
 */
@Data
public class ConsumeVO {
    /** 流水号 */
    private String flowNo;

    /** 卡号 */
    private String cardNo;

    /** 本次消费金额，单位：分，正数 */
    private Long amount;

    /** 消费后余额，单位：分 */
    private Long balanceAfter;

    /** 是不是重复请求（true = 这次没扣钱，返回的是上次的结果） */
    private Boolean repeat;

    /**
     * 从流水构造返回。库里存的是负数，对外一律报正数。
     */
    public static ConsumeVO of(AccountFlow flow, boolean repeat) {
        ConsumeVO vo = new ConsumeVO();
        vo.setFlowNo(flow.getFlowNo());
        vo.setCardNo(flow.getCardNo());
        vo.setAmount(Math.abs(flow.getAmount()));
        vo.setBalanceAfter(flow.getBalanceAfter());
        vo.setRepeat(repeat);
        return vo;
    }
}
