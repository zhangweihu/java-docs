# 第一章 C# 环境搭建与基础语法

> 本章目标：装好开发环境，写出并运行你的第一个 C# 程序；掌握变量、基本类型、运算符、流程控制、方法、数组与字符串入门；学会读懂编译器的报错。
> 学习方式：**每一步都动手敲一遍**，不要复制粘贴后不看效果。

## 1.1 什么是 C# 和 .NET

- **C#** 是一门编程语言（读作“C Sharp”），语法风格接近 Java 与 C++，由微软于 2000 年推出；
- **.NET** 是运行平台/框架：包含一个运行时（负责把程序跑起来、自动管理内存）和庞大的官方类库（文件、网络、JSON、数据库等现成功能）；
- C# 写的代码编译后跑在 .NET 上。你写的“C# 程序”，正式说法常叫“.NET 应用”。

可以想象：**C# 是菜谱（语言），.NET 是厨房和灶台（运行时 + 工具箱）**。

**谁在用 C# ？** 很多网站后端（ASP.NET Core）、Windows 桌面软件、游戏（Unity 引擎脚本）、云服务、AI 应用都在用它。学会了可以跨平台运行在 Windows / Linux / macOS 上。

## 1.2 安装环境（动手！）

1. 打开官网：https://dotnet.microsoft.com/download
2. 下载 **.NET SDK**（选长期支持 LTS 版本，写作时推荐 10.x）。SDK = 开发工具包，里面既包含“编译器”，也包含“运行时”；
3. 安装时按默认选项一路下一步即可（Windows 上勾选“加入 PATH”默认已做）；
4. 安装完成后，**重新打开**一个终端（命令提示符 / PowerShell / VS Code 终端），输入：

```bash
dotnet --version
```

能看到类似 `10.0.x` 的输出，就说明安装成功。

> 如果你还没装编辑器：Windows 上推荐装 Visual Studio Community（免费，选“.NET 桌面开发”工作负载）；更轻量的是 VS Code + 安装“C# Dev Kit”扩展。后面章节示例均可在任何编辑器 + `dotnet` 命令下完成。

## 1.3 第一个程序：Hello, World！

打开终端，进入你打算放代码的目录，执行：

```bash
dotnet new console -o HelloCs
cd HelloCs
dotnet run
```

你会看到控制台输出 `Hello, World!`。

生成的工程里有几个关键文件：

- `HelloCs.csproj`：工程描述文件（XML），记录项目名、目标框架、引用的包等；
- `Program.cs`：程序源代码，初始内容大约如下（C# 10+ 的“顶级语句”写法）：

```csharp
Console.WriteLine("Hello, World!");
```

### 1.3.1 尝试改代码

用编辑器打开 `Program.cs`，改成：

```csharp
Console.WriteLine("你好，C#！");
Console.WriteLine("我叫小明，今年 " + 12 + " 岁");
Console.WriteLine(2 + 3 * 4);   // 先乘除后加减，输出 14
```

再执行 `dotnet run` 看看输出。

> **约定**：`//` 后面的内容叫“注释”，是给人看的说明，程序不会执行。写注释是好习惯。

### 1.3.2 代码是怎样变成程序的

```text
你写的 Program.cs（C# 源代码）
        │  dotnet build / dotnet run
        ▼
中间语言 IL（类似 Java 字节码，平台无关）
        │  运行时（.NET Runtime）在启动时 JIT 编译
        ▼
机器码，真正在 CPU 上运行
```

这与 Java“源码 → 字节码 → JVM 解释/编译”的模型几乎一模一样——如果你学过 Java，会觉得很亲切。

## 1.4 控制台输入与输出

程序与用户交互最基础的方式：输出到屏幕、从键盘读入。

```csharp
Console.WriteLine("你叫什么名字？");   // 输出一行
string? name = Console.ReadLine();    // 读取一行输入（可能为 null）
Console.WriteLine($"你好，{name}！欢迎学习 C#。");
```

- `Console.WriteLine(...)`：输出并换行；`Console.Write(...)`：输出不换行；
- `$"..."` 叫**字符串插值**：`{变量名}` 处会被变量的值替换，非常常用；
- `string?` 中的 `?` 表示“这个字符串可能为空”（用户可能直接按回车）。后面第 3 章会细讲可空引用类型，先照抄即可。

## 1.5 变量与基本数据类型

变量 = 给数据起个名字存放在内存里。C# 是**强类型**语言：每个变量都有明确的类型，类型不匹配编译期就会报错（这是好事，帮你早发现问题）。

### 1.5.1 常用基本类型

| 类型 | 含义 | 示例 | 取值范围（简化） |
| --- | --- | --- | --- |
| `int` | 整数 | `int age = 30;` | 约 ±21 亿 |
| `long` | 大整数 | `long big = 9_000_000_000L;` | 更大 |
| `double` | 双精度小数 | `double pi = 3.14159;` | 约 15~16 位有效数字 |
| `decimal` | 十进制小数（**金额用这个**） | `decimal money = 99.9m;` | 精度高，适合钱 |
| `bool` | 布尔（真/假） | `bool isOk = true;` | `true` / `false` |
| `char` | 单个字符 | `char c = 'A';` | 一个 Unicode 字符 |
| `string` | 字符串（一串字符） | `string s = "你好";` | 长度不限 |

说明几点：

```csharp
int age = 30;
decimal price = 19.9m;        // 小数常量默认是 double，加 m 后缀表示 decimal
long stars = 4_000_000_000L;  // 下划线只是“千分位”易读写法；L 后缀表示 long
var name = "小明";             // var：让编译器根据“=”右边的值推断类型，这里等价于 string
// var 不能脱离初始值： var x;   ← 这样写会报错
const double PI = 3.14159;    // const 常量：一旦赋值就不能改
```

> **什么时候用 `decimal`？** 计算钱、税率、账务时用 `decimal`（不会出现 0.1+0.2≠0.3 这种二进制浮点误差）。科学计算、坐标、温度这类用 `double` 即可。

### 1.5.2 变量的命名规则（必须遵守）

- 只能包含字母、数字、下划线 `_`，且**不能以数字开头**；
- 不能用 C# 关键字（如 `int`、`class`、`new`）当名字；
- 约定俗成：本地变量用**驼峰式** `myAge`，类型与类名用**帕斯卡式** `MyClass`。

### 1.5.3 类型转换

```csharp
// 隐式转换：小范围 → 大范围，安全，编译器自动做
int a = 3;
double b = a;                 // int 自动变成 double，没问题

// 显式转换：大范围 → 小范围，可能丢失数据，必须写 (类型)
double x = 2.7;
int y = (int)x;               // 结果 y = 2（截断小数部分）

// 字符串 → 数字
string s = "42";
int n = int.Parse(s);         // 能成功；但如果 s 不是数字会抛异常（第 5 章讲）

// 更安全的解析：TryParse 成功返回 true，失败返回 false 且不会崩溃
bool ok = int.TryParse("abc", out int result);
Console.WriteLine($"解析成功？{ok}，result={result}");   // False，result=0
```

## 1.6 运算符

### 1.6.1 算术运算符

```csharp
int a = 10, b = 3;
Console.WriteLine(a + b);   // 13
Console.WriteLine(a - b);   // 7
Console.WriteLine(a * b);   // 30
Console.WriteLine(a / b);   // 3   ← 两个整数相除得到整数（3.33 被截断为 3）！
Console.WriteLine(a % b);   // 1   ← 取余（求模）
Console.WriteLine(10.0 / 3); // 3.333... ← 只要有小数参与就是小数除法
```

- 注意：**整数除以整数永远得整数**。想要小数结果，至少让一个操作数是小数。
- 复合赋值：`a += 2` 等价于 `a = a + 2`；还有 `-=`、`*=`、`/=`、`%=`；
- 自增自减：`a++`（先取后加）、`++a`（先加后取），`a--` 同理。

### 1.6.2 比较与逻辑运算符

```csharp
int score = 85;
bool pass = score >= 60;              // >= 比较，结果 true
bool isTop = score >= 90 && pass;     // && 逻辑与（两边都真才真）
bool tryAgain = score < 60 || score == 100; // || 逻辑或（一边真就真）
bool notPass = !pass;                 // ! 逻辑非
Console.WriteLine($"{pass} {isTop} {notPass}");
```

字符串比较用 `==`/`!=`：

```csharp
string fruit = "apple";
Console.WriteLine(fruit == "apple");  // True（字符串值相等比较）
```

### 1.6.3 三元运算符

```csharp
int age = 17;
string tip = age >= 18 ? "成年" : "未成年";   // 条件 ? 真值 : 假值
Console.WriteLine(tip);   // 未成年
```

## 1.7 流程控制

### 1.7.1 if / else if / else

```csharp
int score = 75;
if (score >= 90)
{
    Console.WriteLine("优秀");
}
else if (score >= 60)
{
    Console.WriteLine("及格");
}
else
{
    Console.WriteLine("不及格");
}
```

- `if` 后面的圆括号里放“条件”，为 `true` 才执行大括号里的语句；
- 只有一个语句时大括号可省略，但**强烈建议永远写大括号**，避免日后加代码时出错。

### 1.7.2 switch 语句

```csharp
string day = "Mon";
switch (day)
{
    case "Mon":
    case "Tue":
    case "Wed":
    case "Thu":
    case "Fri":
        Console.WriteLine("工作日");
        break;                     // 每个 case 结束要 break，跳出 switch
    case "Sat":
    case "Sun":
        Console.WriteLine("周末");
        break;
    default:                       // 都不匹配时执行
        Console.WriteLine("未知");
        break;
}
```

> C# 8+ 还有更简洁的 `switch` 表达式和模式匹配，第 3 章会展开讲。

### 1.7.3 循环：for / while / foreach

```csharp
// for：知道循环次数
for (int i = 1; i <= 5; i++)
{
    Console.Write(i + " ");   // 输出：1 2 3 4 5
}
Console.WriteLine();

// while：先判断再执行（可能一次都不执行）
int n = 0;
while (n < 3)
{
    Console.WriteLine($"第 {n + 1} 次");
    n++;
}

// do-while：至少执行一次
int m = 0;
do
{
    Console.WriteLine("我至少会执行一次");
    m++;
} while (m < 0);
```

- `break`：立刻跳出循环；`continue`：跳过本次循环剩余语句，进入下一次。

## 1.8 方法（函数）

把一段可复用的逻辑包起来，起个名字，需要时调用：

```csharp
// 定义方法：返回类型 + 名字 + 参数列表 + 方法体
int Add(int x, int y)
{
    return x + y;
}

double CircleArea(double radius)
{
    return Math.PI * radius * radius;
}

// 调用
Console.WriteLine(Add(3, 4));             // 7
Console.WriteLine(CircleArea(2));         // 12.566370614359172
```

### 1.8.1 方法小知识

- 没有返回值用 `void`：`void PrintHello() { Console.WriteLine("Hi"); }`
- 参数可以有默认值，调用时可省略：`void Greet(string name, string greeting = "你好")`，调用 `Greet("小明")` 等价 `Greet("小明", "你好")`；
- 命名参数：`CircleArea(radius: 2)`，代码更可读；
- **重载**：同名的多个方法，只要参数列表不同即可并存：

```csharp
int Max(int a, int b) => a > b ? a : b;
double Max(double a, double b) => a > b ? a : b;
```

- 上面用了 **表达式体（`=>`）**，`方法 => 表达式` 是单行方法的简写，后面会频繁看到。

> 在“顶级语句”程序里，方法可以写在 `Program.cs` 的下方（顶级语句之后的任何位置都行）。

## 1.9 数组入门

数组：存放**同类型、固定长度**的一组数据。

```csharp
int[] scores = { 78, 85, 92, 60, 45 };   // 声明 + 初始化

Console.WriteLine(scores.Length);        // 5：数组长度
Console.WriteLine(scores[0]);            // 78：下标从 0 开始
scores[0] = 80;                          // 修改第一个元素
Console.WriteLine(scores[^1]);           // 45：^1 表示倒数第一个（C# 8+）

// foreach：只读遍历，最常用
int sum = 0;
foreach (int s in scores)
{
    sum += s;
}
Console.WriteLine($"总分 {sum}，平均 {sum / (double)scores.Length:F2}");
```

- 下标越界会抛异常：`scores[10]` → 运行时抛 `IndexOutOfRangeException`（第 5 章讲异常）；
- 数组长度固定。需要动态增删请用 `List<T>`（第 4 章）。

## 1.10 字符串入门

字符串是 C# 里最常用的类型，这一节掌握最基本的操作：

```csharp
string s = "  Hello, C# World  ";

Console.WriteLine(s.Length);            // 19：字符数
Console.WriteLine(s.Trim());            // 去掉首尾空格
Console.WriteLine(s.ToUpper());         // 全部大写
Console.WriteLine(s.ToLower());         // 全部小写
Console.WriteLine(s.Contains("C#"));    // True：是否包含子串
Console.WriteLine(s.StartsWith("Hello")); // False（前面有空格）
Console.WriteLine(s.Trim().StartsWith("Hello")); // True
Console.WriteLine(s.Substring(2, 5));   // 从下标 2 开始取 5 个字符
Console.WriteLine(s.IndexOf("C#"));     // 子串位置（找不到返回 -1）

string[] parts = "apple,banana,orange".Split(',');
foreach (string p in parts) Console.WriteLine(p);   // 拆分成数组

string joined = string.Join(" | ", parts);          // apple | banana | orange
Console.WriteLine(joined);

// 字符串比较
string a = "abc", b = "ABC";
Console.WriteLine(a == b);              // False：区分大小写
Console.WriteLine(a.Equals(b, StringComparison.OrdinalIgnoreCase)); // True
```

### 1.10.1 字符串插值与原样字符串

```csharp
string name = "小明";
int age = 12;
Console.WriteLine($"{name}今年{age}岁，明年{age + 1}岁");   // 插值里还能写表达式

// @"" 原样字符串：路径里的 \ 不用转义
string path = @"C:\Users\you\Documents\file.txt";
Console.WriteLine(path);

// 多行文本用三个引号 """（C# 11+）
string poem = """
    床前明月光
    疑是地上霜
    """;
Console.WriteLine(poem);
```

> 字符串是**不可变**的：`s.ToUpper()` 不会修改原字符串 `s`，而是返回一个新字符串。修改字符串的“正确姿势”之一是第 7 章的 `StringBuilder`。

## 1.11 读懂编译器报错（重要技能）

C# 编译器报错通常形如：

```text
Program.cs(6,14): error CS1002: 应输入 ;
```

含义拆解：`Program.cs(6,14)` = 文件第 6 行第 14 列；`CS1002` 是错误编号（可去搜索引擎查“CS1002”）；最后是中文描述。

新手高频错误速查：

| 报错编号（示例） | 常见原因 | 修法 |
| --- | --- | --- |
| CS1002 `应输入 ;` | 语句末尾漏分号 | 上一行补 `;` |
| CS0103 `名称不存在` | 变量名拼错 / 未声明 | 检查拼写与声明位置 |
| CS0201 `只有赋值、调用…可作语句` | 单独写了表达式 | 把它赋给变量或用掉 |
| CS0165 `使用了未赋值的局部变量` | 声明了但没赋值就使用 | 先初始化 |
| CS0029 `无法将 X 转换 Y` | 类型不匹配 | 检查类型/加显式转换 |
| CS1513 `应输入 }` | 少了右大括号 | 检查大括号配对 |
| CS8602（提示） | 可能为 null 的变量被直接使用 | 判空后再用（第 3/5 章） |

**练习方法**：故意在代码里删掉一个分号，`dotnet build` 看报错，再改回来。多做几次，读报错的能力就上来了。

## 1.12 完整可运行示例：控制台计算器 v1

把下面的代码整体替换到 `Program.cs`，`dotnet run` 运行：

```csharp
Console.WriteLine("===== 简易计算器 =====");
Console.WriteLine("支持 + - * /，输入 q 退出");

while (true)
{
    Console.Write("输入第一个数字：");
    string? inputA = Console.ReadLine();
    if (inputA == "q") break;

    if (!double.TryParse(inputA, out double a))
    {
        Console.WriteLine("不是有效数字，请重试");
        continue;
    }

    Console.Write("输入运算符（+ - * /）：");
    string? opLine = Console.ReadLine();
    if (string.IsNullOrEmpty(opLine)) continue;
    char op = opLine[0];

    Console.Write("输入第二个数字：");
    if (!double.TryParse(Console.ReadLine(), out double b))
    {
        Console.WriteLine("不是有效数字，请重试");
        continue;
    }

    double result;
    switch (op)
    {
        case '+': result = a + b; break;
        case '-': result = a - b; break;
        case '*': result = a * b; break;
        case '/':
            if (b == 0)
            {
                Console.WriteLine("除数不能为 0！");
                continue;                    // 直接进入下一轮
            }
            result = a / b;
            break;
        default:
            Console.WriteLine("未知运算符");
            continue;
    }

    Console.WriteLine($"{a} {op} {b} = {result:F4}");   // F4：保留 4 位小数
}

Console.WriteLine("已退出，再见！");
```

## 1.13 实战与验收

**必做**

1. 装好 .NET SDK，`dotnet --version` 有输出；
2. 跑通 1.12 的计算器，并测试：输入 `10`、`/`、`0`（应提示除数不能为 0 而不是崩溃）；输入 `abc`（应提示重新输入）；
3. 写一个方法 `bool IsLeapYear(int year)` 判断闰年（规则：能被 4 整除且不能被 100 整除，或能被 400 整除），在 `Main` 里测试 `2024`（True）和 `1900`（False）。

**进阶题（任选）**

- 打印 9×9 乘法表（双层 for）；
- 输入 N 个成绩，输出最高分、最低分、平均分；
- 写一个方法把摄氏温度转华氏：`F = C × 9/5 + 32`（注意小数陷阱）。

**验收清单**

- [ ] 能说出 `int` / `double` / `decimal` / `bool` / `string` 的用途
- [ ] 能解释为什么 `10 / 3` 结果是 3
- [ ] 遇到编译错误能根据 `CS 编号` + 行号定位问题
- [ ] 完成计算器且“输入非法数据不崩溃”

## 1.14 小结

本章你学会了：安装 .NET、`dotnet new/run/build`、控制台 IO、基本类型与转换、运算符、流程控制、方法、数组、字符串、读编译错误。这些是任何 C# 程序的“细胞”。

下一章我们进入 **面向对象编程**：用“类”把数据和操作组织起来，这也是 C#（以及 Java）与脚本语言最大不同的编程范式。

## 语言对照

| 概念 | C# | Java | Python | Rust |
| --- | --- | --- | --- | --- |
| 输出 | `Console.WriteLine` | `System.out.println` | `print` | `println!` |
| 输入一行 | `Console.ReadLine()` | `new Scanner(System.in).nextLine()` | `input()` | `stdin` 读行 |
| 字符串插值 | `$"{x}"` | `"..." + x` / `formatted` | `f"{x}"` | `format!` |
| 强类型声明 | `int age = 1` | `int age = 1` | 无 | `let age: i32 = 1` |
| 常量 | `const` | `final` | 惯例全大写 | `const` |
| 整数除法 | 截断（同 Java） | 截断 | `//` 截断 | 截断 |
| 数组遍历 | `foreach` | 增强 for | `for ... in` | `for ... in` |
| 三目 | `cond ? a : b` | `cond ? a : b` | `a if cond else b` | `if cond {a} else {b}` |

下一章：[02-面向对象编程.md](./02-面向对象编程.md) ｜ [返回 README](./README.md)
