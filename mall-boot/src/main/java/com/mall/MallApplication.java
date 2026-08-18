package com.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商城单体应用启动类。
 *
 * 启动命令：mvn spring-boot:run 或 java -jar mall-boot-1.0.0.jar
 * 文档地址：http://localhost:8080/swagger-ui.html
 *
 * @EnableAsync      开启 @Async 异步邮件发送
 * @EnableScheduling 开启 @Scheduled 订单超时关闭任务
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.mall.mapper") // 扫描 MyBatis-Plus Mapper 接口，免写 @Mapper
public class MallApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
        System.out.println("""
                ==============================================
                  Mall 商城单体项目启动成功
                  接口文档: http://localhost:8080/swagger-ui.html
                ==============================================""");
    }
}
