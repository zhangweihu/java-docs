# 第二十四章 JVM 内存与调优

> 本章目标：理解 JVM 内存结构、类加载机制、GC 原理，掌握常用 JVM 参数、性能监控命令（jps/jstat/jmap/jstack）与典型调优案例（OOM 排查、Full GC 优化、线上故障定位），成为"能看堆栈、能抓现场"的 Java 工程师。
>
> 前置知识：第一章基础语法、第五章多线程、第九章 Spring Boot（调优对象）。

## 24.1 JVM 是什么

```
源码 .java ──► 编译 ──► 字节码 .class ──► JVM 解释/编译执行
                                          │
                    JVM = Java 虚拟机（Java Virtual Machine）
                    "一次编译，到处运行" 的底层秘密
```

- **JDK** = JRE + 开发工具（javac、jstack 等）
- **JRE** = JVM + 核心类库
- **JVM** = 真正运行字节码的引擎 + 内存管理 + 垃圾回收

### 24.1.1 JVM 体系结构（三大子系统）

```
┌─────────────────────────────────────────────────┐
│               ClassLoader（类加载子系统）          │
│  加载 .class → 校验 → 准备 → 解析 → 初始化          │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│              运行时数据区（内存）                   │
│  方法区 / 堆 / 虚拟机栈 / 本地方法栈 / 程序计数器      │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│            执行引擎（解释器 + JIT + GC）           │
│  GC 垃圾回收器  ← 调优的主角                        │
└─────────────────────────────────────────────────┘
```

## 24.2 运行时数据区（内存模型）

### 24.2.1 五大区域总览

```
                    JVM 内存
┌──────────────────────────────────────────────────────────┐
│ 线程共享                                             │
│  ┌─────────────────────────────┐   ┌────────────────┐  │
│  │ 堆（Heap）——对象的大本营      │   │ 方法区（元空间）  │  │
│  │ 新生代：Eden + S0 + S1       │   │ 类元信息/静态变量 │  │
│  │ 老年代：长期存活对象          │   │ 运行时常量池      │  │
│  └─────────────────────────────┘   └────────────────┘  │
├──────────────────────────────────────────────────────────┤
│ 线程私有                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │ 虚拟机栈       │  │ 本地方法栈     │  │ 程序计数器    │  │
│  │ 栈帧：局部变量表 │  │ native 方法    │  │ 记录下一条指令 │  │
│  └──────────────┘  └──────────────┘  └─────────────┘  │
└──────────────────────────────────────────────────────────┘
```

| 区域 | 线程 | 存什么 | 异常 | 是否 GC |
| --- | --- | --- | --- | --- |
| 堆 Heap | 共享 | 对象实例、数组 | `OutOfMemoryError: Java heap space` | **是** |
| 方法区/元空间 Metaspace | 共享 | 类元信息、常量池、静态变量 | `OutOfMemoryError: Metaspace` | 是（JDK8 后） |
| 虚拟机栈 Stack | 私有 | 栈帧（局部变量、操作数栈、方法调用） | `StackOverflowError` | 否 |
| 本地方法栈 | 私有 | native 方法 | `StackOverflowError` | 否 |
| 程序计数器 PC | 私有 | 当前执行的字节码行号 | 无 | 否 |

> **JVM 调优 90% 在堆**，堆也是最常出问题的地方。元空间 JDK8 后从堆移到本地内存（不再受 `-Xmx` 限制）。

### 24.2.2 堆内存分区（调优主战场）

```
堆（-Xms = 初始，-Xmx = 最大，一般设为相同值避免扩容抖动）
│
├── 新生代（Young，默认约 1/3 堆）
│   ├── Eden 区（新对象都放这，约 8/10 新生代）
│   ├── S0 幸存区（约 1/10）
│   └── S1 幸存区（约 1/10）
│
└── 老年代（Old，约 2/3 堆）
    存活超过阈值（默认 15 次 Minor GC）的对象
```

**对象的一生**：

```
new 对象 ──► Eden ──(Minor GC 存活)──► S0/S1（年龄+1）
    │                                      │
    │                             年龄 ≥ 15（默认）
    │                                      ▼
    └──────────────────────────────► 老年代（Major GC 时回收）
```

> 默认比例 `-XX:NewRatio=2`（新生代:老年代 = 1:2），`-XX:SurvivorRatio=8`（Eden:S0:S1 = 8:1:1）。

## 24.3 类加载机制

### 24.3.1 类加载五个阶段

```
加载 ──► 校验 ──► 准备 ──► 解析 ──► 初始化
 │         │        │        │        │
读 .class  格式    静态变量   符号引用  静态变量赋值
创建 Class  合法性   赋默认值   转直接引用  静态代码块执行
```

**高频面试题**：

```java
class Test {
    static int a = 1;   // 准备阶段：a = 0；初始化阶段：a = 1
}
```

### 24.3.2 双亲委派模型（面试必问）

```
                    Bootstrap ClassLoader（启动类加载器）
                    加载 JVM 自带类：java.lang.*、rt.jar
                              ▲
                    Ext/Platform ClassLoader（扩展类加载器）
                    加载 jre/lib/ext 下的类
                              ▲
                    Application ClassLoader（应用类加载器）
                    加载 classpath 下的类
                              ▲
                    自定义 ClassLoader（可热部署/隔离）
```

**双亲委派规则**：加载类时，先**向上委托父加载器**，父能加载就不自己加载；父都加载不了才自己加载。

**为什么？** 保证核心类不被篡改 —— 你写的 `java.lang.String` 永远加载不到（被 Bootstrap 抢走了），防止类重复和安全性问题。

**打破双亲委派**：Tomcat 的类隔离、Spring Boot 的 jar 内嵌类加载、SPI（ServiceLoader）都打破了，实现 `findClass` 重写 `loadClass`。

## 24.4 垃圾回收（GC）

### 24.4.1 判断对象是否可回收

**可达性分析（主流）**：

```
GC Roots：虚拟机栈引用的对象、静态变量、常量、JNI 引用
           │
    ┌──────┼──────┐
    ▼      ▼      ▼
  对象A   对象B   对象C
                  │
                  ▼
               对象D（B、C 存活则 D 存活）
```

从 GC Roots 出发，**能到达的对象存活**，不能到达的回收。循环引用（A↔B 互指但无人引用）也能被正确回收。

### 24.4.2 常见 GC 算法

| 算法 | 思想 | 特点 |
| --- | --- | --- |
| 标记-清除 | 标记垃圾再清除 | 简单但有**内存碎片** |
| 复制 | 存活对象复制到另一半 | 无碎片，浪费一半空间（新生代用） |
| 标记-整理 | 标记后把存活对象**移到一端** | 无碎片、无空间浪费（老年代用） |

**分代收集**（综合运用）：

```
新生代（对象朝生夕死 98%）→ 复制算法（Eden→S0→S1，用 S0/S1 互相倒）
老年代（对象存活久）      → 标记-清除 / 标记-整理
```

### 24.4.3 GC 类型

| GC | 触发 | 区域 | 特点 |
| --- | --- | --- | --- |
| Minor GC（Young GC） | Eden 满了 | 新生代 | 频繁，快（毫秒级） |
| Major GC（Old GC） | 老年代满了 | 老年代 | 较慢 |
| Full GC | 老年代满 / 元空间满 / `System.gc()` | 全部 | **最慢，线上要避免频繁触发** |

**晋升老年代的条件**：
1. 年龄达到阈值（默认 15，`-XX:MaxTenuringThreshold`）
2. 大对象直接进老年代（`-XX:PretenureSizeThreshold`）
3. 动态年龄判断：S 区存活对象超过 50% 空间

### 24.4.4 垃圾收集器（面试常问）

| 收集器 | 适用 | 特点 |
| --- | --- | --- |
| Serial / Serial Old | 单核、客户端 | 单线程，STW 长 |
| ParNew | 新生代 | 多线程并行，配合 CMS |
| **CMS** | 老年代 | 并发收集，**停顿短**（JDK8 时代主流） |
| **G1** | 全堆 | **JDK9+ 默认**，分区 + 可预测停顿 |
| **ZGC** | 大堆（JDK15+） | 停顿 < 1ms，TB 级堆 |

> JDK8 默认：Parallel Scavenge + Parallel Old（吞吐优先）。JDK9+ 默认 G1。调优不追求"最新"，线上稳定最重要。

**STW（Stop The World）**：GC 时所有业务线程暂停。**调优的本质 = 在"停顿时间"和"吞吐量"之间找平衡**。

## 24.5 常用 JVM 参数（必须背）

### 24.5.1 内存参数

```bash
java -Xms512m \                    # 初始堆大小
     -Xmx1024m \                   # 最大堆大小（两者相等最好，避免扩容抖动）
     -Xmn256m \                    # 新生代大小
     -XX:MetaspaceSize=128m \      # 元空间初始大小
     -XX:MaxMetaspaceSize=256m \   # 元空间最大大小
     -XX:SurvivorRatio=8 \         # Eden:S0:S1 = 8:1:1
     -XX:MaxTenuringThreshold=15 \ # 晋升老年代年龄阈值
     -jar app.jar
```

### 24.5.2 GC 与日志参数

```bash
java -Xms512m -Xmx512m \
     -XX:+UseG1GC \                       # 指定垃圾收集器（JDK9+ 默认）
     -XX:MaxGCPauseMillis=200 \           # G1 目标停顿 200ms
     -XX:+PrintGCDetails \                # 打印 GC 详情（JDK8）
     -Xlog:gc*:file=/data/logs/gc.log \   # GC 日志输出到文件（JDK9+）
     -XX:+HeapDumpOnOutOfMemoryError \    # OOM 时自动 dump 堆快照（必配！）
     -XX:HeapDumpPath=/data/logs/heap.hprof \  # dump 文件位置
     -XX:+PrintGCDateStamps \
     -jar app.jar
```

> **企业必配三件套**：GC 日志（事后分析）、`HeapDumpOnOutOfMemoryError`（OOM 留现场）、堆/内存参数固定（`-Xms = -Xmx`）。

## 24.6 监控与排查命令（jps/jstat/jmap/jstack）

### 24.6.1 jps：查看 Java 进程

```bash
jps -l        # 列出所有 Java 进程及主类
# 输出：12345 com.example.demo.DemoApplication
```

### 24.6.2 jstat：监控 JVM 状态（最常用）

```bash
# 每 1000ms 输出一次，共 10 次
jstat -gcutil 12345 1000 10

# 输出说明：
#  S0   S1     E      O      M     CCS    YGC     YGCT   FGC   FGCT     GCT
#  0.00 100.00 45.62  60.00  92.00  88.00    128   1.256    3   0.820   2.076
#  │     │     │      │      │      │      │        │     │     │       │
#  S0/S1 幸存区  Eden 老年代  元空间 压缩类  年轻代GC  年轻代  FullGC FullGC 总时间
#  占用%  占用% 占用% 占用%  占用%  占用%   次数      总耗时  次数   总耗时
```

**判读经验**：
- `O`（老年代）持续增长且迟迟不降 → 有对象泄漏/存活太久
- `FGC` 次数频繁（如每分钟几十次）→ **Full GC 频繁，重大事故信号**
- `E` 一直很高 + YGC 频繁 → 新生代偏小，考虑调大 `-Xmn`

### 24.6.3 jmap：堆内存与 dump

```bash
jmap -heap 12345                  # 查看堆配置与实际使用
jmap -histo 12345 | head -30      # 查看对象数量排行（前 30）
# num     #instances         #bytes  class name
#   1:        1200000      48000000  [B
#   2:         800000      32000000  java.util.HashMap$Node

jmap -dump:format=b,file=/data/logs/heap.hprof 12345   # dump 堆快照（配合 MAT 分析）
```

**判读**：对象数量排行里出现大量业务对象（如 `Order`）且总大小异常 → 内存泄漏点。

### 24.6.4 jstack：线程快照（排查死锁/卡死）

```bash
jstack 12345 > thread.log       # 输出线程快照
jstack 12345 | grep -A 10 "BLOCKED"   # 找阻塞线程

# 死锁定位：最后一段会直接提示
# Found one Java-level deadlock:
# "Thread-1": waiting to lock monitor 0x... (object at ..., a DeadLockDemo$LockB)
```

**排查卡死流程**：
1. `jstack` 抓线程快照
2. 找 `WAITING`/`BLOCKED` 状态的线程
3. 看堆栈里锁定了哪个对象、等哪个锁
4. 多个线程互相持有对方的锁 → **死锁**

### 24.6.5 可视化工具

| 工具 | 用途 |
| --- | --- |
| **MAT**（Eclipse Memory Analyzer） | 分析 hprof 堆转储，找泄漏"嫌疑犯" |
| **JVisualVM** | 本地/远程监控 CPU、内存、线程 |
| **Arthas**（阿里开源） | 线上诊断神器：看方法耗时、反编译、热更新 |
| JMC + Flight Recorder | JDK11+ 官方，低开销记录 |

> **Arthas 线上救急**：`java -jar arthas-boot.jar` 附加到进程，`dashboard` 看整体，`thread -n 3` 找最忙线程，`trace 类 方法` 看方法耗时分布 —— 不需要重启应用，生产环境可用（注意权限审计）。

## 24.7 典型问题排查实战

### 24.7.1 场景一：OOM（内存溢出）排查

**现象**：日志出现 `java.lang.OutOfMemoryError: Java heap space`，服务挂掉/重启。

**排查流程**：

```bash
# 1. 前提：启动参数必须配了 HeapDumpOnOutOfMemoryError（否则没现场！）
# 报错时自动生成 /data/logs/heap.hprof

# 2. 用 MAT 打开 heap.hprof，点 "Leak Suspects Report"
#    直接给出嫌疑对象和引用链：
#    "Problem Suspect 1: 80% of memory is held by 1200000 instances of com.demo.Order"

# 3. 分析引用链（GC Root 到对象的路径），找到谁"持有不放"
#    典型泄漏点：
#    - 静态集合只增不减：static Map<Long, Object> cache（不断 put 不清）
#    - 未关闭的连接/流
#    - ThreadLocal 未 remove
#    - 监听器注册后未注销
```

**代码层面的泄漏示例（反面教材）**：

```java
@Component
public class LeakyCache {
    // 静态 Map 只增不减，就是内存泄漏
    public static final Map<String, Object> CACHE = new HashMap<>();

    public void save(String key, Object value) {
        CACHE.put(key, value);   // 永远不清理 → 迟早 OOM
    }
}
```

**修复思路**：改用有容量上限的缓存（Caffeine/Redis，呼应第十二章）或定期清理；静态集合要防呆。

### 24.7.2 场景二：Full GC 频繁

**现象**：`jstat` 看到 FGC 次数飙升，接口响应变慢，CPU 飙升。

**排查流程**：

```bash
# 1. jstat 确认：FGC 很多，YGCT/FGCT 时间很长
jstat -gcutil 12345 1000 10

# 2. 看 GC 日志定位规律：
#    [Full GC (Allocation Failure) ... 123M->100M ...]
#    老年代回收后还是高占用 → 对象一直往老年代跑

# 3. jmap -histo 看是什么对象占着老年代
jmap -histo 12345 | head -20
```

**常见原因与对策**：

| 原因 | 对策 |
| --- | --- |
| 堆太小 | 调大 `-Xmx`，并让 `-Xms` 等于 `-Xmx` |
| 对象生命周期过长 | 排查是否有对象被长期引用，改为短生命周期 |
| 大对象直接进老年代 | 检查是否频繁创建大数组/大 List，`-XX:PretenureSizeThreshold` |
| 新生代太小，对象频繁晋升 | 调大 `-Xmn`，让短命对象死在新生代 |
| `System.gc()` 被误调用 | 全局搜索删除；RMI 等框架会自动触发，加 `-XX:+DisableExplicitGC` |
| 元空间不足触发 FGC | 观察 M 列，调大 `-XX:MaxMetaspaceSize` |

### 24.7.3 场景三：CPU 飙高 / 线程卡死

```bash
# 1. 找到 CPU 最高的 Java 进程
top -p 12345     # 或 Windows: 任务管理器/进程

# 2. 找到进程内 CPU 最高的线程（十进制的线程号）
top -Hp 12345
# 12345 的某个子线程 PID = 25678

# 3. 转成十六进制（jstack 里线程 id 是十六进制）
printf "%x\n" 25678   # 输出 644e

# 4. jstack 抓快照，搜索十六进制线程号
jstack 12345 | grep -A 30 "0x644e"
# 看到对应方法栈 → 定位到具体业务代码
```

**常见结果**：
- 死循环/空转 → 检查 `while(true)`、`for(;;)` 等待条件
- 死锁 → `Found one Java-level deadlock` 提示
- 锁竞争激烈 → 大量线程 `BLOCKED` 等同一把锁，优化锁粒度（呼应第五章）
- GC 线程满负荷 → 本质是内存问题（回到场景二）

## 24.8 调优方法论（先定目标再动手）

### 24.8.1 调优目标（根据业务选）

| 业务类型 | 目标 | 策略 |
| --- | --- | --- |
| 批处理/吞吐优先 | 单位时间处理更多 | 大堆 + Parallel 收集器，容忍长停顿 |
| 在线交易/延迟优先 | 响应快、停顿短 | G1/ZGC + 控制停顿时间 |
| 内存敏感（云上小规格） | 省内存 | 适度堆大小 + 尽早回收 |

### 24.8.2 调优流程（不要瞎调）

```
1. 监控：先收集数据（jstat/GC 日志/压测）——没有数据不调优
2. 定位：是内存问题？GC 问题？还是代码问题（绝大多数是代码问题！）
3. 假设：提出具体假设（如"大对象过多导致老年代涨得快"）
4. 改参：一次只改一个参数，改完压测对比
5. 验证：观察 GC 频率、停顿时间、吞吐是否改善
6. 回滚：没改善就回滚，不要盲目堆参数
```

> **最重要的认知：90% 的"JVM 问题"其实是代码问题**（泄漏、大集合、长事务、低效 SQL 等）。JVM 调参只是最后的武器，先查代码，再调 JVM。

### 24.8.3 企业级 Spring Boot 启动参数模板

```bash
java -Xms2g -Xmx2g \
     -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -Xlog:gc*:file=/data/logs/gc-%t.log:time,uptime,level,tags:filecount=5,filesize=20m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/data/logs/heap-%p.hprof \
     -XX:+ExitOnOutOfMemoryError \
     -Dfile.encoding=UTF-8 \
     -jar app.jar
```

关键点解读：
- `-Xms = -Xmx`：避免启动后堆扩容导致的抖动和 Full GC
- G1 + `MaxGCPauseMillis`：控制停顿（JDK9+ 默认就是 G1，可省略 `UseG1GC`）
- GC 日志滚动 + 加时间戳：便于长期留存分析
- **`ExitOnOutOfMemoryError`**：OOM 后直接退出，交给编排（K8s/Docker）自动重启，避免带病运行（呼应第十九章）

### 24.8.4 Docker/K8s 中的内存限制（重点坑）

```bash
# 错误示范：只配 -Xmx 不配容器限制
docker run -m 1g app-jar   # 容器限制 1G
java -Xmx2g -jar app.jar   # JVM 想用 2G → 被 OOMKilled 杀掉

# 正确做法：让 JVM 感知容器限制（JDK10+ 默认开启）
java -XX:MaxRAMPercentage=75 -jar app.jar
# 或显式指定（不推荐写死，扩容时容易踩内存）
# java -Xmx768m -jar app.jar
```

> `-XX:MaxRAMPercentage=75` 让 JVM 自动按容器可用内存的 75% 设置堆，容器扩缩容时自动适配。**JDK8u191+ 也支持**（需加 `-XX:+UseContainerSupport`）。

## 24.9 小结与练习

**本章重点**：
- 运行时数据区：堆（对象）> 方法区（类信息）> 栈（方法调用）> PC > 本地方法栈
- 堆分区：Eden/S0/S1（新生代）+ 老年代；对象 15 岁晋升
- 双亲委派模型：自下而上委托，保证核心类安全
- GC：可达性分析 + 分代收集；CMS（老年代）→ G1（默认）→ ZGC（超低停顿）
- 参数：`-Xms/-Xmx/-Xmn/-XX:MetaspaceSize/-XX:+UseG1GC/-XX:MaxGCPauseMillis`
- 命令四件套：`jps`（找进程）、`jstat`（看内存 GC）、`jmap`（堆/dump）、`jstack`（线程/死锁）
- OOM 排查：`HeapDumpOnOutOfMemoryError` 留现场 → MAT 分析引用链 → 修代码
- Full GC 频繁：老年代满 / 大对象 / 新生代小 / `System.gc()`
- 容器部署：`MaxRAMPercentage` 适配容器限制，防 OOMKilled

**面试题参考**：
1. JVM 内存分为哪几块？哪些线程共享？哪些私有？
2. 堆内存如何分区？对象从创建到回收的完整过程？
3. 什么是双亲委派？为什么这样设计？怎么打破？
4. 如何判断对象可以回收？（可达性分析 + GC Roots）
5. Minor GC 和 Full GC 有什么区别？什么时候触发？
6. 常见的垃圾收集器有哪些？G1 和 CMS 区别？
7. 内存泄漏和内存溢出的区别？怎么排查 OOM？
8. Full GC 频繁怎么排查？从哪几个方向看？
9. 线上 CPU 飙升你怎么定位？（top → top -Hp → printf → jstack）
10. 你们项目的 JVM 参数是怎么配的？为什么？

**课后练习**：
1. 用 `jps`/`jstat` 监控自己项目的堆使用，观察 Eden/老年代变化曲线。
2. 写一个内存泄漏 Demo（静态 Map 只增不减），跑起来观察内存持续增长，用 `jmap -dump` + MAT 定位泄漏对象。
3. 写两个线程互相锁定的死锁 Demo，用 `jstack` 验证死锁检测输出。
4. 启动 Spring Boot 时加上 GC 日志参数，压测后分析 GC 日志，统计 YGC/FGC 次数。
5. 用 `-XX:+HeapDumpOnOutOfMemoryError` 触发一次 OOM（`-Xmx64m` 跑大对象），解析 dump 文件。

上一章：[23-Zookeeper.md](./23-Zookeeper.md) | 下一章：[25-MySQL调优.md](./25-MySQL调优.md) | 返回目录：[README.md](./README.md)
