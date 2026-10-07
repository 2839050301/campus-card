package com.campus.card.service;

import com.campus.card.entity.CardAccount;
import com.campus.card.vo.AccountVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
public interface CardAccountService {

    /**
     * 查某个学生的账户信息
     * @param studentNo 学生学号
     * @return
     */
    AccountVO getMyAccount(String studentNo);

    /**
     * 按卡号查账户，不存在直接抛业务异常
     *
     * @param cardNo 卡号
     * @return 账户实体（余额是读这一瞬间的值，别拿它去判断够不够扣）
     */
    CardAccount getByCardNo(String cardNo);

    /**
     * 条件扣款，返回「扣款之后」的余额；扣不动就抛业务异常
     *
     * ★ 判断（够不够）和执行（扣）必须在同一条 SQL 里，否则并发下会超扣。
     * ★ 卡号和金额的合法性由 DTO 上的注解在 Controller 那层挡住（见 ConsumeReq）——
     *   写在 Service 接口上的 jakarta 校验注解，没有 @Validated 是永远不会触发的。
     *
     * @param cardNo 卡号
     * @param amount 扣款金额（分，正数）
     * @return 扣款之后的余额（分），写进流水的余额快照
     */
    Long deductBalance(String cardNo, Long amount);
}
