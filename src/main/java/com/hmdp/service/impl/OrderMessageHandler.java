package com.hmdp.service.impl;

import com.hmdp.dto.OrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Order Message Handler
 */
@Slf4j
@Component
public class OrderMessageHandler {

    @Autowired
    private IVoucherOrderService voucherOrderService;
    
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    
    @Autowired
    private RedissonClient redissonClient;

    /**
     * Handle order message
     */
    @RabbitListener(queues = "order.queue")
    public void handleOrderMessage(OrderMessage message) {
        log.info("收到订单消息: {}", message);
        
        try {
            // 处理订单
            processOrder(message);
            log.info("订单处理成功: {}", message.getOrderId());
            
        } catch (Exception e) {
            log.error("订单处理失败: {}, 错误: {}", message.getOrderId(), e.getMessage(), e);
            
            // Retry count exceeds 3 times, reject message and enter dead letter queue
            if (message.getRetryCount() >= 3) {
                log.error("Order processing retry count has reached the limit, entering dead letter queue: {}", message.getOrderId());
                throw new AmqpRejectAndDontRequeueException("Retry count has reached the limit");
            }
            
            // Increase retry count
            message.setRetryCount(message.getRetryCount() + 1);
            
            // Re-throw exception to trigger retry
            throw new RuntimeException("Order processing failed, need retry");
        }
    }

    /**
     * Process order logic
     */
    private void processOrder(OrderMessage message) {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        Long orderId = message.getOrderId();

        // Create distributed lock
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        
        try {
            // Try to acquire lock
            if (!lock.tryLock()) {
                log.error("Failed to acquire lock, duplicate order not allowed: {}", orderId);
                return;
            }
            
            // Double check: check again if user has already ordered
            Integer count = voucherOrderService.query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
                    
            if (count > 0) {
                log.error("User has already ordered, duplicate order not allowed: {}", orderId);
                return;
            }
            
            // Deduct stock
            boolean stockSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
                    
            if (!stockSuccess) {
                log.error("Stock deduction failed: {}", orderId);
                throw new RuntimeException("Insufficient stock");
            }
            
            // Create order
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            
            boolean saveSuccess = voucherOrderService.save(voucherOrder);
            if (!saveSuccess) {
                log.error("Order save failed: {}", orderId);
                throw new RuntimeException("Order save failed");
            }
            
            log.info("Order created successfully: {}", orderId);
            
        } finally {
            // Release lock
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Handle dead letter queue message
     */
    @RabbitListener(queues = "order.dead.queue")
    public void handleDeadLetterMessage(OrderMessage message) {
        log.error("Received dead letter queue message, order processing finally failed: {}", message);
        
        // Here you can send alerts, log records, manual processing, etc.
        // For example, send emails, SMS notifications to administrators
        // Or record to database for subsequent manual processing
        
        log.error("Order {} finally failed to process, manual intervention required", message.getOrderId());
    }
} 