package com.hmdp.service.impl;

import com.hmdp.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.hmdp.config.RabbitMQConfig.*;

/**
 * 消息发送服务
 */
@Slf4j
@Service
public class MessageService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送订单消息
     */
    public boolean sendOrderMessage(OrderMessage orderMessage) {
        try {
            rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_ROUTING_KEY, orderMessage);
            log.info("订单消息发送成功: {}", orderMessage.getOrderId());
            return true;
        } catch (Exception e) {
            log.error("订单消息发送失败: {}", orderMessage.getOrderId(), e);
            return false;
        }
    }

    /**
     * 发送订单消息（带确认）
     */
    public boolean sendOrderMessageWithConfirm(OrderMessage orderMessage) {
        try {
            // 发送消息并等待确认
            rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(ORDER_EXCHANGE, ORDER_ROUTING_KEY, orderMessage);
                return operations.waitForConfirms(5000); // 等待5秒确认
            });
            log.info("订单消息发送成功（已确认）: {}", orderMessage.getOrderId());
            return true;
        } catch (Exception e) {
            log.error("订单消息发送失败: {}", orderMessage.getOrderId(), e);
            return false;
        }
    }
} 