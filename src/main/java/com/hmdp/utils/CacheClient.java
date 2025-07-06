package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
//方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    //方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void  setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    //方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallback
    ,Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //从redis查询商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        
        //判断缓存中是否有值
        if (json != null) {
            //判断是否是有效数据
            if (StrUtil.isNotBlank(json)) {
                //如果是有效数据，直接返回
                R r = JSONUtil.toBean(json, type);
                return r;
            } else {
                //如果是空值（防止缓存穿透的空值），直接返回null
                return null;
            }
        }
        
        //缓存中没有数据，需要查询数据库，使用互斥锁防止缓存击穿
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            //尝试获取互斥锁
            boolean isLock = trylock(lockKey);
            if (!isLock) {
                //获取锁失败，等待一段时间后重试
                Thread.sleep(50);
                return queryWithPassThrough(keyPrefix, id, type, dbFallback, time, unit);
            }
            
            //获取锁成功，再次检查缓存（双重检查）
            json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                if (StrUtil.isNotBlank(json)) {
                    return JSONUtil.toBean(json, type);
                } else {
                    return null;
                }
            }
            
            //确实没有缓存，查询数据库
            r = dbFallback.apply(id);
            if (r == null){
                //数据库中也不存在，将空值写回redis防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            
            //数据库中存在，写入缓存并返回
            this.set(key, r, time, unit);
            return r;
            
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(lockKey);
        }
    }
    private static final ExecutorService CACHE_REBUTLD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallback
            ,Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //从redis查询商铺信息
        String Josn = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(Josn)){
            //如果不存在，直接返回
            return null;
        }
        RedisData redisData = JSONUtil.toBean(Josn, RedisData.class);
        R r= JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，返回商铺信息
            return r;
        }

        //过期，获取互斥锁
        //判断是否获取锁
        String LockKey = LOCK_SHOP_KEY + id;
        boolean isLock = trylock(LockKey);
        if (isLock){
            //获取锁，开启独立线程
            CACHE_REBUTLD_EXECUTOR.submit(() ->
            {
                //查询数据库
                R r1 = dbFallback.apply(id);
                //写入redis
                this.setWithLogicalExpire(key,r1,time,unit);


                //释放锁
                unlock(LockKey);
            });


        }

        //返回商铺信息

        return r;
    }
    public boolean trylock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);


    }

    public  void unlock(String key){
        stringRedisTemplate.delete(key);
    }


}
