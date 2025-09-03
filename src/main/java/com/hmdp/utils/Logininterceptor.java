package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class Logininterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断ThreadLocal的用户是否存在
        System.out.println("🔐 LoginInterceptor - URL: " + request.getRequestURL());
        System.out.println("🔐 LoginInterceptor - User in ThreadLocal: " + UserHolder.getUser());
        if(UserHolder.getUser() == null){
            System.out.println("❌ LoginInterceptor - User is null, returning 401");
            response.setStatus(401);
            return false;
        }
        System.out.println("✅ LoginInterceptor - User exists, allowing access");
        //放行
        return true;
    }


}


