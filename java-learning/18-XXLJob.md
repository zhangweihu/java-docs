# 第十八章 XXL-Job 分布式任务调度

> 本章目标：理解定时任务从单体到分布式的演进，掌握 XXL-Job 的核心概念（调度中心/执行器/任务），会用 Spring Boot 接入 XXL-Job 完成动态配置、集群分片、失败重试等企业级任务场景。
>
> 前置知识：第五章多线程、第十五章 Spring Cloud（分布式环境下任务调度才有意义）。

## 18.1 为什么需要分布式任务调度

### 18.1.1 原生定时任务的痛点

Spring 自带的 `@Scheduled` 能做简单的定时任务：

```java
@Component
public class SimpleTask {

    // 每 5 秒执行一次（cron 表达式）
    @Scheduled(cron = "0/5 * * * * ?")
    public void task1() {
        System.out.println("定时任务执行：" + LocalDateTime.now());
    }

    // 固定间隔执行
    @Scheduled(fixedDelay = 1000)
    public void task2() {
        System.out.println("固定延迟 1 秒执行");
    }
}
```

在**单体单实例**下够用，但进入微服务/集群时代后问题暴露：

| 痛点 | 说明 |
| --- | --- |
| **重复执行** | 服务部署 3 个实例，同一个 `@Scheduled` 任务会**执行 3 遍**（如发优惠券发 3 次） |
| **无法动态配置** | 改执行时间要改代码、重新发版 |
| **无失败重试** | 任务失败静默丢失，无人知晓 |
| **无监控告警** | 任务执行情况没有可视化界面 |
| **不支持分片** | 10 万条数据处理无法分给多个机器并行 |
| **不支持日志追踪** | 任务跑了多久、在哪台机器执行的，无从查起 |

### 18.1.2 解决方案：XXL-Job

**XXL-Job** 是大众点评（许雪里）开源的**分布式任务调度平台**，国内使用率最高的任务调度中间件。

核心设计（**调度与执行分离**）：

```
┌──────────────────────┐               ┌──────────────────────┐
│     调度中心          │  HTTP 调用     │     执行器（业务服务）  │
│  xxl-job-admin       │ ─────────────► │  (集成 xxl-job-core)  │
│  · 任务管理/CRON配置   │               │  · 真正执行任务的机器    │
│  · 触发任务/失败重试    │  ◄──────────  │  · 上报心跳与执行结果    │
│  · 日志/监控/告警      │  注册/心跳      │                      │
└──────────────────────┘               └──────────────────────┘
        (独立部署)                            (多个实例 = 集群)
```

**两个角色的职责**：

| 角色 | 职责 |
| --- | --- |
| **调度中心**（admin） | 集中管理所有任务、配置 CRON、触发执行、记录执行日志、失败重试、告警 |
| **执行器**（业务服务） | 真正干活的地方，启动时自动注册到调度中心，接收调度指令执行 JobHandler |

**核心优势**：
- **只调度一次**：无论执行器部署多少个实例，一个任务默认只在一个实例执行
- **动态修改**：在控制台改 CRON，立即生效，不用重启服务
- **失败重试 + 告警**：任务失败自动重试，支持邮件/钉钉告警
- **分片广播**：任务可广播给所有实例，每个实例处理一部分数据
- **可视化管理**：执行日志、调度日志一目了然

## 18.2 快速开始：调度中心 + 执行器

### 18.2.1 启动调度中心（xxl-job-admin）

**方式一：Docker 一键启动**（推荐，含 MySQL 初始化）

```bash
# 1. 先启动 MySQL（xxl-job 需要数据库存储任务配置）
docker run -d --name mysql \
  -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=xxl_job \
  -p 3306:3306 mysql:8.0

# 2. 启动调度中心（连接上面的 MySQL）
docker run -d --name xxl-job-admin \
  -p 8080:8080 \
  -e PARAMS="--spring.datasource.url=jdbc:mysql://宿主机IP:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai --spring.datasource.username=root --spring.datasource.password=123456" \
  xuxueli/xxl-job-admin:2.4.1
```

**方式二：源码方式**（更可控，用于学习）

1. 从 GitHub 拉取 `xuxueli/xxl-job` 源码
2. 用 Navicat 执行 `doc/db/tables_xxl_job.sql` 初始化数据库
3. 修改 `xxl-job-admin` 的 `application.properties` 数据库连接
4. 启动 `XxlJobAdminApplication`，访问 `http://localhost:8080/xxl-job-admin`（默认账号密码 **admin/123456**）

> 调度中心初始化脚本里会创建 `xxl_job_qrtz_*` 等表——调度中心底层用 **Quartz** 做 CRON 触发。

### 18.2.2 创建业务服务并集成执行器

**步骤 1：引入依赖**

```xml
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>2.4.1</version>
</dependency>
```

**步骤 2：配置执行器**

```yaml
# application.yml
xxl:
  job:
    admin:
      addresses: http://localhost:8080/xxl-job-admin   # 调度中心地址
    accessToken: default_token                          # 调度中心配置的通信令牌
    executor:
      appname: order-job-executor                       # 执行器名称（调度中心要一致）
      address: ''                                       # 自动注册
      ip: ''
      port: 9999                                        # 执行器 RPC 端口
      logpath: ./logs/jobhandler/                       # 任务日志存放目录
      logretentiondays: 30                              # 日志保留天数
```

**步骤 3：配置类初始化执行器 Bean**

```java
package com.example.order.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken}")
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job 执行器初始化");
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        return executor;
    }
}
```

**步骤 4：编写第一个 JobHandler（任务处理器）**

```java
package com.example.order.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderJob {

    /**
     * 任务示例：每天凌晨统计昨日订单
     * @XxlJob 注解的 value 就是"任务Handler名称"，调度中心按它来触发
     */
    @XxlJob("orderDailyStatJob")
    public void orderDailyStatJob() {
        // 拿到本次调度的参数（在调度中心"任务参数"里配置）
        String param = XxlJobHelper.getJobParam();
        log.info("开始执行订单日报统计，参数：{}", param);

        // 模拟统计逻辑
        int count = 1000;
        log.info("昨日订单数：{}", count);

        // 汇报执行结果（成功），可在调度中心看到"处理结果"
        XxlJobHelper.handleSuccess("统计完成，订单数=" + count);
    }

    /**
     * 失败示例：演示异常处理与失败重试
     */
    @XxlJob("failDemoJob")
    public void failDemoJob() {
        try {
            // 业务逻辑...
            int result = 1 / 0;   // 故意出错
        } catch (Exception e) {
            log.error("任务执行失败", e);
            // 汇报失败，调度中心会按配置的"失败重试次数"自动重试
            XxlJobHelper.handleFail("业务执行出错：" + e.getMessage());
        }
    }
}
```

### 18.2.3 在调度中心配置任务

打开 `http://localhost:8080/xxl-job-admin`，按以下步骤操作：

1. **执行器管理** → 新增执行器：AppName 填 `order-job-executor`（与 yml 一致），名称随意 → 保存后启动业务服务，看到"注册节点"出现即成功
2. **任务管理** → 新增任务：
   - 执行器：选择 `order-job-executor`
   - 任务描述：订单日报统计
   - **调度类型**：CRON，填 `0 0 0 * * ?`（每天零点）
   - **运行模式**：BEAN（自动扫描 `@XxlJob` 注解）
   - JobHandler：`orderDailyStatJob`（与注解 value 一致）
   - 失败重试次数：3
   - 报警邮件：可填自己的邮箱
3. 保存后点击**执行一次** → 调度日志中能看到本次调度与执行日志

> **经验**：初次实验建议把 CRON 设为每 30 秒一次（`0/30 * * * * ?`）方便观察。

## 18.3 调度中心核心功能

### 18.3.1 任务管理界面字段说明

| 字段 | 说明 |
| --- | --- |
| 调度类型 | CRON / 固定速度（固定间隔）/ 父子任务（执行完触发另一个） |
| 运行模式 | **BEAN**（注解扫描）/ GLUE(Java)（在线写代码，不用发版） |
| JobHandler | BEAN 模式下对应 `@XxlJob("名字")` 的名字 |
| 阻塞处理策略 | **单机串行**（排队）/ 丢弃后续调度 / 覆盖之前调度 |
| 路由策略 | 第一个/轮询/一致性HASH/**分片广播**等（见 18.4） |
| 失败重试次数 | 失败后自动重试 N 次 |
| 超时时间 | 超过 N 秒视为失败 |
| 任务参数 | 传给 JobHandler 的字符串参数 |

### 18.3.2 调度日志与执行日志

- **调度日志**：记录"什么时候、哪台机器、调度是否成功"——点进详情还能看到执行器返回的完整日志
- **执行日志**：任务代码里 `log.info` 的内容会同步到调度中心，**在线查看、无需登录服务器**（定位问题利器）

> 这也是 xxl-job 完胜 `@Scheduled` 的地方：分布式环境下一个平台看全部任务、全部日志。

## 18.4 路由策略与分片广播（面试重点）

### 18.4.1 路由策略

执行器集群（多实例）时，任务该由谁执行？调度中心支持 10+ 种路由策略：

| 策略 | 说明 | 适用场景 |
| --- | --- | --- |
| **第一个** | 固定第一台执行 | 需要固定机器跑 |
| **轮询** | 轮流分配 | 负载均衡，最常用 |
| **随机** | 随机选一台 | — |
| **一致性HASH** | 按任务参数 hash，同一参数总落到同一机器 | 有状态任务 |
| **分片广播** | **所有机器同时执行**，参数带分片序号 | 大数据量分批处理（见下） |
| 故障转移 | 一台失败自动切下一台 | 高可用 |

### 18.4.2 分片广播：处理 100 万条数据的正确姿势

**场景**：每天凌晨要把 100 万条会员积分结转到新表。一台机器要跑很久，还容易超时。正确做法：**分片广播**，让 3 台机器并行处理，每台只处理 1/3。

```java
@XxlJob("shardingJob")
public void shardingJob() {
    // 拿到分片信息：当前是第几片（0 开始）、总共有几片
    int shardIndex = XxlJobHelper.getShardIndex();   // 0, 1, 2
    int shardTotal = XxlJobHelper.getShardTotal();   // 3

    log.info("第 {} 台机器开始处理，分片 {}/{}", shardIndex, shardIndex, shardTotal);

    // 核心思想：按 id 取模分片，每台只处理属于自己那部分数据
    // 例如 id % 3 == 0 的数据归机器 1，== 1 归机器 2，== 2 归机器 3
    for (Long memberId = 1; memberId <= 1_000_000; memberId++) {
        if (memberId % shardTotal == shardIndex) {
            processMember(memberId);   // 处理积分结转
        }
    }
}
```

**SQL 层面的分片写法**（配合 MP 的 LambdaQueryWrapper）：

```java
// 每台机器只查自己负责的数据：id % shardTotal = shardIndex
// MySQL 写法：WHERE MOD(id, 3) = 0 / MOD(id, 3) = 1 / MOD(id, 3) = 2
List<Member> list = memberMapper.selectList(
    new LambdaQueryWrapper<Member>()
        .apply("MOD(id, {0}) = {1}", shardTotal, shardIndex)
);
```

> **面试必背**：分片广播三要素——`getShardIndex()`（我是谁）、`getShardTotal()`（一共几个）、按业务主键取模均匀切分。

## 18.5 实战：动态传参 + 处理超时任务

### 18.5.1 任务参数传递

```yaml
# 调度中心"任务参数"里配置：
# {"date":"2026-08-17","type":"sale"}
```

```java
@XxlJob("paramJob")
public void paramJob() {
    String param = XxlJobHelper.getJobParam();
    log.info("任务参数：{}", param);

    // 解析 JSON（用 Jackson 或 Fastjson）
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(param);
    String date = node.get("date").asText();
    String type = node.get("type").asText();
    // 按参数执行业务...
}
```

### 18.5.2 超时任务处理（面试常问）

**问**：任务跑一半机器挂了/重启，怎么保证数据不丢？

**答**：XXL-Job 提供 **`@XxlJob` 任务超时报警 + 幂等设计**，最稳妥的方案是配合数据库"任务执行记录表"实现**幂等**：

```java
@XxlJob("idempotentJob")
public void idempotentJob() {
    String bizDate = XxlJobHelper.getJobParam();   // 例如 2026-08-18

    // 1. 幂等校验：同一批次的处理记录已存在则直接跳过
    JobRecord record = jobRecordMapper.selectOne(
        new LambdaQueryWrapper<JobRecord>()
            .eq(JobRecord::getBizDate, bizDate)
            .eq(JobRecord::getStatus, "SUCCESS"));
    if (record != null) {
        XxlJobHelper.handleSuccess("该批次已处理，跳过");
        return;
    }

    // 2. 处理业务...

    // 3. 记录完成状态（数据库唯一键兜底防重）
    jobRecordMapper.insert(new JobRecord(bizDate, "SUCCESS"));
}
```

### 18.5.3 与 Spring Cloud / 微服务结合

- 执行器是**业务服务本身**，天然适合挂在任意微服务上（如 `order-service` 里集成执行器，管理订单相关任务）
- 通过 Feign 调用其他服务完成任务协作（衔接第十五章）
- 任务结果落库 + Redis 缓存，与第十二、十七章联动

## 18.6 小结与练习

**本章重点**：
- 分布式任务调度的痛点：重复执行、无法动态配置、无重试告警
- XXL-Job 架构：**调度中心**（管调度）+ **执行器**（干活的）分离
- `@XxlJob("名字")` 注解 + `XxlJobHelper`（拿参数/汇报结果/拿分片）
- 集群唯一执行（默认单台触发）与 **分片广播**（大数据量并行）
- 幂等设计：失败重试 + 数据不丢的兜底方案

**面试题参考**：
1. `@Scheduled` 在集群环境下有什么问题？怎么解决？
2. XXL-Job 的架构？调度中心和执行器怎么通信？（HTTP）
3. 路由策略有哪些？分片广播怎么用？
4. 任务重复执行怎么处理？（幂等 + 唯一键）
5. XXL-Job 和 Elastic-Job / Quartz 的区别？
6. 任务执行到一半服务重启怎么办？（失败重试 + 幂等 + 记录表）

**课后练习**：
1. 用 Docker 启动 xxl-job-admin，创建一个执行器并注册成功。
2. 写一个 `@XxlJob("testJob")`，调度中心配置 CRON 每 30 秒执行一次，观察日志。
3. 实现分片广播任务：3 个实例并行处理 1~10000 的数字，每台只处理自己的部分，验证总数正确。
4. 给任务配置"失败重试 2 次"，故意抛异常，观察调度中心的执行日志与重试记录。
5. 设计一个"生成每日报表"任务的幂等方案。

上一章：[17-MyBatisPlus.md](./17-MyBatisPlus.md) | 下一章：[19-Docker与K8s.md](./19-Docker与K8s.md) | 返回目录：[README.md](./README.md)
