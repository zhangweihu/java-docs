# 第九章 Spring Boot 入门

> 本章掌握：Spring Boot 核心思想、创建第一个项目、RESTful API、整合 MyBatis、配置文件、异常处理、邮件与打包部署。
> Spring Boot 是当今 Java 企业级开发的**事实标准**，学完前八章基础后，本章带你进入真正的 Web 开发。

## 9.1 什么是 Spring Boot

### 9.1.1 为什么要用 Spring Boot

传统 Spring 开发存在大量痛点：

| 痛点 | Spring Boot 的解决方式 |
| --- | --- |
| 繁琐的 XML 配置 | **自动配置**：根据依赖自动装配 Bean |
| 手动引入一堆依赖 | **起步依赖**：一个依赖解决一类场景 |
| 需要外置 Tomcat 部署 | **内嵌服务器**：`java -jar` 直接运行 |
| 环境配置困难 | **约定大于配置**：默认值开箱即用 |

**一句话**：Spring Boot = Spring 框架 + 自动配置 + 内嵌服务器 + 约定大于配置，让开发"开箱即用"。

### 9.1.2 版本选择

| 版本 | JDK 要求 | 包名 | 说明 |
| --- | --- | --- | --- |
| Spring Boot 2.7.x | JDK 8+ | `javax.*` | 老项目主流，企业存量多 |
| **Spring Boot 3.x** | **JDK 17+** | **`jakarta.*`** | **当前主流，本文以此为例** |

> 二者 API 几乎一致，主要区别是包名 `javax.servlet.*` → `jakarta.servlet.*`。本章示例基于 **Spring Boot 3.x + JDK 17**。

## 9.2 创建第一个项目

### 9.2.1 方式一：Spring Initializr（推荐）

访问 https://start.spring.io/ 生成项目，或使用 IDEA：`File → New → Project → Spring Initializr`。

关键选择：
- **Language**：Java
- **Type**：Maven
- **Spring Boot**：3.x
- **Java**：17
- **Dependencies**：Spring Web

### 9.2.2 项目结构说明

```
demo/
├── pom.xml                          # Maven 依赖管理
└── src/main/
    ├── java/com/example/demo/
    │   ├── DemoApplication.java     # 启动类（唯一入口）
    │   └── controller/              # 控制层（接收请求）
    │   └── service/                 # 业务层（业务逻辑）
    │   └── mapper/ (或 dao/)        # 数据访问层（SQL）
    │   └── entity/ (或 pojo/)       # 实体类
    │   └── config/                  # 配置类
    └── resources/
        ├── application.yml          # 配置文件
        └── static/                  # 静态资源（html/css/js）
```

### 9.2.3 pom.xml（核心依赖）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 父工程：统一管理版本号 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>demo</name>
    <description>Spring Boot 学习项目</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Web 起步依赖：包含 Spring MVC + 内嵌 Tomcat -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 打包插件：生成可执行的 jar（内嵌 Tomcat） -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 9.2.4 启动类

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类：@SpringBootApplication 组合了三个注解
 * - @SpringBootConfiguration  配置类
 * - @EnableAutoConfiguration   开启自动配置
 * - @ComponentScan             扫描本包及子包的组件
 */
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("======== Spring Boot 启动成功！======");
        System.out.println("访问地址：http://localhost:8080");
    }
}
```

### 9.2.5 第一个 REST 接口

```java
package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @RestController = @Controller + @ResponseBody
 * 返回 JSON（而不是跳转页面）
 */
@RestController
public class HelloController {

    // GET 请求：http://localhost:8080/hello
    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring Boot!";
    }

    // 返回对象时自动转为 JSON
    @GetMapping("/user")
    public User getUser() {
        return new User(1, "张三", 20);
    }

    // 内部类（演示用，实际放 entity 包）
    public static class User {
        private int id;
        private String name;
        private int age;

        public User(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }
}
```

### 9.2.6 启动与测试

```powershell
# 方式一：IDEA 中直接运行 DemoApplication 的 main 方法

# 方式二：命令行
mvn spring-boot:run

# 方式三：打包后运行
mvn package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

浏览器访问 `http://localhost:8080/hello`：

```
Hello, Spring Boot!
```

访问 `http://localhost:8080/user` 返回 JSON：

```json
{"id":1,"name":"张三","age":20}
```

## 9.3 常用注解速查

### 9.3.1 请求映射注解

```java
package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")   // 类级前缀，统一路径
public class AnnotationDemo {

    // ===== 请求方法 =====
    @GetMapping("/get")        // 查询：GET /api/get
    public String get() {
        return "GET 请求";
    }

    @PostMapping("/post")      // 新增：POST /api/post
    public String post() {
        return "POST 请求";
    }

    @PutMapping("/put")        // 修改：PUT /api/put
    public String put() {
        return "PUT 请求";
    }

    @DeleteMapping("/delete")  // 删除：DELETE /api/delete
    public String delete() {
        return "DELETE 请求";
    }

    // ===== 参数接收 =====
    // @RequestParam：接收 ?key=value 形式（GET 的查询参数）
    // http://localhost:8080/api/param?name=张三&age=20
    @GetMapping("/param")
    public Map<String, Object> param(
            @RequestParam String name,               // 必填参数
            @RequestParam(defaultValue = "18") int age,  // 带默认值
            @RequestParam(required = false) String city) {  // 可选
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("age", age);
        map.put("city", city == null ? "未知" : city);
        return map;
    }

    // @PathVariable：接收路径参数
    // http://localhost:8080/api/user/5
    @GetMapping("/user/{id}")
    public String pathVariable(@PathVariable("id") Long id) {
        return "查询用户 id = " + id;
    }

    // @RequestBody：接收 JSON 请求体（POST 常用）
    // Body 示例：{"name":"李四","age":22}
    @PostMapping("/user")
    public Map<String, Object> body(@RequestBody Map<String, Object> user) {
        System.out.println("收到用户：" + user);
        return user;   // 原样返回
    }
}
```

> 用 **Postman / IDEA HTTP Client / curl** 发送不同请求测试。

## 9.4 分层架构与完整 CRUD 案例

以"用户管理"为例，演示 **Controller → Service → Entity** 三层架构（暂用内存存储，9.6 节接入数据库）。

### 9.4.1 实体类 entity/User.java

```java
package com.example.demo.entity;

public class User {
    private Long id;
    private String name;
    private Integer age;
    private String email;

    public User() {}

    public User(Long id, String name, Integer age, String email) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

### 9.4.2 业务层 service/UserService.java

```java
package com.example.demo.service;

import com.example.demo.entity.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Service：业务层组件，交给 Spring 容器管理
 * 此处用 ConcurrentHashMap 模拟数据库（9.6 节替换为 MyBatis）
 */
@Service
public class UserService {
    // 模拟数据库表
    private final ConcurrentHashMap<Long, User> db = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    // 新增
    public User add(User user) {
        long id = idGenerator.getAndIncrement();
        user.setId(id);
        db.put(id, user);
        return user;
    }

    // 根据 id 查询
    public User findById(Long id) {
        return db.get(id);
    }

    // 查询全部
    public List<User> findAll() {
        return new ArrayList<>(db.values());
    }

    // 修改
    public User update(Long id, User user) {
        if (!db.containsKey(id)) {
            throw new RuntimeException("用户不存在：" + id);
        }
        user.setId(id);
        db.put(id, user);
        return user;
    }

    // 删除
    public boolean delete(Long id) {
        return db.remove(id) != null;
    }
}
```

### 9.4.3 控制层 controller/UserController.java

```java
package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RESTful 风格接口设计：
 * GET    /users       查询所有
 * GET    /users/{id}  查询单个
 * POST   /users       新增
 * PUT    /users/{id}  修改
 * DELETE /users/{id}  删除
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired   // 依赖注入：Spring 自动注入 UserService 实例
    private UserService userService;

    @GetMapping
    public List<User> list() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public User detail(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public User add(@RequestBody User user) {
        return userService.add(user);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return userService.delete(id);
    }
}
```

### 9.4.4 测试接口（curl 示例）

```powershell
# 新增用户
curl -X POST http://localhost:8080/users -H "Content-Type: application/json" -d "{\"name\":\"张三\",\"age\":20,\"email\":\"zhang@qq.com\"}"

# 查询所有
curl http://localhost:8080/users

# 查询单个
curl http://localhost:8080/users/1

# 修改
curl -X PUT http://localhost:8080/users/1 -H "Content-Type: application/json" -d "{\"name\":\"张三改\",\"age\":21,\"email\":\"zhang@qq.com\"}"

# 删除
curl -X DELETE http://localhost:8080/users/1
```

## 9.5 配置文件 application.yml

### 9.5.1 基础配置

```yaml
# src/main/resources/application.yml
server:
  port: 8080                  # 服务端口
  servlet:
    context-path: /demo       # 上下文路径（可选）：所有接口加 /demo 前缀

spring:
  application:
    name: demo-project        # 应用名
  profiles:
    active: dev               # 激活的环境（见下节）
```

### 9.5.2 多环境配置

```yaml
# application-dev.yml（开发环境）
server:
  port: 8080

# application-prod.yml（生产环境）
server:
  port: 80
```

切换环境只需修改 `spring.profiles.active`：

```yaml
spring:
  profiles:
    active: prod   # 启动时加载 application-prod.yml
```

### 9.5.3 读取自定义配置

```yaml
# application.yml 中自定义配置
myapp:
  name: 我的应用
  version: 1.0.0
  author: 张三
  features:
    - 用户管理
    - 邮件通知
```

```java
package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 方式一：@ConfigurationProperties 批量绑定（推荐）
 * 属性名与 yml 中的 myapp 对应，自动映射
 */
@Component
@ConfigurationProperties(prefix = "myapp")
public class MyAppProperties {
    private String name;
    private String version;
    private String author;
    private List<String> features;

    // getter / setter（必须写）
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
}
```

```java
// 使用方式二：@Value 单个取值
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigDemo {

    @Value("${myapp.name}")        // 从配置取单个值
    private String appName;

    @Value("${server.port}")
    private int port;

    @GetMapping("/config")
    public String config() {
        return "应用名：" + appName + "，端口：" + port;
    }
}
```

## 9.6 整合 MyBatis 操作数据库

### 9.6.1 添加依赖

```xml
<!-- pom.xml 中追加 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
```

### 9.6.2 配置数据源

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/java_demo?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  configuration:
    map-underscore-to-camel-case: true   # 下划线转驼峰：user_name → userName
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 打印 SQL 日志
```

```sql
-- 准备表（复用第六章的 java_demo 库）
CREATE TABLE IF NOT EXISTS user (
    id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    name  VARCHAR(50) NOT NULL,
    age   INT,
    email VARCHAR(100)
);
```

### 9.6.3 Mapper 接口（注解方式 SQL）

```java
package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * @Mapper：MyBatis 数据访问接口
 * 使用注解直接写 SQL，无需 XML（简单场景够用）
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user")
    List<User> findAll();

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    // useGeneratedKeys：自动获取自增主键回填到实体
    @Insert("INSERT INTO user(name, age, email) VALUES(#{name}, #{age}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE user SET name=#{name}, age=#{age}, email=#{email} WHERE id=#{id}")
    int update(User user);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
```

### 9.6.4 Service 改用 Mapper

```java
package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public User add(User user) {
        userMapper.insert(user);   // 自增主键自动回填到 user.id
        return user;
    }

    public User findById(Long id) {
        return userMapper.findById(id);
    }

    public List<User> findAll() {
        return userMapper.findAll();
    }

    public User update(Long id, User user) {
        if (userMapper.findById(id) == null) {
            throw new RuntimeException("用户不存在：" + id);
        }
        user.setId(id);
        userMapper.update(user);
        return user;
    }

    public boolean delete(Long id) {
        return userMapper.delete(id) > 0;
    }
}
```

> Controller 层无需改动 —— 分层的好处就在于此，替换数据访问实现不影响上层。

## 9.7 统一响应结果与全局异常处理

### 9.7.1 统一响应类 common/Result.java

```java
package com.example.demo.common;

/**
 * 统一响应结构：{code: 200, message: "成功", data: ...}
 */
public class Result<T> {
    private int code;        // 状态码：200 成功，500 失败
    private String message;  // 提示信息
    private T data;          // 数据

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
```

### 9.7.2 自定义业务异常 exception/BusinessException.java

```java
package com.example.demo.exception;

/**
 * 业务异常：例如"用户不存在"、"库存不足"
 */
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }
}
```

### 9.7.3 全局异常处理器 exception/GlobalExceptionHandler.java

```java
package com.example.demo.exception;

import com.example.demo.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @RestControllerAdvice：全局异常处理
 * 所有 Controller 抛出的异常都会被这里统一捕获，返回规范 JSON
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    // 处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();
        return Result.error(400, msg);
    }

    // 处理所有其他异常（兜底）
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        e.printStackTrace();
        return Result.error("系统异常，请稍后重试");
    }
}
```

### 9.7.4 Controller 使用 Result + 参数校验

```java
package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<List<User>> list() {
        return Result.success(userService.findAll());
    }

    @GetMapping("/{id}")
    public Result<User> detail(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在：" + id);   // 交给全局异常处理
        }
        return Result.success(user);
    }

    @PostMapping
    public Result<User> add(@RequestBody @Valid User user) {   // @Valid 触发校验
        return Result.success("添加成功", userService.add(user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!userService.delete(id)) {
            throw new BusinessException("删除失败：用户不存在");
        }
        return Result.success(null);
    }
}
```

### 9.7.5 实体类加校验注解

```java
package com.example.demo.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class User {
    private Long id;

    @NotBlank(message = "姓名不能为空")            // 非空
    private String name;

    @NotNull(message = "年龄不能为空")
    @Min(value = 0, message = "年龄不能小于 0")     // 最小值
    private Integer age;

    @Email(message = "邮箱格式不正确")              // 邮箱格式
    private String email;

    // getter / setter（省略，同 9.4.1）
}
```

```xml
<!-- 别忘了加校验依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

测试效果：POST 空姓名 → 返回 `{"code":400,"message":"姓名不能为空","data":null}`。

## 9.8 整合邮件发送（衔接第八章）

Spring Boot 封装了 JavaMail，配置更简单（第八章知识直接迁移）。

### 9.8.1 依赖与配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

```yaml
# application.yml（QQ 邮箱示例）
spring:
  mail:
    host: smtp.qq.com
    port: 465
    username: 你的QQ号@qq.com
    password: 你的16位授权码      # 注意：是授权码，不是密码
    default-encoding: UTF-8
    properties:
      mail:
        smtp:
          ssl:
            enable: true
          auth: true
```

### 9.8.2 发送邮件 service/MailService.java

```java
package com.example.demo.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;   // Spring Boot 自动注入

    // 发送纯文本邮件
    public void sendText(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    // 发送 HTML 邮件
    public void sendHtml(String to, String subject, String html) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);          // true = HTML 格式
        mailSender.send(mimeMessage);
    }

    // 异步发送（@Async：放入线程池执行，接口立即返回）
    @Async
    public void sendAsync(String to, String subject, String html) throws MessagingException {
        sendHtml(to, subject, html);
        System.out.println("异步邮件已发送 -> " + to);
    }

    // 发送验证码
    public void sendCode(String to, String code) throws MessagingException {
        String html = "<div style='padding:20px;border:1px solid #ddd;'>"
                + "<h3>邮箱验证</h3>"
                + "<p>您的验证码是：<b style='color:#e4393c;font-size:24px;'>" + code + "</b></p>"
                + "<p>5 分钟内有效，请勿泄露。</p></div>";
        sendHtml(to, "验证码", html);
    }
}
```

### 9.8.3 开启异步支持

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync   // 开启异步支持（@Async 生效）
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 9.8.4 验证码接口

```java
package com.example.demo.controller;

import com.example.demo.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@RestController
@RequestMapping("/mail")
public class MailController {

    @Autowired
    private MailService mailService;

    // GET http://localhost:8080/mail/code?to=xx@qq.com
    @GetMapping("/code")
    public String sendCode(@RequestParam String to) {
        try {
            String code = String.format("%06d", new Random().nextInt(1000000));
            mailService.sendAsync(to, "验证码", null);   // 实际应传入生成的 code HTML
            return "验证码已发送到：" + to;
        } catch (Exception e) {
            return "发送失败：" + e.getMessage();
        }
    }
}
```

> 小优化：验证码应存入 Redis 或内存并设置过期时间（5 分钟），比对时校验。真实项目建议用 Redis。

## 9.9 日志与监控

```java
package com.example.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogDemo {
    // SLF4J 日志门面（Logback 是默认实现）
    private static final Logger log = LoggerFactory.getLogger(LogDemo.class);

    @GetMapping("/log")
    public String test() {
        log.trace("trace 级别");
        log.debug("debug 级别");
        log.info("info 级别，业务日志");
        log.warn("warn 级别，警告");
        log.error("error 级别，错误：{}", new RuntimeException("演示异常"));
        return "日志测试完成";
    }
}
```

```yaml
# 日志级别配置
logging:
  level:
    root: info                  # 全局级别
    com.example.demo: debug     # 指定包级别
  file:
    name: logs/app.log          # 输出到文件
```

## 9.10 打包与部署

### 9.10.1 打包

```powershell
cd demo
mvn clean package          # 跳过测试可加 -DskipTests
```

生成 `target/demo-0.0.1-SNAPSHOT.jar`（**内嵌 Tomcat，无需外置服务器**）。

### 9.10.2 运行

```powershell
# 本地运行
java -jar target/demo-0.0.1-SNAPSHOT.jar

# 指定端口
java -jar target/demo-0.0.1-SNAPSHOT.jar --server.port=9090

# 指定环境
java -jar target/demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# 后台运行（Linux）
nohup java -jar app.jar > app.log 2>&1 &
```

### 9.10.3 部署到服务器

```bash
# 1. 上传 jar 到服务器
scp target/demo.jar root@服务器IP:/opt/app/

# 2. 服务器上运行
cd /opt/app && nohup java -jar demo.jar > app.log 2>&1 &

# 3. 查看日志
tail -f app.log
```

## 9.11 小结与练习

**本章重点**：
- Spring Boot 核心：自动配置、起步依赖、内嵌服务器、约定大于配置
- RESTful API 与常用注解
- 三层架构：Controller → Service → Mapper
- 整合 MyBatis 与 MySQL
- 统一响应 Result + 全局异常处理
- 整合邮件（JavaMailSender）
- 多环境配置、打包部署

**课后练习**：
1. 新建一个 Spring Boot 项目，实现学生信息的 RESTful CRUD（内存版）。
2. 接入 MySQL，用 MyBatis 完成真实数据库的增删改查。
3. 为接口增加参数校验和全局异常处理，返回统一 JSON。
4. 实现"注册发送验证码"功能：验证码 5 分钟有效，发送 HTML 邮件（提示：用 Map 模拟存储）。
5. 将项目打包部署到本机，用浏览器和 Postman 完整测试一遍。

上一章：[08-发送邮件.md](./08-发送邮件.md) | 下一章：[10-Lombok与MapStruct.md](./10-Lombok与MapStruct.md) | 返回目录：[README.md](./README.md)
