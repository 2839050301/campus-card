package com.campus.card.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @Description 通知投递用的 HTTP 客户端。
 * @Author u
 * @Date 2026/10/4
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);   // 连接超时 3 秒
        factory.setReadTimeout(3_000);      // 读超时 3 秒
        return new RestTemplate(factory);
    }
}
