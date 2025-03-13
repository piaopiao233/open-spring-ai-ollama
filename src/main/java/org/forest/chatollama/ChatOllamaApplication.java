package org.forest.chatollama;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.forest.chatollama.mapper")
public class ChatOllamaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatOllamaApplication.class, args);
    }

}
