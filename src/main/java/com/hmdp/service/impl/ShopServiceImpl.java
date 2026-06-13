package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryById(Long id) {

        // 1. 组装商铺缓存 key：一个商铺 id 对应 Redis 里的一个 hash。
        // 后续如果要统一 key 前缀，可以替换成 RedisConstants.CACHE_SHOP_KEY + id。
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;

        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();

        Result isCached = queryShopResultFromCache(valueOperations, shopKey);
        if (isCached != null) {
            return isCached;
        }

        //要么在缓存里  不在？
        // 不是NULL == 非法存储标记 ----return Result.fail("店铺不存在");
        // 是NULL？  -- 说明还没操作过对应缓存  走主流程


        //走主流程 -- 高并发  缓存击穿


        //  --- 副线  不是我重建缓存

        if (!tryLock(lockKey)) {

            try {
                //  避免疯狂递归
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            return queryById(id);

        }


        //  --- 主线  重建缓存  OR  正常建立redis路径


        // 抢到了  重建缓存
        // ---  在抢到锁之前  确信没有redis
        Shop shop;

        try {

            //  二次检查  可能有线程此刻刚刚好 重建缓存  不检查会无效SQL
            isCached = queryShopResultFromCache(valueOperations, shopKey);
            if (isCached != null) {
                return isCached;
            }

            //SQL
            shop = getById(id);

            // 解决缓存穿透
            if (shop == null) {
                valueOperations.set(shopKey,
                        "",
                        CACHE_NULL_TTL,
                        TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }

            String shopJson = JSONUtil.toJsonStr(shop);

            // 解决缓存雪崩
            valueOperations.set(shopKey, shopJson, CACHE_SHOP_TTL + RandomUtil.randomLong(1, 11), TimeUnit.MINUTES);
        } finally {
            // 不管你是否重建立最后必须释放  防止堵塞？
            unlock(lockKey);
        }


        // 缓存创建或重建成功
        return Result.ok(shop);


    }


    // 判断店铺到底缓存了吗  包括缓存穿透
    private static Result queryShopResultFromCache(ValueOperations<String, String> valueOperations, String shopKey) {
        String shopJson = valueOperations.get(shopKey);

        if (StrUtil.isNotBlank(shopJson)) {

            Shop shop = JSONUtil.toBean(shopJson, Shop.class);

            return Result.ok(shop);
        }


        if (shopJson != null) {
            return Result.fail("店铺不存在");
        }
        return null;
    }

    @Override
    public Result update(Shop shop) {


        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }

        String shopKey = CACHE_SHOP_KEY + shop.getId();


        boolean success = updateById(shop);
        if (!success) {
            return Result.fail("更新店铺失败");
        }

        stringRedisTemplate.delete(shopKey);

        return Result.ok();
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
