package org.forest.chatollama.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 把 Long 类型序列化为 String
            builder.serializerByType(Long.class, ToStringSerializer.instance);
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
