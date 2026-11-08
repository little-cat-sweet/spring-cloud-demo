package com.example.userservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
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

    @Value("${user.name}")
    private String name;


    @Value("${user.age}")
    private Integer age;


    @GetMapping("/config")
    public String config(){

        return name + ":" + age;
    }
}
