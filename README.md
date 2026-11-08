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
user:
  name: Mark
  age: 20
```

用户服务的 `/user/config` 接口会读取并返回配置内容。服务通过 `application.yml` 中的 `optional:nacos:user-service.yaml` 导入该配置。

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

## 常用接口

| 请求 | 说明 |
| --- | --- |
| `GET http://localhost:9000/user/{id}` | 经网关访问用户服务 |
| `GET http://localhost:9000/order/{id}` | 经网关访问订单服务，再由订单服务调用用户服务 |
| `GET http://localhost:9000/trace-demo/order/{id}` | 专用链路演示入口：Gateway → Order → User，并在各服务日志打印 traceId/spanId |
| `GET http://localhost:8081/user/config` | 查看 Nacos 配置读取结果 |

也可以直接访问服务端口（用户服务 `8081`、订单服务 `8082`）。

## 构建

在项目根目录执行：

```bash
./mvnw clean package
```

各服务需要在 Nacos、Sentinel 等依赖服务可用后启动。默认 Nacos 地址为 `127.0.0.1:8848`，Sentinel Dashboard 地址为 `localhost:8858`。
