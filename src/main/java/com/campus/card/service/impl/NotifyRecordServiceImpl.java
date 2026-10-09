package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.card.constant.NotifyStatusConstant;
import com.campus.card.entity.NotifyRecord;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.NotifyRecordMapper;
import com.campus.card.service.NotifyRecordService;
import com.campus.card.util.SignUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description
 * @Author u
 * @Date 2026/10/4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyRecordServiceImpl implements NotifyRecordService {

    /**
     * response_body 最多存这么多字符，防止对方返回一个大 HTML 错误页把 TEXT 撑满
     */
    private static final int RESPONSE_MAX_LEN = 500;

    private final ObjectMapper objectMapper;
    private final NotifyRecordMapper notifyRecordMapper;
    private final RestTemplate restTemplate;
    /**
     * 通知地址
     */
    @Value("${campus.pay.notify-url}")
    private String notifyUrl;
    /**
     * 签名密钥
     */
    @Value("${campus.pay.notify-secret}")
    private String notifySecret;

    /**
     * 退避秒数表，逗号分隔
     */
    @Value("${campus.pay.retry-backoff-seconds}")
    private String backoffCsv;


    /**
     * 解析后的退避表：第N次失败等backoff[n-1]秒
     */
    private int[] backoff;

    @PostConstruct
    void initBackoff() {
        String[] parts = backoffCsv.split(",");
        this.backoff = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            this.backoff[i] = Integer.parseInt(parts[i].trim());
        }
        log.info("通知退避表已加载：最多重试 {} 次，档位 {} 秒", backoff.length, backoffCsv);

    }

    public void createPending(RechargeOrder order){
        NotifyRecord nr = new NotifyRecord();
        nr.setOrderNo(order.getOrderNo());
        nr.setNotifyUrl(notifyUrl);
        nr.setRequestBody(buildBody(order));
        nr.setResponseBody("");
        nr.setStatus(NotifyStatusConstant.WAIT);
        nr.setNotifyTimes(0);
        nr.setNextRetryTime(LocalDateTime.now());
        notifyRecordMapper.insert(nr);
    }

    /**
     * 交付投递
     *
     * @param record
     */
   public boolean deliver(NotifyRecord record) {
        String requestBody = record.getRequestBody();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = SignUtil.hmacSha256(notifySecret, timestamp + "." + requestBody);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Campus-Timestamp", timestamp);
        headers.set("X-Campus-Sign", sign);

        String responseBody;
        boolean ok;
        try {
            String resp = restTemplate.postForObject(
                    record.getNotifyUrl(),
                    new HttpEntity<>(requestBody, headers),
                    String.class);
            responseBody = resp == null ? "" : resp;
            ok = isOk(responseBody);
        } catch (RestClientException e) {
            ok = false;
            responseBody = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("通知投递失败 orderNo={} url={} 原因={}",
                    record.getOrderNo(), record.getNotifyUrl(), responseBody);
        }
        record.setResponseBody(truncate(responseBody));
        record.setNotifyTimes(record.getNotifyTimes() + 1);
        if (ok) {
            record.setStatus(NotifyStatusConstant.SUCCESS);
            record.setNextRetryTime(null);
            log.info("通知投递成功 orderNo={},第{}次", record.getOrderNo(), record.getNotifyTimes());
        } else if (record.getNotifyTimes() > backoff.length) {
            record.setStatus(NotifyStatusConstant.FAIL);
            record.setNextRetryTime(null);
            log.error("通知终端最终失败，需人工介入：orderNo={} 通知已投 {} 次全部失败，url={}",
                    record.getOrderNo(), record.getNotifyTimes(), record.getNotifyUrl());
        } else {
            int delay =backoff[record.getNotifyTimes() - 1];
            record.setNextRetryTime(LocalDateTime.now().plusSeconds(delay));
            log.warn("通知将在 {} 秒后重试，orderNo={}，第 {} 次失败",
                    delay, record.getOrderNo(), record.getNotifyTimes());
        }
        notifyRecordMapper.update(null, new LambdaUpdateWrapper<NotifyRecord>()
                .eq(NotifyRecord::getId, record.getId())
                .set(NotifyRecord::getNotifyTimes, record.getNotifyTimes())
                .set(NotifyRecord::getResponseBody, record.getResponseBody())
                .set(NotifyRecord::getStatus, record.getStatus())
                .set(NotifyRecord::getNextRetryTime, record.getNextRetryTime()));

        return ok;
    }


    private boolean isOk(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            return node.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            return false; //对方返回了 HTML / 空串 / 非JSON
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= RESPONSE_MAX_LEN ? s : s.substring(0, RESPONSE_MAX_LEN);
    }

    @Override
    public int deliverDue(int limit) {
        List<NotifyRecord> due = notifyRecordMapper.selectDue(LocalDateTime.now(), limit);
        if (due.isEmpty()) {
            return 0;
        }
        for (NotifyRecord r : due) {
            try {
                deliver(r);
            } catch (Exception e) {
                log.error("通知投递出现未预期异常 id={} orderNo={}", r.getId(), r.getOrderNo(), e);
            }
        }
        return due.size();
    }

    @Override
    public int requeue(String orderNo) {
        return notifyRecordMapper.update(null,new LambdaUpdateWrapper<NotifyRecord>()
                .eq(NotifyRecord::getOrderNo, orderNo)
                .set(NotifyRecord::getStatus, NotifyStatusConstant.WAIT)
                .set(NotifyRecord::getNotifyTimes, 0)
                .set(NotifyRecord::getNextRetryTime, LocalDateTime.now()));
    }

    /**
     * 上报文体。在入账那一刻就拼好并落库，重试时原样重发。
     *
     * @param order 订单
     * @return {@link String }
     */
    private String buildBody(RechargeOrder order) {
        return "{\"orderNo\":\"" + order.getOrderNo()
                + "\",\"cardNo\":\"" + order.getCardNo()
                + "\",\"studentNo\":\"" + order.getStudentNo()
                + "\",\"amount\":" + order.getAmount()
                + ",\"status\":" + order.getStatus()
                + ",\"payTime\":\"" + order.getPayTime() + "\"}";
    }

}
