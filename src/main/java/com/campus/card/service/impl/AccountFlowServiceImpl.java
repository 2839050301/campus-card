package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.card.entity.AccountFlow;
import com.campus.card.mapper.AccountFlowMapper;
import com.campus.card.service.AccountFlowService;
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
}
