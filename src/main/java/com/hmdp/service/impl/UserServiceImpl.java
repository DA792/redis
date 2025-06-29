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
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Random;

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


        //保存验证码到session
        session.setAttribute("code",code);


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

        //检验验证码
            Object acheCode = session.getAttribute("code");
            String code = loginForm.getCode();
            if (acheCode == null || !acheCode.toString().equals(code) ){
                //不一致，报错
                return Result.fail("验证码错误");
            }
            //如果一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //用户不存在
        if (user == null){
            //创建新用户
           user =   creatnewUserWithPhone(phone);
            //保存用户信息到session

        }

        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));





        return Result.ok();
    }

    private User creatnewUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
