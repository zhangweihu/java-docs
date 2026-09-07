# 第十二章：Polars 数据分析

> 目标：理解 Apache Arrow 列式内存模型，掌握 Polars 的 DataFrame/表达式/惰性框架/IO 能力，完成一个**订单数据分析实战**（与 python 侧 pandas 对照学习）。
>
> 前置：[11-unsafe与FFI.md](./11-unsafe与FFI.md) ｜ 下一章：[13-大数据与爬虫实战.md](./13-大数据与爬虫实战.md)
>
> 本章让你在 Rust 中拥有"Python pandas/SQL"级别的数据分析能力，并能直接读写 Parquet——为下一章大数据实战（DataFusion）打基础。

## 12.1 为什么是 Polars

- **Polars** 是 Rust 编写的高性能 DataFrame 库（有 Python 绑定）；在 Rust 侧是数据分析事实标准；
- 底层基于 **Apache Arrow** 列式内存格式；
- **惰性框架（LazyFrame）** 提供查询优化：谓词下推、投影下推、Join 重排等，把"你写什么"优化成"最省的执行计划"。

| 能力 | Polars (Rust) | pandas (Python) | SQL |
| --- | --- | --- | --- |
| 建表/筛选/聚合 | `df!`/`filter`/`group_by` | `pd.DataFrame` | `SELECT`/`WHERE`/`GROUP BY` |
| 列运算 | `with_columns` + `col().alias()` | `df["x"] + 1` | 表达式 |
| 合并 | `join`/`concat` | `merge`/`concat` | `JOIN`/`UNION` |
| 排序 | `sort`/`sort_by` | `sort_values` | `ORDER BY` |
| 文件 | CSV/Parquet/JSON | 同左 | `COPY` |

## 12.2 依赖与最小程序

```toml
[dependencies]
polars = { version = "0.45", features = ["lazy", "csv", "parquet", "strings", "fmt", "dtype-datetime"] }
anyhow = "1"
```

> Polars API 演进较快，若字段/方法在你安装的版本报错，以该版本文档为准（核心概念不变）。

最简示例（急切 API，类似 pandas）：

```rust
use polars::prelude::*;

fn main() -> anyhow::Result<()> {
    // df! 宏快速建表
    let df = df![
        "name" => ["Alice", "Bob", "Carol"],
        "dept" => ["eng", "eng", "ops"],
        "salary" => [100, 120, 90],
    ]?;

    // 打印时用 pretty 输出更清晰
    println!("{:?}", df);
    Ok(())
}
```

## 12.3 核心操作（急切 API）

```rust
use polars::prelude::*;

fn main() -> anyhow::Result<()> {
    let df = df![
        "name"   => ["Alice", "Bob", "Carol", "Dave"],
        "dept"   => ["eng", "eng", "ops", "ops"],
        "salary" => [100, 120, 90, 110],
    ]?;

    // 选择列
    let names = df.select(["name"])?;

    // 过滤（expr：col 取列，lit 造字面量，gt 是 >）
    let rich = df.clone().filter(col("salary").gt(100))?;
    // 分组聚合：按 dept 分组，取 salary 均值，新列名 avg_salary
    let avg_by_dept = df
        .clone()
        .group_by(["dept"])?
        .agg([col("salary").mean().alias("avg_salary")])?;

    // 新列：薪资上调 10%（with_columns 不改变行数）
    let raised = df.clone().lazy().with_columns([
        (col("salary") * lit(1.1)).alias("raised"),
    ]).collect()?;

    println!("平均薪资按部门:\n{avg_by_dept:?}");
    println!("上调后:\n{raised:?}");
    Ok(())
}
```

表达式 API 的核心心智：**所有操作都写在表达式里**（`col`/`lit` + 组合子），`filter`/`with_columns`/`group_by().agg()` 只是"放置表达式"的位置。这个模型与 SQL 的"SELECT 子句里写表达式"一一对应。

## 12.4 惰性框架 LazyFrame：真正的性能

把 `.lazy()` 前的 DataFrame（或 CSV/Parquet 读取）变成 `LazyFrame`，最后 `.collect()` 触发执行。链中所有 filter/project 会被优化器下推：

```rust
use polars::prelude::*;

fn main() -> anyhow::Result<()> {
    // 直接惰性读 CSV（可流式，不整载入内存）
    let lazy = LazyCsvReader::new("data/orders.csv")
        .has_header(true)
        .finish()?;

    // 声明式描述"想算什么"，collect 时才真正跑
    let top = lazy
        .filter(col("amount").gt(50))
        .group_by(["region"])
        .agg([col("amount").sum().alias("total")])
        .sort("total", SortOptions::default())
        .limit(3)
        .collect()?;

    println!("{top:?}");
    Ok(())
}
```

`.explain(true)` 可以打印执行计划，观察谓词/投影下推效果：

```rust
let plan = lazy.clone().explain(true)?;
println!("{plan}");
```

## 12.5 Parquet：大数据生态的存储格式

Parquet 是列式存储格式（Hadoop/Spark/Hive 生态默认格式之一）。Polars 读写 Parquet 一行代码：

```rust
use polars::prelude::*;

fn main() -> anyhow::Result<()> {
    // 写：DataFrame → orders.parquet（自动列式压缩）
    let df = df![
        "order_id" => [1, 2, 3],
        "amount"   => [30.5, 120.0, 60.0],
        "paid_at"  => ["2026-09-01", "2026-09-02", "2026-09-02"],
    ]?;
    let mut file = std::fs::File::create("orders.parquet")?;
    ParquetWriter::new(&mut file).finish(&df)?;

    // 读：只读需要的列（列式存储天然支持投影，谓词可下推到 parquet 谓词过滤）
    let back = LazyFrame::scan_parquet("orders.parquet", Default::default())?
        .select([col("order_id"), col("amount")])
        .filter(col("amount").gt(50.0))
        .collect()?;
    println!("{back:?}");
    Ok(())
}
```

> 与 java 侧对照：Spark/Hive 写出的 Parquet，Polars/DataFusion 可以直接读；**Parquet + Arrow 是跨语言数据交换的事实协议**。

## 12.6 类型系统与 null/缺失值

- 列是强类型的：`Int32`/`Float64`/`Utf8`（String）/`Datetime` 等；
- 缺失值用 `null`（不是 NaN），`fill_null`/`drop_nulls` 处理；
- 类型不匹配会报错而非静默转换——显式用 `.cast(DataType::Float64)`：

```rust
let df = df![
    "a" => [Some(1), None, Some(3)],       // Option<T> 表达 null
    "b" => [10, 20, 30],
]?;
let cleaned = df.clone()
    .lazy()
    .with_columns([
        col("a").fill_null(lit(0)),          // null → 0
        col("a").cast(DataType::Float64),    // 显式转浮点
        (col("a") + col("b")).alias("sum_ab"),
    ])
    .collect()?;
```

## 12.7 实战：订单数据分析

**需求**：`data/orders.csv`（region, amount, category），分别计算：总销售额、各地区销售额 Top、各品类单均金额与订单数、金额 > 80 的高价值订单占比；结果输出一份汇总 DataFrame，并导出 Parquet 供下一步分析工具读取。

```rust
use polars::prelude::*;

fn main() -> anyhow::Result<()> {
    // 若不存在则先造样例数据
    let sample = df![
        "order_id" => [1,2,3,4,5,6,7,8],
        "region"   => ["east","east","east","west","west","west","north","north"],
        "category" => ["food","elec","food","elec","food","elec","food","elec"],
        "amount"   => [10.0, 200.0, 30.0, 90.0, 50.0, 300.0, 20.0, 120.0],
    ]?;
    // 可先 CsvWriter 存文件再 LazyCsvReader 读，验证两种路径（此处直接 df 起步）

    let total: f64 = sample.clone().lazy()
        .select([col("amount").sum().alias("total")])
        .collect()?
        .column("total")?.f64()?.get(0).unwrap_or(0.0);

    let by_region = sample.clone().lazy()
        .group_by(["region"])
        .agg([col("amount").sum().alias("sales"), col("amount").count().alias("orders")])
        .sort("sales", SortOptions { descending: true, ..Default::default() })
        .collect()?;

    let by_category = sample.clone().lazy()
        .group_by(["category"])
        .agg([
            col("amount").mean().alias("avg_amount"),
            col("amount").count().alias("order_cnt"),
        ])
        .collect()?;

    // 高价值订单数 = 过滤后的行数（df.height() 返回行数）
    let high_count = sample.clone().lazy()
        .filter(col("amount").gt(80.0))
        .collect()?
        .height();
    let ratio = high_count as f64 / sample.height() as f64;

    println!("总销售额: {total:.2}");
    println!("各地区销售额:\n{by_region:?}");
    println!("各品类:\n{by_category:?}");
    println!("高价值订单占比: {:.1}%", ratio * 100.0);

    // 导出 Parquet 供下游（如 DataFusion/Spark）使用
    let mut file = std::fs::File::create("analysis.parquet")?;
    ParquetWriter::new(&mut file).finish(&sample)?;
    println!("已导出 analysis.parquet");
    Ok(())
}
```

**验收清单**：

1. 程序输出与手算一致（总销售额 820，east 240 > west 440? 复核：east=240, west=440, north=140，排序后 west 最高）；
2. 把前 4 个查询改写成 **一条惰性链** 再 `.collect()`，观察 `.explain(true)` 的计划；
3. 增加 `category == "elec"` 的过滤，确认谓词在计划中被下推；
4. 把结果 DataFrame `write_json` / `write_csv` 各导出一份。

**扩展练习**：读一个真实 CSV（可先用第 4 章词频 CLI 的产物或系统日志），做日期字符串→`Datetime` 转换后按月份聚合；将 `df` 转为 Arrow `RecordBatch`（`df.iter_chunks`）体验底层列式结构。

> 关键收获：Polars 让你在 Rust 里告别"手写循环算统计"。惰性 + 表达式把 SQL 的表达力带回了内存计算，而 Parquet 读写让 Rust 程序能无缝加入 Hadoop/Spark 生态的数据管道。下一章打通最后一公里：爬虫采集 + 大数据查询引擎。

上一章：[11-unsafe与FFI.md](./11-unsafe与FFI.md) ｜ 下一章：[13-大数据与爬虫实战.md](./13-大数据与爬虫实战.md) ｜ [返回 README](./README.md)
