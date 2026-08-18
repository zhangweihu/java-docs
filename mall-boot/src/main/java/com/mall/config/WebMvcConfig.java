package com.mall.config;

import com.mall.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录拦截器并放行白名单。
 * 白名单之外的接口（如购物车/下单）必须携带合法 Token 才能访问。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtInterceptor())
                .addPathPatterns("/**")                       // 拦截所有请求
                .excludePathPatterns(
                        "/user/login",                        // 登录
                        "/user/register",                     // 注册
                        "/product/**",                        // 商品浏览公开
                        "/doc.html", "/webjars/**",           // 接口文档
                        "/swagger-ui/**", "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/error"                              // 错误页
                );
    }
}
