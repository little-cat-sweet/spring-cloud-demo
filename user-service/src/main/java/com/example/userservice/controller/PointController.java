package com.example.userservice.controller;

import com.example.userservice.point.PointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 积分查询接口，用来验证"同一条消息重复投递多次，积分只加一次"。
 */
@RestController
@RequestMapping("/user/point")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /** 查询用户当前积分 */
    @GetMapping("/{userId}")
    public Map<String, Object> get(@PathVariable Long userId) {
        return Map.of("userId", userId, "point", pointService.getPoint(userId));
    }

    /** 重置积分（仅演示用，方便反复做幂等实验） */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestParam(value = "userId", defaultValue = "1") Long userId) {
        pointService.resetPoint(userId);
        return Map.of("userId", userId, "point", 0, "reset", true);
    }
}
