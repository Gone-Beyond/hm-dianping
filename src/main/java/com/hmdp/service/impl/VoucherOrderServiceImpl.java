package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final RedissonClient redissonClient;

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService,
                                   RedisIdWorker redisIdWorker,
                                   RedissonClient redissonClient) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.redissonClient = redissonClient;
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (voucherId == null) {
            return Result.fail("Voucher id cannot be null");
        }

        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("Seckill voucher does not exist");
        }

        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now)) {
            return Result.fail("Seckill has not started");
        }

        if (seckillVoucher.getEndTime().isBefore(now)) {
            return Result.fail("Seckill has ended");
        }

        if (seckillVoucher.getStock() < 1) {
            return Result.fail("Insufficient stock");
        }


        //====================redisson加锁=======================
        Long userId = UserHolder.getUser().getId();
        RLock lock = redissonClient.getLock("lock:order:" + userId + ":" + voucherId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            return Result.fail("Duplicate orders are not allowed");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

        //····················································


    }

    //抽出下单业务方法
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("Duplicate orders are not allowed");
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            return Result.fail("Insufficient stock");
        }

        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder()
                .setId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId);

        boolean orderSaved = save(voucherOrder);
        if (!orderSaved) {
            throw new IllegalArgumentException("Failed to save voucher order");
        }

        return Result.ok(orderId);
    }
}
