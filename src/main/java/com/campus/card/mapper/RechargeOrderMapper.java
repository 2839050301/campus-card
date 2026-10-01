package com.campus.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.card.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    //查询某个同学在这天充值总额（只统计支付成功的单）
    // ★ status=2 直接写成字面量，不能用 #{2}
    //   #{2} 不是「第二个参数」的写法 —— MyBatis 会把 2 当成参数名去 ParamMap 里找，
    //   而实际可用的名字只有 [studentNo, billDate, param1, param2]，
    //   于是抛 BindingException: Parameter '2' not found，
    //   接口表现为「系统繁忙」（被 GlobalExceptionHandler 兜底吞掉了）。
    @Select("SELECT IFNULL(SUM(amount),0) from t_recharge_order WHERE student_no=#{studentNo} and status=2 and bill_date=#{billDate}")
    Long sumPaidAmount(@Param("studentNo") String studentNo, @Param("billDate") LocalDate billDate);
}
