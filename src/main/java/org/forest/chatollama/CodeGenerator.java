package org.forest.chatollama;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.nio.file.Paths;
import java.util.Collections;

public class CodeGenerator {

    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456")
                .globalConfig(builder -> builder
                        .author("居森林")
                        .outputDir(Paths.get(System.getProperty("user.dir")) + "/src/main/java")
                        .commentDate("yyyy-MM-dd HH:mm:ss")
                )
                .packageConfig(builder -> builder
                        .parent("org.forest.chatollama")
                        .entity("model")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .pathInfo(Collections.singletonMap(OutputFile.xml,
                                System.getProperty("user.dir") + "/src/main/resources/mapper"))  // XML 路径
                )
                .strategyConfig(builder -> {
                            builder.entityBuilder().enableLombok();
                            builder.addInclude("chat_message").controllerBuilder().enableRestStyle();
                        }
                )
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
