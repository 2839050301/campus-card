package com.campus.card.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.card.common.BizException;
import com.campus.card.constant.LimitConstant;
import com.campus.card.constant.OrderStatusConstant;
import com.campus.card.dto.RechargeCreateReq;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.service.RechargeOrderService;
import com.campus.card.util.OrderNoGenerator;
import com.campus.card.vo.LoginUser;
import com.campus.card.vo.RechargeOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @Description
 * @Author u
 * @Date 2026/10/2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeOrderServiceImpl implements RechargeOrderService {



    /* 充值过期时间 15min */
    private static final int EXPIRE_MINUTES = 15;

    private final RechargeOrderMapper  rechargeOrderMapper;
    private final OrderNoGenerator orderNoGenerator;

    /**
     * ★ 这里刻意【不加】@Transactional，三个原因：
     * 1) 本方法只有一条写语句（下面那次 insert），单条 INSERT 本身就是原子的，事务买不到额外保证；
     *    真正保证幂等的是 t_recharge_order.uk_request_no 唯一索引。
     * 2) 加了事务，读视图会跨过那次「阻塞后失败」的 INSERT：事务里第一条 SELECT（sumPaidAmount）
     *    就把快照定死了，catch 里回查看不到赢家刚提交的行 → 第 3 层幂等失效。
     * 3) 加了事务，锁也会跨过去：失败的 INSERT 会在重复的索引记录上留下共享锁 S，再 SELECT ... FOR UPDATE
     *    要升级成排他锁 X，多个输家都持 S 抢 X → InnoDB 直接判定死锁
     *    （Deadlock found when trying to get lock; try restarting transaction）。
     * 而 MySQL 的重复键检查是「等赢家提交后才报 1062」，所以报错时那行一定已经提交 ——
     * autocommit 下紧随其后的 SELECT 会重新拍快照，必然看得到它。
     */
    @Override
    public RechargeOrderVO createOrder(LoginUser user, RechargeCreateReq req) {
        //验证幂等
        //同一个requestNo再来一次直接返回刚刚那一单
        RechargeOrder isExist = rechargeOrderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getRequestNo, req.getRequestNo()));
        if(isExist!=null){
            log.info("重复下单，返回原单 requestNo={} orderNo={}", req.getRequestNo(), isExist.getOrderNo());
            return toVO(isExist, user.getName());
        }
        //充值金额
        Long amount = req.getAmount();
        //
        if (amount < LimitConstant.MIN_AMOUNT) {
            throw new BizException("单笔充值不能低于1元");
        }
        if (amount > LimitConstant.SINGLE_LIMIT) {
            throw new BizException("单笔充值不能超过1000元");
        }
        if (amount % LimitConstant.AMOUNT_STEP != 0) {
            throw new BizException("充值金额必须精确到元");
        }

        Long payToday = rechargeOrderMapper.sumPaidAmount(user.getAccount(), LocalDate.now());
        long alreadyPay = payToday == null ? 0 : payToday;
        if(alreadyPay+amount>LimitConstant.DAILY_LIMIT){
            throw new BizException("超出单日累计充值额度（2000元）");
        }


        //插入
        RechargeOrder order = new RechargeOrder();

        order.setOrderNo(orderNoGenerator.generateOrderNo("R"));
        order.setRequestNo(req.getRequestNo());
        order.setStudentNo(user.getAccount());
        // ★ 卡号来自 token 里的登录用户，不是 requestNo（requestNo 是幂等号，不是卡号）
        order.setCardNo(user.getCardNo());
        order.setAmount(amount);
        order.setPayMethod(req.getPayMethod());
        order.setStatus(OrderStatusConstant.WAITING_FOR_PAYMENT);
        order.setChannelOrderNo("");
        order.setCreateTime(LocalDateTime.now());
        order.setExpireTime(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
        order.setBillDate(LocalDate.now());
        order.setRemark("");


        try {
            rechargeOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // ★ 这里【不要】写 .last("FOR UPDATE")：普通 SELECT 就够。
            //   autocommit 下这条语句是新事务、重新拍快照，而报 1062 时赢家一定已提交 → 必然看得到。
            //   加了 FOR UPDATE 反而要去抢 X 锁，和输家自己刚留下的 S 锁形成升级死锁。
            RechargeOrder onceAgain = rechargeOrderMapper.selectOne(new LambdaQueryWrapper<RechargeOrder>()
                    .eq(RechargeOrder::getRequestNo, req.getRequestNo()));
            if(onceAgain==null){
                throw e;
            }
            log.info("唯一索引兜底命中 requestNo={} orderNo={}", req.getRequestNo(), onceAgain.getOrderNo());
            return toVO(onceAgain, user.getName());
        }

        log.info("创建充值单成功 orderNo={} requestNo={} amount={}分",
                order.getOrderNo(), order.getRequestNo(), order.getAmount());
        return toVO(order, user.getName());
    }

    @Override
    public IPage<RechargeOrderVO> pageMyOrders(LoginUser user, Integer status, long current, long size) {
        return rechargeOrderMapper.selectVoPage(new Page<>(current,size),user.getAccount(),status);
    }

    @Override
    public RechargeOrderVO getMyOrder(LoginUser user, String orderNo) {
        RechargeOrderVO vo = rechargeOrderMapper.selectVoByOrderNo(user.getAccount(), orderNo);
        if (vo == null) {
            throw new BizException("充值单不存在");
        }
        return vo;
    }


    private RechargeOrderVO toVO(RechargeOrder o, String studentName) {
        RechargeOrderVO vo = new RechargeOrderVO();
        vo.setOrderNo(o.getOrderNo());
        vo.setRequestNo(o.getRequestNo());
        vo.setStudentNo(o.getStudentNo());
        vo.setStudentName(studentName);
        vo.setCardNo(o.getCardNo());
        vo.setAmount(o.getAmount());
        vo.setPayMethod(o.getPayMethod());
        vo.setStatus(o.getStatus());
        vo.setChannelOrderNo(o.getChannelOrderNo());
        vo.setCreateTime(o.getCreateTime());
        vo.setPayTime(o.getPayTime());
        vo.setCloseTime(o.getCloseTime());
        vo.setExpireTime(o.getExpireTime());
        vo.setBillDate(o.getBillDate());
        vo.setRemark(o.getRemark());
        // ★ 这里是【新建单】的返回路径（createOrder 特有）：走到这里说明单子刚插进去，
        //   此刻必然还没有通知记录 —— 通知记录是入账成功那一瞬才写的（Day 6 的
        //   createPending），而这张单的状态是 0 待支付。所以固定给「0 待通知」是【对的】。
        // ★ 查询路径（pageMyOrders / getMyOrder）不走这里，它们走 RechargeOrderMapper.xml，
        //   那三个字段由 LEFT JOIN t_notify_record 取真值。两条路径别混。
        vo.setNotifyStatus(0);
        vo.setNotifyTimes(0);
        return vo;
    }
}
