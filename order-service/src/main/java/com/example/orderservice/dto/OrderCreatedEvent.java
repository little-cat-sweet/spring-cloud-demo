package com.example.orderservice.dto;

import java.math.BigDecimal;

/**
 * 订单创建事件：订单服务发给 MQ 的业务消息体。
 *
 * <p>说明两点：
 * <ul>
 *   <li>生产者和消费者各有一份同结构类（user-service 的 dto 包里那份），
 *       微服务之间不共享实体类，靠"消息契约"对齐字段。真实项目里会把契约抽到
 *       独立的 api/dto 模块，或用 schema 注册中心，这里保持简单。</li>
 *   <li>createTime 用 long（毫秒时间戳）而不是 LocalDateTime：MQ 的消息转换器
 *       不一定注册了 JavaTimeModule，用对象传 LocalDateTime 容易在序列化环节踩坑。
 *       真实项目里通常传 ISO-8601 字符串，这里为了好读用时间戳。</li>
 * </ul>
 */
public class OrderCreatedEvent {

    /** 订单号，后续幂等去重的主键 */
    private String orderNo;

    /** 下单用户 */
    private Long userId;

    /** 订单金额，单位：元 */
    private BigDecimal amount;

    /** 创建时间，毫秒时间戳 */
    private Long createTime;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(String orderNo, Long userId, BigDecimal amount, Long createTime) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.amount = amount;
        this.createTime = createTime;
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
