package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR =
            Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 写入普通缓存，并设置真实 TTL
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(value),
                time,
                unit
        );
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)))
                .build();

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 使用空值缓存解决缓存穿透
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit
    ) {
        // 1. 拼接完整 Redis Key
        String key = keyPrefix + id;

        // 2. 查询 Redis
        String json = stringRedisTemplate.opsForValue().get(key);

        // 3. 命中正常缓存
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 4. 命中空值缓存
        if (json != null) {
            return null;
        }

        // 5. Redis 未命中，调用传入的数据库查询函数
        R result = dbFallback.apply(id);

        // 6. 数据库中也不存在，写入空字符串
        if (result == null) {
            stringRedisTemplate.opsForValue().set(
                    key,
                    "",
                    CACHE_NULL_TTL,
                    TimeUnit.MINUTES
            );

            return null;
        }

        // 7. 数据库中存在，写入正常缓存
        set(key, result, time + RandomUtil.randomLong(1, 11), unit);

        // 8. 返回数据库查询结果
        return result;
    }

    /**
     * 使用互斥锁解决热点 key 的缓存击穿。
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            String lockPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit
    ) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        if (json != null) {
            return null;
        }

        String lockKey = lockPrefix + id;

        if (!tryLock(lockKey)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return queryWithMutex(keyPrefix, lockPrefix, id, type, dbFallback, time, unit);
        }

        try {
            json = stringRedisTemplate.opsForValue().get(key);

            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }

            if (json != null) {
                return null;
            }

            R result = dbFallback.apply(id);

            if (result == null) {
                stringRedisTemplate.opsForValue().set(
                        key,
                        "",
                        CACHE_NULL_TTL,
                        TimeUnit.MINUTES
                );
                return null;
            }

            set(key, result, time + RandomUtil.randomLong(1, 11), unit);
            return result;
        } finally {
            unlock(lockKey);
        }
    }

    /**
     * 使用逻辑过期解决热点 key 的缓存击穿。
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix,
            String lockPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit
    ) {
        String key = keyPrefix + id;
        String lockKey = lockPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R result = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            return result;
        }

        if (tryLock(lockKey)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    String latestJson = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isBlank(latestJson)) {
                        return;
                    }

                    RedisData latestData = JSONUtil.toBean(latestJson, RedisData.class);

                    if (latestData.getExpireTime().isAfter(LocalDateTime.now())) {
                        return;
                    }

                    R latest = dbFallback.apply(id);
                    setWithLogicalExpire(key, latest, time, unit);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        return result;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }



}
