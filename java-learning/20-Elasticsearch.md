# 第二十章 Elasticsearch 搜索引擎

> 本章目标：理解 ES 与 MySQL 的区别与各自定位，掌握核心概念（索引/文档/分片/倒排索引），会用 REST API 与 Spring Data Elasticsearch 完成增删改查、分词搜索、聚合统计等实战。
>
> 前置知识：第三章集合与字符串、第十七章 MyBatis-Plus（数据层概念对比）。

## 20.1 为什么需要 Elasticsearch

### 20.1.1 MySQL 搜索的痛点

商品搜索 `WHERE name LIKE '%手机%'` 有什么问题？

| 问题 | 说明 |
| --- | --- |
| **性能差** | `%关键词%` 无法走索引，全表扫描，千万级数据慢到崩溃 |
| **不支持分词** | 搜"苹果手机"匹配不到"苹果 手机"，用户要自己拼词 |
| **不支持相关度排序** | 无法按"匹配度"排序，命中次数多的不排前面 |
| **不支持纠错/联想** | 输错字搜不到，没有"你是不是想搜xxx" |
| **不支持模糊/同义词** | 搜"手机"搜不到"移动电话" |

### 20.1.2 ES 是什么

**Elasticsearch**：基于 **Lucene** 的分布式**搜索引擎**，擅长海量数据的**全文检索**与**实时分析**。

| 对比项 | MySQL | Elasticsearch |
| --- | --- | --- |
| 定位 | 事务性存储（OLTP） | 搜索与分析（OLAP） |
| 存储结构 | 表 + 行 + 列 | **索引 + 文档**（JSON） |
| 查询方式 | SQL | **RESTful JSON** / DSL |
| 搜索能力 | LIKE 弱匹配 | **倒排索引 + 分词 + 相关度** |
| 大数据量 | 千万级吃力 | **亿级轻松** |
| 事务 | 强一致（ACID） | 最终一致，**无事务** |
| 更新 | 快 | 慢（文档整体重建） |

> **结论**：MySQL 存"事实"（订单、用户），ES 存"搜索"（商品、文章、日志）。两者配合使用：**数据落 MySQL，同步到 ES 提供搜索**。

### 20.1.3 典型应用场景

- 电商商品搜索（京东、淘宝）
- 站内全文搜索（文章、文档）
- **日志检索**（ELK：Elasticsearch + Logstash + Kibana，第十一章日志的进阶）
- 搜索联想 / 拼写纠错
- 数据分析聚合（销售报表）

## 20.2 核心概念

### 20.2.1 与 MySQL 概念对照

| MySQL | Elasticsearch | 说明 |
| --- | --- | --- |
| 数据库 Database | **索引 Index** | 一个索引 = 一类文档的集合 |
| 表 Table | **类型 Type**（7.0 已废弃） | 索引下不再分类型 |
| 行 Row | **文档 Document** | 一条 JSON 数据 |
| 列 Column | **字段 Field** | JSON 里的键 |
| 主键 | `_id` | 文档唯一 ID |
| 索引（加速） | **倒排索引** | ES 的核心武器 |
| 表结构 | **映射 Mapping** | 字段类型定义 |

### 20.2.2 倒排索引（面试必考）

**正排索引**（MySQL）记录"文档里有哪些词"；**倒排索引**记录"每个词出现在哪些文档"。

```
文档1：苹果 手机 降价 促销
文档2：华为 手机 新品 发布
文档3：苹果 电脑 上新

倒排索引（词典 → 文档列表）：
   苹果 → [文档1, 文档3]
   手机 → [文档1, 文档2]
   降价 → [文档1]
   新品 → [文档2]
   华为 → [文档2]

搜"苹果手机" → 词被切成 [苹果, 手机] → 分别查词典
   → 苹果：[1,3] 手机：[1,2] → 文档1 同时命中，相关度最高排第一
```

**为什么快**：查一次词典（内存）就能定位所有相关文档，不用全表扫描。

### 20.2.3 集群与分片

```
┌─────────────────────────────────────────┐
│              ES 集群                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │ Node 1  │  │ Node 2  │  │ Node 3  │  │  ← 节点（一台 ES 实例）
│  │ 主分片0  │  │ 主分片1  │  │ 主分片2  │  │  ← 分片：数据切片存储
│  │ 副本分片1 │  │ 副本分片2 │  │ 副本分片0 │  │  ← 副本：高可用 + 分摊读
│  └─────────┘  └─────────┘  └─────────┘  │
└─────────────────────────────────────────┘
```

| 概念 | 说明 |
| --- | --- |
| 节点 Node | 一台 ES 实例，集群由多个节点组成 |
| 分片 Shard | 索引数据被切成多份分布在不同节点，支持横向扩展 |
| 副本 Replica | 分片的备份，主分片挂了自动顶上，同时分摊查询压力 |
| 集群健康 | green（正常）/ yellow（有副本缺失）/ red（有主分片不可用） |

## 20.3 安装与基础操作

### 20.3.1 Docker 安装（单机模式）

```bash
# 单节点 ES 8.x（新版本默认开启安全认证，先关掉便于学习）
docker run -d --name elasticsearch \
  -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -v /d/data/es:/usr/share/elasticsearch/data \
  elasticsearch:8.12.0

# 验证（返回 JSON 即成功）
curl http://localhost:9200
```

**安装中文分词器 IK**（搜索中文必装）：

```bash
docker exec -it elasticsearch bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.12.0/elasticsearch-analysis-ik-8.12.0.zip
docker restart elasticsearch
```

### 20.3.2 索引操作（REST API）

```bash
# 创建索引（同时定义 Mapping）
curl -X PUT "localhost:9200/product" -H "Content-Type: application/json" -d '{
  "mappings": {
    "properties": {
      "name":       { "type": "text", "analyzer": "ik_max_word" },
      "category":   { "type": "keyword" },
      "price":      { "type": "double" },
      "description":{"type": "text", "analyzer": "ik_max_word" }
    }
  }
}'

# 查看索引
curl "localhost:9200/product"

# 删除索引
curl -X DELETE "localhost:9200/product"
```

> **字段类型**：`text` 会分词（用于全文搜索）；`keyword` 不分词（用于精确匹配、排序、聚合）；`double`/`integer` 数值类型（范围查询）。

### 20.3.3 文档增删改查

```bash
# 1. 新增/覆盖文档（PUT 指定 _id）
curl -X PUT "localhost:9200/product/_doc/1" -H "Content-Type: application/json" -d '{
  "name": "Apple iPhone 15 Pro 手机",
  "category": "手机",
  "price": 8999,
  "description": "钛金属边框，A17 Pro 芯片，性能强悍"
}'

curl -X PUT "localhost:9200/product/_doc/2" -H "Content-Type: application/json" -d '{
  "name": "华为 Mate 60 Pro",
  "category": "手机",
  "price": 6999,
  "description": "卫星通话，昆仑玻璃，国产旗舰"
}'

# 2. 查询单个文档
curl "localhost:9200/product/_doc/1"

# 3. 更新文档
curl -X POST "localhost:9200/product/_doc/1/_update" -H "Content-Type: application/json" -d '{
  "doc": { "price": 8499 }
}'

# 4. 删除文档
curl -X DELETE "localhost:9200/product/_doc/2"
```

## 20.4 DSL 查询（搜索核心）

### 20.4.1 全文搜索：match（会分词）

```bash
# 搜"手机"：先分词成 [手机]，再查倒排索引，按相关度排序
curl -X POST "localhost:9200/product/_search" -H "Content-Type: application/json" -d '{
  "query": { "match": { "name": "手机" } }
}'
```

### 20.4.2 精确匹配：term（不分词）

```bash
# category 是 keyword，只能 term 精确匹配
curl -X POST "localhost:9200/product/_search" -H "Content-Type: application/json" -d '{
  "query": { "term": { "category": "手机" } }
}'
```

### 20.4.3 组合查询：bool

```bash
# 需求：手机分类 + 价格 5000~10000 + 名称含"Pro"
curl -X POST "localhost:9200/product/_search" -H "Content-Type: application/json" -d '{
  "query": {
    "bool": {
      "must":     [ { "match": { "name": "Pro" } } ],          # 必须匹配（and）
      "filter":   [ { "term": { "category": "手机" } },
                    { "range": { "price": { "gte": 5000, "lte": 10000 } } } ],
      "must_not": [ { "match": { "description": "翻新" } } ]   # 必须不匹配
    }
  },
  "sort": [ { "price": "desc" } ],          # 按价格倒序
  "from": 0, "size": 10                     # 分页
}'
```

### 20.4.4 聚合统计：aggs（Group By）

```bash
# 按分类统计商品数量（等价 SQL: SELECT category, COUNT(*) FROM product GROUP BY category）
curl -X POST "localhost:9200/product/_search" -H "Content-Type: application/json" -d '{
  "size": 0,
  "aggs": {
    "by_category": { "terms": { "field": "category" } }
  }
}'
```

### 20.4.5 DSL 速查表

| 语法 | SQL 类比 | 场景 |
| --- | --- | --- |
| `match` | `LIKE` + 分词 | 全文搜索 |
| `match_phrase` | `LIKE "%整个短语%"` | 短语精确匹配（"华为 手机"） |
| `term` | `=` | keyword 精确匹配 |
| `terms` | `IN (...)` | 多值匹配 |
| `range` | `BETWEEN` / `>` `<` | 数值/日期范围 |
| `bool(must/filter/must_not/should)` | `AND/OR/NOT` | 组合查询 |
| `exists` | `IS NOT NULL` | 字段存在性 |
| `wildcard` | `LIKE '%xx%'` | 通配符（慎用，慢） |
| `aggs` | `GROUP BY` + 聚合函数 | 统计 |

## 20.5 Spring Boot 整合（Spring Data Elasticsearch）

### 20.5.1 依赖与配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
```

### 20.5.2 实体类（@Document）

```java
package com.example.search.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * @Document 声明索引名与分片副本
 */
@Data
@Document(indexName = "product")
public class Product {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")   // 中文分词
    private String name;

    @Field(type = FieldType.Keyword)                           // 精确匹配
    private String category;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
}
```

### 20.5.3 Repository 接口

```java
package com.example.search.repository;

import com.example.search.entity.Product;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 继承 ElasticsearchRepository，自带 CRUD + 分页
 */
@Repository
public interface ProductRepository extends ElasticsearchRepository<Product, Long> {

    // 按名称搜索（方法名自动推导 DSL）
    List<Product> findByName(String name);

    // 名称包含关键词
    List<Product> findByNameContaining(String keyword);

    // 价格区间
    List<Product> findByPriceBetween(Double min, Double max);
}
```

### 20.5.4 Service 与自定义查询

```java
package com.example.search.service;

import com.example.search.entity.Product;
import com.example.search.repository.ProductRepository;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final ElasticsearchOperations operations;

    public ProductService(ProductRepository repository, ElasticsearchOperations operations) {
        this.repository = repository;
        this.operations = operations;
    }

    /** 保存（MySQL 数据落库后同步到 ES） */
    public void save(Product product) {
        repository.save(product);
    }

    /** 删除 */
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** 关键词搜索：名称或描述命中都算，按相关度排序 */
    public List<Product> search(String keyword) {
        NativeSearchQueryBuilder query = new NativeSearchQueryBuilder();
        query.withQuery(QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery("name", keyword))
                .should(QueryBuilders.matchQuery("description", keyword)));
        SearchHits<Product> hits = operations.search(query.build(), Product.class);
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}
```

### 20.5.5 Controller

```java
@RestController
@RequestMapping("/es/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam String keyword) {
        return productService.search(keyword);
    }
}
```

### 20.5.6 数据同步方案（MySQL → ES，面试常问）

| 方案 | 原理 | 适用 |
| --- | --- | --- |
| **双写** | 业务代码里先写 MySQL 再写 ES | 简单小项目（一致性难保证） |
| **MQ 异步同步** | 写 MySQL → 发消息 → 消费者写 ES（衔接第十六章） | **推荐**，解耦且削峰 |
| **Canal 监听 binlog** | 伪装成 MySQL 从库，监听到变更自动同步 | 不改业务代码，大厂标配 |
| **定时全量同步** | 定时任务全量刷新（衔接第十八章） | 数据量小、容忍延迟 |

> **典型架构**：MySQL（事务存储）→ Canal/MQ → ES（搜索）→ 查询走 ES，写操作走 MySQL。

## 20.6 ELK 日志体系（了解）

第十一章讲了日志框架，企业级日志分析用 **ELK**：

| 组件 | 职责 |
| --- | --- |
| **E**lasticsearch | 存储 + 检索日志 |
| **L**ogstash | 采集、过滤、转换日志 |
| **K**ibana | 可视化看板（图表、报表） |

```bash
# 一条命令启动 ELK
docker run -d --name elk -p 5601:5601 -p 9200:9200 -p 5044:5044 sebp/elk:8.12.0
# Kibana 界面：http://localhost:5601
```

> Java 项目常用 **Filebeat**（轻量采集）替代 Logstash 收集日志，性能更好。日志检索、错误聚合、告警都在 Kibana 完成。

## 20.7 小结与练习

**本章重点**：
- ES 定位：**全文搜索 + 大数据分析**，MySQL 存事实、ES 提供搜索
- 倒排索引原理（词典 → 文档列表，面试必考）
- 核心概念：索引/文档/Mapping/分片副本
- DSL：`match`（分词）vs `term`（精确）、`bool` 组合、`aggs` 聚合
- Spring Data ES：`@Document` + `ElasticsearchRepository`
- 数据同步：双写 / MQ / Canal

**面试题参考**：
1. 为什么 MySQL 的 LIKE 不适合搜索？倒排索引是什么？
2. ES 和 MySQL 怎么选型？数据怎么保持同步？
3. `match` 和 `term` 的区别？
4. `text` 和 `keyword` 的区别？
5. ES 写入的可靠性？（刷新、translog、副本）
6. 海量数据下 ES 搜索为什么快？（倒排索引 + 分片 + 内存缓存）

**课后练习**：
1. Docker 启动 ES 并安装 IK 分词器，用 `_analyze` API 验证"华为手机"的分词结果。
2. 建 product 索引，插入 5 条商品数据，分别用 `match`、`term`、`range`、`bool` 查询。
3. 用 Spring Data ES 写一个商品搜索接口，支持关键词 + 价格范围 + 分类过滤。
4. 实现"写 MySQL → 发 MQ → 消费者同步 ES"的完整链路（结合第十六章）。
5. 用 `aggs` 统计商品按分类的数量与平均价。

上一章：[19-Docker与K8s.md](./19-Docker与K8s.md) | 下一章：[21-JenkinsCI-CD.md](./21-JenkinsCI-CD.md) | 返回目录：[README.md](./README.md)
