# 第七十章 DataMax 报表开发与 DDD 集成实战（用领域驱动设计驾驭企业报表）

> 本章目标：第六十九章讲了 BI 指标与可视化，本章落到具体工具——企业报表几乎都绕不开"报表工具"（DataMax 是国产 Java 报表工具的典型代表）。但报表模块从来不是"画几张表"那么简单：SQL 满天飞、口径失控、权限裸奔、改动牵一发动全身。本章以 DDD 战术设计（第四十一章）为方法论，系统讲解：报表模块如何建模成独立限界上下文、DataMax 的核心能力（设计器/数据集/参数/表达式/导出）、数据集设计如何守住架构边界（应用服务 + 读模型，杜绝 SQL 直穿领域层）、参数与数据权限如何做、Spring Boot 集成与部署实战，最后给出一套可落地的报表开发流程与踩坑清单。
>
> 前置知识：第四十一章 DDD 战术设计（实体/聚合/仓储）、第四十三章 CQRS 与读模型、第六十九章 BI 可视化（指标/口径）、第三十三章 MySQL、第九章 Spring Boot、第十七章 MyBatis-Plus。

## 70.1 为什么报表模块需要 DDD：报表开发的三宗罪

先看一个典型的"报表地狱"长什么样：

```
需求："运营要一张订单月报"
过程：运营提需求 → 开发在报表工具里写一段 200 行 SQL → 上线
结果：① 这张表只有写的人会改；② 三个报表里 GMV 口径三个样；
      ③ 报表直接连了生产库，一个慢查询拖垮主链路；④ 权限靠报表工具自带，和业务脱节
```

报表模块沦为"技术债重灾区"的根本原因，是它**一直被当成"画图活"而不是"工程活"**。DDD 给报表开发带来的不是负担，而是三样解药：

| 报表三宗罪 | DDD 解药 |
| --- | --- |
| SQL 满天飞、口径失控 | **统一语言 + 读模型**：报表只认"指标"（69 章指标平台），不认裸 SQL |
| 与业务逻辑纠缠、改动互相影响 | **独立限界上下文**：报表上下文与交易上下文解耦，各自演进 |
| 权限/校验散落、无领域规则 | **领域建模**：报表/参数/权限都是领域对象，规则进模型 |

> **本章主线**：把"报表"当成一个正经的领域来建模——报表（Report）是实体，参数（Parameter）、数据集（Dataset）、报表分类（Category）是值对象/实体，报表权限是规则——然后用 DataMax 做"呈现层"的实现载体。**DataMax 是工具，DDD 是方法，两者不冲突：工具负责画和算，模型负责边界和规则。**

## 70.2 DataMax 是什么：Java 企业报表工具的典型代表

DataMax（科多软件旗下报表产品线）是**面向 Java 平台的国产企业级报表工具**，定位是"让开发者快速做出中国式复杂报表"。它和 69 章讲的 BI（Superset/帆软）的差别：

| 维度 | BI 工具（69 章） | 报表工具（DataMax 类） |
| --- | --- | --- |
| 用户 | 业务自助分析 | 开发者 + 固定报表需求 |
| 形态 | 平台/看板/自助查询 | 嵌入业务系统的"一张张报表" |
| 特点 | 灵活探索 | **格式精细、中国式报表**（合并单元格、复杂表头、交叉表） |
| 集成 | 独立部署 | **作为 Java 应用的一部分**部署（Spring Boot 可嵌） |
| 输出 | 网页看板 | 网页 + **打印/导出（PDF/Excel）** |

### DataMax 核心能力清单

| 能力 | 说明 |
| --- | --- |
| **报表设计器**（DataMax Studio） | 可视化拖拽设计报表模板：布局、表头、单元格、样式 |
| **数据集** | 报表数据来源：SQL 数据集、API/JavaBean 数据集、参数化查询 |
| **参数** | 报表入参（时间范围、机构、筛选条件），支持联动/下拉 |
| **表达式** | 类 Excel 单元格表达式：`SUM`、`IF`、日期函数、统计函数、单元格引用 |
| **报表类型** | 明细报表、分组报表、交叉报表、主从报表、图表报表、填报表 |
| **导出与打印** | HTML/PDF/Excel/Word 导出，精确打印（套打） |
| **Java 集成** | 报表引擎以 Web 应用方式部署，Java 代码/URL 方式调用，可嵌 Spring Boot |
| **权限** | 报表级/数据级权限，可与业务系统对接 |

> **理解 DataMax 的一句话**：它是一个"**模板 + 数据集 + 表达式**"三层模型的可视化报表引擎——模板管长相，数据集管数据从哪来，表达式管单元格怎么算。DDD 要做的，就是把"数据集从哪来、谁有权看、口径是什么"这些**模板之外的事**建模起来。

## 70.3 报表模块的限界上下文：从"画图"到"领域"

### 70.3.1 战略定位：报表是独立的限界上下文

```
┌──────────────────────────────────────────────┐
│ 限界上下文：报表上下文（Reporting Context）        │
│   领域模型：Report / Parameter / Dataset /       │
│             Category / ReportPermission        │
│   职责：报表定义、数据获取、参数渲染、权限判定、导出    │
└──────────────┬───────────────────────────────┘
               │ 防腐层（ACL）——只通过应用服务/读模型取数
┌──────────────▼───────────────────────────────┐
│ 上游上下文：交易上下文（订单/用户，40/41 章）        │
│           提供：读模型（DWS/ADS 宽表）、指标定义      │
└──────────────────────────────────────────────┘
```

三个关键设计决策：

1. **独立上下文**：报表模块不塞进交易上下文。报表需求（"加一列""改个口径"）的高频变动，不应污染订单聚合的稳定核心；
2. **防腐层（ACL）**：报表上下文**不直接访问上游的仓储/Mapper**，只消费上游发布的数据（读模型/事件）。上游改表，报表上下文不受影响；
3. **读模型对接**（衔接 43 章 CQRS）：报表天然是"读"，用 CQRS 的读模型（Query Model）作为数据集的数据源，**写模型（订单聚合）与读模型（报表宽表）彻底分离**。

> **面试点**：为什么报表要独立上下文？答：报表是"高频变化 + 只读 + 低一致性要求"的领域，与"低频变化 + 读写都有 + 强一致"的交易核心完全不同频。分开后，报表随便改不影响核心，核心演进不牵连报表。

### 70.3.2 通用语言（Ubiquitous Language）

DDD 第一步是统一词汇——**业务说"报表"、开发说"DataMax 模板"、测试说"那张表"**，这就是口径失序的源头。报表上下文必须定义并全员遵守的通用语言：

| 通用语言 | 定义 | 反例 |
| --- | --- | --- |
| **报表**（Report） | 一张有名字、有分类、有数据源、有模板的报表定义 | "那个 rpt 文件" |
| **数据集**（Dataset） | 报表的数据来源（读模型查询），**是查询规格不是 SQL** | "那段 SQL" |
| **参数**（Parameter） | 用户输入，约束报表查询范围（时间/机构/状态） | "那个下拉框" |
| **口径**（Caliber） | 指标的统一定义（69 章指标平台），报表必须引用而非自造 | "我这边算的 GMV" |
| **报表权限**（ReportPermission） | 谁能看哪张表、能看到哪些机构/哪些行 | "给他开个账号" |

## 70.4 报表模块领域建模：实体、值对象与聚合

### 70.4.1 核心对象识别

```
Report（实体，聚合根）
 ├─ reportCode：报表编码（唯一标识）
 ├─ name：报表名称
 ├─ category（值对象）：报表分类（财务/运营/管理）
 ├─ dataset（实体）：数据集——数据源定义 + 查询规格 + 口径引用
 ├─ parameters（值对象集合）：参数列表（含默认值/联动关系）
 ├─ permission（值对象）：可见机构范围、数据过滤规则
 ├─ templateRef：DataMax 模板引用（呈现层实现细节，领域不关心）
 └─ 业务规则：
     · 报表必须挂分类（ensureCategory）
     · 数据集必须引用已注册口径（ensureCaliber）——口径铁律
     · 修改已发布报表需走版本（发布状态机）
```

```java
// 报表聚合根（充血模型，衔接 41 章）
public class Report {
    private ReportId id;                    // 实体标识（值对象）
    private String reportCode;
    private String name;
    private Category category;              // 值对象
    private Dataset dataset;                // 子实体（聚合内）
    private List<Parameter> parameters;     // 值对象集合
    private ReportPermission permission;    // 值对象
    private ReportStatus status;            // 枚举：DRAFT/PUBLISHED/DISABLED

    /** 规则1：报表必须挂分类（不变量在聚合内保证） */
    public void ensureCategory(Category c) {
        if (c == null) throw new DomainException("报表必须挂分类");
        this.category = c;
    }

    /** 规则2：口径铁律——数据集只能引用已注册口径，禁止裸 SQL */
    public void bindCaliber(CaliberRef caliber) {
        if (!caliber.isRegistered()) throw new DomainException("口径未注册：" + caliber.code());
        this.dataset = Dataset.ofCaliber(caliber);
    }

    /** 规则3：发布状态机——只有已发布报表才能被查询 */
    public void publish() {
        ensureStatus(ReportStatus.DRAFT, "发布");
        if (dataset.isEmpty()) throw new DomainException("空数据集不可发布");
        this.status = ReportStatus.PUBLISHED;
    }
}
```

### 70.4.2 值对象设计

| 值对象 | 内容 | 为什么是值对象 |
| --- | --- | --- |
| `Category` | code + name | 分类换名字不影响报表身份 |
| `Parameter` | key、label、type（DATE/ORG/ENUM）、默认值 | 无身份，随报表整体替换 |
| `ReportPermission` | orgIds（可见机构）、rowFilter（行级过滤）、exportable（可否导出） | 组合语义，整体比较 |
| `CaliberRef` | 指标编码 + 口径版本 | 只引用指标平台的编码，不自带计算逻辑 |

> **边界纪律**：`templateRef`（DataMax 模板）**不属于领域核心**——它是呈现层实现细节。领域层只知道"这张报表有模板"，不知道模板是 DataMax 的 `.rd` 还是别的工具。这样未来换报表工具，领域层零改动（依赖倒置，28 章）。

### 70.4.3 仓储与应用服务

```java
public interface ReportRepository {
    ReportId nextId();
    void save(Report report);
    Optional<Report> findByCode(String reportCode);   // 报表编码是业务唯一键
    Page<Report> pageByCategory(Category category, Pageable pageable);
}
```

```java
// 应用服务：报表上下文的用例入口（用例 = 应用服务方法）
@Service
public class ReportApplicationService {
    private final ReportRepository reportRepo;

    /** 用例：注册一张新报表（含口径绑定与分类） */
    @Transactional
    public ReportId registerReport(RegisterReportCommand cmd) {
        Report report = new Report(new ReportId(reportRepo.nextId()));
        report.ensureCategory(cmd.category());
        report.bindCaliber(cmd.caliberRef());      // 口径铁律在应用层也拦一道
        reportRepo.save(report);
        return report.id();
    }

    /** 用例：发布报表（状态机） */
    @Transactional
    public void publishReport(String reportCode) {
        Report report = reportRepo.findByCode(reportCode)
                .orElseThrow(() -> new DomainException("报表不存在"));
        report.publish();
        reportRepo.save(report);
    }
}
```

## 70.5 数据集设计：守住建仓边界的关键一战

报表开发最大的架构风险，就是**把 SQL 直接写死在报表模板里**。这样做的后果：报表与库表结构强耦合、口径无法统一、权限无法统一过滤、慢查询无法治理。

### 70.5.1 两种数据集方案对比

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **裸 SQL 数据集** | 模板里写 `SELECT ... FROM dws_gmv WHERE ...` | 快、灵活 | 口径失控、权限难加、表结构变动全炸 |
| **读模型 + API 数据集**（推荐） | 领域层定义读模型查询 → 应用服务暴露 API → DataMax 用 API 数据集接 | 口径统一、可加权限/缓存/限流、解耦 | 多一层开发 |

```
推荐架构：
DataMax 模板 ──API 数据集──► ReportQueryService（应用服务）
                                ├─ 权限过滤（orgIds 拼进查询）
                                ├─ 口径校验（只查已注册指标）
                                ├─ 缓存（Redis，29 章）
                                └─ 查读模型表（DWS/ADS，62/64 章）
```

### 70.5.2 读模型查询服务（Java 实现）

```java
// 读模型：报表数据集查询（只读，无业务规则，允许灵活查询）
@Service
public class ReportQueryService {

    private final CaliberRegistry caliberRegistry;   // 口径注册表（69 章指标平台）
    private final PermissionEvaluator permissionEvaluator;
    private final JdbcTemplate dws;                  // 查 DWS/ADS 宽表（64 章 ClickHouse/Doris 同理）

    /** DataMax API 数据集调用入口：传报表编码 + 参数 + 当前用户 */
    public QueryResult queryDataset(String reportCode, Map<String, Object> params, Long userId) {
        Report report = reportRepo.findByCode(reportCode)
                .orElseThrow(() -> new DomainException("报表不存在"));

        // ① 状态校验：只允许已发布报表
        report.assertPublished();

        // ② 权限过滤：把可见机构拼进查询条件（数据级权限）
        List<Long> orgIds = permissionEvaluator.visibleOrgIds(userId, report.permission());
        if (orgIds.isEmpty()) return QueryResult.empty();

        // ③ 由报表的数据集定义生成查询（口径来自 CaliberRef，非裸 SQL）
        String sql = report.dataset().buildQuery(orgIds, params);
        List<Map<String, Object>> rows = dws.queryForList(sql);

        return new QueryResult(rows);
    }
}
```

> **口径铁律落地**：`buildQuery()` 只接受 `CaliberRef`（指标编码），聚合根在 `bindCaliber` 时已保证"只能绑定已注册口径"。**报表模板里永远不出现裸 SQL**，SQL 由应用服务按模型生成——这就是"DDD 把 SQL 关进笼子"。

### 70.5.3 API 数据集在 DataMax 中的使用

在 DataMax Studio 中新建"API 数据集"，指向 Spring Boot 暴露的接口：

```
数据集 URL：POST /api/v1/report/query
请求参数：
  { "reportCode": "order_monthly",
    "params": { "startDate": "2026-08-01", "endDate": "2026-08-31" },
    "userId": 1001 }
返回字段：
  { "rows": [ { "orgName": "华东", "gmv": 1280000, "orderCnt": 8600 } ] }
```

> **替代方案**：如果报表查询简单、团队无强治理诉求，SQL 数据集可直接查**只读库/读模型宽表**（走 59 章迁移的只读从库），但"口径注册 + 权限过滤"这两条底线不能破。

## 70.6 参数与权限：报表的两个"安全阀"

### 70.6.1 参数设计

| 参数类型 | 例子 | 实现要点 |
| --- | --- | --- |
| 时间 | 开始/结束日期 | 默认最近 7 天；**强制参数防全表扫描** |
| 机构 | 组织树下拉 | 数据来自组织读模型；级联过滤 |
| 枚举 | 订单状态 | 值来自字典服务，而非硬编码 |
| 联动 | 选大区 → 过滤城市 | DataMax 参数联动 + 后端校验 |

**参数三铁律**：

1. **必填参数不给默认全量**：`endDate` 缺省时拒绝查询，杜绝"忘选时间跑全表"；
2. **后端二次校验**：DataMax 前端能传，后端必须再验（参数注入/越权）；
3. **参数进 SQL 必须参数化**：拼 SQL 时用占位符，防注入（54 章安全）。

### 70.6.2 数据权限：行级过滤

报表权限不只是"能不能打开这张表"，更是"**打开后能看到哪些行**"：

```
用户 1001（华东大区经理）请求"订单月报"
 → 可见机构 = 华东及下属城市（权限服务算出 orgIds）
 → 查询自动带上 org_id IN (华东, 上海, 杭州, ...)
 → 即使报表工具被绕过直接调 API，后端仍强制过滤（权限在服务端，不在报表）
```

| 权限层级 | 控制什么 | 在哪实现 |
| --- | --- | --- |
| 报表级 | 能不能看这张表 | 报表上下文 + DataMax 资源权限 |
| 操作级 | 能不能导出/打印 | 应用服务校验 `exportable` |
| **行级（数据级）** | 能看到哪些机构/行 | **后端查询服务强制拼入**，与报表工具无关 |

> **安全认知**：报表工具的权限只是"门锁"，**行级过滤必须由后端查询服务保证**——因为报表工具权限可以被绕过（直接调 API、导出后分发），后端数据过滤才是真正的数据安全边界（54 章零信任：永远不信任任何一端）。

## 70.7 Java 集成实战：Spring Boot 部署 DataMax

### 70.7.1 集成方式

| 方式 | 说明 | 适用 |
| --- | --- | --- |
| **同进程嵌入** | DataMax 报表引擎作为 Spring Boot 依赖/Web 组件部署在同一应用 | 中小系统，最简单 |
| 独立部署 | 报表引擎单独 Web 应用，业务系统通过 URL/API 调用 | 报表量大、需要独立扩展 |
| 前后端分离 | 后端只出 JSON 数据，前端 ECharts/自研渲染 | 已深度自研的团队 |

### 70.7.2 同进程集成要点（伪代码/配置示意）

```xml
<!-- 报表引擎依赖（以实际产品为准，示意） -->
<dependency>
    <groupId>com.comtop.datamax</groupId>
    <artifactId>datamax-engine</artifactId>
    <version>${datamax.version}</version>
</dependency>
```

```yaml
# 报表引擎配置
datamax:
  report-dir: /opt/reports            # 报表模板目录
  temp-dir: /tmp/datamax
  datasource: reportReadOnly          # 报表专用只读数据源（不与主业务混用）
  auth-type: token                    # 与业务系统统一鉴权
```

```java
// 统一入口：报表访问拦截器——先过业务鉴权，再放行到报表引擎
@Component
public class ReportAccessInterceptor implements HandlerInterceptor {
    private final PermissionEvaluator permissionEvaluator;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ① 解析报表编码（URL 参数 reportCode）
        String reportCode = request.getParameter("reportCode");
        // ② 从上下文取当前用户（JWT/SSO，9/13 章）
        Long userId = SecurityContext.currentUserId();
        // ③ 报表级 + 行级权限校验
        if (!permissionEvaluator.canView(userId, reportCode)) {
            response.setStatus(403);
            return false;
        }
        return true;   // 放行到报表引擎渲染
    }
}
```

### 70.7.3 报表访问 URL 模式

```
# 业务系统内嵌报表的典型访问方式（引擎渲染 + 参数传递）
GET /datamax/report/view?reportCode=order_monthly&startDate=2026-08-01&endDate=2026-08-31
GET /datamax/report/export?reportCode=order_monthly&format=excel&...   # 导出 Excel
GET /datamax/report/export?reportCode=order_monthly&format=pdf&...     # 导出 PDF
```

> **导出控制**：导出是数据泄露的高发口。`exportable=false` 的报表，拦截器在导出 URL 上同样拦截；导出行为记录审计日志（48 章可观测性）。

## 70.8 报表开发流程：从需求到发布的标准流水线

```
① 需求澄清（事件风暴式快问快答，44 章）
   这个报表谁看？看什么指标？口径是什么？粒度到哪？多久刷一次？
② 口径确认 → 查指标平台，没有就注册新口径（69 章指标平台）
③ 读模型准备 → 数仓出 DWS/ADS 宽表（62 章）
④ 领域建模 → 注册 Report 聚合、绑定 CaliberRef、配参数与权限（70.4）
⑤ DataMax 设计 → Studio 里设计模板、建 API 数据集、配参数控件（70.5~70.6）
⑥ 联调验证 → 口径与 BI 大屏对账（69 章对账思想），性能压测（慢查询治理）
⑦ 发布 → 报表状态机 DRAFT→PUBLISHED，写操作日志
⑧ 监控 → 报表访问量、慢查询、导出审计（48 章）
```

> **一条红线**：任何报表上线前必须回答三个问题——**口径谁定的？权限到行了吗？慢查询会拖垮谁？** 三个答不上来就不允许发布。

## 70.9 生产踩坑清单与面试速查

**踩坑清单**：

- [ ] 裸 SQL 写进报表模板：口径失控、表结构变动报表全炸（用 API 数据集 + 读模型）；
- [ ] 报表直连生产库：慢查询拖垮主链路（专用只读数据源/从库，59 章只读架构）；
- [ ] 行级权限依赖报表工具：导出/API 绕过工具后数据泄露（后端强制过滤）；
- [ ] 参数不校验：忘选时间跑全表、参数注入（必填 + 参数化 SQL + 后端二次校验）；
- [ ] 报表与业务强耦合：需求高频变动污染核心上下文（独立限界上下文 + 防腐层）；
- [ ] 无状态管理：报表改了没版本，口径错了不知道谁改的（发布状态机 + 审计）；
- [ ] 导出无审计：Excel 流出无记录（导出拦截 + 审计日志）；
- [ ] 缓存过期策略缺失：热点报表每次全量查库（Redis 缓存 + 定时预热，29 章）。

**面试速查**：

| 问题 | 一句话答案 |
| --- | --- |
| 报表模块为什么要用 DDD？ | 报表高频变化+只读，独立上下文隔离，口径与权限建模成领域规则 |
| DataMax 是什么？ | 国产 Java 报表工具：设计器+数据集+表达式，可嵌 Spring Boot |
| 报表 SQL 放哪？ | 不放模板，由应用服务按读模型生成，只认指标口径 |
| 数据集两种方案？ | 裸 SQL（快但失控）vs 读模型+API 数据集（推荐，可治理） |
| 行级权限怎么保证？ | 后端查询服务强制拼 orgIds，报表工具权限可被绕过不算数 |
| 参数要注意什么？ | 必填防全表、后端二次校验、参数化防注入 |
| 报表上线三问？ | 口径谁定？权限到行吗？慢查询拖垮谁？ |

### 本章小结

- **定位**（70.1~70.2）：报表是工程不是画图；DataMax 是"模板+数据集+表达式"的报表引擎，管呈现不管口径；
- **建模**（70.3~70.4）：报表独立限界上下文 + 防腐层；Report 聚合根承载"挂分类/绑口径/状态机"三条铁律；
- **边界**（70.5~70.6）：读模型 + API 数据集把 SQL 关进笼子；参数三铁律 + 后端行级过滤双安全阀；
- **落地**（70.7~70.8）：Spring Boot 同进程集成、统一鉴权拦截器、八步开发流水线；
- **衔接**：41 章 DDD 战术设计是本的方法论，69 章 BI/指标平台是本的上游口径，64/62 章数仓是本的数据源。

至此，第 70 章 DataMax 报表开发与 DDD 集成实战学习完成，Java 学习体系扩展到 70 章。下一章待定，先返回：[README.md](./README.md)
