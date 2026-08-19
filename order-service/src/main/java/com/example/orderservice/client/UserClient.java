package com.example.orderservice.client;

import com.example.orderservice.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name="user-service"
//        fallback = UserClientFallback.class
)
public interface UserClient {


    @GetMapping("/user/{id}")
    String getUser(
            @PathVariable("id") Long id
    );



}