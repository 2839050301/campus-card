package com.campus.card.controller;

import com.campus.card.util.SignUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Description
 * @Author u
 * @Date 2026/10/4
 */
@Slf4j
@RestController
@RequestMapping("/api/mock/terminal")
public class TerminalNotifyController {

    @Value("${campus.pay.notify-secret}")
    private String notifySecret;

    /**
     * ★ 演示开关：置 true 后接收端"装死"，用来验退避重试（V2/V3）。
     * volatile 保证一个请求线程改的、另一个线程立刻看得见。
     */
    private volatile boolean forceFail = false;

    @PostMapping("/notify")
    public Map<String, Object> receive(
            @RequestHeader(value = "X-Campus-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Campus-Sign", required = false) String sign,
            @RequestBody String body) {

        if (forceFail) {
            log.warn("[模拟对方系统] 故障模式中，拒绝本次通知");
            return resp(500, "对方系统繁忙，请稍后重试");
        }

        // ★ 验签：用同一个密钥把 timestamp + body 重算一遍
        String expected = SignUtil.hmacSha256(notifySecret, timestamp + "." + body);
        if (!SignUtil.verify(expected, sign)) {
            log.warn("[模拟对方系统] 验签失败，拒绝。timestamp={} sign={}", timestamp, sign);
            return resp(401, "sign 校验失败");
        }

        log.info("[模拟对方系统] 收到充值成功通知：{}", body);
        return resp(0, "OK");
    }

    /**
     * 演示用：切换故障模式。POST /api/mock/terminal/fail?on=true
     */
    @PostMapping("/fail")
    public Map<String, Object> toggleFailMode(@RequestParam boolean on) {
        this.forceFail = on;
        log.warn("[模拟对方系统] 故障模式 = {}", on);
        return resp(0, on ? "已进入故障模式" : "已恢复正常");
    }

    /**
     * ★ 用 LinkedHashMap 而不是 Map.of：
     * 一方面顺序稳定、日志好读，另一方面不会踩 Map.of 对 null 值直接 NPE 的坑。
     */
    private Map<String, Object> resp(int code, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("msg", msg);
        return m;
    }
}
