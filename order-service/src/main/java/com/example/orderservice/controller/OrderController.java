package com.example.orderservice.controller;

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
    public String createOrder(
            @PathVariable("id") Long id
    ){
        String user = userClient.getUser(id);
        return "order create, " + user;
    }

}