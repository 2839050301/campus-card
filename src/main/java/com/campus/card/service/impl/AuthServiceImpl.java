package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.card.common.BizException;
import com.campus.card.dto.LoginReq;
import com.campus.card.entity.CardAccount;
import com.campus.card.entity.User;
import com.campus.card.mapper.CardAccountMapper;
import com.campus.card.mapper.UserMapper;
import com.campus.card.service.AuthService;
import com.campus.card.util.JwtUtil;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * @Description
 * @Author u
 * @Date 2026/10/1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final CardAccountMapper cardAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STUDENT = "STUDENT";

    @Override
    public LoginVO login(LoginReq req) {
        String account = req.getAccount().trim();
        String password = req.getPassword().trim();

        //查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (user == null) {
            throw new BizException("账户不存在");
        }
        //对比密码
        boolean isAdmin = ROLE_ADMIN.equals(user.getRole());
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(isAdmin?"工号或密码错误":"学号或密码错误");
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setAccount(user.getAccount());
        loginUser.setName(user.getName());
        loginUser.setRole(user.getRole());
        loginUser.setCollege(user.getCollege());

        if(ROLE_STUDENT.equals(user.getRole())){
            CardAccount cardAccount = cardAccountMapper.selectOne(
                    new LambdaQueryWrapper<CardAccount>().eq(CardAccount::getStudentNo, account));
            if(cardAccount == null){
                throw new BizException("账户不存在");
            }
            if(cardAccount.getStatus()!=1){
                throw new BizException("该一卡通账户已被冻结，请联系结算中心");
            }
            loginUser.setCardNo(cardAccount.getCardNo());
        }
        //签发token
        String token = jwtUtil.create(loginUser);
        return new LoginVO(token, loginUser);

    }
}
