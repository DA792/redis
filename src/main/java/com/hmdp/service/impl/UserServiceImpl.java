package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     *  // TODO 发送短信验证码并保存验证码
     * @param phone
     * @param session
     * @return
     */
    public Result sendCode(String phone, HttpSession session) {
        //检测手机号

        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果手机号不正确，返回
            return Result.fail("手机号格式错误");

        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);


        //保存验证码到redis
       stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);


        //发送验证码
        log.info("发送验证码成功,验证码{}",code);

        //返回ok
        return Result.ok();
    }

    /**
     * 实现登录功能
     * @param loginForm
     * @param session
     * @return
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果手机号不正确，返回
            return Result.fail("手机号格式错误");
        }

        //redis检验验证码
            String acheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
            String code = loginForm.getCode();
            if (acheCode == null || !acheCode.equals(code) ){
                //不一致，报错
                return Result.fail("验证码错误");
            }
            //如果一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //用户不存在
        if (user == null){
            //创建新用户
           user =   creatnewUserWithPhone(phone);
        }
        //保存用户信息到redis
      //1随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //2将User对象存储为hashmap类型
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 3.手动转换为String类型的Map
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userDTO.getId().toString());
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());

        //3存储
        String tokens = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokens,userMap);
        //设置时间
        stringRedisTemplate.expire(tokens,30,TimeUnit.MINUTES);
        //4返回token
        return Result.ok(token);








    }

    private User creatnewUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
