package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.card.common.BizException;
import com.campus.card.constant.LimitConstant;
import com.campus.card.entity.CardAccount;
import com.campus.card.entity.User;
import com.campus.card.mapper.CardAccountMapper;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.mapper.UserMapper;
import com.campus.card.service.CardAccountService;
import com.campus.card.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardAccountServiceImpl implements CardAccountService {

    private final CardAccountMapper cardAccountMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final UserMapper userMapper;

    @Override
    public AccountVO getMyAccount(String studentNo) {

        CardAccount cardAccount = cardAccountMapper
                .selectOne(new LambdaQueryWrapper<CardAccount>().eq(CardAccount::getStudentNo, studentNo));
        if (cardAccount == null) {
            throw new BizException("账户不存在");
        }
        //查询今日已充
        Long todayRecharged = rechargeOrderMapper.sumPaidAmount(studentNo, LocalDate.now());

        User user = userMapper
                .selectOne(new LambdaQueryWrapper<User>().eq(User::getAccount, studentNo));

        return createVo(cardAccount, user, todayRecharged);

    }

    private static @NonNull AccountVO createVo(CardAccount cardAccount, User user, Long todayRecharged) {
        AccountVO vo = new AccountVO();
        vo.setCardNo(cardAccount.getCardNo());
        vo.setStudentNo(cardAccount.getStudentNo());
        vo.setName(user == null ? "" : user.getName());
        vo.setCollege(user == null ? "" : user.getCollege());
        vo.setBalance(cardAccount.getBalance());
        vo.setStatus(cardAccount.getStatus());
        vo.setSingleLimit(LimitConstant.SINGLE_LIMIT);
        vo.setDailyLimit(LimitConstant.DAILY_LIMIT);
        vo.setTodayRecharged(todayRecharged == null ? 0L : todayRecharged);
        return vo;
    }
}
