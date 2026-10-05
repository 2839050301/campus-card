package com.campus.card.task;

import com.campus.card.service.NotifyRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Description
 * @Author u
 * @Date 2026/10/4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRetryTask {

    /**
     * 一轮最多处理多少条 防止一次捞太多把线程占住
     */
    private static final int BATCH_SIZE = 100;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final NotifyRecordService notifyRecordService;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    public void deliverDueNotification() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        RLock lock = redissonClient.getLock("lock:notify:deliver");
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            int n = notifyRecordService.deliverDue(BATCH_SIZE);
            if (n > 0) {
                log.info("本轮投递通知{}条", n);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("通知投递异常", e);
        } finally {
            if(locked&&lock.isHeldByCurrentThread()){
                lock.unlock();
            }
            running.set(false);
        }
    }
}
