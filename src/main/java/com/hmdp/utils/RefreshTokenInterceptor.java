package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        System.out.println("🔄 RefreshTokenInterceptor - URL: " + request.getRequestURL());
        System.out.println("🔄 RefreshTokenInterceptor - Token: " + token);
        if (StrUtil.isBlankIfStr(token)){
            System.out.println("❌ RefreshTokenInterceptor - No token, skipping");
            return true;
        }
        // 2。基于TOKEN获取redis的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //判断用户是否存在
        if (userMap.isEmpty()){
            System.out.println("❌ RefreshTokenInterceptor - No user data in Redis for key: " + key);
            return true;
        }
        System.out.println("✅ RefreshTokenInterceptor - Found user: " + userMap.get("nickName"));
        //将Map转成UserDTO数据
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //保存用户到ThreadLocal
        UserHolder.saveUser(userDTO);
        System.out.println("💾 RefreshTokenInterceptor - Saved user to ThreadLocal: " + userDTO.getNickName());
        //刷新token有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        System.out.println("⏰ RefreshTokenInterceptor - Refreshed token TTL");

        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // ✅ 重要：清理 ThreadLocal，防止内存泄露
        UserHolder.removeUser();
    }
}


