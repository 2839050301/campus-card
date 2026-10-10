package com.campus.card.service.impl;

import com.campus.card.common.BizException;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.service.AlipayPayService;
import com.campus.card.service.PayCallbackService;
import com.campus.card.service.alipay.AlipaySignUtil;
import com.campus.card.vo.AlipayPayVO;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.RechargeOrderVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * @Description 支付宝支付：拼参数签名 + 收通知验签
 * @Author u
 * @Date 2026/10/10
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AlipayPayServiceImpl implements AlipayPayService {

    /** 支付宝说「钱到了」的那个状态值（★ 这是【支付宝报文里】的状态值，不是我们回调方法的 result） */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";

    /**
     * 我们自己的回调方法 handlePayCallback 的 result 取值 —— 只有这个值代表「渠道说付款成功」。
     * ★ 千万别把上面的 TRADE_SUCCESS 当成它传进去：两个常量名字像，值不一样
     *   （一个 `TRADE_SUCCESS`，一个 `SUCCESS`）。
     *   项目里 `AdminRechargeServiceImpl` 和 `DemoDataServiceImpl` 恰好都用一个**叫 TRADE_SUCCESS
     *   但值为 "SUCCESS"** 的常量来传这个参数 —— 照抄那两个调用点，最容易被这一点骗到。
     *   骗到的后果不是编译错，是「钱扣了、账没加、而且支付宝认为投递成功不再重发」。
     */
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String METHOD_PAGE_PAY = "alipay.trade.page.pay";
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final String SUBJECT = "校园一卡通充值";
    private static final String SIGN_TYPE_RSA2 = "RSA2";

    /** ★ 是空格分隔的，不是 T 分隔的：2026-10-10 14:03:11 */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RechargeOrderMapper rechargeOrderMapper;
    private final PayCallbackService payCallbackService;
    private final ObjectMapper objectMapper;

    @Value("${campus.alipay.app-id}")
    private String appId;
    @Value("${campus.alipay.gateway-url}")
    private String gatewayUrl;
    @Value("${campus.alipay.merchant-private-key}")
    private String merchantPrivateKey;
    @Value("${campus.alipay.alipay-public-key}")
    private String alipayPublicKey;
    @Value("${campus.alipay.notify-url}")
    private String notifyUrl;
    @Value("${campus.alipay.return-url:}")
    private String returnUrl;

    @Override
    public AlipayPayVO prepay(String orderNo, LoginUser user) {
        // ★ 这个查询本身就是带学生维度的：别人的单在你这儿就是「不存在」，连存在性都不泄露
        RechargeOrderVO order = rechargeOrderMapper.selectVoByOrderNo(user.getAccount(), orderNo);
        if (order == null) {
            throw new BizException("充值单不存在");
        }
        if (order.getStatus() != OrderStatusConstant.WAITING_FOR_PAYMENT) {
            throw new BizException("该单当前状态不能支付");
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException("该单已过期，请重新下单");
        }

        // ★ TreeMap：让参数表天然是排好序的，打日志看报文时舒服
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("method", METHOD_PAGE_PAY);
        params.put("charset", "UTF-8");
        params.put("sign_type", SIGN_TYPE_RSA2);
        params.put("timestamp", LocalDateTime.now().format(TIMESTAMP));
        params.put("version", "1.0");
        params.put("notify_url", notifyUrl);
        if (!returnUrl.isBlank()) {
            params.put("return_url", returnUrl);
        }
        params.put("biz_content", bizContent(order));
        // ★ 签名放最后：它签的是上面所有参数，自己当然不能参与拼串
        params.put("sign", AlipaySignUtil.sign(params, merchantPrivateKey));

        // ★★ 支付宝要的形状：除 biz_content 外所有参数放 URL 查询串，biz_content 放 POST 体。
        //    网关自己的说法是「请确认 charset 参数放在了 URL 查询字符串中」；
        //    把全部参数都塞进 POST 体，会被判 invalid-signature（实测，别问为什么）。
        String bizContent = params.get("biz_content");
        String action = gatewayUrl + "?" + queryString(params);

        log.info("发起支付宝支付：orderNo={} 金额={}分", orderNo, order.getAmount());
        return new AlipayPayVO(gatewayUrl, params, action, bizContent);
    }

    /**
     * 除 biz_content 外的参数拼成查询串，键值都做 URL 编码。
     * ★ 别和拼串混了：签名的拼串用原文（不编码），传输的查询串必须编码，这是两件事。
     */
    private String queryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if ("biz_content".equals(e.getKey())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        // ★★ 第一件事永远是验签。验不过就一个字段都别信、一条数据库都别碰
        if (!AlipaySignUtil.verify(params, alipayPublicKey)) {
            log.warn("支付宝通知验签失败：out_trade_no={}", params.get("out_trade_no"));
            return "failure";
        }

        String orderNo = params.get("out_trade_no");
        // app_id 只用来排查，不当判据 —— 真正的判据是上面那个签名
        if (!appId.equals(params.get("app_id"))) {
            log.warn("支付宝通知的 app_id 与配置不一致：报文={} 配置={}", params.get("app_id"), appId);
        }

        String tradeStatus = params.get("trade_status");
        if (!TRADE_SUCCESS.equals(tradeStatus)) {
            // 不是成功状态（比如用户还没付完）不是错误，收下就行，别让支付宝重发
            log.info("支付宝通知不是成功状态，忽略：orderNo={} trade_status={}", orderNo, tradeStatus);
            return "success";
        }

        if (amountMismatch(orderNo, params.get("total_amount"))) {
            return "failure";
        }

        // ★ 记账只有一份实现：真通知和演示回调走同一个方法，只是这次给它真的渠道单号
        // ★ 第二个参数必须是 RESULT_SUCCESS（"SUCCESS"），不是上面那个 TRADE_SUCCESS —— 见常量处的注释
        payCallbackService.handlePayCallback(orderNo, RESULT_SUCCESS, params.get("trade_no"), "");
        return "success";
    }

    /**
     * 核对金额：拿库里的金额跟通知里的比。
     * ★ 入账用的是库里的金额，通知里的只用来**核对** —— 对不上说明我们下单时就算错了，
     *   或者有人动了报文，两种情况都不该入账。
     */
    private boolean amountMismatch(String orderNo, String notifiedAmount) {
        if (orderNo == null || orderNo.isBlank()) {
            log.error("支付宝通知里没有 out_trade_no");
            return true;
        }
        RechargeOrder order = rechargeOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.error("支付宝通知里的单号在库里不存在：orderNo={}", orderNo);
            return true;
        }
        BigDecimal notified;
        try {
            notified = new BigDecimal(notifiedAmount == null ? "" : notifiedAmount.trim());
        } catch (NumberFormatException e) {
            log.error("支付宝通知里的金额不是一个合法的数：orderNo={} total_amount={}", orderNo, notifiedAmount);
            return true;
        }
        // ★ 用 compareTo 不用 equals：100.0 和 100.00 是同一笔钱
        if (notified.compareTo(new BigDecimal(yuan(order.getAmount()))) != 0) {
            log.error("支付宝通知金额与订单不一致：orderNo={} 通知={} 库里的={}",
                    orderNo, notifiedAmount, yuan(order.getAmount()));
            return true;
        }
        return false;
    }

    /** 分转元：★ 只在这里转一次，而且必须 toPlainString（不然可能出来 1E+2 这种科学计数法） */
    private String yuan(Long fen) {
        long value = (fen == null) ? 0L : fen;
        return BigDecimal.valueOf(value).movePointLeft(2).toPlainString();
    }

    /** 业务参数：一段 JSON 字符串。★ 用 ObjectMapper 拼，别手写字符串拼 JSON（转义会咬人） */
    private String bizContent(RechargeOrderVO order) {
        Map<String, String> biz = new TreeMap<>();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("total_amount", yuan(order.getAmount()));
        biz.put("subject", SUBJECT);
        biz.put("product_code", PRODUCT_CODE);
        try {
            return objectMapper.writeValueAsString(biz);
        } catch (JsonProcessingException e) {
            throw new BizException("构造支付宝支付参数失败：" + e.getMessage());
        }
    }
}
