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
    @GetMapping("/{id}")
    @SentinelResource(
            value = "getUser",
            fallback = "fallback"
    )
    public String getUser(
            @PathVariable("id") Long id
    ) {

        if(id == 1){
            throw new RuntimeException();
        }


        return "post is " + port + ", id is " + id;
    }

    public String fallback(Long id, Throwable e){

        return "用户服务暂时不可用";
    }
}
