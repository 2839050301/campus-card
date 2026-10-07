package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.card.constant.FlowTypeConstant;
import com.campus.card.dto.ConsumeReq;
import com.campus.card.entity.AccountFlow;
import com.campus.card.entity.CardAccount;
import com.campus.card.mapper.AccountFlowMapper;
import com.campus.card.service.AccountFlowService;
import com.campus.card.service.CardAccountService;
import com.campus.card.service.TerminalService;
import com.campus.card.vo.ConsumeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Description
 * @Author u
 * @Date 2026/10/7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalServiceImpl implements TerminalService {
    private final AccountFlowMapper accountFlowMapper;
    private final CardAccountService cardAccountService;
    private final AccountFlowService accountFlowService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsumeVO consume(ConsumeReq req) {
        //幂等查询 如果请求过了 就把上次结果传回去
        AccountFlow exist = accountFlowMapper.selectOne(new LambdaQueryWrapper<AccountFlow>()
                .eq(AccountFlow::getOrderNo, req.getRequestNo())
                .eq(AccountFlow::getFlowType, FlowTypeConstant.CONSUME));
        if (exist != null) {
            log.info("重复的消费请求，直接返回上次结果 requestNo={} flowNo={}",
                    req.getRequestNo(), exist.getFlowNo());
            return ConsumeVO.of(exist, true);
        }
        //查账户：写流水要用学号 顺带把卡不存在/已冻结  在扣款之前拦截掉
        CardAccount account = cardAccountService.getByCardNo(req.getCardNo());

        //条件扣款：判断和执行在同一条sql 扣不动就抛异常
        Long balanceAfter=cardAccountService.deductBalance(req.getCardNo(),req.getAmount());

        // 写负数流水：关联单号填终端请求号 唯一索引是幂等的最后一道防线
        AccountFlow accountFlow = accountFlowService.recordConsume(account,req,balanceAfter);
        log.info("刷卡消费成功 requestNo={} cardNo={} amount={}分 balanceAfter={}分",
                req.getRequestNo(), req.getCardNo(), req.getAmount(), balanceAfter);
        return ConsumeVO.of(accountFlow, false);
    }
}
