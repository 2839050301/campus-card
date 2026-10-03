package com.campus.card.common;

import com.campus.card.vo.LoginUser;


/**
 * @Description
 * @Author u
 * @Date 2026/9/30
 */
public class UserContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_ADMIN = "ADMIN";
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

    public static LoginUser requireStudent() {
        LoginUser me = UserContext.verifyLogin();
        if (!ROLE_STUDENT.equals(me.getRole())) {
            throw new BizException("该接口仅学生可用");
        }
        return me;
    }

    public static LoginUser requireAdmin() {
        LoginUser me = verifyLogin();
        if (!ROLE_ADMIN.equals(me.getRole())) {
            throw new BizException("该接口仅管理员可用");
        }
        return me;
    }

}
