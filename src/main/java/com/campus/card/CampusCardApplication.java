package com.campus.card;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@SpringBootApplication
@MapperScan("com.campus.card.mapper")
@EnableScheduling
public class CampusCardApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusCardApplication.class, args);
    }
}