package org.forest.chatollama.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class SystemConfig implements WebMvcConfigurer {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MARIADB));
        // 防止误操作全表更新插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("DateTimeModule");

            // Long 类型序列化为 String
            module.addSerializer(Long.class, ToStringSerializer.instance);

            // LocalDate
            module.addSerializer(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            module.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            // LocalDateTime
            module.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            module.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // LocalTime
            module.addSerializer(LocalTime.class,
                    new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
            module.addDeserializer(LocalTime.class,
                    new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss")));

            builder.addModule(module);
        };
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 覆盖所有接口（包括 /ChatMessage/test）
                .allowedOriginPatterns("*") // 允许所有来源（支持凭证）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 必须包含 OPTIONS
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(true) // 可选：允许携带凭证
                .maxAge(3600); // 预检请求缓存 1 小时
    }

}
