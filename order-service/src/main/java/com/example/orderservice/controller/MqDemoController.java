package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderCreatedEvent;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MQ 演示：订单服务作为生产者。这里放了两条对比用的接口：
 *
 * <ul>
 *   <li><b>第 1 步的字符串消息</b>（{@code /order/mq/send}）：topic = order-created，
 *       只用来验证"能发出去、能被消费"，消息体是一个纯字符串。</li>
 *   <li><b>第 3 步的业务消息</b>（{@code /order/mq/send-event}）：topic = order-created-event，
 *       消息体是 {@link OrderCreatedEvent} 对象，框架会自动序列化成 JSON。
 *       后续的幂等、事务消息都在这条链路上做。</li>
 * </ul>
 *
 * <p>两条链路故意用不同 topic / 不同消费组：同一 topic 上放两种格式的消息，
 * 消费端很难处理（String 监听器和对象监听器会互相收到解析不了的消息）。
 * 真实项目里一个 topic 只承载一种消息格式。
 */
@RestController
@RequestMapping("/order/mq")
public class MqDemoController {

    private static final Logger log = LoggerFactory.getLogger(MqDemoController.class);

    /** 第 1 步：纯字符串消息的 topic */
    public static final String TOPIC = "order-created";

    /** 第 3 步：业务消息（JSON）的 topic */
    public static final String TOPIC_EVENT = "order-created-event";

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发送字符串：等 broker 返回 SendResult 才继续，
     * 能拿到 msgId 和落到了哪个队列，是最容易观察的发送方式。
     */
    @GetMapping("/send")
    public String send(@RequestParam(value = "msg", defaultValue = "hello rocketmq") String msg) {
        SendResult result = rocketMQTemplate.syncSend(TOPIC, msg);
        log.info("[MQ-发送] topic={}, status={}, msgId={}", TOPIC, result.getSendStatus(), result.getMsgId());
        return "status=" + result.getSendStatus()
                + "\nmsgId=" + result.getMsgId()
                + "\nqueue=" + result.getMessageQueue();
    }

    /**
     * 发送业务消息：直接把 POJO 交给 RocketMQTemplate，框架用消息转换器
     * 序列化成 JSON，并在消息头里写入 contentType=application/json。
     * 消费端声明 {@code RocketMQListener<OrderCreatedEvent>} 就能自动反序列化回对象。
     *
     * <p>用 POST：这才是"创建订单"的语义。GET 只保留给上面那条最简单的发送演示。
     */
    @PostMapping("/send-event")
    public OrderCreatedEvent sendEvent(
            @RequestParam(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(value = "amount", defaultValue = "99.00") BigDecimal amount) {

        OrderCreatedEvent event = new OrderCreatedEvent(
                generateOrderNo(), userId, amount, System.currentTimeMillis());

        // 生产环境订单号会由发号器/雪花算法生成，这里只要保证唯一即可
        SendResult result = rocketMQTemplate.syncSend(TOPIC_EVENT, event);

        log.info("[MQ-发送] topic={}, orderNo={}, userId={}, amount={}, status={}, msgId={}",
                TOPIC_EVENT, event.getOrderNo(), event.getUserId(), event.getAmount(),
                result.getSendStatus(), result.getMsgId());

        return event;
    }

    /** 订单号：时间前缀 + 6 位随机数，够演示用且人眼可读 */
    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FMT)
                + ThreadLocalRandom.current().nextInt(100000, 1000000)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /**
     * 幂等实验专用：把<b>同一条</b>消息（同一个 orderNo）重复发送 N 次。
     *
     * <p>MQ 本身是 at-least-once 语义，重复投递在真实环境里必然发生（重试、ACK 丢失、
     * 生产端重发）。这里用"主动重发"来稳定复现，方便验证消费端的幂等是否生效：
     * 发 3 次、每次 10 分，正确的结果是用户积分只加 10，而不是 30。
     *
     * <p>验证步骤：
     * <pre>
     * POST /user/point/reset?userId=7            # 先清零（user-service 8081）
     * POST /order/mq/send-event-dup?userId=7&amount=10&times=3
     * GET  /user/point/7                          # 预期 point=10
     * </pre>
     */
    @PostMapping("/send-event-dup")
    public Map<String, Object> sendEventDup(
            @RequestParam(value = "userId", defaultValue = "7") Long userId,
            @RequestParam(value = "amount", defaultValue = "10.00") BigDecimal amount,
            @RequestParam(value = "times", defaultValue = "3") int times) {

        // 关键：只生成一次 orderNo，循环发送同一个事件对象
        OrderCreatedEvent event = new OrderCreatedEvent(
                generateOrderNo(), userId, amount, System.currentTimeMillis());

        for (int i = 1; i <= times; i++) {
            rocketMQTemplate.syncSend(TOPIC_EVENT, event);
            log.info("[MQ-发送] 第 {}/{} 次发送同一条消息: orderNo={}, userId={}, amount={}",
                    i, times, event.getOrderNo(), userId, amount);
        }
        return Map.of("orderNo", event.getOrderNo(), "times", times, "userId", userId, "amount", amount);
    }
}
