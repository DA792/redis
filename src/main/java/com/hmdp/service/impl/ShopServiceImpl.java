package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {



    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    public Result queryById(Long id) throws InterruptedException {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
//       //逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,20L,TimeUnit.SECONDS);
        if (shop == null){
            return Result.fail("店铺不存在");
        }



        return Result.ok(shop);
    }


    private static final ExecutorService CACHE_REBUTLD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期
     * @param id
     * @return
     */
//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //从redis查询商铺信息
//        String shopJosn = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isBlank(shopJosn)){
//            //如果不存在，直接返回
//            return null;
//        }
//        RedisData redisData = JSONUtil.toBean(shopJosn, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //判断缓存是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            //未过期，返回商铺信息
//            return shop;
//        }
//
//        //过期，获取互斥锁
//        //判断是否获取锁
//        String Lockshop = LOCK_SHOP_KEY + id;
//        boolean isLock = trylock(Lockshop);
//        if (isLock){
//            //获取锁，开启独立线程
//            CACHE_REBUTLD_EXECUTOR.submit(() ->
//            {
//                //重建缓存
//               this.saveShop2Redis(id,20L);
//
//                //释放锁
//                unlock(Lockshop);
//            });
//
//
//        }
//
//        //返回商铺信息
//
//        return shop;
//    }

//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //从redis查询商铺信息
//        String shopJosn = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isNotBlank(shopJosn)){
//            //如果存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJosn, Shop.class);
//            return shop;
//        }
//        //判断是否是空值
//        if (shopJosn != null){
//            return null;
//        }
//        //不存在，从数据库查
//        Shop shop = getById(id);
//        if (shop == null){
//            //不存在,将空值写回redis
//            stringRedisTemplate.opsForValue().set(key," ",CACHE_SHOP_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        //存在，返回，并返回redis
//        stringRedisTemplate.opsForValue().set(key   ,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL , TimeUnit.MINUTES);
//        return shop;
//    }
//    public Shop queryWithMutex(Long id) throws InterruptedException {
//        String key = CACHE_SHOP_KEY + id;
//        //从redis查询商铺信息
//        String shopJosn = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if (StrUtil.isNotBlank(shopJosn)){
//            //如果存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJosn, Shop.class);
//            return shop;
//        }
//        //判断是否是空值
//        if (shopJosn != null){
//            return null;
//        }
//        //缓存未命中
//        //4.1获取互斥锁
//        String LockKey = "lock:shop:" + key;
//        boolean islock = trylock(LockKey);
//        //4.2判断是否获取锁
//        if (!islock) {
//            //4.3没有获取，休眠一段时间，在查询缓存
//            Thread.sleep(200);
//            return queryWithMutex(id);
//        }
//
//        //4.4获取，根据查询数据库
//        Shop shop = getById(id);
//        if (shop == null) {
//            //不存在,将空值写回redis
//            stringRedisTemplate.opsForValue().set(key, " ", CACHE_SHOP_TTL, TimeUnit.MINUTES);
//            unlock(LockKey);
//            return null;
//        }
//        //存在，返回，并返回redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        unlock(LockKey);
//        return shop;
//    }
    public   void saveShop2Redis(Long id, Long expireScond){
        //查询店铺数据
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireScond));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

//
//    public boolean trylock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//
//
//    }
//
//    public  void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Transactional
    public Result update1(Shop shop) {

        if (shop.getId()  == null){
            return Result.fail("商铺id不能不存在");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
