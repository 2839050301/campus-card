package com.campus.card.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.vo.RechargeOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

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

    @Select("SELECT IFNULL(SUM(amount), 0) FROM t_recharge_order " +
            "WHERE student_no = #{studentNo} AND status = 2 AND bill_date = #{billDate}")
    Long sumPaidAmount(@Param("studentNo") String studentNo, @Param("billDate") LocalDate billDate);

    IPage<RechargeOrderVO> selectVoPage(@Param("page") IPage<RechargeOrderVO> page,
                                        @Param("studentNo") String studentNo,
                                        @Param("status") Integer status);


    /**
     * 充值单详情。★ 两个参数都必须写 @Param —— XML 里用的是 #{studentNo} / #{orderNo}，
     * 不写 @Param 时 MyBatis 只会拿 Java 形参名当键（[account, orderNo, param1, param2]），
     * #{studentNo} 找不到就抛
     * BindingException: Parameter 'studentNo' not found. Available parameters are [orderNo, account, param1, param2]
     * —— 和 RechargeOrderMapper 上面那个 #{2} 是同一类错，都被兜底吞成「系统繁忙」。
     */
    RechargeOrderVO selectVoByOrderNo(@Param("studentNo") String studentNo,
                                      @Param("orderNo") String orderNo);


    /**
     * 按订单编号锁定
     *
     * @param orderNo
     * @return {@link RechargeOrder }
     */
    @Select("SELECT * FROM t_recharge_order WHERE order_no =#{orderNo} FOR UPDATE")
    RechargeOrder lockBYOrderNo(@Param("orderNo") String orderNo);


    /**
     * 按单号查订单
     *
     * @param orderNo 订单没有
     * @return {@link RechargeOrder }
     */
    @Select("select * from t_recharge_order where order_no=#{orderNo}")
    RechargeOrder selectByOrderNo(@Param("orderNo") String orderNo);
    /**
     * 管理端分页。
     */
    IPage<RechargeOrderVO> selectAdminPage(
            @Param("page") Page<RechargeOrderVO> Page,
            @Param("status") Integer status,
            @Param("payMethod") String payMethod,
            @Param("keyword") String kw);

    /**
     * 管理端详情
     */
    RechargeOrderVO selectAdminVoByOrderNo(String orderNo);
}
