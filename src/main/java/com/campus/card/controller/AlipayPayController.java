package com.campus.card.controller;

import com.campus.card.common.Result;
import com.campus.card.common.UserContext;
import com.campus.card.service.AlipayPayService;
import com.campus.card.vo.AlipayPayVO;
import com.campus.card.vo.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description 支付宝支付：取支付参数 + 异步通知 + 页面回跳
 * @Author u
 * @Date 2026/10/10
 */
@Tag(name = "08 · 支付网关")
@Slf4j
@RestController
@RequestMapping("/api/pay/alipay")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AlipayPayController {

    private final AlipayPayService alipayPayService;

    @Value("${campus.alipay.return-page:http://127.0.0.1:18088/pay.html}")
    private String returnPage;

    @Operation(summary = "取支付宝支付参数")
    @PostMapping("/prepay")
    public Result<AlipayPayVO> prepay(@RequestParam String orderNo) {
        LoginUser me = UserContext.requireStudent();
        return Result.ok(alipayPayService.prepay(orderNo, me));
    }

    /**
     * 支付宝的异步通知。
     * ★ 返回类型是 String，不是 Result —— 返回什么字由支付宝定（success / failure），
     *   一个对外的回调接口，它的返回格式由调用方决定。
     * ★ 它也不能要求登录：支付宝的服务器没有我们的 token（所以第 7 步要放行 /api/pay/**）。
     */
    @Operation(summary = "支付宝异步通知（由支付宝服务器调用）")
    @PostMapping(value = "/notify", produces = "text/plain;charset=UTF-8")
    public String notify(HttpServletRequest request) {
        // ★ 支付宝发的是表单（application/x-www-form-urlencoded），不是 JSON 请求体，
        //   所以这里用 getParameterMap 读，绝不能用 @RequestBody
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) ->
                params.put(key, (values == null || values.length == 0) ? "" : values[0]));
        log.info("收到支付宝通知：out_trade_no={} trade_status={}",
                params.get("out_trade_no"), params.get("trade_status"));
        return alipayPayService.handleNotify(params);
    }

    /**
     * 用户付完钱、浏览器被跳回这里。
     * ★ 这里绝不改钱：跳转和通知是两条独立的路，钱以通知为准（见 §2.4）。
     */
    @Operation(summary = "支付宝页面回跳（由用户浏览器访问）")
    @GetMapping("/return")
    public void back(@RequestParam(value = "out_trade_no", required = false) String orderNo,
                     HttpServletResponse response) throws IOException {
        String target = (orderNo == null || orderNo.isBlank())
                ? returnPage
                : returnPage + "?orderNo=" + orderNo;
        response.sendRedirect(target);
    }
}