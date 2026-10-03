package com.campus.card.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @Description 全局 JSON 时间格式：LocalDateTime 一律输出「epoch 毫秒」
 * @Author u
 * @Date 2026/10/1
 *
 * 为什么必须这么干：
 *   前端 js/common.js 里两个工具函数的实现是
 *       dt(ts)       -> new Date(Number(ts))
 *       countdown(d) -> Number(d) - Date.now()
 *   它们要的是「毫秒数字」。而 Jackson 对 LocalDateTime 的默认行为是输出
 *   ISO 字符串 "2026-09-30T12:50:44"，Number("2026-09-30T12:50:44") === NaN，
 *   于是前端渲染出 "NaN-NaN-NaN NaN:NaN:NaN"。
 *
 * 为什么放全局，而不是在每个 VO 字段上写 @JsonFormat：
 *   接口有 16 个，时间字段散落在 order / flow / notify / recon 各种 VO 里。
 *   写一处管所有，以后新增 VO 不用记得加注解，也不会漏。
 *
 * 为什么不动 LocalDate：
 *   billDate 是「账单日期」，前端当纯文本显示（"2026-09-30"），
 *   LocalDate 的默认输出正好就是这个格式。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeToMillis() {
        return builder -> builder.serializerByType(LocalDateTime.class, new LocalDateTimeToMillisSerializer());
    }

    /** LocalDateTime -> epoch 毫秒 */
    static class LocalDateTimeToMillisSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeNumber(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
    }
}
