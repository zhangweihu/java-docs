# 第十章 Lombok 与 MapStruct

> 本章掌握：Lombok 常用注解与原理、MapStruct 对象映射、编译期注解处理器的使用技巧。
> 这两个工具是**开发效率神器**：Lombok 消除样板代码，MapStruct 消除繁琐的 DTO 转换。企业项目几乎必用，也是面试高频话题。

## 10.1 Lombok 是什么

### 10.1.1 为什么需要 Lombok

写一个普通的 JavaBean，你需要手写：

```java
public class User {
    private Long id;
    private String name;
    private Integer age;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', age=" + age + "}";
    }

    // hashCode / equals 还要再写几十行...
}
```

**痛点**：字段一多，getter/setter/toString/hashCode/equals 全是机械重复代码，占了文件 80% 的篇幅，改一个字段要同步改一堆方法。

**Lombok 的答案**：只需一行注解，编译时自动生成：

```java
import lombok.Data;

@Data                        // 自动生成 getter/setter/toString/equals/hashCode
public class User {
    private Long id;
    private String name;
    private Integer age;
}
```

### 10.1.2 原理：编译期注解处理器

Lombok **不是**运行时反射，而是利用 JDK 提供的**注解处理器（Annotation Processor）**机制：

```
源码 User.java（带 @Data 注解）
   │  javac 编译时触发注解处理器
   ▼
Lombok 处理器扫描注解 → 生成方法代码 → 注入到类字节码中
   ▼
生成 User.class（含 getter/setter/toString...）
```

- **编译期生效**：生成的方法直接编译进 `.class` 字节码，**运行期零反射、零性能损耗**
- **源码不可见**：`User.java` 里看不到 `getName()`，但 IDEA 可以"反编译查看"——点击使用处或 `Alt+F7` 即可跳转
- 依赖：编译期需要，运行时**不需要**（方法已进字节码），所以依赖可以声明为 `provided` 或直接引用

### 10.1.3 环境准备

**Maven 依赖（pom.xml）：**

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.34</version>
    <scope>provided</scope>   <!-- 编译期需要，运行不需要 -->
</dependency>
```

**IDEA 配置**（IntelliJ IDEA 2020.3+ 已内置 Lombok 插件，无需安装）：
1. `Settings → Plugins`：搜索 Lombok，确认已启用
2. `Settings → Build → Compiler → Annotation Processors`：勾选 **Enable annotation processing**

> 用 Maven 命令行编译时无需额外配置（`annotationProcessorPaths` 见 10.6.2）。

### 10.1.4 Lombok 常用注解速查

| 注解 | 生成内容 | 典型使用场景 |
| --- | --- | --- |
| `@Getter` / `@Setter` | 所有字段的 getter / setter | 字段级别的精确控制 |
| `@ToString` | `toString()` 方法 | 调试打印 |
| `@EqualsAndHashCode` | `equals()` + `hashCode()` | 需要按值比较时 |
| `@NoArgsConstructor` | 无参构造器 | MyBatis/JPA 反序列化需要 |
| `@AllArgsConstructor` | 全参构造器 | 快速创建对象 |
| `@RequiredArgsConstructor` | 只含 `final`/`@NonNull` 字段的构造器 | 与 Spring 构造器注入搭配 |
| `@Data` | 上面注解的组合（Getter+Setter+ToString+EqualsAndHashCode+RequiredArgsConstructor） | **最常用**，POJO 直接加 |
| `@Builder` | 建造者模式 `User.builder().name("张三").build()` | 字段多、链式赋值 |
| `@NonNull` | 空值校验（为 null 抛 NPE） | 参数/字段非空约束 |
| `@Slf4j` | 生成 `log` 日志对象（`LoggerFactory.getLogger(...)`） | 类内直接 `log.info(...)` |
| `@Value` | 不可变类（所有字段 private final + 全参构造） | DTO / 配置对象 |

> 其他还有 `@SneakyThrows`（偷抛受检异常）、`@Cleanup`（自动 close 资源）、`@Synchronized`（方法加锁）、`@Accessors`（链式 setter）等，按需了解即可。

## 10.2 Lombok 实战

### 10.2.1 完整示例：@Data + @Builder + @Slf4j

```java
package com.example.lombokdemo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Slf4j;

/**
 * 用户实体：一个 @Data 代替几十行样板代码
 */
@Data                                  // getter/setter/toString/equals/hashCode
@Builder                               // 建造者模式
@NoArgsConstructor                     // 无参构造（MyBatis 反序列化必需）
@AllArgsConstructor                    // 全参构造
public class User {
    private Long id;
    private String name;
    private Integer age;
    private String email;
}
```

```java
package com.example.lombokdemo;

import lombok.extern.slf4j.Slf4j;

/**
 * @Slf4j：自动生成 log 变量，等价于
 *   private static final org.slf4j.Logger log =
 *       org.slf4j.LoggerFactory.getLogger(LombokDemo.class);
 */
@Slf4j
public class LombokDemo {

    public static void main(String[] args) {
        // 1. 使用 @Builder 链式创建
        User user = User.builder()
                .id(1L)
                .name("张三")
                .age(20)
                .email("zhang@qq.com")
                .build();
        log.info("创建用户：{}", user);      // 自动生成 toString，直接打印

        // 2. 使用 getter / setter（源码里没有，但编译后存在）
        user.setAge(21);
        log.info("年龄更新为：{}", user.getAge());

        // 3. 全参构造 / 无参构造
        User u2 = new User(2L, "李四", 25, "li@qq.com");
        User u3 = new User();

        // 4. equals / hashCode：两个内容相同的对象相等
        User u4 = User.builder().id(3L).name("王五").age(30).build();
        User u5 = User.builder().id(3L).name("王五").age(30).build();
        log.info("u4.equals(u5) = {}", u4.equals(u5));   // true
        log.info("u4.hashCode() == u5.hashCode() = {}",
                u4.hashCode() == u5.hashCode());          // true

        // 5. 构造器版链式（@NoArgsConstructor + @AllArgsConstructor 的另一个用途）
        System.out.println("用户：" + u2);
    }
}
```

输出效果（IDEA 中 @Slf4j 的日志带时间/级别/类名）：

```
20:31:05.123 [main] INFO com.example.lombokdemo.LombokDemo - 创建用户：User(id=1, name=张三, age=20, email=zhang@qq.com)
20:31:05.127 [main] INFO com.example.lombokdemo.LombokDemo - 年龄更新为：21
...
```

### 10.2.2 @NonNull 空值校验

```java
package com.example.lombokdemo;

import lombok.NonNull;

public class OrderService {

    // 参数为 null 时自动抛 NullPointerException，并带参数名提示
    public void createOrder(@NonNull Long userId, @NonNull String product) {
        System.out.println("创建订单：用户 " + userId + "，商品 " + product);
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();
        service.createOrder(1L, "手机");
        service.createOrder(null, "手机");   // 抛异常：userId is marked non-null but is null
    }
}
```

### 10.2.3 @RequiredArgsConstructor + Spring 构造器注入（重要）

Spring 推荐**构造器注入**，Lombok 一句话搞定：

```java
package com.example.lombokdemo.service;

import com.example.lombokdemo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * final 字段自动生成构造器参数，Spring 自动按类型注入
 * 等价于手写：
 *   public UserService(UserMapper userMapper) { this.userMapper = userMapper; }
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;   // final 字段 → 进入构造器

    public String findName(Long id) {
        return userMapper.findById(id).getName();
    }
}
```

### 10.2.4 使用注意（面试高频）

1. **@Data 与继承**：父类字段默认不参与 equals/hashCode，需要 `@EqualsAndHashCode(callSuper = true)`。
2. **@Builder 与无参构造**：`@Builder` 会生成全参构造，导致 MyBatis/JPA 反射创建对象失败 → **必须配 `@NoArgsConstructor`**（10.2.1 已演示）。
3. **序列化**：`@Data` 生成的 getter/setter 不影响序列化框架，可正常使用。
4. **不要对 JPA 实体滥用 @Data**：懒加载代理对象 + 生成的 equals/hashCode 可能引发问题，实体建议只加 `@Getter/@Setter`。
5. **版本兼容**：JDK 17 用 1.18.24+；JDK 21 用 1.18.30+；Spring Boot 3.x 管理版本一般不用手写版本号。

## 10.3 MapStruct 是什么

### 10.3.1 场景：DTO ↔ Entity 转换

分层架构中，Controller 不该直接暴露数据库实体，要转成 DTO：

```java
// 手写转换：字段一多就是灾难
public UserDTO toDTO(User user) {
    UserDTO dto = new UserDTO();
    dto.setId(user.getId());
    dto.setName(user.getName());
    dto.setAge(user.getAge());
    dto.setEmail(user.getEmail());
    dto.setCreateTime(user.getCreateTime());
    return dto;
}
```

传统的 `BeanUtils.copyProperties` 有缺陷：
- **运行时反射**，性能差
- **类型不安全**：字段名拼错不报编译错，运行期静默失败
- 类型不一致（如 `Date` → `String`）无法自动转换

**MapStruct 的答案**：定义映射接口，**编译期生成转换代码**，性能等同手写、类型安全、字段不匹配直接编译报错。

```java
@Mapper   // 一个接口，编译后自动生成实现类
public interface UserConverter {
    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    UserDTO toDTO(User user);      // 同名字段自动映射
}
```

### 10.3.2 原理：同样是编译期注解处理器

```
Converter 接口（@Mapper + 方法签名）
   │  javac 编译时 MapStruct 生成实现类
   ▼
UserConverterImpl（纯 Java 代码，逐一字段赋值，无反射）
```

生成后的代码等价于：

```java
public class UserConverterImpl implements UserConverter {
    @Override
    public UserDTO toDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setAge(user.getAge());
        dto.setEmail(user.getEmail());
        return dto;
    }
}
```

**性能**：与手写完全一致（每毫秒可执行数十万次），比 BeanUtils 快 10~100 倍。

### 10.3.3 环境准备

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

IDEA 中勾选 **Enable annotation processing**（同 10.1.3）。

## 10.4 MapStruct 实战

### 10.4.1 基础映射：实体 → DTO

```java
package com.example.mapstructdemo.entity;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String name;
    private Integer age;
    private String email;
    private LocalDateTime createTime;

    // getter / setter（可借助 @Data，见 10.6.3 组合用法）
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
```

```java
package com.example.mapstructdemo.dto;

import java.time.LocalDateTime;

public class UserDTO {
    private Long id;
    private String name;
    private Integer age;
    private String email;
    private LocalDateTime createTime;

    // getter / setter（同上，省略，读者自行补全）
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
```

```java
package com.example.mapstructdemo.converter;

import com.example.mapstructdemo.dto.UserDTO;
import com.example.mapstructdemo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @Mapper：告诉 MapStruct 这是一个映射器
 * 编译后自动生成 UserConverterImpl 实现类
 */
@Mapper
public interface UserConverter {

    // 单例模式：通过 Mappers 工厂获取实现类
    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    // 同名字段自动映射，无需任何配置
    UserDTO toDTO(User user);

    // 反向：DTO → 实体
    User toEntity(UserDTO dto);
}
```

```java
package com.example.mapstructdemo;

import com.example.mapstructdemo.converter.UserConverter;
import com.example.mapstructdemo.dto.UserDTO;
import com.example.mapstructdemo.entity.User;

import java.time.LocalDateTime;

public class MapStructDemo {

    public static void main(String[] args) {
        User user = new User();
        user.setId(1L);
        user.setName("张三");
        user.setAge(20);
        user.setEmail("zhang@qq.com");
        user.setCreateTime(LocalDateTime.now());

        // 转换：一行代码，内部是编译期生成的字段赋值代码
        UserDTO dto = UserConverter.INSTANCE.toDTO(user);

        System.out.println("DTO id    = " + dto.getId());
        System.out.println("DTO name  = " + dto.getName());
        System.out.println("DTO email = " + dto.getEmail());

        // 反向转换
        User back = UserConverter.INSTANCE.toEntity(dto);
        System.out.println("还原 name = " + back.getName());
    }
}
```

### 10.4.2 字段名不一致：@Mapping

```java
package com.example.mapstructdemo.converter;

import com.example.mapstructdemo.dto.UserDTO;
import com.example.mapstructdemo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserConverter2 {

    UserConverter2 INSTANCE = Mappers.getMapper(UserConverter2.class);

    /**
     * @Mapping 处理字段名不一致：
     *   source 是源字段名，target 是目标字段名
     *   user.createAt → dto.createTime
     *   user.age → dto.userAge（改名）
     *   ignore = true：忽略某字段不转换
     */
    @Mappings({
            @Mapping(source = "createAt", target = "createTime"),
            @Mapping(source = "age", target = "userAge"),
            @Mapping(target = "email", ignore = true)   // 不复制 email（如敏感字段脱敏）
    })
    UserDTO toDTO(User user);
}
```

### 10.4.3 类型自动转换

MapStruct 内置大量类型转换规则：

| 源类型 | 目标类型 | 说明 |
| --- | --- | --- |
| `Integer` → `String` | `String` | 自动 toString |
| `String` → `Integer` | `Integer` | 自动 parseInt |
| `Date` → `String` | `String` | 需配合 `@Mapping(dateFormat = "...")` |
| `BigDecimal` → `BigDecimal` | 任意数值类型 | 自动转换 |
| 集合 → 集合 | List/Set | 自动遍历转换每个元素 |

```java
package com.example.mapstructdemo.converter;

import com.example.mapstructdemo.dto.OrderDTO;
import com.example.mapstructdemo.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Date;
import java.util.List;

@Mapper
public interface OrderConverter {

    OrderConverter INSTANCE = Mappers.getMapper(OrderConverter.class);

    // Date → String：指定日期格式
    @Mapping(source = "createTime", target = "createTimeStr", dateFormat = "yyyy-MM-dd HH:mm:ss")
    OrderDTO toDTO(Order order);

    // 集合映射：List<Order> → List<OrderDTO>，自动遍历调用 toDTO
    List<OrderDTO> toDTOList(List<Order> orders);
}
```

### 10.4.4 多参数 / 多源映射

```java
package com.example.mapstructdemo.converter;

import com.example.mapstructdemo.dto.OrderDTO;
import com.example.mapstructdemo.entity.Order;
import com.example.mapstructdemo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface OrderConverter2 {

    OrderConverter2 INSTANCE = Mappers.getMapper(OrderConverter2.class);

    /**
     * 从两个对象取字段合并到一个 DTO：
     *   order.price + user.name → dto（多源映射，source 用 "参数名.字段名"）
     * 默认参数名：order / user
     */
    @Mapping(source = "order.price", target = "price")
    @Mapping(source = "user.name", target = "buyerName")
    @Mapping(source = "user.email", target = "buyerEmail")
    OrderDTO combine(Order order, User user);
}
```

### 10.4.5 表达式与默认值

```java
package com.example.mapstructdemo.converter;

import com.example.mapstructdemo.dto.UserDTO;
import com.example.mapstructdemo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserConverter3 {

    UserConverter3 INSTANCE = Mappers.getMapper(UserConverter3.class);

    // defaultValue：源字段为 null 时使用默认值
    @Mapping(target = "age", source = "age", defaultValue = "18")

    // expression：Java 表达式（String 类型可直接写成 "默认用户"）
    @Mapping(target = "name", expression = "java(user.getName() == null ? \"匿名\" : user.getName())")
    UserDTO toDTO(User user);
}
```

## 10.5 MapStruct 与 Spring 整合

```java
package com.example.demo.converter;

import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;

/**
 * componentModel = "spring"：生成的实现类注册为 Spring Bean
 * 使用时直接 @Autowired 注入，无需 Mappers.getMapper()
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    UserDTO toDTO(User user);

    User toEntity(UserDTO dto);
}
```

```java
package com.example.demo.controller;

import com.example.demo.converter.UserConverter;
import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor               // 构造器注入
public class UserController {

    private final UserService userService;
    private final UserConverter userConverter;   // MapStruct 生成的 Spring Bean

    // GET /users/1 → 返回 UserDTO（不暴露数据库实体字段）
    @GetMapping("/{id}")
    public UserDTO detail(@PathVariable Long id) {
        User user = userService.findById(id);
        return userConverter.toDTO(user);
    }
}
```

## 10.6 Lombok + MapStruct 组合（必看）

实体类用 `@Data` 时，MapStruct 需要读取生成的 getter/setter —— 两者的注解处理器必须在**同一次编译**中执行，且**顺序不能乱**。用 Maven 必须同时配置 `annotationProcessorPaths`：

```xml
<properties>
    <lombok.version>1.18.34</lombok.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>17</source>
                <target>17</target>
                <annotationProcessorPaths>
                    <!-- 顺序重要：Lombok 在前，MapStruct 在后 -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

```java
// 实体：Lombok 生成 getter/setter，MapStruct 正常读取
package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String name;
    private Integer age;
    private String email;
    private LocalDateTime createTime;
}
```

```java
// 转换器：正常编写即可
package com.example.demo.converter;

import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserConverter {
    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    @Mapping(target = "createTimeStr",
             source = "createTime", dateFormat = "yyyy-MM-dd HH:mm:ss")
    UserDTO toDTO(User user);
}
```

> 如果没有配置 `annotationProcessorPaths` 而报 `No property named "name" exists in source parameter(s)`，多半是 Lombok 生成代码的时机晚于 MapStruct —— 按上面的配置即可解决（这是最常见的坑）。

## 10.7 小结与练习

**本章重点**：
- Lombok：编译期注解处理器，`@Data`/`@Builder`/`@Slf4j`/`@RequiredArgsConstructor` 最常用
- MapStruct：编译期生成转换代码，比 BeanUtils 快、类型安全
- 两者共存时必须配置 `annotationProcessorPaths`（Lombok 在前）

**面试题参考**：
1. Lombok 的原理？为什么运行时不需要依赖？
2. `@Data` 与 `@Value` 的区别？（可变 vs 不可变）
3. MapStruct 相比 `BeanUtils.copyProperties` 的优势？
4. 如何解决 MapStruct 与 Lombok 的冲突？

**课后练习**：
1. 用 `@Data` + `@Builder` 重写第七章的学生实体类，删除全部手写 getter/setter。
2. 为 Spring Boot 用户管理项目增加 `UserDTO`（隐藏数据库字段如 `createTime` 格式化），用 MapStruct 转换。
3. 写一个 `OrderConverter`：`Order` + `User` → `OrderDTO`（买家信息合并，考察多源映射）。
4. 体验：把 MapStruct 生成的 `UserConverterImpl` 反编译出来，与手写代码对比性能。

上一章：[09-SpringBoot.md](./09-SpringBoot.md) | 下一章：[11-日志框架.md](./11-日志框架.md) | 返回目录：[README.md](./README.md)
