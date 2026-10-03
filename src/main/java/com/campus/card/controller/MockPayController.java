package com.campus.card.controller;

import com.campus.card.common.ErrCode;
import com.campus.card.common.Result;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.service.PayCallbackService;
import com.campus.card.vo.PayCallbackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description 模拟支付渠道的异步回调入口
 * @Author u
 * @Date 2026/10/3
 */
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
public class MockPayController {

    private final PayCallbackService payCallbackService;

    @PostMapping("/pay")
    public Result<PayCallbackVO> pay(
            @RequestParam("orderNo") String orderNo,
            @RequestParam(value = "result", required = false, defaultValue = "SUCCESS") String result
    ) {

        PayCallbackVO vo = payCallbackService.handlePayCallback(orderNo, result);
        if (vo.getStatus() != null && vo.getStatus() == OrderStatusConstant.CLOSED){
            return Result.fail(ErrCode.BIZ_ERROR, vo.getMessage());
        }
        return Result.ok(vo);
    }

}
