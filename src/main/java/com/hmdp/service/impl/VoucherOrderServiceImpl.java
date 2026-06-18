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

import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.annotation.PostConstruct;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    // 前
    // 预加载lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 中间件 控制并发  +  承前启后
    // 订单阻塞队列
    private static final BlockingDeque<VoucherOrder> ORDER_WAITING_QUEUE = new LinkedBlockingDeque<>(1024 * 1024);

    // 异步下单线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 代理对象
    private IVoucherOrderService proxy;

    // FIXME 为了“后” 做准备  异步实现写与改的完全分离---关键  中间件  堵塞队列 + 关键方法 handleVoucherOrder(order);
    // FIXME 方法 handleVoucherOrder(order); 就是实现写与改的  <小插曲> 事务是否生效的问题
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(
                () -> {
                    while (true) {
                        try {
                            VoucherOrder voucherOrder = ORDER_WAITING_QUEUE.take();
                            // 拿到单子了
                            handleVoucherOrder(voucherOrder);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            log.error("处理订单异常", e);
                        }
                    }
                });
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final RedissonClient redissonClient;

    public VoucherOrderServiceImpl(StringRedisTemplate stringRedisTemplate,
                                   ISeckillVoucherService seckillVoucherService,
                                   RedisIdWorker redisIdWorker,
                                   RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.redissonClient = redissonClient;
    }

    // 不用管其他工具的实现细节 --- 就是分流 + 异步  （底层不用管）
    @Override
    public Result seckillVoucher(Long voucherId) {

        // ========================前===================================
        // 1. 从 Redis 缓存中获取秒杀券详情并校验时间（有穿透问题懒得改了  小问题）
        String voucherJson = stringRedisTemplate.opsForValue().get("seckill:voucher:" + voucherId);
        SeckillVoucher seckillVoucher = null;
        if (cn.hutool.core.util.StrUtil.isBlank(voucherJson)) {
            // 缓存未命中，兜底查数据库
            seckillVoucher = seckillVoucherService.getById(voucherId);
            if (seckillVoucher == null) {
                return Result.fail("秒杀券不存在");
            }
            // 写入 Redis 缓存
            stringRedisTemplate.opsForValue().set("seckill:voucher:" + voucherId, cn.hutool.json.JSONUtil.toJsonStr(seckillVoucher));
        } else {
            seckillVoucher = cn.hutool.json.JSONUtil.toBean(voucherJson, SeckillVoucher.class);
        }

        // 2. 校验时间
        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀已经结束");
        }

        Long userId = UserHolder.getUser().getId();

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,

                Arrays.asList("seckill:stock:" + voucherId, "seckill:order:" + voucherId),
                userId.toString()
        );


        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // ===========================================================


        // ========================中间件===================================

        long orderId = redisIdWorker.nextId("order");

        // 后
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        ORDER_WAITING_QUEUE.add(

                new VoucherOrder()
                        .setId(orderId)
                        .setUserId(userId)
                        .setVoucherId(voucherId)

        );

        // ===========================================================

        // ========================后（）===================================


            // 4. 获取代理对象并赋值给成员变量，传递给子线程
            // TODO 非常妙的设计  1 proxy因为是类成员变量  所以局部赋值却全局可用！
            //  2 异步线程类创建之后就一直再跑  proxy却 seckillVoucher执行时才有数值 为什么没有空指针？
            //  --- 因为  堵塞堆列的等待机制   proxy在队列有东西之前不会使用  所以这里是不是有BUG？
            //  proxy赋值应该在ORDER_WAITING_QUEUE.add(voucherOrder); 之前吧？ (已经修改)
            // TODO 新问题  每次新线程来了都重新给proxy赋值合适吗  合适


        // ===========================================================


        // 5. 提前返回订单 ID
        return Result.ok(orderId);


    }

    // 抽出下单业务方法
    // 原来父亲线程拿来用的createVoucherOrder（可以UserHolder）
    // 没用了
    /*@Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("不允许重复下单");
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            return Result.fail("库存不足");
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
    }*/


    //  儿子异步线程拿来用的createVoucherOrder
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if (count > 0) {
            return;
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!stockUpdated) {
            return;
        }

        save(voucherOrder);
    }

    // 异步下单业务方法
    @Override
    @Transactional
    public void handleVoucherOrder(VoucherOrder voucherOrder) {


        // 1. 获取用户 id
        Long userId = voucherOrder.getUserId();

        // TODO 等会提问
        // 2. 创建锁对象（注意：即便后台消费是单线程，但在集群部署下，不同的 Tomcat 实例依然需要分布式锁）
        RLock lock = redissonClient.getLock("lock:order:" + userId + ":" + voucherOrder.getVoucherId());

        // 3. 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 如果没拿到锁，说明此用户在别的实例上已经有在执行的下单任务了，直接返回
            log.error("不允许重复下单");
            return;
        }

        try {
            // 4. 调用事务方法保存订单

            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 5. 释放锁
            lock.unlock();
        }
    }


}
