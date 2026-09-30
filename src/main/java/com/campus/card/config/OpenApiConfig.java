package com.campus.card.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusCardOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("校园一卡通充值结算系统")
                .version("1.0.0")
                .description("""
                        充值结算 + 通道路由 + 异步回调 + 通知重试 + 三方对账。

                        统一返回体：成功 {"success":true,"data":...}；
                        失败 {"success":false,"errCode":"...","errMsg":"..."}。
                        金额一律用「分」，整数。
                        """));
    }
}
