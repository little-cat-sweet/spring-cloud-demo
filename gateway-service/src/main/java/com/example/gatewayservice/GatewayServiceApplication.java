package com.example.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(GatewayServiceApplication.class);

    @Bean
    public GatewayFilter traceDemoLoggingFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            if (path.startsWith("/trace-demo/")) {
                log.info("Trace demo gateway forwarding request: method={}, path={}",
                        exchange.getRequest().getMethod(), path);
            }
            return chain.filter(exchange);
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

}
