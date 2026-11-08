package com.example.orderservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.example.orderservice.client.UserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/order")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);


    @Autowired
    private UserClient userClient;


    @GetMapping("/{id}")
    @SentinelResource(value = "callUserApi", fallback = "callUserFallback")
    public String getOrder(@PathVariable("id") Long id) {
        try {
            log.info("Calling user-service: id={}", id);
            return "order invoked : " + userClient.getUser(id);
        } catch (feign.FeignException e) {
            log.error("Feign call to user-service failed: id={}", id, e);
            throw new RuntimeException("远程调用user-service失败", e);
        }
    }

    public String callUserFallback(Long id, Throwable e) {
        log.error("Order request degraded: id={}", id, e);
        return "Sentinel 客户端熔断降级，无法调用用户服务";
    }

}
