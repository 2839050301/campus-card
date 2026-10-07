package com.campus.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.card.entity.CardAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Mapper
public interface CardAccountMapper extends BaseMapper<CardAccount> {


    /**
     * 锁住账户行。
     */
    @Select("SELECT * FROM t_card_account WHERE card_no = #{cardNo} FOR UPDATE")
    CardAccount lockByCardNo(@Param("cardNo") String cardNo);

    /**
     * 加余额。
     */
    @Update("UPDATE t_card_account SET balance = balance + #{amount} " +
            "WHERE card_no = #{cardNo} AND status = 1")
    int addBalance(@Param("cardNo") String cardNo, @Param("amount") Long amount);

    /**
     * 条件扣款：判断（够不够）和执行（扣）在同一条 SQL 里。
     * 返回 0 行 = 卡号不存在 / 账户已冻结 / 余额不足（三种情况在 CardAccountServiceImpl 里分开报）。
     *
     * @param cardNo 卡号
     * @param amount 扣款金额（分，正数）
     * @return 影响行数：1 = 扣款成功；0 = 卡号不存在 / 账户已冻结 / 余额不足
     */
    @Update("UPDATE t_card_account SET balance = balance - #{amount} " +
            "WHERE card_no=#{cardNo} AND status=1 AND balance>=#{amount}")
    int deductBalance(@Param("cardNo") String cardNo, @Param("amount") Long amount);
}
