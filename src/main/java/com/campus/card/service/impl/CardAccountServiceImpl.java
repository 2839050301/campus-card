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

    @Override
    public CardAccount getByCardNo(String cardNo) {
        CardAccount account = cardAccountMapper.selectOne(new LambdaQueryWrapper<CardAccount>()
                .eq(CardAccount::getCardNo, cardNo));
        if (account == null) {
            throw new BizException("账户不存在：" + cardNo);
        }
        return account;
    }

    @Override
    public Long deductBalance(String cardNo, Long amount) {
        int rows = cardAccountMapper.deductBalance(cardNo, amount);
        if(rows==0){
            CardAccount cur = cardAccountMapper.selectOne(new LambdaQueryWrapper<CardAccount>()
                    .eq(CardAccount::getCardNo, cardNo));
            if(cur==null){
                throw new BizException("账户不存在："+cardNo);
            }
            if(cur.getStatus()==null||cur.getStatus()!=1){
                throw new BizException("账户已冻结，无法消费");
            }
            log.warn("余额不足 cardNo={} 当前余额={}分 本次需要={}分", cardNo, cur.getBalance(), amount);
            throw new BizException("余额不足");
        }
        // 扣完再读
        CardAccount after = cardAccountMapper.selectOne(new LambdaQueryWrapper<CardAccount>()
                .eq(CardAccount::getCardNo, cardNo));
        return after.getBalance();
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
