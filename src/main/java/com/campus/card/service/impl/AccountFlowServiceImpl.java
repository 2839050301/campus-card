package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.card.constant.FlowTypeConstant;
import com.campus.card.dto.ConsumeReq;
import com.campus.card.entity.AccountFlow;
import com.campus.card.entity.CardAccount;
import com.campus.card.mapper.AccountFlowMapper;
import com.campus.card.service.AccountFlowService;
import com.campus.card.util.OrderNoGenerator;
import com.campus.card.vo.FlowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountFlowServiceImpl implements AccountFlowService {

    private final AccountFlowMapper accountFlowMapper;
    private final OrderNoGenerator orderNoGenerator;

    @Override
    public IPage<FlowVO> pageFlow(String studentNo, String flowType, long current, long size) {
        LambdaQueryWrapper<AccountFlow> queryWrapper = new LambdaQueryWrapper<AccountFlow>()
                .eq(AccountFlow::getStudentNo, studentNo)
                .eq(StringUtils.hasText(flowType), AccountFlow::getFlowType, flowType)
                .orderByDesc(AccountFlow::getCreateTime)
                .orderByDesc(AccountFlow::getId);

        Page<AccountFlow> pageResult = accountFlowMapper.selectPage(new Page<AccountFlow>(current, size), queryWrapper);
        return pageResult.convert(f -> {
            FlowVO vo = new FlowVO();
            vo.setId(f.getId());
            vo.setCardNo(f.getCardNo());
            vo.setStudentNo(f.getStudentNo());
            vo.setOrderNo(f.getOrderNo());
            vo.setFlowType(f.getFlowType());
            vo.setAmount(f.getAmount());
            vo.setBalanceAfter(f.getBalanceAfter());
            vo.setCreateTime(f.getCreateTime());
            vo.setRemark(f.getRemark());
            return vo;
        });


    }

    @Override
    public AccountFlow recordConsume(CardAccount account, ConsumeReq req, Long balanceAfter) {
        AccountFlow flow = new AccountFlow();
        flow.setFlowNo(orderNoGenerator.generateOrderNo("F"));
        flow.setCardNo(account.getCardNo());
        flow.setStudentNo(account.getStudentNo());
        // ★ 关联单号填终端请求号，不填 "-"：既不撞唯一索引，又白捡一道幂等防线
        flow.setOrderNo(req.getRequestNo());
        flow.setFlowType(FlowTypeConstant.CONSUME);
        // ★ 库里存负数：日终校验「余额 = 该账户所有流水之和」依赖它
        flow.setAmount(-req.getAmount());
        flow.setBalanceAfter(balanceAfter);
        flow.setRemark("食堂刷卡消费 · " + req.getTerminalNo());
        accountFlowMapper.insert(flow);
        return flow;
    }
}
