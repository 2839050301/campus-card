package com.campus.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String account;

    @JsonIgnore
    private String password;
    private String name;
    private String role;    //STUDENT  or  ADMIN
    private String college;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
