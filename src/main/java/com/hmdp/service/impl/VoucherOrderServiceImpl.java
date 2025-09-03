package com.hmdp.service.impl;

import com.hmdp.dto.OrderMessage;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisldWoker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.aop.framework.AopContext;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.config.RabbitMQConfig.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisldWoker redisldWoker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    // Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀卷 - 基于Redis+Lua脚本的异步实现
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2.为0，有购买资格，发送消息到RabbitMQ
        long orderId = redisldWoker.nextId("order");
        
        // 创建订单消息
        OrderMessage orderMessage = new OrderMessage(orderId, userId, voucherId);
        
        try {
            // 发送消息到RabbitMQ
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_ROUTING_KEY, orderMessage);
            log.info("订单消息发送成功: {}", orderId);
        } catch (Exception e) {
            log.error("订单消息发送失败: {}", orderId, e);
            return Result.fail("系统繁忙，请稍后重试");
        }

        // 3.返回订单id
        return Result.ok(orderId);
    }

    public Result createVouncherOrder(Long voucherId) {
        //6.一人一单
        Long userId = UserHolder.getUser().getId();
        //6.1查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //6.2判断是否存在
        if (count > 0) {
            return Result.fail("重复下单");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisldWoker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id

        Long UserId = UserHolder.getUser().getId();
        voucherOrder.setUserId(UserId);
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        //返回订单
        save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }

    /**
     * 测试辅助方法：重置Redis库存和清理订单记录
     * @param voucherId 优惠券ID
     * @param stock 库存数量
     * @return
     */
    public Result resetSeckillData(Long voucherId, Integer stock) {
        try {
            // 1.重置Redis库存
            String stockKey = "seckill:stock:" + voucherId;
            stringRedisTemplate.opsForValue().set(stockKey, stock.toString());
            
            // 2.清理Redis订单记录
            String orderKey = "seckill:order:" + voucherId;
            stringRedisTemplate.delete(orderKey);
            
            // 3.重置数据库库存
            seckillVoucherService.update()
                    .setSql("stock = " + stock)
                    .eq("voucher_id", voucherId)
                    .update();
            
            log.info("重置秒杀数据成功，voucherId={}, stock={}", voucherId, stock);
            return Result.ok("重置成功");
        } catch (Exception e) {
            log.error("重置秒杀数据失败", e);
            return Result.fail("重置失败：" + e.getMessage());
        }
    }
}
