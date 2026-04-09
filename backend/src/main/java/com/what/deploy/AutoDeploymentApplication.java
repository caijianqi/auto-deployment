package com.what.deploy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableWebSocket
@MapperScan("com.what.deploy.mapper")
public class AutoDeploymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutoDeploymentApplication.class, args);
    }
}
