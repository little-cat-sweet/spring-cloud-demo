package com.example.userservice.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MQ 演示：用户服务作为消费者。
 * consumerGroup 相同的实例组成一个消费组，一条消息在组内只会被一个实例消费。
 */
@Service
@RocketMQMessageListener(
        topic = "order-created",
        consumerGroup = "user-service-consumer-group"
)
public class OrderCreatedListener implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    @Override
    public void onMessage(String message) {
        // 方法正常返回 = ACK；抛异常 = 消费失败，broker 会重投（后面专门演示）
        log.info("[MQ-消费] 收到订单创建消息: {}", message);
    }
}
