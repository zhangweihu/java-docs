# 第三十一章 Spring Boot 项目实战指南（从 0 到 1 的企业级单应用）

> 前 30 章是"知识点"，本章是"**把这些知识点组装成一个完整可上线的项目**"。以商城系统为例，带你走完 Spring Boot 单体应用的完整开发流程：需求 → 架构 → 分层 → 业务 → 缓存 → 安全 → 测试 → 部署。

## 31.1 项目背景与需求分析

### 31.1.1 项目定位

**xx商城系统（单体版）**：一个面向中小企业的小型电商后台，包含商品、订单、用户、购物车等核心模块。目标：**用 4 周时间，单人完成一个可演示、可部署、代码规范、有缓存有安全的完整项目。**

> 为什么做单体而不是微服务？单体是微服务的基础，**先把单体的分层、解耦、规范做对**，拆微服务只是"把模块搬出去"。面试官也更喜欢听到"我先把单体做好，再按业务边界拆分"的演进思路。

### 31.1.2 功能需求清单

| 模块 | 功能 | 涉及知识点 |
| --- | --- | --- |
| 用户模块 | 注册、登录（JWT）、个人信息 | Spring Security、Redis、参数校验 |
| 商品模块 | 商品 CRUD、分类、上下架、分页搜索 | MyBatis-Plus、逻辑删除 |
| 购物车模块 | 加入/修改/删除、合并计算 | Redis（Hash） |
| 订单模块 | 下单、支付回调、订单列表 | 分布式锁、事务、状态机 |
| 管理端 | 商品管理、订单管理、数据统计 | RBAC 权限 |
| 周边功能 | 图片上传、邮件通知、定时任务 | 文件存储、JavaMail、XXL-Job |

## 31.2 技术选型与架构设计

### 31.2.1 技术栈清单

| 层面 | 技术 | 版本建议 |
| --- | --- | --- |
| 语言/框架 | Java + Spring Boot | JDK 17 + Spring Boot 3.x |
| ORM | MyBatis-Plus | 3.5+ |
| 数据库 | MySQL 8.0（第 25 章调优） | 8.0 |
| 缓存 | Redis 7.x（第 12/29 章） | 7.x |
| 安全 | Spring Security + JWT（第 13 章） | 6.x |
| 工具 | Lombok + MapStruct（第 10 章） | 最新稳定版 |
| 部署 | Docker + Docker Compose（第 19 章） | 最新 |
| 监控 | Actuator + Prometheus（第 30 章） | 3.x |

### 31.2.2 分层架构（标准三层 + 收口）

```
┌─────────────────────────────────────────────┐
│ Controller 表现层：接收参数、返回 Result，不写业务      │
├─────────────────────────────────────────────┤
│ Service 业务层：事务边界、业务规则、组合调用             │
├─────────────────────────────────────────────┤
│ Mapper 数据层：SQL / MyBatis-Plus              │
└─────────────────────────────────────────────┘
        ├── common：统一响应、异常、工具类
        ├── config：Redis/安全/跨域等配置类
        └── dto/vo：入参出参隔离（第 10 章 MapStruct）
```

### 31.2.3 包结构规范（实战标准）

```
com.xxx.mall
├── MallApplication.java            # 启动类
├── common/                         # 公共
│   ├── result/                     # Result、ResultCode
│   ├── exception/                  # 业务异常、全局处理器
│   └── utils/                      # 工具类
├── config/                         # 配置类（Redis/安全/跨域）
├── controller/                     # 表现层
├── service/                        # 接口
│   └── impl/                       # 实现
├── mapper/                         # 数据层
├── entity/                         # 实体
├── dto/                            # 入参对象
└── vo/                             # 出参对象
```

> **规范的意义**：团队协作时，任何新人都能在 10 分钟内找到自己要改的类。**面试问"你的项目结构怎么设计的"，答这套就够**。

## 31.3 项目初始化

### 31.3.1 创建工程

```xml
<!-- pom.xml 核心依赖 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>   <!-- 参数校验 -->
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>   <!-- Redis -->
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>    <!-- 安全 -->
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>3.5.7</version>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 31.3.2 application.yml 分层配置

```yaml
# application.yml（公共）
spring:
  application:
    name: mall
  profiles:
    active: dev

---
# application-dev.yml（开发）
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mall?useUnicode=true&characterEncoding=utf8
    username: root
    password: 123456
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      lettuce:
        pool:
          max-active: 20      # 连接池（29.10 强调过不能默认）
          max-idle: 10
          max-wait: 3000ms

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发打印 SQL
  global-config:
    db-config:
      logic-delete-field: deleted      # 逻辑删除（第 17 章）
      id-type: assign_id               # 雪花 ID（第 25 章分库分表全局 ID）
```

## 31.4 统一响应与异常处理（每个项目的第一步）

### 31.4.1 统一响应体 Result

```java
@Data
public class Result<T> {
    private Integer code;      // 0 成功，非 0 失败
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
```

### 31.4.2 业务异常 + 全局处理器

```java
// 业务异常：业务逻辑主动抛出
public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() { return code; }
}

// 全局异常处理：拦截所有异常，统一包装成 Result
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);   // 未知异常一定要打完整堆栈
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
```

> **为什么 Controller 不自己 try-catch？** 异常处理是横切关注点，统一收口到 `@RestControllerAdvice`，业务代码只关心正常流程，错误处理交给框架。这也是第 28 章 SRP 的落地。

## 31.5 数据层设计

### 31.5.1 核心表结构

```sql
CREATE TABLE `user` (
  `id`          BIGINT NOT NULL COMMENT '雪花ID',
  `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  `nickname`    VARCHAR(50)  DEFAULT NULL,
  `phone`       VARCHAR(20)  DEFAULT NULL,
  `status`      TINYINT      DEFAULT 1 COMMENT '1正常 0禁用',
  `deleted`     TINYINT      DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `product` (
  `id`           BIGINT NOT NULL,
  `name`         VARCHAR(100) NOT NULL,
  `category_id`  BIGINT NOT NULL,
  `price`        DECIMAL(10,2) NOT NULL,
  `stock`        INT NOT NULL DEFAULT 0 COMMENT '库存',
  `image`        VARCHAR(255) DEFAULT NULL,
  `status`       TINYINT DEFAULT 1 COMMENT '1上架 0下架',
  `deleted`      TINYINT DEFAULT 0,
  `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 订单表（核心表，字段务必按规范设计）
CREATE TABLE `orders` (
  `id`           BIGINT NOT NULL,
  `order_no`     VARCHAR(32) NOT NULL COMMENT '订单号（业务唯一）',
  `user_id`      BIGINT NOT NULL,
  `total_amount` DECIMAL(10,2) NOT NULL,
  `status`       TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已发货 3已完成 4已取消',
  `address`      VARCHAR(255) DEFAULT NULL,
  `pay_time`     DATETIME DEFAULT NULL,
  `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

**设计要点**：
- `deleted` 逻辑删除 + 唯一索引冲突问题：`uk_username` 加 `deleted` 联合或用"用户名+删除标记"处理
- 订单号用雪花 ID 或 `日期+随机数`，**不直接用自增**（第 25 章分库分表全局 ID）
- 金额用 `DECIMAL` 不用 `FLOAT`（精度问题）
- 状态用 `TINYINT` + 注释，不用魔法字符串

### 31.5.2 实体与 Mapper

```java
@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.ASSIGN_ID)      // 雪花 ID
    private Long id;
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    // 复杂的自定义 SQL 才需要写 XML/注解
    @Select("SELECT * FROM product WHERE name LIKE CONCAT('%', #{kw}, '%') AND status = 1")
    List<Product> searchByKeyword(@Param("kw") String kw);
}
```

## 31.6 核心业务模块实现

### 31.6.1 用户模块（注册 + 登录 + JWT）

```java
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    @Override
    public void register(RegisterDTO dto) {
        // 1. 用户名唯一性校验
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        // 2. BCrypt 加密存储（第 13 章：绝不能存明文）
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(new BCryptPasswordEncoder().encode(dto.getPassword()));
        userMapper.insert(user);
    }

    @Override
    public String login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        // 1. 校验密码
        if (user == null || !new BCryptPasswordEncoder()
                .matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }
        // 2. 生成 JWT（第 13 章 JwtUtil）
        String token = JwtUtil.createToken(user.getId(), user.getUsername());
        // 3. token 存 Redis（支持服务端注销）
        redis.opsForValue().set("login:token:" + user.getId(), token,
                Duration.ofHours(24));
        return token;
    }
}
```

### 31.6.2 下单（事务 + 锁 + 状态机，项目的重头戏）

```java
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final RedissonClient redisson;

    @Override
    @Transactional(rollbackFor = Exception.class)   // 本地事务
    public Order createOrder(OrderCreateDTO dto, Long userId) {
        // 1. 分布式锁：防同一用户重复下单
        RLock lock = redisson.getLock("order:user:" + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(429, "操作太频繁，请稍后再试");
            }
            // 2. 校验商品 & 扣库存（行级锁防超卖）
            Product product = productMapper.selectById(dto.getProductId());
            if (product == null || product.getStatus() != 1) {
                throw new BusinessException(404, "商品不存在或已下架");
            }
            int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, product.getId())
                    .gt(Product::getStock, 0)          // 库存 > 0 才扣（原子条件）
                    .setSql("stock = stock - " + dto.getNum()));
            if (rows == 0) {
                throw new BusinessException(500, "库存不足");
            }
            // 3. 生成订单（状态机：待支付）
            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setTotalAmount(product.getPrice()
                    .multiply(BigDecimal.valueOf(dto.getNum())));
            order.setStatus(0);
            orderMapper.insert(order);
            return order;
        } finally {
            if (locked) {
                lock.unlock();   // Redisson 看门狗 + 只释放自己的锁
            }
        }
    }
}
```

**这一段的面试价值**：同时展示了 **@Transactional 事务 + Redisson 分布式锁 + 乐观扣库存（条件更新）+ 订单状态机**四个点，回答"你的项目怎么防超卖、防重复下单"时直接引用。

### 31.6.3 支付回调（幂等处理）

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void handlePayCallback(String orderNo) {
    Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
            .eq(Order::getOrderNo, orderNo));
    if (order == null) {
        throw new BusinessException(404, "订单不存在");
    }
    // 幂等：只有"待支付"状态才允许流转到"已支付"
    if (order.getStatus() != 0) {
        log.warn("订单 {} 状态已不是待支付，忽略重复回调", orderNo);
        return;
    }
    order.setStatus(1);
    order.setPayTime(LocalDateTime.now());
    orderMapper.updateById(order);
}
```

**支付回调的幂等**：回调可能重发多次（网络抖动），用"状态机判断"天然幂等——**只允许待支付 → 已支付，其他状态直接忽略**。这是状态机的核心价值。

## 31.7 缓存与性能优化

### 31.7.1 商品详情缓存（Cache Aside）

```java
@Override
public ProductVO getProductDetail(Long id) {
    String key = "product:detail:" + id;
    // 1. 查缓存
    String cached = redis.opsForValue().get(key);
    if (cached != null) {
        return JSON.parseObject(cached, ProductVO.class);
    }
    // 2. 缓存 miss → 查 DB（加锁防击穿，29.8 节互斥锁重建）
    RLock lock = redisson.getLock("cache:product:" + id);
    try {
        lock.lock(2, TimeUnit.SECONDS);
        cached = redis.opsForValue().get(key);   // 双检查
        if (cached != null) {
            return JSON.parseObject(cached, ProductVO.class);
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            // 3. 缓存空值防穿透（TTL 短）
            redis.opsForValue().set(key, "", Duration.ofMinutes(5));
            return null;
        }
        // 4. 回填缓存，TTL 加随机值防雪崩
        int ttl = 3600 + ThreadLocalRandom.current().nextInt(300);
        redis.opsForValue().set(key, JSON.toJSONString(product), Duration.ofSeconds(ttl));
        return convert(product);
    } finally {
        lock.unlock();
    }
}
```

### 31.7.2 购物车（Redis Hash）

```java
// 购物车用 Hash 存储：key=cart:{userId}，field=商品id，value=数量
public void addToCart(Long userId, Long productId, Integer num) {
    String key = "cart:" + userId;
    redis.opsForHash().increment(key, productId.toString(), num);
}
```

### 31.7.3 接口性能自查清单

| 检查项 | 做法 | 章节 |
| --- | --- | --- |
| 是否缓存热点数据 | 商品详情/用户信息 | 29 |
| 是否有 N+1 查询 | MyBatis-Plus `selectBatchIds` 批量 | 25 |
| 是否深分页 | 延迟关联/游标 | 25 |
| 是否循环调用 RPC/DB | 批量合并 | 25 |
| 大对象是否序列化到 Redis | 用 JSON 或 Proto | 29 |

## 31.8 安全设计

### 31.8.1 安全配置要点（完整参考第 13 章）

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/product/**").permitAll()   // 公开
                .requestMatchers("/api/admin/**").hasRole("ADMIN")                 // 管理员
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 31.8.2 接口安全清单

- **参数校验**：`@Validated` + `@NotBlank`/`@Min` 等注解，拒绝非法输入
- **SQL 注入**：MyBatis `#{}` 预编译（**不要用 `${}` 拼接**）
- **XSS**：输出编码，富文本白名单过滤
- **越权**：查询订单必须带当前登录 userId 条件（`and user_id = #{userId}`），**不能只按订单号查**
- **敏感数据**：密码 BCrypt、手机号脱敏返回
- **日志安全**：不打印密码/token 明文

## 31.9 周边功能（上传、邮件、定时任务）

### 31.9.1 文件上传

```java
@PostMapping("/api/upload")
public Result<String> upload(@RequestParam("file") MultipartFile file) {
    // 1. 校验类型/大小（防上传恶意文件）
    String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
    Set<String> allow = Set.of("jpg", "png", "gif", "webp");
    if (!allow.contains(ext)) {
        throw new BusinessException(400, "不支持的文件类型");
    }
    // 2. 文件名随机化，防覆盖/路径穿越
    String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
    // 3. 存本地（生产用 OSS/MinIO）
    file.transferTo(Paths.get(uploadDir, filename));
    return Result.ok("/files/" + filename);
}
```

### 31.9.2 异步邮件通知（第 8 章整合）

```java
@Service
public class NotificationService {
    private final JavaMailSender mailSender;
    private final ThreadPoolTaskExecutor executor;   // 独立线程池，不占 Tomcat

    public void sendOrderSuccessAsync(String to, String content) {
        executor.execute(() -> {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setTo(to);
                helper.setSubject("下单成功通知");
                helper.setText(content, true);   // HTML 内容
                mailSender.send(msg);
            } catch (MessagingException e) {
                log.error("邮件发送失败 to={}", to, e);   // 失败记录日志 + 补偿
            }
        });
    }
}
```

### 31.9.3 定时任务（订单超时关闭）

```java
// 方案一：XXL-Job 定时扫描（第 18 章）
// 方案二：Spring @Scheduled 简单场景
@Component
public class OrderTimeoutJob {
    @Scheduled(cron = "0 */5 * * * ?")   // 每 5 分钟
    public void closeTimeoutOrders() {
        // 关闭 30 分钟未支付订单（批量，注意分页防止大事务）
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        // update orders set status=4 where status=0 and create_time < deadline
    }
}
```

> **提示**：`@Scheduled` 默认单线程，多个任务会互相阻塞；生产用 XXL-Job（第 18 章）或配线程池。分页批量更新避免大事务（第 25 章）。

## 31.10 测试与联调

### 31.10.1 单元测试

```java
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    void createOrder_stockEnough_shouldSuccess() {
        // 准备数据
        // 调用
        Order order = orderService.createOrder(dto, 1L);
        // 断言
        assertNotNull(order.getOrderNo());
        assertEquals(0, order.getStatus());
    }

    @Test
    void createOrder_stockNotEnough_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> orderService.createOrder(dto, 1L));
    }
}
```

### 31.10.2 接口测试

- **Postman/IDEA HTTP Client**：冒烟测试每个接口
- **Apifox**：团队接口文档 + Mock + 自动化测试
- **压测**：JMeter 对下单/商品详情做压测，找瓶颈（第 30 章容量评估）

## 31.11 打包部署（Docker 一键上线）

### 31.11.1 打包

```bash
mvn clean package -DskipTests
# 产物：target/mall.jar
```

### 31.11.2 Dockerfile（第 19 章标准写法）

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/mall.jar app.jar
# JVM 调优参数（第 24 章）+ 容器感知内存
ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/dumps -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 31.11.3 docker-compose.yml（全栈一键启动）

```yaml
version: "3.8"
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: mall
    volumes:
      - ./sql:/docker-entrypoint-initdb.d    # 初始化建表脚本
    ports: ["3306:3306"]

  redis:
    image: redis:7
    command: redis-server --appendonly yes   # AOF 持久化
    ports: ["6379:6379"]

  mall:
    build: .
    depends_on: [mysql, redis]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:mysql://mysql:3306/mall
    ports: ["8080:8080"]
```

### 31.11.4 CI/CD 接入（第 21 章）

```
push 代码 → Jenkins 拉取 → mvn package → docker build → docker push
          → 服务器拉镜像 → docker-compose up -d → 健康检查 → 通知
```

## 31.12 项目总结与面试讲解指南

### 31.12.1 项目亮点清单（面试这样讲）

1. **分层清晰**：Controller-Service-Mapper + 统一 Result/异常处理，代码可维护性强
2. **防超卖**：乐观库存扣减（`stock > 0` 条件更新）+ Redisson 分布式锁 + 本地事务
3. **防重复支付**：订单状态机天然幂等，回调重发不重复处理
4. **缓存体系**：Cache Aside + 互斥锁防击穿 + 空值防穿透 + 随机 TTL 防雪崩
5. **安全**：BCrypt 加密 + JWT 无状态 + 参数校验 + 越权防护
6. **可观测**：统一日志 + traceId + Actuator 健康检查 + Prometheus 指标
7. **部署**：Docker Compose 一键部署 + CI/CD 自动构建

### 31.12.2 面试常问的"项目问题"

| 问题 | 回答要点 |
| --- | --- |
| 为什么做这个项目？ | 验证知识体系、完整走一遍从 0 到 1 的工程化流程 |
| 项目最大的难点？ | 并发下单（防超卖/防重复）、缓存一致性 |
| 遇到最大的坑？ | 逻辑删除 + 唯一索引冲突；`@RefreshScope` 不生效 |
| 如果用户量涨 100 倍怎么办？ | 先缓存→读写分离→分库分表→拆微服务（第 30 章） |
| 项目里最满意的设计？ | 状态机驱动订单流转、统一异常收口 |

### 31.12.3 扩展方向（进阶预告）

单体做扎实后，下一步就是第 32 章的 **Spring Cloud 微服务实战**：把用户/商品/订单拆成独立服务，用 Nacos 注册发现、Gateway 网关、Seata 分布式事务串起来。

## 31.13 小结与练习

**本章重点**：
- 项目全流程：需求 → 技术选型 → 架构 → 分层 → 编码 → 测试 → 部署
- 包结构与分层规范：common/config/controller/service/mapper
- 统一 Result + 全局异常：业务代码零 try-catch
- 防超卖三件套：事务 + 分布式锁 + 乐观扣减
- 支付回调幂等：状态机天然幂等
- 缓存五件套：Cache Aside + 互斥锁 + 空值 + 随机 TTL + 双检查
- 安全基线：BCrypt、JWT、参数校验、越权防护
- Docker Compose 一键部署

**课后练习**：
1. 按 31.3 的包结构创建项目，跑通注册/登录（带 JWT）。
2. 实现商品 CRUD + 分页 + 逻辑删除（MyBatis-Plus）。
3. 实现下单接口：事务 + 分布式锁 + 乐观扣库存，用 JMeter 并发压测验证不超卖。
4. 给商品详情加缓存（含防击穿/穿透/雪崩），压测对比优化前后 QPS。
5. 写支付回调接口，验证重复回调的幂等性。
6. 用 Docker Compose 把整个项目部署起来，走通完整业务流程。
7. 用 31.12.2 的问题表对自己做一次模拟面试。

上一章：[30-微服务治理实战.md](./30-微服务治理实战.md) | 下一章：[32-SpringCloud项目实战.md](./32-SpringCloud项目实战.md) | 返回目录：[README.md](./README.md)
