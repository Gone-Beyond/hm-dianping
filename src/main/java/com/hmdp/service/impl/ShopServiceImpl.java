package com.hmdp.service.impl;

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

        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();

        Result isCached = queryShopResultFromCache(valueOperations, shopKey);
        if (isCached != null) {
            return isCached;
        }

        Shop shop = getById(id);

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
}
