# Spring Cloud Demo

一个基于 Spring Cloud Alibaba 的微服务示例工程，演示 Nacos 服务注册与配置、Spring Cloud Gateway 路由、OpenFeign 服务调用，以及 Sentinel 限流和降级。

## 技术栈

- Java 17
- Spring Boot 3.3.5
- Spring Cloud 2023.0.3
- Spring Cloud Alibaba 2023.0.3.2
- Nacos：服务注册发现与配置中心
- Sentinel：流量控制与熔断降级
- Micrometer Tracing + Brave：生成并传播分布式 traceId/spanId

## 模块说明

| 模块 | 默认端口 | 说明 |
| --- | ---: | --- |
| `gateway-service` | 9000 | API 网关，将 `/user/**`、`/order/**` 转发到对应服务 |
| `order-service` | 8082 | 订单服务，通过 OpenFeign 调用用户服务 |
| `user-service` | 8081 | 用户服务，提供用户接口及配置中心读取示例 |
| `user-service-2` | 8083 | 第二个用户服务实例示例，服务名为 `user-service` |

## 启动依赖服务

### Nacos

```bash
docker pull nacos/nacos-server:v2.3.2
docker run --name nacos \
  -e MODE=standalone \
  -p 8848:8848 -p 9848:9848 -p 9849:9849 \
  -d nacos/nacos-server:v2.3.2
```

控制台地址：<http://localhost:8848/nacos>（默认账号密码：`nacos` / `nacos`）。

### Sentinel Dashboard

```bash
docker pull bladex/sentinel-dashboard
docker run --name sentinel -p 8858:8858 -d bladex/sentinel-dashboard
```

控制台地址：<http://localhost:8858>。

## 配置中心示例

在 Nacos 控制台进入 **配置管理 → 配置列表**，新增配置：

- **Data ID**：`user-service.yaml`
- **Group**：`DEFAULT_GROUP`
- **配置格式**：YAML
- **配置内容**：

```yaml
demo:
  user:
    name: Mark
    age: 20
```

用户服务的 `/user/config` 接口会读取并返回配置内容。服务通过 `application.yml` 中的 `optional:nacos:user-service.yaml` 导入该配置。

> ⚠️ 不要用 `user.name` 作为配置 key：它是 JVM 系统属性（默认值为操作系统用户名），优先级高于 Nacos 配置，会把配置中心的值静默覆盖掉（例如 Nacos 配的是 `Mark`，接口却返回 `chenhongyun`）。所以示例统一使用 `demo.user.*` 前缀，并在 `UserController` 上加了 `@RefreshScope`，改完 Nacos 配置无需重启即可生效。

## Sentinel 限流与降级

启动并访问相关服务接口后，进入 Sentinel Dashboard，在左侧选择对应服务，再打开 **簇点链路** 查看资源并配置流控或降级规则。簇点链路资源在接口被调用后才会出现。

用户服务的 `getUser` 接口通过 `@SentinelResource` 绑定了 fallback。访问 `/user/1` 会触发原有异常，并由 Sentinel 返回“用户服务暂时不可用”。接口资源名为 `getUser`；访问过接口后，可在 Dashboard 的 **簇点链路** 中找到它，并为资源配置**降级规则**（异常比例、异常数或慢调用比例）。

## 分布式 Trace ID 演示

本工程通过 Micrometer Tracing 和 Brave 生成追踪上下文，并将 `traceId`、`spanId` 放入应用日志。日志格式配置在各服务的 `application.yml` 中，业务代码使用普通日志（例如 `log.info(...)`）即可自动带出 MDC 中的追踪字段，无需每次手动拼接。

### 启动与调用

启动 Nacos，然后启动以下三个应用：

1. `user-service`（8081）
2. `order-service`（8082）
3. `gateway-service`（9000）

调用专用演示入口：

```bash
curl http://localhost:9000/trace-demo/order/2
```

请求链路为 **Gateway → Order → User**。分别查看三个应用控制台里同一时间的请求日志：同一条调用链的 `traceId` 应相同，每个服务/操作的 `spanId` 通常不同。日志级别后的两个字段依次为服务名、traceId、spanId，例如：

```text
INFO [order-service,4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7]
```

Order 服务使用 OpenFeign 调用 User。要让 Feign 调用接入 Micrometer Observation 并传播追踪上下文，`order-service/pom.xml` 需要包含：

```xml
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-micrometer</artifactId>
</dependency>
```

同时 `order-service/src/main/resources/application.yml` 中启用：

```yaml
spring:
  cloud:
    openfeign:
      micrometer:
        enabled: true
```

本项目已包含上述依赖和配置。新增服务间调用时，也要确认该客户端技术栈有对应的 Observation/Tracing 支持，否则跨服务请求可能新建 trace，导致上下游 traceId 不一致。

### 错误日志

使用 SLF4J 的错误日志写法并把异常对象作为最后一个参数传入，可输出完整堆栈；日志格式会自动附带当前 traceId/spanId：

```java
log.error("调用用户服务失败: id={}", id, exception);
```

不要只拼接 `exception.getMessage()`，否则不会打印完整堆栈。若在入口服务记录异常，建议在异常处理或降级处也记录一次，便于用 traceId 搜索整条链路。

## RocketMQ 消息队列

### 启动 RocketMQ

```bash
docker network create rmq-net

# namesrv
docker run -d --name rmqnamesrv --network rmq-net -p 9876:9876 \
  -e "JAVA_OPT_EXT=-server -Xms256m -Xmx256m" apache/rocketmq:5.3.1 sh mqnamesrv

# broker（配置见 docker/rocketmq/broker.conf）
docker run -d --name rmqbroker --network rmq-net \
  -p 10909:10909 -p 10911:10911 -p 10912:10912 \
  -v "$PWD/docker/rocketmq/broker.conf:/home/rocketmq/conf/broker.conf" \
  -e "JAVA_OPT_EXT=-server -Xms512m -Xmx512m -Xmn256m" \
  apache/rocketmq:5.3.1 sh mqbroker -c /home/rocketmq/conf/broker.conf

# 控制台：容器内实际监听 8082，映射到宿主 8180，避开 Nacos 的 8080 和订单服务的 8082
docker run -d --name rmq-dashboard --network rmq-net -p 8180:8082 \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=rmqnamesrv:9876" \
  apacherocketmq/rocketmq-dashboard:latest
```

控制台地址：<http://localhost:8180>。

`docker/rocketmq/broker.conf` 中的 `brokerIP1` 必须填**宿主机**能访问的 IP（示例里是本机局域网 IP）。换网络或重连 WiFi 后 IP 变化，需要改这一行并重启 broker 容器，否则客户端拿到旧地址会连不上。

### 配置与代码

生产者 `order-service`：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: order-producer-group
```

消费者 `user-service` 只需配置 `rocketmq.name-server`，topic 和消费组在注解上声明。

| 类 | 角色 | 说明 |
| --- | --- | --- |
| `order-service` 的 `MqDemoController` | 生产者 | 同步发送，两个 topic 各一个接口，见下表 |
| `user-service` 的 `OrderCreatedListener` | 消费者 | `String` 消息，消费组 `user-service-consumer-group`，topic `order-created` |
| `user-service` 的 `OrderEventConsumer` | 消费者 | 业务消息 `OrderCreatedEvent`，消费组 `user-service-point-group`，topic `order-created-event` |

两个 topic 的分工：

| topic | 消息内容 | 发送接口 | 用途 |
| --- | --- | --- | --- |
| `order-created` | 纯字符串 | `GET /order/mq/send?msg=hello` | 最初的连通性验证，保留做对照 |
| `order-created-event` | `OrderCreatedEvent` 的 JSON | `POST /order/mq/send-event?userId=7&amount=128.50` | 业务消息，后续幂等 / 事务消息都在这里做 |

```bash
# 字符串消息
curl "http://localhost:8082/order/mq/send?msg=hello"

# 业务消息（返回生成的事件，含 orderNo）
curl -X POST "http://localhost:8082/order/mq/send-event?userId=7&amount=128.50"
```

预期：

- 字符串：接口返回 `status=SEND_OK` 与 `msgId`；`user-service` 打印 `[MQ-消费] 收到订单创建消息: hello`
- 业务消息：接口返回 `{"orderNo":"ORD...","userId":7,"amount":128.50,"createTime":...}`；`user-service` 打印 `[MQ-消费-业务消息] orderNo=..., userId=7, amount=128.50`，说明 JSON 已被反序列化成对象

> **序列化要点**：生产者必须发 POJO（`syncSend(topic, event)`），由框架写入 `contentType=application/json`；如果自己先转成 JSON 字符串再发（`syncSend(topic, jsonStr)`），消息头会变成 `text/plain`，消费端按对象接收会抛转换异常。消费端只要把泛型写成 `RocketMQListener<OrderCreatedEvent>`，框架就会按泛型反序列化。
>
> 首次给新 topic 发消息时，消费端可能要等十几秒（路由信息懒加载）才拉到第一条消息，属正常现象。

### 消息重试与死信队列

消费端 `onMessage` **正常返回 = ACK**，**抛异常 = 消费失败**，broker 会把消息转入重试队列 `%RETRY%<consumerGroup>` 并延迟重投。

`OrderEventConsumer` 里加了一条业务校验：金额为负直接抛异常，用来演示整条重试链路。

```bash
# 发一条必然失败的消息
curl -X POST "http://localhost:8082/order/mq/send-event?userId=7&amount=-1"
```

实测时间线（`maxReconsumeTimes = 3`）：

| 时刻 | reconsumeTimes | 说明 |
| --- | --- | --- |
| 19:51:22 | 0 | 首次消费失败 |
| 19:51:37 | 1 | 第 1 次重投（延迟级别 10s） |
| 19:52:07 | 2 | 第 2 次重投（30s） |
| 19:53:07 | 3 | 第 3 次重投（1m），仍是失败 |
| 之后 | — | 超过 `maxReconsumeTimes`，进死信队列 `%DLQ%<consumerGroup>` |

验证死信：

```bash
docker exec rmqbroker sh -c 'sh mqadmin topicList -n rmqnamesrv:9876' | grep DLQ
docker exec rmqbroker sh -c 'sh mqadmin topicStatus -n rmqnamesrv:9876 -t "%DLQ%user-service-point-group"'
docker exec rmqbroker sh -c 'sh mqadmin queryMsgByUniqueKey -n rmqnamesrv:9876 -t "%DLQ%user-service-point-group" -i <msgId>'
```

控制台的 Topic 列表里也能看到 `%DLQ%` 开头的主题，消息体、重试次数都在里面。

要点：

- 默认 `maxReconsumeTimes = 16`，延迟级别一路涨到 2 小时，本地演示等不起，所以显式调成 3（约 100 秒进死信）。
- 重试间隔由 broker 的 `messageDelayLevel` 决定（默认 `1s 5s 10s 30s 1m 2m 3m ... 2h`），消费端只能改次数，不能改间隔。
- **死信不会自动重投**，必须有兜底：监控 `%DLQ%` 主题告警 + 人工排查后重投，或落一张"补偿表"由定时任务扫描。
- 像"金额为负"这种重试也不会成功的错误，理想做法是不抛异常直接记日志丢弃（或进业务死信表），只有网络抖动、下游超时这类**暂时性失败**才该靠重试解决。

### 幂等消费

MQ 的投递语义是 **at-least-once（至少一次）**：网络抖动、消费超时、ACK 丢失都会让同一条消息被投递多次。所以"加积分"这种非幂等操作必须自己做去重。

`user-service` 的 `PointService` 用 Redis 实现：

- 幂等标记：`SET mq:idem:point:{orderNo} 1 NX EX 30min`，设置成功 = 首次处理，失败 = 已经处理过
- 积分存储：`INCR point:user:{userId}`（第 7 步接入 MySQL 后会换成积分表 + 流水表）
- Redis 用 **db 3**，与本地其他项目隔离

验证方法（生产者提供了一个"同一条消息连发 N 次"的接口）：

```bash
# 1. 清空积分
curl -X POST "http://localhost:8081/user/point/reset?userId=88"

# 2. 同一条消息（同一个 orderNo，每笔 10 分）连发 3 次
curl -X POST "http://localhost:8082/order/mq/send-event-dup?userId=88&amount=10&times=3"

# 3. 查积分：预期 10，不是 30
curl "http://localhost:8081/user/point/88"
```

实测日志（3 次投递 → 1 次加分 + 2 次跳过）：

```
[MQ-消费-业务消息] orderNo=ORD2026..., userId=88, amount=10
[MQ-消费-业务消息] orderNo=ORD2026..., userId=88, amount=10
[幂等] 重复消息，跳过: orderNo=ORD2026..., userId=88
[MQ-消费-业务消息] orderNo=ORD2026..., userId=88, amount=10
[积分] userId=88 加 10 分，当前总分=10, orderNo=ORD2026...
[幂等] 重复消息，跳过: orderNo=ORD2026..., userId=88
```

Redis 里能看到两个 key：`mq:idem:point:{orderNo}`（TTL 30 分钟）和 `point:user:88`（值为 10）。

要点：

- 必须用 `setIfAbsent(key, value, ttl)` 这种原子写法。`hasKey` 再 `set` 有竞态：两个实例同时判断"不存在"，然后都加积分。
- 重复消息要**正常返回（ACK）**，不能抛异常，否则 broker 会一直重投这条本来就已经处理过的消息。
- 占位成功但业务失败时，要**删掉占位并抛异常**，让重试能够重新处理；否则消息会被自己的占位永远挡住。
- Redis 只是第一道防线。TTL 过期、Redis 宕机、主从切换都可能漏掉重复，最终一致性要靠数据库**唯一索引**兜底（第 7 步的 `t_point_log.uk_order_no`）。

## 常用接口

| 请求 | 说明 |
| --- | --- |
| `GET http://localhost:9000/user/{id}` | 经网关访问用户服务 |
| `GET http://localhost:9000/order/{id}` | 经网关访问订单服务，再由订单服务调用用户服务 |
| `GET http://localhost:9000/trace-demo/order/{id}` | 专用链路演示入口：Gateway → Order → User，并在各服务日志打印 traceId/spanId |
| `GET http://localhost:8081/user/config` | 查看 Nacos 配置读取结果 |
| `GET http://localhost:8082/order/mq/send?msg=hello` | 发送字符串消息到 `order-created` |
| `POST http://localhost:8082/order/mq/send-event?userId=7&amount=128.50` | 发送业务消息 `OrderCreatedEvent` 到 `order-created-event` |
| `POST http://localhost:8082/order/mq/send-event-dup?userId=88&amount=10&times=3` | 同一条消息（同一个 orderNo）连发 N 次，用于验证消费幂等 |
| `GET http://localhost:8081/user/point/{userId}` | 查询用户积分（Redis） |
| `POST http://localhost:8081/user/point/reset?userId=88` | 重置积分，方便反复做幂等实验 |

也可以直接访问服务端口（用户服务 `8081`、订单服务 `8082`）。

## 构建

在项目根目录执行：

```bash
./mvnw clean package
```

各服务需要在 Nacos、Sentinel 等依赖服务可用后启动。默认 Nacos 地址为 `127.0.0.1:8848`，Sentinel Dashboard 地址为 `localhost:8858`。
