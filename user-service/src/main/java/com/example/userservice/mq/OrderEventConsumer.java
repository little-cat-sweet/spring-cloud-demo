package com.example.userservice.mq;

import com.example.userservice.dto.OrderCreatedEvent;
import com.example.userservice.point.PointService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 业务消息消费者（第 3、4、5 步）。
 *
 * <p>和 {@link OrderCreatedListener} 的区别：泛型写的是 {@link OrderCreatedEvent} 而不是 String。
 * rocketmq-spring 的监听容器会读取实现类上的泛型，用消息转换器把 JSON 反序列化成该类型，
 * 所以这里拿到的是对象，不用自己写 {@code JSON.parseObject}。
 *
 * <p><b>第 4 步（重试 / 死信）：</b>方法正常返回 = ACK；抛异常 = 消费失败，
 * 消息进重试队列按延迟级别重投，超过 {@code maxReconsumeTimes} 后进死信队列。
 *
 * <p><b>第 5 步（幂等）：</b>MQ 只保证 at-least-once，同一条消息可能来多次，
 * 所以真正的业务动作交给 {@link PointService#addPointOnce}，内部用 Redis setnx 去重。
 * 注意重复消息时要<b>正常返回</b>（ACK），不能抛异常 —— 否则 broker 会一直重投这条
 * 本来就"已经处理过"的消息。
 */
@Service
@RocketMQMessageListener(
        // 与 order-service 的 MqDemoController.TOPIC_EVENT 保持一致。
        // 两个服务是独立进程，不能直接引用对方的常量，只能靠约定（或抽公共模块）。
        topic = "order-created-event",
        // 消费组可通过配置覆盖，便于本地同时起多个实例做对照实验
        consumerGroup = "${rocketmq.demo.consumer-group:user-service-point-group}",
        // 最多重试 3 次，之后进死信队列
        maxReconsumeTimes = 3
)
public class OrderEventConsumer implements RocketMQListener<OrderCreatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final PointService pointService;

    public OrderEventConsumer(PointService pointService) {
        this.pointService = pointService;
    }

    @Override
    public void onMessage(OrderCreatedEvent event) {
        log.info("[MQ-消费-业务消息] orderNo={}, userId={}, amount={}, createTime={}",
                event.getOrderNo(), event.getUserId(), event.getAmount(), event.getCreateTime());

        // 模拟业务校验失败：金额为负属于脏数据，重试也不会成功。
        // 在这里抛异常，消息会按延迟级别重投，超过 maxReconsumeTimes 后进死信队列。
        // 触发方式：POST /order/mq/send-event?userId=7&amount=-1
        if (event.getAmount() == null || event.getAmount().signum() < 0) {
            throw new IllegalArgumentException("订单金额非法，拒绝处理: orderNo="
                    + event.getOrderNo() + ", amount=" + event.getAmount());
        }

        // 幂等加积分：重复消息会被 Redis setnx 挡掉，返回 false，此时正常返回即可（ACK）
        pointService.addPointOnce(event);
    }
}
