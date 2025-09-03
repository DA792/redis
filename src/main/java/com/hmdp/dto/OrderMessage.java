package com.hmdp.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单消息实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 优惠券ID
     */
    private Long voucherId;
    
    /**
     * 创建时间
     */
    private Long createTime;
    
    /**
     * 重试次数
     */
    private Integer retryCount = 0;
    
    public OrderMessage(Long orderId, Long userId, Long voucherId) {
        this.orderId = orderId;
        this.userId = userId;
        this.voucherId = voucherId;
        this.createTime = System.currentTimeMillis();
    }
} 