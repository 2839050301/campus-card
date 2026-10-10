package com.campus.card.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.campus.card.common.BizException;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.service.AlipayPayService;
import com.campus.card.service.PayCallbackService;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * @Description 支付宝支付：由官方 SDK 生成支付表单 + 收通知验签
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
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final String SUBJECT = "校园一卡通充值";
    /** RSA2 就是 SHA256withRSA —— 和 AlipaySdkConfig 里那个值必须一致 */
    private static final String SIGN_TYPE_RSA2 = "RSA2";
    /** ★ 验签时按这个字符集取字节，必须和报文里的 charset 一致 */
    private static final String CHARSET_UTF8 = "UTF-8";

    private final RechargeOrderMapper rechargeOrderMapper;
    private final PayCallbackService payCallbackService;
    private final ObjectMapper objectMapper;
    /** ★ 官方 SDK 客户端：拼参数、签名、验签都归它（Bean 在 config/AlipaySdkConfig） */
    private final AlipayClient alipayClient;

    @Value("${campus.alipay.app-id}")
    private String appId;
    @Value("${campus.alipay.gateway-url}")
    private String gatewayUrl;
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

        // ★ 参数表、签名、以及「哪些参数放查询串、biz_content 放 POST 体」这些事，
        //   全部交给官方 SDK —— 我们手拼时踩过的两个坑，SDK 内部早就定死了。
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        if (!returnUrl.isBlank()) {
            request.setReturnUrl(returnUrl);
        }
        request.setBizContent(bizContent(order));

        String formHtml;
        try {
            // ★ pageExecute 只在本地拼表单 + 签名，不发 HTTP 请求（所以它不需要网络，单测也能跑）
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            formHtml = response.getBody();
        } catch (AlipayApiException e) {
            // 拼不出来基本只有一种原因：私钥没配 / 配错了。这是技术异常，按 Day 5 的规矩往上抛
            throw new BizException("调用支付宝 SDK 生成支付表单失败：" + e.getMessage());
        }

        log.info("发起支付宝支付：orderNo={} 金额={}分（表单由官方 SDK 生成）", orderNo, order.getAmount());
        return new AlipayPayVO(gatewayUrl, formHtml);
    }

    /**
     * 用「支付宝公钥」验签 —— 交给官方 SDK。
     * ★★ 上行和下行拼串规则不一样，这里就是当年真栽过的那个坑：
     *    我们【发出去】的请求要带 sign_type 一起签；支付宝【回来】的报文要连 sign_type 一起剔。
     *    官方 rsaCheckV1() 内部头两行就是 params.remove("sign") + params.remove("sign_type")。
     * ★ 它【就地改】传进来的这张表：调用之后 params 里就没有 sign / sign_type 了。
     *   下面接着用 out_trade_no / trade_status / trade_no / total_amount 都没问题，
     *   但别再把它当「原件」去写日志或存库。
     * ★ 任何异常都算「没验过」：签名格式不对、公钥粘错、字符集不匹配，在这里是同一件事。
     */
    private boolean verify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params, alipayPublicKey, CHARSET_UTF8, SIGN_TYPE_RSA2);
        } catch (AlipayApiException e) {
            log.warn("支付宝通知验签异常：{}", e.getMessage());
            return false;
        }
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        // ★★ 第一件事永远是验签。验不过就一个字段都别信、一条数据库都别碰
        if (!verify(params)) {
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
        try {
            payCallbackService.handlePayCallback(orderNo, RESULT_SUCCESS, params.get("trade_no"), "");
        } catch (Exception e) {
            // ★★ 异常路径也必须显式回 "failure"，不能让异常漏到全局异常处理器。
            //    漏出去的后果：@RestControllerAdvice 把它包成 Result JSON 返回 ——
            //    支付宝看到不是纯文本 "success" 会当失败重试，碰巧结果一样，但那是运气不是设计。
            //    显式回 "failure" 的语义是「这次没入账，请再投」：
            //    - 技术性异常（DB 抖动、锁等待超时）：事务已回滚、钱没入账，重试就是第二次机会；
            //    - 永久性失败（单号在库里不存在）：重试也不会成功，但支付宝重试有上限，
            //      停发后这笔钱留在支付宝侧，对账引擎会按 CHANNEL_ONLY（长款）把它捞出来人工处理。
            //    反面是吞掉异常回 "success" —— 钱在支付宝扣了、我们一分没记，而且永不重发，钱就永久丢了。
            log.error("支付宝通知处理失败，回 failure 让支付宝重试：orderNo={}", orderNo, e);
            return "failure";
        }
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
