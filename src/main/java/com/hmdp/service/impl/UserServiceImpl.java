package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {



    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {



        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }


        String code = RandomUtil.randomNumbers(6);



        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );

        log.info("手机号: {}, 验证码: {}", phone, code);


        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        String code = loginForm.getCode();




        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);



        if (StrUtil.isBlank(cacheCode) || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }



        // 删除验证码，防止重复使用
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);




        //  抄的  我不会plus写法
        // SQL
        User user = query()
                .eq("phone", phone)
                .one();


        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

            //SQL
            save(user);

        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 7. 生成 token
        String token = UUID.randomUUID().toString(true);


        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (fieldName, fieldValue) -> fieldValue.toString()
                        )
        );



        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);


        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result sign() {
        // 1. 获取当前用户

        Long userId = UserHolder.getUser().getId();

        // 2. 获取日期

        LocalDateTime now = LocalDateTime.now();

        // 3. 拼接 Key

        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));


        // 4. 算出今天是本月第几天（offset）

        int dayOfMonth = now.getDayOfMonth();

        // 5. SETBIT key offset 1

        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1. 获取当前用户

        Long userId = UserHolder.getUser().getId();

        // 2. 获取日期

        LocalDateTime now = LocalDateTime.now();

        // 3. 拼接 Key

        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));

        // 4. 算出今天是本月第几天（offset）

        int dayOfMonth = now.getDayOfMonth();

        List<Long> results = stringRedisTemplate.opsForValue()
                .bitField(
                        key,
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0)
                );


        if (results == null || results.isEmpty()) {
            return Result.ok(0);
        }


        Long num = results.get(0);

        if (num == null || num == 0) {
            return Result.ok(0);
        }

        int count = 0;

        while (true) {
            if ((num & 1) == 0) {
                break;
            }
            count++;

            num >>>= 1;
        }

        return Result.ok(count);
    }
}
