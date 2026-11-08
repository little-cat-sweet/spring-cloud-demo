package com.example.userservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RefreshScope   // 让 Nacos 配置变更后无需重启即可生效
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Value("${server.port}")
    private String port;

    private int times;
    @GetMapping("/{id}")
    @SentinelResource(value = "getUser", fallback = "fallback")
    public String getUser(@PathVariable("id") Long id) {
        log.info("hello");
        if (id == 1) {
            log.error("User lookup failed: id={}, attempt={}", id, times++, new RuntimeException("user service error"));
            throw new RuntimeException("user service error");
        }
        
        return "...";
    }

    public String fallback(Long id, Throwable e){

        return "用户服务暂时不可用";
    }

    // 注意：不要用 user.name / user.age 这种 key。
    // user.name 是 JVM 系统属性（= 操作系统用户名），优先级高于 Nacos 配置，
    // 会导致配置中心的值被静默覆盖，所以这里统一加 demo. 前缀。
    @Value("${demo.user.name:unknown}")
    private String name;


    @Value("${demo.user.age:0}")
    private Integer age;


    @GetMapping("/config")
    public String config(){

        return name + ":" + age;
    }
}
