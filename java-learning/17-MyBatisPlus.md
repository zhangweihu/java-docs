# 第十七章 MyBatis-Plus

> 本章目标：掌握 MyBatis-Plus 的核心能力（CRUD 免写 SQL、条件构造器、分页插件、逻辑删除、乐观锁、代码生成器），让数据层开发效率翻倍。
>
> 前置知识：第九章 MyBatis（本章是其增强版，建议先复习 9.x 小节）。

## 17.1 为什么用 MyBatis-Plus

### 17.1.1 原生 MyBatis 的痛点

回顾第九章，我们用 MyBatis 写数据层，每次都要：

```xml
<select id="selectById" resultType="User">
    SELECT * FROM user WHERE id = #{id}
</select>
<insert id="insert" parameterType="User">
    INSERT INTO user(name, age, email) VALUES(#{name}, #{age}, #{email})
</insert>
<!-- 增删改查要写一堆几乎相同的 SQL -->
```

这些 SQL **千篇一律**，还要写对应的 Mapper 接口方法。CRUD 代码占了开发量的一半，却没有技术含量。

### 17.1.2 MyBatis-Plus 是什么

**MyBatis-Plus（MP）**：MyBatis 的增强工具，**只做增强不做改变**（不侵入、不替换，官方宣传语）。

```
写 SQL 版（MyBatis）：    Mapper 接口 + XML 里手写 CRUD SQL
免 SQL 版（MP）：         BaseMapper<T> 自带 20+ 个通用方法，0 SQL 完成 CRUD
```

**核心价值**：
- **BaseMapper**：继承后自动拥有增删改查，不用写一条 CRUD SQL
- **条件构造器**：`QueryWrapper` / `LambdaQueryWrapper` 链式拼条件，告别动态 SQL 拼接
- **分页插件**：一句配置，自动生成 `LIMIT`
- **逻辑删除**：一个注解搞定"删除变更新"
- **乐观锁**：一个注解 + 插件，并发更新不丢数据
- **代码生成器**：根据表自动生成 Entity/Mapper/Service/Controller

### 17.1.3 MP 与 JPA 的区别（面试常问）

| 对比项 | MyBatis-Plus | Spring Data JPA |
| --- | --- | --- |
| 风格 | 偏 SQL（可写复杂 SQL 优化） | 偏面向对象（Hibernate 自动生成 SQL） |
| 复杂查询 | 手写 SQL 灵活可控 | 复杂 SQL 难写，性能难调 |
| 学习成本 | 低（会 MyBatis 就会） | 中（要理解 ORM 生命周期） |
| 国内使用 | **极流行**（国内主流） | 较少 |
| 底层 | 封装 MyBatis | 封装 Hibernate |

> 结论：国内企业数据层**事实标准 = MyBatis + MyBatis-Plus**。

## 17.2 快速开始

### 17.2.1 依赖与配置

```xml
<!-- Spring Boot 3 用 mybatis-plus-spring-boot3-starter -->
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
```

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mp_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 控制台打印 SQL（开发期开启）
  global-config:
    db-config:
      id-type: assign_id        # 主键策略：雪花算法
```

建表 SQL：

```sql
CREATE TABLE user (
    id        BIGINT PRIMARY KEY COMMENT '主键',
    name      VARCHAR(30) COMMENT '姓名',
    age       INT COMMENT '年龄',
    email     VARCHAR(50) COMMENT '邮箱',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted   TINYINT DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    version   INT DEFAULT 0 COMMENT '乐观锁版本号'
) COMMENT '用户表';
```

### 17.2.2 实体类 + Mapper + Service

```java
package com.example.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体类：与表 user 映射（表名默认类名下划线转驼峰）
 */
@Data
@TableName("user")                    // 指定表名（驼峰→下划线可省略）
public class User {

    @TableId(type = IdType.ASSIGN_ID) // 主键策略：雪花算法（分布式唯一）
    private Long id;

    private String name;

    private Integer age;

    private String email;

    @TableField(fill = FieldFill.INSERT)      // 插入时自动填充
    private LocalDateTime createTime;

    @TableLogic                          // 逻辑删除注解：delete → update set deleted=1
    private Integer deleted;

    @Version                             // 乐观锁版本号
    private Integer version;
}
```

```java
package com.example.mp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mp.entity.User;

/**
 * Mapper 接口：继承 BaseMapper<User> 即拥有完整 CRUD，无需写 XML
 */
public interface UserMapper extends BaseMapper<User> {
    // 零方法，直接可用。需要复杂 SQL 时在这里追加自定义方法
}
```

```java
package com.example.mp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.mp.entity.User;

/**
 * Service 接口：继承 IService<User>（比 Mapper 多批量、链式查询等能力）
 */
public interface UserService extends IService<User> {
}

// 实现类
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    // 继承 ServiceImpl 后，增删改查、分页、批量保存全部开箱即用
}
```

### 17.2.3 测试

```java
@SpringBootTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testCrud() {
        // 插入（自动回填雪花 id）
        User user = new User();
        user.setName("张三");
        user.setAge(25);
        user.setEmail("zhangsan@example.com");
        userMapper.insert(user);
        System.out.println("生成的 id：" + user.getId());

        // 按 id 查询
        User u = userMapper.selectById(user.getId());
        System.out.println(u);

        // 修改（只更新非 null 字段）
        u.setAge(26);
        userMapper.updateById(u);

        // 删除（逻辑删除：实际执行 UPDATE user SET deleted=1 WHERE id=?）
        userMapper.deleteById(u.getId());
    }
}
```

> **面试常问：MP 是怎么做到免 SQL 的？**
> 运行时通过**反射**解析实体类的 `@TableName/@TableId/@TableField` 注解，结合 `BaseMapper` 接口上的泛型类型，**动态拼接 SQL**。例如 `selectById` → `SELECT id,name,age,email,... FROM user WHERE id=? AND deleted=0`。

## 17.3 常用方法速查

### 17.3.1 BaseMapper 自带方法（重点）

| 方法 | 说明 |
| --- | --- |
| `insert(entity)` | 插入一条 |
| `deleteById(id)` | 按 id 删除 |
| `delete(wrapper)` | 按条件删除 |
| `updateById(entity)` | 按 id 更新（非 null 字段） |
| `update(entity, wrapper)` | 按条件更新 |
| `selectById(id)` | 按 id 查询 |
| `selectByIds(list)` | 批量 id 查询 |
| `selectOne(wrapper)` | 查一条（多条会报错） |
| `selectList(wrapper)` | 查询列表 |
| `selectCount(wrapper)` | 统计数量 |
| `selectPage(page, wrapper)` | 分页查询 |
| `selectMaps(wrapper)` | 返回 Map 列表 |

### 17.3.2 IService 增强方法

| 方法 | 说明 |
| --- | --- |
| `save(entity)` / `saveBatch(list)` | 保存 / 批量保存 |
| `updateById` / `updateBatchById` | 更新 / 批量更新 |
| `removeById` / `remove(wrapper)` | 删除 |
| `getById` / `getOne(wrapper)` | 查询单个 |
| `list(wrapper)` / `listByIds` | 查询列表 |
| `count(wrapper)` | 计数 |
| `page(page, wrapper)` | 分页 |
| `lambdaQuery()` / `lambdaUpdate()` | 链式查询 / 更新 |

## 17.4 条件构造器（Wrapper，核心中的核心）

### 17.4.1 QueryWrapper（字符串列名版）

```java
// 需求：查询年龄在 20~30 之间、姓名含"张"、按年龄倒序的用户
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.between("age", 20, 30)
       .like("name", "张")
       .orderByDesc("age");
List<User> users = userMapper.selectList(wrapper);
```

### 17.4.2 LambdaQueryWrapper（推荐，类型安全）

列名写**方法引用**，编译期就能检查错误，字段改名自动同步：

```java
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getName, "张三")
       .gt(User::getAge, 18)
       .and(w -> w.like(User::getEmail, "@qq.com").or().isNull(User::getEmail))
       .orderByAsc(User::getAge);
List<User> users = userMapper.selectList(wrapper);
```

### 17.4.3 常用条件方法速查表

| 方法 | SQL 语义 | 示例 |
| --- | --- | --- |
| `eq` / `ne` | `=` / `<>` | `eq(User::getName, "张三")` |
| `gt` / `ge` / `lt` / `le` | `>` `>=` `<` `<=` | `gt(User::getAge, 18)` |
| `between` | `BETWEEN a AND b` | `between("age", 20, 30)` |
| `like` | `LIKE %值%` | `like(User::getName, "张")` |
| `likeLeft` / `likeRight` | `LIKE %值` / `LIKE 值%` | 索引优化用右模糊 |
| `isNull` / `isNotNull` | `IS NULL` / `IS NOT NULL` | — |
| `in` / `notIn` | `IN (...)` | `in(User::getId, 1, 2, 3)` |
| `orderByAsc` / `orderByDesc` | 排序 | — |
| `last` | 拼接 SQL 尾部（慎用，有注入风险） | `last("LIMIT 10")` |

### 17.4.4 LambdaUpdateWrapper（条件更新）

```java
// 需求：把所有姓张的用户年龄 +1
LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
updateWrapper.set(User::getAge, User::getAge)   // 占位，实际用 setSql 更直接
        .like(User::getName, "张");
userMapper.update(null, updateWrapper);
```

```java
// 更推荐：setSql 直接写 SQL 片段（age = age + 1，数据库侧原子操作）
LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
wrapper.setSql("age = age + 1").like(User::getName, "张");
userMapper.update(null, wrapper);
```

## 17.5 分页插件

### 17.5.1 配置分页拦截器

```java
package com.example.mp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件，指定数据库类型
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 17.5.2 使用分页

```java
@Test
void testPage() {
    // 第 1 页，每页 10 条
    Page<User> page = new Page<>(1, 10);

    // 分页查询（条件：按年龄倒序）
    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(User::getAge);

    Page<User> result = userMapper.selectPage(page, wrapper);

    System.out.println("总记录数：" + result.getTotal());
    System.out.println("总页数：" + result.getPages());
    System.out.println("当前页数据：" + result.getRecords());
}
```

> **注意**：分页插件原理是**拦截器在 SQL 执行前自动改写**：`SELECT ... → SELECT COUNT(*) + SELECT ... LIMIT 10`。没配插件时，`LIMIT` 不会生效（这是新手最常见的坑）。

### 17.5.3 自定义 SQL 分页

复杂查询（联表）分页时，自定义 Mapper 方法也支持分页：

```java
public interface UserMapper extends BaseMapper<User> {
    // 自定义分页查询：第一个参数必须是 Page，返回 IPage
    IPage<UserVO> selectUserWithDept(Page<UserVO> page, @Param("name") String name);
}
```

```xml
<select id="selectUserWithDept" resultType="com.example.mp.vo.UserVO">
    SELECT u.*, d.dept_name
    FROM user u
    LEFT JOIN dept d ON u.dept_id = d.id
    <where>
        <if test="name != null and name != ''">
            AND u.name LIKE CONCAT('%', #{name}, '%')
        </if>
    </where>
</select>
```

## 17.6 逻辑删除与乐观锁（高并发必学）

### 17.6.1 逻辑删除

**需求**：数据不能物理删除（要留存审计、防误删）。做法：删除 = `UPDATE deleted=1`，查询自动加 `WHERE deleted=0`。

```java
// 实体字段加 @TableLogic 即可，无需任何配置
@TableLogic
private Integer deleted;
```

效果（全自动）：

```sql
-- 调用 deleteById(1) 实际执行：
UPDATE user SET deleted=1 WHERE id=1 AND deleted=0

-- 调用 selectById(1) 自动追加：
SELECT ... FROM user WHERE id=1 AND deleted=0
```

> 全局配置也支持：`mybatis-plus.global-config.db-config.logic-delete-field: deleted`。

### 17.6.2 乐观锁（防并发更新丢失）

**问题场景**：两个人同时改同一条数据（如库存 100，A 减 1、B 减 2，都读到 100，后写的覆盖先写的）→ **丢失更新**。

**乐观锁思路**：更新前比对版本号，版本不一致说明被别人改过，放弃更新。

```java
// 1. 实体类加 @Version（见 17.2.2），配置插件（见 17.5.1 中追加一行）
interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

// 2. 使用：先查（拿到 version）→ 修改 → 更新
User user = userMapper.selectById(1L);   // version = 0
user.setAge(30);
userMapper.updateById(user);             // 执行：
// UPDATE user SET age=30, version=1 WHERE id=1 AND version=0
// 影响行数为 0 → 说明被别人改过，需要重试或提示用户
```

```java
// 3. 重试示例（更新失败自动重试 3 次）
public boolean updateWithRetry(User user) {
    for (int i = 0; i < 3; i++) {
        User latest = userMapper.selectById(user.getId());
        latest.setAge(user.getAge());
        if (userMapper.updateById(latest) > 0) {
            return true;   // 更新成功
        }
    }
    return false;          // 3 次都冲突，交给上层处理
}
```

> **面试常问：乐观锁 vs 悲观锁？**
> 乐观锁：不加数据库锁，靠 version 比对，**适合读多写少**，性能好；悲观锁：`SELECT ... FOR UPDATE` 加行锁，**适合写多、冲突激烈**，并发低。

## 17.7 代码生成器（一键生成全套代码）

根据数据表自动生成 Entity / Mapper / Service / Controller，告别手写。Spring Boot 3 使用独立模块 `mybatis-plus-generator`：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-generator</artifactId>
    <version>3.5.7</version>
</dependency>
<dependency>
    <groupId>org.apache.velocity</groupId>
    <artifactId>velocity-engine-core</artifactId>
    <version>2.3</version>
</dependency>
```

```java
package com.example.mp.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import java.util.Collections;

/**
 * 运行 main 方法，自动生成整套代码
 */
public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create(
                "jdbc:mysql://localhost:3306/mp_demo?serverTimezone=Asia/Shanghai",
                "root", "123456")
            .globalConfig(builder -> builder
                .author("你的名字")
                .outputDir("d:/project/generated")           // 输出目录
                .disableOpenDir())
            .packageConfig(builder -> builder
                .parent("com.example.mp")                    // 包名
                .moduleName("user")                          // 模块名
                .pathInfo(Collections.singletonMap(
                        OutputFile.xml, "d:/project/generated/xml")))
            .strategyConfig(builder -> builder
                .addInclude("user", "dept")                  // 要生成的表
                .entityBuilder().enableLombok()              // 实体用 Lombok
                .controllerBuilder().enableRestStyle())      // Controller 用 @RestController
            .execute();
    }
}
```

> 生成的代码开箱即用，但**复杂业务 SQL 仍需手写**——工具是提速，不是替代思考。

## 17.8 常用注解速查表

| 注解 | 作用 |
| --- | --- |
| `@TableName("表名")` | 指定实体对应表 |
| `@TableId(type=...)` | 主键策略：`AUTO` 自增 / `ASSIGN_ID` 雪花 / `INPUT` 手动 |
| `@TableField("列名")` | 字段映射（列名与属性不一致时用） |
| `@TableField(exist=false)` | 该字段**不是表字段**（如临时计算字段） |
| `@TableField(fill=FieldFill.INSERT)` | 插入时自动填充（配合 MetaObjectHandler） |
| `@TableLogic` | 逻辑删除字段 |
| `@Version` | 乐观锁版本号 |
| `@EnumValue` | 枚举映射（较少用） |
| `@TableId(type=IdType.AUTO)` | 数据库自增主键 |

**自动填充示例**（create_time/update_time 不用每次手动 set）：

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

## 17.9 小结与练习

**本章重点**：
- `BaseMapper<T>` + `IService<T>` 免写 CRUD SQL
- **LambdaQueryWrapper** 链式条件构造（类型安全，最常用）
- 分页插件必须配置，否则 LIMIT 不生效
- **逻辑删除**（`@TableLogic`）+ **乐观锁**（`@Version` + 插件）
- 代码生成器一键生成全套代码

**面试题参考**：
1. MyBatis-Plus 和 MyBatis 的区别？MP 有什么优缺点？
2. MP 免 SQL 的原理？（注解 + 反射 + 动态 SQL）
3. 条件构造器 Wrapper 有哪些？为什么推荐 Lambda 版？
4. 分页插件原理？（MybatisPlusInterceptor 拦截器改写 SQL）
5. 逻辑删除和物理删除的取舍？
6. 乐观锁怎么实现？更新冲突怎么办？

**课后练习**：
1. 建一张 `product` 表，用代码生成器生成全套代码，测试 CRUD。
2. 用 LambdaQueryWrapper 完成：价格在 100~500 之间、名称含"手机"、按销量倒序。
3. 配置分页插件，实现商品分页查询（每页 8 条）。
4. 给商品表加 `deleted`、`version` 字段，实现逻辑删除 + 乐观锁，并用多线程验证丢失更新被拦截。
5. 自定义 Mapper 方法：联表查询商品+分类，并支持分页。

上一章：[16-RabbitMQ.md](./16-RabbitMQ.md) | 下一章：[18-XXLJob.md](./18-XXLJob.md) | 返回目录：[README.md](./README.md)
