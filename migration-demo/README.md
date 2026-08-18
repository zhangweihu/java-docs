# migration-demo —— 数据迁移实操项目

> 配套文档：`../java-learning/38-数据迁移.md`（第 38 章）
> 技术栈：Spring Boot 3.2.5 + Flyway 9.22 + MySQL 8，JDK 17+

本项目演示**版本化数据库迁移**的完整链路：应用启动时 Flyway 自动按版本执行 `db/migration` 下的迁移脚本，全程只执行一次、可校验、可审计。

## 项目结构

```
migration-demo/
├── docker-compose.yml                        # 一键启动 MySQL
├── pom.xml                                   # Maven 配置（含 flyway-maven-plugin）
└── src/main/
    ├── java/com/migration/demo/
    │   └── MigrationDemoApplication.java     # 启动类（打印迁移验证结果）
    └── resources/
        ├── application.yml                   # 数据源 + Flyway 配置
        └── db/migration/                     # ★ 迁移脚本目录（重点）
            ├── V1__create_user_table.sql     # 建用户表
            ├── V2__add_user_email.sql        # 加字段 + 回填数据 + 唯一索引
            ├── V3__seed_user_data.sql        # 初始化数据
            ├── V4__create_order_table.sql    # 建订单表（组合索引）
            └── V5__add_order_status_index.sql# 加状态索引 + 演示订单
```

## 快速开始

```bash
# 1. 启动 MySQL（自动创建 migration_demo 库）
docker compose up -d

# 2. 启动应用（Flyway 自动执行 5 个迁移脚本）
mvn spring-boot:run

# 3. 观察日志：Flyway 依次执行 V1~V5，随后打印验证结果
#    Flyway 迁移历史：rank=1..5，success 全为 1
#    user 表数据条数：2（预期）
#    order 表数据：1 条
```

## 动手练习（对应 38.7）

```bash
# ① 查看迁移历史表
mysql -h 127.0.0.1 -uroot -proot123 migration_demo \
  -e "SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history;"

# ② 修改已执行的 V1 脚本（如加个注释）→ 重启应用 → 观察 validate 校验失败
#    体会"已发布脚本只增不改"，然后用 repair 修复（仅本地练习！）
mvn spring-boot:run        # 报错：Validate failed: Checksum mismatch for migration version 1
mvn flyway:repair          # 修复后重启恢复正常

# ③ 新增 V6 迁移脚本（加一张表），重启自动执行
#    cp src/main/resources/db/migration/V5__add_order_status_index.sql \
#       src/main/resources/db/migration/V6__your_change.sql

# ④ 用 R__ 脚本演示可重复迁移（每次内容变化都会重跑，适合视图/函数）
#    新建 R__init_view.sql，内容如 CREATE OR REPLACE VIEW ...，重启观察

# ⑤ 用 Flyway Maven 插件显式执行迁移（CI/CD 场景）
mvn -Dflyway.url=jdbc:mysql://localhost:3306/migration_demo \
    -Dflyway.user=root -Dflyway.password=root123 flyway:migrate
```

## 生产使用要点（详见学习文档 38.5~38.6）

- **只增不改**：已上线的迁移脚本永远不修改，改动只能新增版本（否则 checksum 校验失败）；
- **迁移先行**：迁移脚本先于（或同版本）代码生效，且 Schema 向后兼容（新列用可空/默认值）；
- **回滚方式**：社区版没有自动 undo，用"新增版本把结构改回去"实现正向回滚；
- **大表变更**：千万级表加列/加索引用影子表或 `pt-osc` / `gh-ost` 在线变更，禁止直接 ALTER；
- **备份兜底**：重大迁移前必须备份，先 staging 演练再上生产。
