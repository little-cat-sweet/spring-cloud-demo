package com.example.orderservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.example.orderservice.client.UserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {


    @Autowired
    private UserClient userClient;


    @GetMapping("/{id}")
    @SentinelResource(value = "callUserApi", fallback = "callUserFallback")
    public String getOrder(@PathVariable("id") Long id) {
        try {
            return "order invoked : " + userClient.getUser(id);
        } catch (feign.FeignException e) {
            // 将Feign远程异常包装成RuntimeException，让Sentinel识别为失败请求
            throw new RuntimeException("远程调用user-service失败", e);
        }
    }

    public String callUserFallback(Long id, Throwable e) {
        return "Sentinel 客户端熔断降级，无法调用用户服务";
    }

}