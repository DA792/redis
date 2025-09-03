package com.hmdp.test;

import com.hmdp.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.hmdp.config.RabbitMQConfig.*;

/**
 * RabbitMQ连接测试
 */
@Slf4j
@Component
public class RabbitMQTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 暂时注释掉自动测试，避免启动失败
    // @PostConstruct
    public void testConnection() {
        try {
            log.info("开始测试RabbitMQ连接...");
            
            // 测试发送消息
            OrderMessage testMessage = new OrderMessage(1L, 1L, 1L);
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_ROUTING_KEY, testMessage);
            
            log.info("RabbitMQ连接测试成功！");
            
        } catch (Exception e) {
            log.error("RabbitMQ连接测试失败: {}", e.getMessage(), e);
        }
    }
} 