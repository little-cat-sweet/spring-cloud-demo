package com.example.userservice.point;

import com.example.userservice.dto.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 积分服务（第 5 步：幂等）。
 *
 * <p>MQ 的投递语义是 <b>at-least-once（至少一次）</b>：网络抖动、消费者重启、
 * 消费成功但 ACK 丢失，都会导致同一条消息被投递多次。所以"加积分"这类非幂等操作
 * 必须自己保证重复执行不会产生副作用。
 *
 * <p>这里用 Redis 做两件事：
 * <ol>
 *   <li><b>幂等标记</b>：{@code SET mq:idem:point:{orderNo} 1 NX EX 30min}，
 *       设置成功 = 第一次处理；失败 = 已经处理过，直接跳过。</li>
 *   <li><b>积分存储</b>：{@code INCR point:user:{userId}}。第 7 步接入 MySQL 后，
 *       这里会换成"插积分流水表 + 更新积分表"，Redis 只保留幂等标记。</li>
 * </ol>
 *
 * <p>两个必须注意的点：
 * <ul>
 *   <li>必须用 {@code setIfAbsent(key, value, ttl)} 这种<b>原子</b>写法。
 *       先 {@code hasKey} 再 {@code set} 会有竞态：两个实例同时判断"不存在"，
 *       然后都去加积分，幂等就失效了。</li>
 *   <li>占位成功但业务失败时，<b>要删掉占位</b>并抛出异常，让 MQ 重试；
 *       否则这条消息永远处理不了（重试时被自己的占位挡掉）。</li>
 * </ul>
 */
@Service
public class PointService {

    private static final Logger log = LoggerFactory.getLogger(PointService.class);

    /** 幂等标记的 key 前缀，后接订单号 */
    private static final String IDEM_KEY_PREFIX = "mq:idem:point:";

    /** 用户积分的 key 前缀，后接 userId */
    private static final String POINT_KEY_PREFIX = "point:user:";

    /**
     * 幂等标记的过期时间。
     * 太短：过期后迟到的重复消息会被当成新消息，重复加分；
     * 太长：key 堆积占用内存。
     * 真实项目里这个时长要覆盖"消息可能重复投递的最大时间窗"，
     * 并且用积分流水表的唯一索引做最后兜底。
     */
    private static final Duration IDEM_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public PointService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 幂等地加积分：同一条订单消息无论来几次，只加一次。
     *
     * @return {@code true} 首次处理；{@code false} 重复消息，已跳过
     */
    public boolean addPointOnce(OrderCreatedEvent event) {
        String idemKey = IDEM_KEY_PREFIX + event.getOrderNo();

        // SET key value NX EX ttl —— 原子的"不存在才写入"
        Boolean firstTime = redis.opsForValue().setIfAbsent(idemKey, "1", IDEM_TTL);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.warn("[幂等] 重复消息，跳过: orderNo={}, userId={}", event.getOrderNo(), event.getUserId());
            return false;
        }

        try {
            // 真实项目这里是：插 t_point_log（orderNo 唯一索引）+ update t_user_point
            Long total = redis.opsForValue()
                    .increment(POINT_KEY_PREFIX + event.getUserId(), event.getAmount().longValue());
            log.info("[积分] userId={} 加 {} 分，当前总分={}, orderNo={}",
                    event.getUserId(), event.getAmount(), total, event.getOrderNo());
        } catch (RuntimeException ex) {
            // 业务失败必须释放占位，否则 MQ 重试也会被这个 key 挡掉，消息就"永远处理不了"了
            redis.delete(idemKey);
            throw ex;
        }
        return true;
    }

    /** 查询某用户当前积分（demo 用，真实项目查 MySQL） */
    public long getPoint(Long userId) {
        String value = redis.opsForValue().get(POINT_KEY_PREFIX + userId);
        return value == null ? 0L : Long.parseLong(value);
    }

    /** 清空某用户积分，方便反复演示（真实项目当然不会提供这种接口） */
    public void resetPoint(Long userId) {
        redis.delete(POINT_KEY_PREFIX + userId);
        log.warn("[积分] 已重置 userId={} 的积分（演示用）", userId);
    }
}
