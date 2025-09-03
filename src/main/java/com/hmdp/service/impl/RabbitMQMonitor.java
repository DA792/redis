package com.hmdp.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.hmdp.config.RabbitMQConfig.*;

/**
 * RabbitMQ监控类
 */
@Slf4j
@Component
public class RabbitMQMonitor {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 检查队列状态
     */
    public void checkQueueStatus() {
        try {
            // 获取队列消息数量
            Long messageCount = rabbitTemplate.execute(channel -> {
                return channel.messageCount(ORDER_QUEUE);
            });
            
            log.info("订单队列消息数量: {}", messageCount);
            
            // 如果队列消息过多，可以发送告警
            if (messageCount > 1000) {
                log.warn("订单队列消息过多: {}", messageCount);
                // 这里可以发送告警邮件或短信
            }
            
        } catch (Exception e) {
            log.error("检查队列状态失败", e);
        }
    }

    /**
     * 清理死信队列
     */
    public void clearDeadLetterQueue() {
        try {
            // 这里可以实现清理死信队列的逻辑
            // 比如定期清理或者手动清理
            log.info("清理死信队列");
        } catch (Exception e) {
            log.error("清理死信队列失败", e);
        }
    }
} 