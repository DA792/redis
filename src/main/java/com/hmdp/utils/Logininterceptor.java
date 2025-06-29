package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class Logininterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session
        HttpSession session = request.getSession();

        //获取session中的用户
        Object user = session.getAttribute("user");

        //判断用户是否存在
        if (user == null){
            //拦截
            response.setStatus(401);
            return false;

        }
        //保存用户到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // ✅ 重要：清理 ThreadLocal，防止内存泄露
        UserHolder.removeUser();
    }
}


