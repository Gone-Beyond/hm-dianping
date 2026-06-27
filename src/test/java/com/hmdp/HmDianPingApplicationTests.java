package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Test
    void loadShopData() {

        List<Shop> list = shopService.list();

        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(shop -> shop.getTypeId()));

        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {

            Long typeId = entry.getKey();

            String key = SHOP_GEO_KEY  + typeId;

            List<Shop> shops = entry.getValue();

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());

            for (Shop shop : shops) {

                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));

            }

            stringRedisTemplate.opsForGeo().add(key, locations);

        }

    }



    @Test
    void testHyperLogLog() {

        String key = "hll:test";

        String[] users = new String[1000];

        for (int i = 0; i < 1000000; i++) {

            users[i % 1000] = "user_" + i;

            if (i % 1000 == 999) {
                stringRedisTemplate.opsForHyperLogLog().add(key, users);
            }

        }


        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);

        System.out.println("HyperLogLog统计结果："+ count);

    }












}