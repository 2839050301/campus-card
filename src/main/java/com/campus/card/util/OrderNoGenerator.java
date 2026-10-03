package com.campus.card.util;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @Description 充值单号生成器：R + 日期 + 3 位自增 + 4 位随机，如 R20261001001A7K3
 * @Author u
 * @Date 2026/10/2
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {
    private static final String KEY="campus:seq:%s:%s";
    private static final char[] ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final StringRedisTemplate stringRedisTemplate;
    private static final SecureRandom RANDOM = new SecureRandom();
    //生成订单号
    public String generateOrderNo(String prefix) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = String.format(KEY,prefix,day);

        Long seq =stringRedisTemplate.opsForValue().increment(key);
        if(seq!=null&&seq==1L){
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        }
        return String.format("%s%s%03d%s",prefix,day,seq,random4());


    }
    private static String random4(){
        char[] buf = new char[4];
        for(int i=0;i<4;i++){
            buf[i] = ALPHABET[(RANDOM.nextInt(ALPHABET.length))];
        }
        return new String(buf);
    }

}
