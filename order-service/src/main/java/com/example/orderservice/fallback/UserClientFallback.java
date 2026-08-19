package com.example.orderservice.fallback;

import com.example.orderservice.client.UserClient;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallback
        implements UserClient {


    @Override
    public String getUser(Long id){

        return "用户服务异常，请稍后再试";

    }
}