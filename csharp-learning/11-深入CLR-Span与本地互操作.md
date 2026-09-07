# 第十一章 深入 CLR 与底层：内存、Span、unsafe 与本地互操作

> 本章目标：往下钻一层，真正理解 C# 程序的内存模型（栈/堆、GC）、值/引用语义，掌握 `Span<T>`/`ref struct` 与高性能路径，最后安全地使用 `unsafe`/指针和 P/Invoke 调用本地（C/C++/操作系统）代码。
> 前置：1~10 章。这是“知其所以然”的一章，重在建立正确的底层心智模型，工作中绝大多数代码**不需要** unsafe。

## 11.1 CLR：C# 程序运行的“虚拟机”

- C# 编译成 **IL（中间语言）**，由 **CLR（Common Language Runtime，公共语言运行时）** 加载执行：JIT 编译成机器码、管理内存（GC）、提供类型安全；
- 对标 Java：CLR ≈ JVM，IL ≈ 字节码。.NET 生态还有 AOT（NativeAOT 直接编成原生程序，见 11.8）；
- 运行中的程序区域大致分：**线程栈**（小、快、随方法调用分配释放）、**托管堆**（大、GC 管理）、**代码区**等。

## 11.2 值类型与引用类型的存储真相

- **值类型（struct/int/bool…）**：变量里直接存“数据本身”。局部变量通常在线程栈上；作为 class 的字段时内嵌在该对象里；
- **引用类型（class/string/数组…）**：变量存的是**引用（指向堆对象的地址）**，对象本体在托管堆上；
- **装箱（boxing）**：值类型被当成 `object`/接口用时，会在堆上包一层（有开销，尽量少隐式装箱）：

```csharp
int i = 42;
object boxed = i;        // 装箱：堆上分配
int j = (int)boxed;      // 拆箱

// 反例：集合装 object 时代码里容易悄悄装箱
List<object> objs = new();
objs.Add(42);            // 隐式装箱 ×N
// 泛型 List<int> 不存在这个问题——这就是“泛型集合比 ArrayList 快”的原因之一
```

**字符串为什么不可变？** `string` 是不可变引用类型。任何“修改”都产生新对象，旧对象交给 GC。大量拼接要用 `StringBuilder`（第七章）就是因为它维护可变缓冲，不反复分配。

## 11.3 GC：垃圾回收机制

- CLR 自动跟踪堆上“不再被引用”的对象并回收内存；开发者通常**不用手动释放**；
- 分代回收：**Gen0/Gen1/Gen2**。新对象进 Gen0，存活久了升级。GC 多数只“扫”Gen0，所以很快；大对象（>85KB）进 **LOH** 单独管理；
- 对 GC 有影响的做法：频繁创建大临时对象 → 引发 GC 压力；长生命周期对象持有大引用 → 内存滞留。

```csharp
// 诊断示例：测一次“生成大量对象”的 GC 行为（Release 下跑）
for (int i = 0; i < 3; i++)
{
    long before = GC.GetTotalMemory(false);
    var tmp = Enumerable.Range(1, 200_000).Select(x => new byte[256]).ToList();
    Console.WriteLine($"分配了 {GC.GetTotalMemory(false) - before:N0} 字节，Gen0 集合数 = {GC.CollectionCount(0)}");
    tmp.Clear();
}
```

常用诊断命令（生产排查内存用）：

```bash
dotnet-counters monitor --process-id <pid>        # 实时计数器（需先 dotnet tool install -g dotnet-counters）
dotnet-dump collect --process-id <pid>            # 抓内存快照离线分析
```

## 11.4 资源清理：IDisposable 与 IAsyncDisposable

GC 只管**托管内存**。文件句柄、数据库连接、网络 socket 等“非托管资源”必须显式释放——用 `IDisposable` + `using`：

```csharp
public class TempFile : IDisposable
{
    private readonly string _path;
    public TempFile(string path)
    {
        _path = path;
        File.WriteAllText(_path, "临时内容");
    }

    public string Path => _path;

    public void Dispose()
    {
        if (File.Exists(_path)) File.Delete(_path);
        Console.WriteLine("已清理临时文件");
    }
}
```

```csharp
using (var tf = new TempFile("tmp.txt"))          // 无论是否抛异常都会 Dispose
{
    Console.WriteLine(tf.Path);
}
// 作用域结束自动调用 Dispose；using 声明形式同理（第 5 章）
```

需要异步清理（如异步连接）实现 `IAsyncDisposable` + `await using`。**原则**：谁创建了非托管资源，谁负责释放；能用 `using`/`await using` 就别手动 try/finally。

## 11.5 Span\<T\> 与 ref struct：零分配切片

### 11.5.1 问题：切片为什么贵

对 `string`/数组做 `Substring`/`Skip` 往往要**复制数据**。复制大数组很浪费。

### 11.5.2 方案：Span 是“某个内存块的视图”

```csharp
// Span<T>：可读写、可切片，不复制底层数据（对数组/string 操作零分配）
int[] numbers = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

Span<int> all = numbers;               // 整个数组的视图
Span<int> middle = all.Slice(3, 4);    // {3,4,5,6} 视图（没复制）
middle[0] = 30;                        // 会反映到原数组！
Console.WriteLine(numbers[3]);         // 30

// 只读版：ReadOnlySpan<char> 用于字符串
string s = "hello world";
ReadOnlySpan<char> part = s.AsSpan(0, 5);
Console.WriteLine(part.ToString());    // hello
```

- `Span<T>` 是 **`ref struct`**：**只能活在栈上**，不能装箱、不能进堆、不能放进 class 字段/async 方法，这是为了安全（防“引用过期”）；
- 需要“可跨越 await/存放”的堆上安全版本用 `Memory<T>`；
- 高频路径（JSON 解析、字符串解析、序列化）都靠 Span 把分配降到最低。

### 11.5.3 用 Span 手写解析示例

```csharp
// 从 "name=value;name2=value2" 里逐个取键值（演示零分配遍历）
void ParsePairs(ReadOnlySpan<char> text)
{
    foreach (var range in text.Split(';'))
    {
        int eq = range.IndexOf('=');
        if (eq < 0) continue;
        ReadOnlySpan<char> k = range[..eq];
        ReadOnlySpan<char> v = range[(eq + 1)..];
        Console.WriteLine($"{k.ToString()} = {v.ToString()}");
    }
}
ParsePairs("a=1;b=hello;c=3.14");
// .NET 8+ 的 Span.Split 直接返回 range 迭代器，全程零分配
```

## 11.6 unsafe 与指针：何时用、怎么安全用

### 11.6.1 为什么有 unsafe

正常 C# 是内存安全的。`unsafe` 允许你直接用指针，获得与 C/C++ 同等的底层能力（性能关键路径、与本地代码交互），但也把“内存安全”的责任交回你手上。**规则：能不用就不用；用了要小范围隔离。**

开启：`.csproj` 里加 `<AllowUnsafeBlocks>true</AllowUnsafeBlocks>`。

### 11.6.2 基础示例

```csharp
unsafe static class NativeMath
{
    // 指针参数 + stackalloc 栈上分配 + fixed 固定托管对象
    public static int SumStack()
    {
        int* buf = stackalloc int[5];        // 在栈上分配 5 个 int
        int sum = 0;
        for (int i = 0; i < 5; i++)
        {
            buf[i] = i + 1;
            sum += buf[i];
        }
        return sum;                          // 15
    }

    public static void WriteFirst(int[] arr)
    {
        fixed (int* p = arr)                  // fixed：把托管数组“钉”住，防止 GC 搬动
        {
            *p = -1;                          // 直接改第一个元素
        }
    }
}

// 调用
Console.WriteLine(NativeMath.SumStack());
var a = new int[] { 7, 8, 9 };
NativeMath.WriteFirst(a);
Console.WriteLine(a[0]);                      // -1
```

### 11.6.3 unsafe 安全守则（UB 提醒）

| 危险操作 | 后果 | 安全写法 |
| --- | --- | --- |
| 指针越界读写 | 破坏内存/崩溃 | 严格算边界；优先用 Span |
| 悬挂指针（指向已释放/已移动对象） | 未定义行为 | `fixed` 钉住；不保存跨作用域指针 |
| 从 `stackalloc` 逃逸指针 | 栈回收后悬垂 | 只在当前方法内用 |
| 随意转换指针类型 | 对齐/别名问题 | 明确内存布局再转 |

> 若你要在 unsafe 里写大量代码，建议先读官方文档“unsafe 代码”章节，并在关键路径加边界断言。**绝大多数业务代码永远碰不到 unsafe。**

## 11.7 P/Invoke：调用本地（C/C++/系统）代码

Windows API、C 库、显卡驱动…… 通过 **P/Invoke**（`DllImport`）调用非托管函数。核心是声明函数签名 + 处理好数据封送（marshaling）。

### 11.7.1 调用系统库

```csharp
using System.Runtime.InteropServices;

internal static class NativeMethods
{
    // 声明外部函数：DllImport + EntryPoint + CharSet
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    internal static extern int GetComputerNameW(
        [Out] char[] buffer, ref uint size);
}
```

```csharp
// 更简单的跨平台示例：取用户名（libc）
[DllImport("libc", SetLastError = true)]
internal static extern uint geteuid();

Console.WriteLine($"当前 euid = {geteuid()}");
```

### 11.7.2 与 C 函数传数据：结构体布局

本地结构体需要 `[StructLayout(LayoutKind.Sequential)]` 保证字段按 C 顺序排列：

```csharp
[StructLayout(LayoutKind.Sequential)]
public struct Point
{
    public int X;
    public int Y;
}

// C 侧：int AddPoint(Point* p1, Point* p2);
[DllImport("mylib")]
internal static extern int AddPoint(ref Point p1, ref Point p2);
```

### 11.7.3 字符串与指针要点

- `string` → C 侧 `char*`：`[MarshalAs(UnmanagedType.LPStr)]` 或 `LPWStr`；
- 回调函数（函数指针）用 `delegate` + `Marshal.GetFunctionPointerForDelegate`；
- C# 侧抛异常不能穿过原生边界；原生侧出错码要用 `SetLastError` + `Marshal.GetLastWin32Error()`。

### 11.7.4 安全封装模式（推荐）

不要把 `DllImport` 撒得到处都是，包成一个“安全门面”：

```csharp
public static class SysInfo
{
    // 对外：安全、无指针
    public static string? UserName()
    {
        try
        {
            var buf = new char[256];
            uint len = (uint)buf.Length;
            return NativeMethods.GetComputerNameW(buf, ref len) ? new string(buf, 0, (int)len) : null;
        }
        catch (DllNotFoundException) { return null; }   // 平台没有该库
        catch (EntryPointNotFoundException) { return null; }
    }
}
```

> 完整跨语言生成绑定可用 **LibraryImport**（源生成版 DllImport）或工具 **CsWin32**、**bindgen 类似物**。

## 11.8 从 C# 导出给别处调用：NativeAOT

把 C# 编译成**原生动态库**给 C/Python/Go 等调用（无 JIT、启动快、体积小但功能受限）：

```bash
# .csproj 里启用：
# <PublishAot>true</PublishAot>
# 再用 UnmanagedCallersOnly 导出：
```

```csharp
// 导出给 C 调用的示例（需 NativeAOT 支持子集）
public class Exports
{
    [UnmanagedCallersOnly(EntryPoint = "add")]
    public static int Add(int a, int b) => a + b;
}
```

编译后得到 `.so`/`.dylib`/`.dll`，其它语言即可 `extern "C" int add(int, int)` 调用。适合做高性能核心 + 其它语言做胶水层的架构。

## 11.9 性能测量：别猜，量一下

优化前先测量。`.NET` 生态标准工具是 **BenchmarkDotNet**：

```bash
dotnet add package BenchmarkDotNet
```

```csharp
using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Running;
using System.Text;

public class Bench
{
    private readonly int[] _data = Enumerable.Range(1, 10_000).ToArray();

    [Benchmark]
    public long SumWithFor()
    {
        long s = 0;
        for (int i = 0; i < _data.Length; i++) s += _data[i];
        return s;
    }

    [Benchmark]
    public long SumWithLinq() => _data.Sum();
}

BenchmarkRunner.Run<Bench>();
// 输出会告诉你每次调用平均耗时与内存分配
```

## 11.10 完整可运行示例：高性能字符串解析（Span）+ P/Invoke 一个 C 风格函数

```csharp
using System.Runtime.InteropServices;

// —— 1) 零分配解析 CSV 一行 ——
int CountCsv(ReadOnlySpan<char> line)
{
    int count = 0;
    foreach (var r in line.Split(','))
        if (!r.IsEmpty) count++;
    return count;
}
Console.WriteLine(CountCsv("a,b,,d,e"));      // 4（空段不计）

// —— 2) 检查“是否大小写字母混合”用 ReadOnlySpan 避免子串分配 ——
bool HasUpper(ReadOnlySpan<char> s)
{
    foreach (var c in s)
        if (char.IsUpper(c)) return true;
    return false;
}
Console.WriteLine(HasUpper("cSharp".AsSpan()));   // True

// —— 3) P/Invoke：Windows 上取当前目录的短文件名版本（演示用，仅 Windows）——
if (OperatingSystem.IsWindows())
{
    Console.WriteLine("（Windows 环境：可在此调用 kernel32 的函数做演示）");
}

// —— 4) 手动内存视图：把一段字节按整数读（演示 Span 视图）——
byte[] raw = { 1, 0, 0, 0, 2, 0, 0, 0 };
var ints = MemoryMarshal.Cast<byte, int>(raw);   // 把字节数组按 int 解读
foreach (var v in ints) Console.Write(v + " ");  // 1 2
Console.WriteLine();
```

> 本示例多数片段在 Linux/macOS 也能跑；P/Invoke 部分按平台分支给出。

## 11.11 实战与验收

**必做**

1. 用 BenchmarkDotNet 对比：`for` vs `foreach` vs `LINQ Sum`（10 万元素）——记录差异，理解“先测量再优化”；
2. 用 `Span<int>` 实现“数组局部反转”（`Slice` 反转不复制），并验证原数组被改变；
3. 实现一个 `IDisposable` 类型 + `using` 演练（参考 TempFile）；
4. 写一段 `unsafe` 代码（如 `stackalloc` 求局部和），并说明为何不该在生产滥用。

**进阶题（任选）**

- 用 P/Invoke 调用你平台的真实 API（Windows：`GetSystemTime`；Linux：`getpid`/`geteuid`）；
- 用 `MemoryMarshal` 把 `byte[]` 视图当 `struct[]` 解析二进制文件头；
- 用 `dotnet-counters` 观察自己小程序的 GC 计数，找出分配热点。

**验收清单**

- [ ] 能画出值类型 vs 引用类型的内存差异并解释装箱
- [ ] 能讲 GC 分代的大致思想与什么时候会痛
- [ ] 知道 `IDisposable` + `using` 何时必要
- [ ] 能解释 `Span<T>` 为何能零分配切片、为何不能跨 await
- [ ] 理解 unsafe 的风险边界与“小范围隔离”原则
- [ ] 会声明并调用一个 `DllImport` 函数
- [ ] 用 BenchmarkDotNet 做过一次“先测量再优化”

## 11.12 小结

向下钻完这一层，你看到的 C# 不再是“语法”，而是一台有栈有堆、有 GC、有 JIT/AOT 的机器：值类型避免分配、Span 让解析零复制、unsafe/P-Invoke 打通与原生世界的边界。理解这些，写高性能服务、排内存问题、接系统 API 都不再神秘。

最后一章看向“全景”：**.NET 生态巡礼**——桌面、游戏、云、AI、大数据，以及多语言体系的最终对照。

## 语言对照

| 概念 | C# | Java | Python | Rust |
| --- | --- | --- | --- | --- |
| 运行时 | CLR | JVM | CPython | 无（native） |
| 中间表示 | IL | 字节码 | 字节码（.pyc） | LLVM IR |
| GC | 分代 GC | 分代 GC | 引用计数+GC | 所有权（无 GC） |
| 局部数组视图 | `Span<T>` | 无直接等价 | 无 | `&[T]` 切片 |
| 手动内存 | `unsafe` | sun.misc.Unsafe | ctypes/array | unsafe |
| 调本地库 | P/Invoke | JNI/FFM API | ctypes/cffi | FFI extern "C" |
| 堆对象固定 | `fixed` | 无 | 无 | Pin |

下一章：[12-.NET生态与收官.md](./12-.NET生态与收官.md) ｜ [返回 README](./README.md)
