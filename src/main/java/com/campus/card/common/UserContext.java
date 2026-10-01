package com.campus.card.common;

import com.campus.card.vo.LoginUser;

/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
public class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user){
        HOLDER.set(user);
    }
    public static LoginUser get(){
        return HOLDER.get();
    }

    public static LoginUser verifyLogin(){
        LoginUser user = HOLDER.get();
        if(user == null){
            throw new BizException(ErrCode.UNAUTHORIZED);
        }
        return user;
    }
    public static String getAccount(){
        return verifyLogin().getAccount();
    }
    public static void remove(){
        HOLDER.remove();
    }

}
