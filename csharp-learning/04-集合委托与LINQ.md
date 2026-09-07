# 第四章 集合、委托与 LINQ

> 本章目标：掌握 C# 最常用的集合类型（`List`/`Dictionary`/`HashSet`/`Queue`/`Stack`）、委托与 lambda 表达式、扩展方法，以及 **LINQ**——C# 处理数据查询的利器。
> 前置：第一至三章。

## 4.1 为什么需要集合

数组长度固定。真实数据常常动态增减（一个班级有多少学生、购物车加商品……），我们需要能自由增删的“容器”，这就是集合。

## 4.2 List\<T\>：动态数组（最常用）

```csharp
var names = new List<string>();            // 空列表
names.Add("小明");
names.Add("小红");
names.AddRange(new[] { "小刚", "小丽" });  // 批量加

names.Insert(1, "小美");                    // 插到下标 1
Console.WriteLine(names.Count);             // 5
Console.WriteLine(names.Contains("小明"));  // True

names.Remove("小丽");                        // 删第一个匹配的
names.RemoveAt(0);                           // 按下标删

// 遍历
foreach (var n in names) Console.Write(n + " ");
Console.WriteLine();

// 下标访问 & 修改
Console.WriteLine(names[0]);
names[0] = "大刚";

// 其它常用
names.Sort();                                // 排序（默认升序）
string? found = names.Find(n => n.StartsWith("小"));  // 找第一个
List<string> small = names.FindAll(n => n.Length == 2); // 找所有
Console.WriteLine(string.Join(",", small));
```

### 4.2.1 初始化与集合表达式

```csharp
var scores = new List<int> { 78, 85, 92 };   // 集合初始化器
var more = new List<int>([60, 70]);          // C#12 集合表达式
var total = new List<int>();
total.AddRange(scores);
total.AddRange(more);
```

## 4.3 Dictionary\<K,V\>：键值对

通过 key（键）快速找 value（值），类似查字典：

```csharp
var scores = new Dictionary<string, int>();
scores["小明"] = 92;
scores["小红"] = 88;

// 安全取值：TryGetValue
if (scores.TryGetValue("小明", out int score))
    Console.WriteLine($"小明：{score}");
else
    Console.WriteLine("查无此人");

// 判断存在
Console.WriteLine(scores.ContainsKey("小红"));   // True
Console.WriteLine(scores.ContainsValue(100));    // False

// 遍历
foreach (var kv in scores)
    Console.WriteLine($"{kv.Key} -> {kv.Value}");

// 只遍历键 / 只遍历值
foreach (var k in scores.Keys) { }
foreach (var v in scores.Values) { }

// 删除
scores.Remove("小明");

// 技巧：找不到时给默认值并塞入
var counts = new Dictionary<string, int>();
string word = "apple";
counts[word] = counts.GetValueOrDefault(word) + 1;   // 简易计数
```

> `Dictionary` 按键快速查找，复杂度 O(1)，比在 `List` 里线性查找快得多。**频繁“按名字找对象”就用 Dictionary。**

## 4.4 HashSet\<T\>：不重复集合

```csharp
var tags = new HashSet<string> { "C#", ".NET", "LINQ" };
Console.WriteLine(tags.Add("C#"));      // False：已存在，加不进去
Console.WriteLine(tags.Add("EF"));      // True
Console.WriteLine(tags.Count);          // 4

// 集合运算（去重/交集/差集/并集）
var a = new HashSet<int> { 1, 2, 3, 4 };
var b = new HashSet<int> { 3, 4, 5 };
a.IntersectWith(b);                     // a = {3,4} 交集
a.UnionWith(new[] { 9, 10 });           // 并集
```

典型用途：去重、判存在（`Contains` 是 O(1)）、集合运算。

## 4.5 Queue / Stack / LinkedList（按需认识）

```csharp
var queue = new Queue<string>();         // 先进先出：排队
queue.Enqueue("1号"); queue.Enqueue("2号");
Console.WriteLine(queue.Dequeue());      // 1号（出队）

var stack = new Stack<string>();         // 后进先出：叠盘子
stack.Push("a"); stack.Push("b");
Console.WriteLine(stack.Pop());          // b（出栈）
```

- `Queue`：任务队列、消息缓冲；`Stack`：括号匹配、撤销历史、DFS 遍历；
- `LinkedList<T>`：大量中间插入/删除场景才考虑，日常用不到。

## 4.6 委托与 lambda：把“代码”当参数传

### 4.6.1 委托（delegate）

委托 = “方法的类型”。可以把一个方法当作值赋给变量、传给另一个方法：

```csharp
// 声明委托类型：接收 string 返回 int
delegate int StringScore(string s);

// 一个实现
int LengthScore(string s) => s.Length;

StringScore scorer = LengthScore;        // 方法 → 委托变量
Console.WriteLine(scorer("hello"));      // 5
```

### 4.6.2 内置委托 Func / Action / Predicate

不必自己声明委托，用现成的泛型委托：

```csharp
// Func<TResult> / Func<T1, TResult> ...：有返回值的函数（最后一个泛型是返回类型）
Func<int, int, int> add = (x, y) => x + y;
Func<int, bool> isEven = n => n % 2 == 0;

// Action：无返回值
Action<string> print = s => Console.WriteLine(">> " + s);
print("Hi");

// Predicate<T>：返回 bool 的判断
Predicate<int> positive = n => n > 0;
```

### 4.6.3 lambda 表达式

上面的 `(x, y) => x + y` 就是 **lambda**：左边是参数，右边是表达式。它是委托变量的“最常用写法”。可以带语句块：

```csharp
Func<int, int, int> max = (a, b) =>
{
    if (a > b) return a;
    return b;
};
```

**闭包**：lambda 可以“捕获”外层变量：

```csharp
int baseValue = 10;
Func<int, int> addBase = x => x + baseValue;   // 捕获 baseValue
baseValue = 20;
Console.WriteLine(addBase(1));                 // 21：捕获的是“变量本身”，看调用时的值
```

## 4.7 扩展方法

给**已存在的类型**（包括你没权限改的 `string`、第三方类型）追加方法：

```csharp
public static class StringExtensions
{
    public static bool IsNullOrBlank(this string? s)      // this 关键字 = 扩展方法
        => string.IsNullOrWhiteSpace(s);

    public static int WordCount(this string s)
        => s.Split(' ', StringSplitOptions.RemoveEmptyEntries).Length;
}
```

```csharp
string? text = "  ";
Console.WriteLine(text.IsNullOrBlank());   // True：null 也能调用（方法内判空）
Console.WriteLine("hello world c#".WordCount());  // 3
```

- 静态类 + 静态方法 + 第一个参数 `this 类型`；
- 扩展方法只是语法糖，本质还是调用静态方法；
- **LINQ 就是靠扩展方法实现的**——所以这一节是 LINQ 的地基。

## 4.8 LINQ：查询语言

LINQ（Language Integrated Query）让你用统一语法查询**任何数据源**（数组、集合、数据库表、XML……）。两种写法：**查询语法**（类似 SQL）与**方法语法**（链式调用）。

### 4.8.1 数据准备

```csharp
record Student(string Name, int Age, int Score, string City);

var students = new List<Student>
{
    new("小明", 12, 92, "杭州"),
    new("小红", 11, 88, "杭州"),
    new("小刚", 13, 73, "上海"),
    new("小丽", 12, 61, "北京"),
    new("小美", 11, 95, "上海"),
};
```

### 4.8.2 查询语法（SQL 风格）

```csharp
var result = from s in students
             where s.Score >= 60 && s.City == "杭州"
             orderby s.Score descending
             select new { s.Name, s.Score };      // 投影出新形状

foreach (var item in result)
    Console.WriteLine($"{item.Name} {item.Score}");
```

### 4.8.3 方法语法（链式，更常用）

```csharp
// 过滤 + 排序 + 投影，一行链下来
var top2 = students
    .Where(s => s.Score >= 60)
    .OrderByDescending(s => s.Score)
    .Take(2)
    .Select(s => new { s.Name, s.Score })
    .ToList();

// 延迟执行：Where/OrderBy 并不会立即执行，真正取值（foreach/ToList/Count）才跑
```

**最常用方法速查**：

| 方法 | 作用 | 示例 |
| --- | --- | --- |
| `Where` | 过滤 | `students.Where(s => s.Score > 80)` |
| `Select` | 投影/转换 | `students.Select(s => s.Name)` |
| `OrderBy` / `OrderByDescending` | 排序 | `.OrderBy(s => s.Score)` |
| `ThenBy` | 次级排序 | `.OrderBy(s=>s.City).ThenBy(s=>s.Score)` |
| `First` / `FirstOrDefault` | 取第一个（找不到抛/给默认） | `.FirstOrDefault(s => s.Age == 11)` |
| `Single` / `SingleOrDefault` | 取唯一一个 | 结果多于 1 个会抛 |
| `Any` | 是否存在满足条件的 | `.Any(s => s.Score < 60)` |
| `All` | 是否全部满足 | `.All(s => s.Age > 10)` |
| `Count` / `LongCount` | 计数 | `.Count(s => s.City == "杭州")` |
| `Sum` / `Average` / `Max` / `Min` | 聚合 | `.Average(s => s.Score)` |
| `Distinct` | 去重 | `ints.Distinct()` |
| `GroupBy` | 分组 | 见下 |
| `Skip` / `Take` | 跳过/取前 N | 分页用 `.Skip(20).Take(10)` |
| `SelectMany` | 展平嵌套集合 | 见下 |
| `Contains` | 是否含某元素 | `names.Contains("小明")` |
| `ToList` / `ToArray` / `ToDictionary` | 物化为具体容器 | `.ToDictionary(s => s.Name)` |

### 4.8.4 GroupBy 分组与聚合

```csharp
// 按城市分组，每组算平均分
var byCity = students
    .GroupBy(s => s.City)
    .Select(g => new { City = g.Key, Avg = g.Average(s => s.Score), N = g.Count() });

foreach (var g in byCity)
    Console.WriteLine($"{g.City}: 平均 {g.Avg:F1}，共 {g.N} 人");
```

### 4.8.5 SelectMany 展平

```csharp
// 每个班有多个学生，我想拿到“所有班的所有学生姓名”
var classes = new[]
{
    new { Name = "一班", Students = new[] { "小明", "小红" } },
    new { Name = "二班", Students = new[] { "小刚", "小丽", "小美" } },
};

var allNames = classes.SelectMany(c => c.Students);
Console.WriteLine(string.Join(",", allNames));   // 小明,小红,小刚,小丽,小美
```

### 4.8.6 LINQ 注意事项（踩坑）

- **延迟执行**：`var q = list.Where(...);` 还没算；多次遍历可能多次执行。要“快照”就 `.ToList()`；
- LINQ 不修改原集合（返回新序列）；要改就 `list.RemoveAll(x => …)` / 重新赋值；
- `FirstOrDefault` 找不到时引用类型返回 null、值类型返回默认值（0）；
- LINQ 对空集合安全（Count=0、Average 在空集合上会抛 InvalidOperationException，先判空）。

## 4.9 完整可运行示例：学生成绩分析器

```csharp
record Student(string Name, int Age, int Score, string City);

// —— 扩展方法：判断是否及格 ——
public static class StudentEx
{
    public static bool IsPass(this Student s) => s.Score >= 60;
}

// —— 顶级语句 ——
var students = new List<Student>
{
    new("小明", 12, 92, "杭州"),
    new("小红", 11, 88, "杭州"),
    new("小刚", 13, 73, "上海"),
    new("小丽", 12, 58, "北京"),     // 不及格
    new("小美", 11, 95, "上海"),
};

// 1. 及格率
double passRate = students.Count(s => s.IsPass()) / (double)students.Count;
Console.WriteLine($"及格率：{passRate:P1}");

// 2. 不及格名单
var fail = students.Where(s => !s.IsPass()).Select(s => s.Name);
Console.WriteLine("不及格：" + string.Join(",", fail));

// 3. 前三名（Top3）
var top3 = students.OrderByDescending(s => s.Score).Take(3);
Console.WriteLine("Top3：");
foreach (var s in top3) Console.WriteLine($"  {s.Name} {s.Score}");

// 4. 每城平均分
Console.WriteLine("各城市平均分：");
foreach (var g in students.GroupBy(s => s.City))
    Console.WriteLine($"  {g.Key}: {g.Average(s => s.Score):F1}（{g.Count()}人）");

// 5. 每个城市的最高分
Console.WriteLine("各城市最高分：");
foreach (var g in students.GroupBy(s => s.City))
{
    var best = g.OrderByDescending(s => s.Score).First();
    Console.WriteLine($"  {g.Key}: {best.Name} {best.Score}");
}

// 6. 词频统计小作业热身（Dictionary + LINQ）
var words = "apple banana apple cherry apple banana".Split(' ');
var freq = words.GroupBy(w => w)
                .OrderByDescending(g => g.Count())
                .Select(g => $"{g.Key}:{g.Count()}");
Console.WriteLine(string.Join(" ", freq));   // apple:3 banana:2 cherry:1
```

## 4.10 实战与验收

**必做**

1. 用 `Dictionary<string,int>` 统计一段文本（或手动数组）中每个单词出现次数，再按次数从高到低输出 Top3；
2. 有一组 `(姓名, 城市, 销售额)` 数据，用 LINQ 求：每城市总销售额、销售额最高的城市；
3. 给 `string` 写一个扩展方法 `CountWord(string)` 返回单词数（参考 4.7）。

**进阶题（任选）**

- 实现一个简单的“分页器”：`List<T>` + `Skip/Take`，给定页码与页大小返回对应页；
- 用 `SelectMany` 展开“作者→多本书”的数据输出“书名（作者）”列表；
- 写一个 `Func<T>` 委托+lambda 的简易“菜单命令分发”（体会委托当参数）。

**验收清单**

- [ ] 能说出 `List` / `Dictionary` / `HashSet` 各自适用场景
- [ ] 能写 lambda，说明什么是闭包
- [ ] 能给已有类型写扩展方法
- [ ] 独立写出含 Where/OrderBy/GroupBy/Select 的 LINQ 链
- [ ] 理解“LINQ 延迟执行”与 ToList 的作用

## 4.11 小结

集合 + lambda + 扩展方法 + LINQ 四件套，让你用几行代码完成过去要写十几行循环的查询。更重要的是：**同一套 LINQ 语法后面还能用于 EF Core 查数据库**（第八章），这就是“语言集成查询”的意义。

下一章进入 **错误处理与调试**：程序出问题时如何优雅应对、如何用调试器找到 bug。

## 语言对照

| 概念 | C# | Java | Python | Rust |
| --- | --- | --- | --- | --- |
| 动态数组 | `List<T>` | `ArrayList`/`List` | `list` | `Vec<T>` |
| 键值对 | `Dictionary` | `HashMap` | `dict` | `HashMap` |
| 去重集合 | `HashSet` | `HashSet` | `set` | `HashSet` |
| lambda | `(x) => expr` | `x -> expr` | `lambda x: expr` | `\|x\| expr` |
| 过滤 | `.Where(pred)` | `stream().filter` | 推导式 `[.. if ..]` | `.filter()` |
| 映射 | `.Select(f)` | `stream().map` | `[f(x) for x]` | `.map()` |
| 分组聚合 | `.GroupBy` | `groupingBy + reducing` | `itertools.groupby`/pandas | 手动 |
| 排序 | `OrderBy` | `sorted()` 需要 Comparator | `sorted(key=)` | `sort_by` |
| 懒序列 | `IEnumerable<T>` | `Stream<T>` | generator | `Iterator` |

下一章：[05-错误处理与调试.md](./05-错误处理与调试.md) ｜ [返回 README](./README.md)
