package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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


    @Override
    public Result sendCode(String phone, HttpSession session) {

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }


        String code = RandomUtil.randomNumbers(6);

        session.setAttribute("code", code);

        log.info("手机号: {}, 验证码: {}", phone, code);


        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String code = loginForm.getCode();
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String cacheCode = (String) session.getAttribute("code");

        if (cacheCode == null) {
            return Result.fail("验证码错误");
        }




        if (!cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }



        //  抄的  我不会plus写法

        User user = lambdaQuery()
                .eq(User::getPhone, phone)
                .one();


        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            save(user);

        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);

        session.removeAttribute("code");

        return Result.ok();
    }
}
