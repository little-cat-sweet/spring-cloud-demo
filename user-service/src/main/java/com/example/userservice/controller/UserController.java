package com.example.userservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Value("${server.port}")
    private String port;

    private int times;
    @GetMapping("/{id}")
    public String getUser(@PathVariable("id") Long id) {
        if (id == 1) {
            System.out.println("times : " + times ++);
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
