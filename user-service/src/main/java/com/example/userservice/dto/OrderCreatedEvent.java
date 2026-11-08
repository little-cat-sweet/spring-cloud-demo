package com.example.userservice.dto;

import java.math.BigDecimal;

/**
 * 订单创建事件（消费端镜像）。
 *
 * <p>字段必须与 order-service 的 com.example.orderservice.dto.OrderCreatedEvent 完全一致，
 * 否则 JSON 反序列化会丢字段。真实项目里这份契约应放在共享模块或 schema 注册中心。
 */
public class OrderCreatedEvent {

    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private Long createTime;

    public OrderCreatedEvent() {
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "OrderCreatedEvent{orderNo='" + orderNo + "', userId=" + userId
                + ", amount=" + amount + ", createTime=" + createTime + '}';
    }
}
