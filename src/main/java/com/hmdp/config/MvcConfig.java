package com.hmdp.config;

import com.hmdp.utils.Logininterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new Logininterceptor()).excludePathPatterns(
                "/user/code",      // 发送验证码
                "/user/login",     // 用户登录
                "/blog/hot",       // 热门博客
                "/shop/**",        // 商铺相关接口
                "/shop-type/**",   // 商铺类型接口
                "/voucher/**",     // 优惠券接口（如果需要公开）
                "/upload/**",      // 文件上传（如果需要公开）
                "/error"           // 错误页面

                ).order(1);
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
