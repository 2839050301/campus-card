package com.campus.card.config;

import com.campus.card.interceptor.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@RequiredArgsConstructor
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final LoginInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login")
                .excludePathPatterns("/api/ping")
                .excludePathPatterns("/api/mock/**")
                //   支付宝的服务器没有项目token，通知必须能打进来
                //   放行不等于不设防 —— 这道门由「验签」把守，比 token 更严格
                //   只放这两个具体路径，不要图省事写 "/api/pay/**"：
                //    prepay 是学生端接口，它得继续要 token（写在通配符里就一起放出去了）
                .excludePathPatterns("/api/pay/alipay/notify")
                .excludePathPatterns("/api/pay/alipay/return");
    }

    @Override
    public void addCorsMappings(CorsRegistry  registry) {
        registry.addMapping("/**")
                // ★ 不能用 allowedOrigins("*")
                //   当 allowCredentials(true) 时，Spring 会在 AbstractHandlerMapping.getHandler()
                //   里抛 IllegalArgumentException，导致【每一个请求】都 500 —— 连 swagger 页面都打不开。
                //   allowedOriginPatterns 支持 * 通配，且允许与 allowCredentials(true) 共存。
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

    }
}
