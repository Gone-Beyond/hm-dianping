package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        HttpSession session = request.getSession(false);

        if (session == null) {
            response.setStatus(401);
            return false;
        }

        UserDTO user = (UserDTO) session.getAttribute("user");

        if (user == null) {
            response.setStatus(401);
            return false;
        }

        UserHolder.saveUser(user);


        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {

        UserHolder.removeUser();
    }
}
