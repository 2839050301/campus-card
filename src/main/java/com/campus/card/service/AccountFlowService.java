package com.campus.card.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.dto.ConsumeReq;
import com.campus.card.entity.AccountFlow;
import com.campus.card.entity.CardAccount;
import com.campus.card.vo.FlowVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
public interface AccountFlowService {

    /**
     * 分页查某个学生的账户流水
     * @param studentNo
     * @param flowType
     * @param current
     * @param size
     * @return
     */
    IPage<FlowVO> pageFlow(String studentNo, String flowType, long current, long size);


    /**
     * 写一条消费流水（金额取负 + 关联单号填终端请求号），返回落库后的流水
     *
     * @param account      扣款前查出来的账户（要用它的卡号和学号）
     * @param req          终端请求：请求号写进 order_no、终端号写进 remark
     * @param balanceAfter 扣款之后的余额，写进流水的余额快照
     * @return 落库后的流水（含生成的 flowNo）
     */
    AccountFlow recordConsume(CardAccount account, ConsumeReq req, Long balanceAfter);
}
