package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisldWoker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class HmDianPingApplicationTest {
    
    @Autowired
    private ShopServiceImpl shopService;
    
    @Autowired
    private CacheClient cacheClient;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisldWoker redisldWoker;




   private ExecutorService es = Executors.newFixedThreadPool(500);

   @Test
    void testIdWoker() throws InterruptedException {
       CountDownLatch latch = new CountDownLatch(300);


       Runnable task = () ->{
           for (int i = 0; i < 100; i++) {
               long id = redisldWoker.nextId("order");
               System.out.println("id ="  + id);

           }
           latch.countDown();
       };
       long beginTime = System.currentTimeMillis();
       for (int i = 0; i < 300; i++) {
           es.submit(task);


       }
       latch.await();
       long endTime = System.currentTimeMillis();

       System.out.println("time = " +(endTime - beginTime));



   }
    @Test
    void testSaveShop(){
        shopService.saveShop2Redis(2L, 10L);
        System.out.println("商铺数据已保存到Redis缓存");
    }


    
    /**
     * 测试Redis连接和缓存状态
     */
    @Test
    void testRedisConnection() {
        System.out.println("=== 测试Redis连接状态 ===");
        
        try {
            // 测试Redis连接
            stringRedisTemplate.opsForValue().set("test:connection", "OK", 60, TimeUnit.SECONDS);
            String result = stringRedisTemplate.opsForValue().get("test:connection");
            System.out.println("Redis连接测试: " + (result != null ? "✅ 成功" : "❌ 失败"));
            
            // 检查缓存穿透的空值是否存在
            String cacheValue = stringRedisTemplate.opsForValue().get("cache:shop:999999");
            System.out.println("缓存key [cache:shop:999999] 的值: " + 
                (cacheValue == null ? "null (不存在)" : 
                 cacheValue.isEmpty() ? "\"\" (空字符串)" : 
                 "\"" + cacheValue + "\""));
                 
            // 检查TTL
            Long ttl = stringRedisTemplate.getExpire("cache:shop:999999", TimeUnit.SECONDS);
            System.out.println("TTL剩余时间: " + ttl + "秒");
            
        } catch (Exception e) {
            System.err.println("Redis操作异常: " + e.getMessage());
        }
        
        System.out.println("=== Redis测试结束 ===");
    }
    
    /**
     * 测试缓存穿透 - 查询不存在的商铺ID
     * 缓存穿透：请求查询一个不存在的数据，缓存中没有，数据库中也没有
     */
    @Test
    void testCachePenetration() throws InterruptedException {
        System.out.println("=== 开始测试缓存穿透 ===");
        
        // 先清除可能存在的缓存
        stringRedisTemplate.delete("cache:shop:999999");
        System.out.println("已清除旧缓存");
        
        // 创建线程池模拟高并发
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(100);
        
        long startTime = System.currentTimeMillis();
        
        // 模拟100个请求同时查询不存在的商铺ID
        for (int i = 0; i < 100; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    // 查询一个肯定不存在的商铺ID
                    Long nonExistentId = 999999L;
                    
                    System.out.println("线程" + threadNum + "开始查询...");
                    
                    // 测试缓存穿透解决方案
                    Shop shop = cacheClient.queryWithPassThrough(
                        "cache:shop:", 
                        nonExistentId, 
                        Shop.class, 
                        shopService::getById, 
                        30L, 
                        TimeUnit.MINUTES
                    );
                    
                    System.out.println("线程" + threadNum + "查询ID " + nonExistentId + " 结果: " + (shop == null ? "null" : shop));
                    
                } catch (Exception e) {
                    System.err.println("线程" + threadNum + "查询出错: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有请求完成
        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        System.out.println("缓存穿透测试完成，耗时: " + (endTime - startTime) + "ms");
        
        // 检查最终的缓存状态
        String finalCacheValue = stringRedisTemplate.opsForValue().get("cache:shop:999999");
        System.out.println("最终缓存值: " + 
            (finalCacheValue == null ? "null" : 
             finalCacheValue.isEmpty() ? "空字符串" : 
             "\"" + finalCacheValue + "\""));
        
        System.out.println("=== 缓存穿透测试结束 ===\n");
    }
    
    /**
     * 测试缓存击穿 - 热点数据过期时的高并发访问
     * 缓存击穿：缓存中的热点数据过期，同时有大量请求访问这个数据
     */
    @Test
    void testCacheBreakdown() throws InterruptedException {
        System.out.println("=== 开始测试缓存击穿 ===");
        
        // 1. 先预热缓存，设置一个很短的过期时间（2秒）
        Long hotShopId = 1L;
        cacheClient.setWithLogicalExpire("cache:shop:" + hotShopId, 
            shopService.getById(hotShopId), 2L, TimeUnit.SECONDS);
        
        System.out.println("预热缓存完成，等待3秒让缓存逻辑过期...");
        Thread.sleep(3000); // 等待缓存逻辑过期
        
        // 2. 创建线程池模拟高并发访问热点数据
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(200);
        
        long startTime = System.currentTimeMillis();
        
        // 模拟200个请求同时访问已过期的热点数据
        for (int i = 0; i < 200; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    // 测试逻辑过期解决缓存击穿
                    Shop shop = cacheClient.queryWithLogicalExpire(
                        "cache:shop:", 
                        hotShopId, 
                        Shop.class, 
                        shopService::getById, 
                        20L, 
                        TimeUnit.SECONDS
                    );
                    
                    System.out.println("线程" + threadNum + "查询结果: " + 
                        (shop != null ? "商铺名: " + shop.getName() : "null"));
                    
                } catch (Exception e) {
                    System.err.println("线程" + threadNum + "查询出错: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有请求完成
        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        System.out.println("缓存击穿测试完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("=== 缓存击穿测试结束 ===\n");
    }
    
    /**
     * 测试互斥锁解决缓存击穿（通过缓存穿透方法模拟）
     */
    @Test
    void testMutexLockCacheBreakdown() throws InterruptedException {
        System.out.println("=== 开始测试互斥锁解决缓存击穿 ===");
        
        Long shopId = 2L;
        String cacheKey = "cache:shop:" + shopId;
        
        // 先清除缓存，模拟缓存过期场景
        stringRedisTemplate.delete(cacheKey);
        System.out.println("已清除缓存key: " + cacheKey);
        
        // 创建线程池模拟高并发
        ExecutorService executor = Executors.newFixedThreadPool(30);
        CountDownLatch latch = new CountDownLatch(100);
        
        long startTime = System.currentTimeMillis();
        
        // 模拟100个请求同时访问同一个商铺
        for (int i = 0; i < 100; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    System.out.println("线程" + threadNum + "开始查询...");
                    
                    // 使用缓存穿透方法，它内部有防护机制
                    Shop shop = cacheClient.queryWithPassThrough(
                        "cache:shop:", 
                        shopId, 
                        Shop.class, 
                        shopService::getById, 
                        30L, 
                        TimeUnit.MINUTES
                    );
                    
                    System.out.println("线程" + threadNum + "查询结果: " + 
                        (shop != null ? "成功-" + shop.getName() : "失败"));
                    
                } catch (Exception e) {
                    System.err.println("线程" + threadNum + "查询出错: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有请求完成
        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        System.out.println("缓存穿透保护测试完成，耗时: " + (endTime - startTime) + "ms");
        
        // 检查最终缓存状态
        String finalCacheValue = stringRedisTemplate.opsForValue().get(cacheKey);
        System.out.println("最终缓存状态: " + (finalCacheValue != null ? "已缓存" : "未缓存"));
        
        System.out.println("=== 测试结束 ===");
    }
}
