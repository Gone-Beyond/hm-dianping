package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

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
    private final CacheClient cacheClient;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate, CacheClient cacheClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
    }


    @Override
    public Result queryById(Long id) {

        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);

    }


    private Shop queryWithLogicalExpire(Long id) {
        return cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY,
                LOCK_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
    }



    // 也是异步重建缓存的时候会用的
    //逻辑过期的预热方法 ---先加入redis 保证一定有
    public void saveShopWithLogicalExpire(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        cacheClient.setWithLogicalExpire(
                CACHE_SHOP_KEY + id,
                shop,
                expireSeconds,
                TimeUnit.SECONDS
        );
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

}
