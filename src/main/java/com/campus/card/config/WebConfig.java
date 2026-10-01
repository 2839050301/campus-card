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
                .excludePathPatterns("/api/mock/**");
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
