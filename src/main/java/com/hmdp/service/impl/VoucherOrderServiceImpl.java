package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final String STREAM_ORDERS = "stream.orders";
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @Autowired
    private IVoucherOrderService proxy;

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

    @PostConstruct
    public void init() {
        createStreamGroup();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private void createStreamGroup() {
        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.execute("XGROUP",
                        "CREATE".getBytes(StandardCharsets.UTF_8),
                        STREAM_ORDERS.getBytes(StandardCharsets.UTF_8),
                        GROUP_NAME.getBytes(StandardCharsets.UTF_8),
                        "0".getBytes(StandardCharsets.UTF_8),
                        "MKSTREAM".getBytes(StandardCharsets.UTF_8));
                return null;
            });
        } catch (Exception e) {
            log.info("消费者组已存在，无需重复创建");
        }
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_ORDERS, ReadOffset.lastConsumed())
                    );

                    if (records == null || records.isEmpty()) {
                        continue;
                    }

                    handleRecord(records.get(0));
                } catch (Exception e) {
                    log.error("处理订单消息异常", e);
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_ORDERS, ReadOffset.from("0"))
                );

                if (records == null || records.isEmpty()) {
                    break;
                }

                handleRecord(records.get(0));
            } catch (Exception e) {
                log.error("处理 Pending-List 订单消息异常", e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
        handleVoucherOrder(voucherOrder);
        stringRedisTemplate.opsForStream().acknowledge(STREAM_ORDERS, GROUP_NAME, record.getId());
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        String voucherJson = stringRedisTemplate.opsForValue().get("seckill:voucher:" + voucherId);
        SeckillVoucher seckillVoucher;
        if (StrUtil.isBlank(voucherJson)) {
            seckillVoucher = seckillVoucherService.getById(voucherId);
            if (seckillVoucher == null) {
                return Result.fail("秒杀券不存在");
            }
            stringRedisTemplate.opsForValue().set("seckill:voucher:" + voucherId, JSONUtil.toJsonStr(seckillVoucher));
        } else {
            seckillVoucher = JSONUtil.toBean(voucherJson, SeckillVoucher.class);
        }

        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀已经结束");
        }

        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList("seckill:stock:" + voucherId, "seckill:order:" + voucherId, STREAM_ORDERS),
                userId.toString(),
                voucherId.toString(),
                String.valueOf(orderId)
        );

        int r = result == null ? 1 : result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        return Result.ok(orderId);
    }

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

    @Override
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId + ":" + voucherOrder.getVoucherId());

        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
}
