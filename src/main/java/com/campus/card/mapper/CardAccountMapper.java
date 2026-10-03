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

}
