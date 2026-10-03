package com.campus.card.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.dto.RechargeCreateReq;
import com.campus.card.service.RechargeOrderService;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.RechargeOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Description
 * @Author u
 * @Date 2026/10/2
 */
@Tag(name="03 · 学生端 · 充值")
@RestController
@RequestMapping("/api/recharge")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RechargeController {

    private final RechargeOrderService rechargeOrderService;

    @Operation(summary="创建充值单（三次幂等）")
    @PostMapping("/order")
    public Result<RechargeOrderVO> create(@RequestBody @Valid RechargeCreateReq rechargeCreateReq){
        LoginUser user= UserContext.requireStudent();
        return Result.ok(rechargeOrderService.createOrder(user,rechargeCreateReq));
    }

    @Operation(summary = "我的充值记录分页")
    @GetMapping("/order/page")
    public Result<IPage<RechargeOrderVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status
    ){
        LoginUser user= UserContext.requireStudent();
        return Result.ok(rechargeOrderService.pageMyOrders(user,status,current,size));
    }

    @Operation(summary = "充值单详情")
    @GetMapping("/order/{orderNo}")
    public Result<RechargeOrderVO> detail(@PathVariable String orderNo){
        LoginUser user = UserContext.requireStudent();
        return Result.ok(rechargeOrderService.getMyOrder(user,orderNo));
    }


}
