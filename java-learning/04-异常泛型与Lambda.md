# 第四章 异常处理、泛型、Lambda 与 Stream

> 本章掌握：异常体系与处理方式、泛型的用法、Lambda 表达式、Stream 流式编程（JDK8 核心特性）。

## 4.1 异常体系

```
Throwable（所有错误/异常的父类）
├── Error（严重错误，程序无法处理，如 OutOfMemoryError）
└── Exception（程序可以处理的异常）
    ├── RuntimeException（运行时异常，编译不强制处理）
    │   ├── NullPointerException      空指针
    │   ├── ArrayIndexOutOfBoundsException  数组越界
    │   ├── ArithmeticException       算术异常（除零）
    │   ├── ClassCastException        类型转换异常
    │   └── NumberFormatException     数字格式异常
    └── 受检异常（Checked Exception，编译时必须处理）
        ├── IOException               IO 异常
        ├── SQLException              数据库异常
        └── InterruptedException      线程中断异常
```

## 4.2 try-catch-finally

```java
import java.util.Scanner;

public class ExceptionDemo {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("请输入一个数字：");

        try {
            // 可能发生异常的代码
            int num = Integer.parseInt(sc.nextLine());
            System.out.println("你输入的是：" + num);
            System.out.println("10 / 你输入的数 = " + (10 / num));
        } catch (NumberFormatException e) {
            // 捕获特定异常
            System.out.println("输入的不是数字：" + e.getMessage());
        } catch (ArithmeticException e) {
            System.out.println("除数不能为 0！");
        } catch (Exception e) {
            // 兜底：捕获所有其他异常（必须放在最后）
            System.out.println("发生未知异常：" + e);
        } finally {
            // 无论是否发生异常都会执行（常用于释放资源）
            System.out.println("finally 块执行：资源清理");
            sc.close();
        }
        System.out.println("程序继续执行...");
    }
}
```

## 4.3 throws 与 throw

```java
public class ThrowsDemo {

    // throws：声明方法可能抛出的异常，交给调用者处理
    public static int divide(int a, int b) throws ArithmeticException {
        if (b == 0) {
            // throw：主动抛出异常对象
            throw new ArithmeticException("除数不能为 0");
        }
        return a / b;
    }

    // 受检异常必须声明 throws
    public static void readFile(String path) throws java.io.IOException {
        if (path == null || path.isEmpty()) {
            throw new java.io.IOException("文件路径不能为空");
        }
        System.out.println("开始读取文件：" + path);
    }

    public static void main(String[] args) {
        try {
            System.out.println(divide(10, 2));
            // System.out.println(divide(10, 0));   // 会抛异常
        } catch (ArithmeticException e) {
            System.out.println("捕获到：" + e.getMessage());
        }

        try {
            readFile("");
        } catch (java.io.IOException e) {
            System.out.println("文件异常：" + e.getMessage());
        }
    }
}
```

## 4.4 自定义异常

```java
// 自定义异常：继承 Exception（受检）或 RuntimeException（非受检）
class AgeOutOfRangeException extends RuntimeException {
    public AgeOutOfRangeException(String message) {
        super(message);
    }
}

public class CustomExceptionDemo {
    // 校验年龄
    public static void register(int age) {
        if (age < 0 || age > 150) {
            throw new AgeOutOfRangeException("年龄不合法：" + age);
        }
        System.out.println("注册成功，年龄：" + age);
    }

    public static void main(String[] args) {
        try {
            register(25);
            register(200);   // 触发自定义异常
        } catch (AgeOutOfRangeException e) {
            System.out.println("注册失败：" + e.getMessage());
        }
    }
}
```

## 4.5 泛型（Generic）

泛型：**参数化类型**，让代码可以处理多种类型且保证类型安全。集合框架广泛使用泛型。

```java
import java.util.ArrayList;
import java.util.List;

// 泛型类：<T> 是类型参数（Type），可以是任意标识符
class Box<T> {
    private T content;

    public void put(T content) {
        this.content = content;
    }

    public T get() {
        return content;
    }
}

// 泛型方法：在方法返回值前声明 <T>
class GenericUtil {
    public static <T> T lastElement(List<T> list) {
        return list.get(list.size() - 1);
    }
}

public class GenericDemo {
    public static void main(String[] args) {
        // 使用泛型类：指定类型参数为 String
        Box<String> strBox = new Box<>();
        strBox.put("Hello 泛型");
        String s = strBox.get();          // 无需强转
        System.out.println(s);

        // 指定为 Integer
        Box<Integer> intBox = new Box<>();
        intBox.put(100);
        Integer num = intBox.get();
        System.out.println(num);

        // 泛型方法使用
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        String last = GenericUtil.lastElement(list);
        System.out.println("最后一个元素：" + last);

        // 类型通配符 ?
        printList(list);
        List<Integer> nums = new ArrayList<>();
        nums.add(1);
        printList(nums);
    }

    // ? 通配符：可以接收任何类型的 List（只读场景）
    public static void printList(List<?> list) {
        for (Object obj : list) {
            System.out.print(obj + " ");
        }
        System.out.println();
    }
}
```

> 注意：泛型**不能使用基本类型**（要用包装类）；泛型在运行时会**类型擦除**（JVM 中都是 Object）。

## 4.6 Lambda 表达式（JDK8）

Lambda 是**匿名函数**的简写，配合函数式接口使用（只有一个抽象方法的接口）。

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class LambdaDemo {
    public static void main(String[] args) {
        // ========== 函数式接口 Runnable ==========
        // 传统写法：匿名内部类
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("传统写法线程");
            }
        });
        t1.start();

        // Lambda 写法
        Thread t2 = new Thread(() -> System.out.println("Lambda 线程"));
        t2.start();

        // ========== Comparator 排序 ==========
        List<String> names = new ArrayList<>(Arrays.asList("banana", "apple", "cherry", "date"));

        // 传统写法
        names.sort(new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.length() - b.length();
            }
        });
        System.out.println("按长度排序：" + names);

        // Lambda 简写（按字母逆序）
        names.sort((a, b) -> b.compareTo(a));
        System.out.println("字母逆序：" + names);

        // 方法引用：类名::方法名
        names.sort(String::compareTo);
        System.out.println("字母升序：" + names);

        // ========== forEach ==========
        List<Integer> nums = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        nums.forEach(n -> System.out.print(n * 2 + " "));
        System.out.println();
    }
}
```

**Lambda 语法**：

```
(参数列表) -> { 方法体 }
```

- 参数类型可省略：`(a, b) -> ...`
- 单参数可省略括号：`n -> ...`
- 单条语句可省略大括号和 return：`n -> n * 2`

## 4.7 Stream 流式编程（JDK8）

Stream 对集合进行**声明式**的链式操作：过滤、映射、排序、聚合。

```java
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamDemo {
    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>(Arrays.asList(3, 8, 1, 9, 5, 8, 2));

        // ========== filter 过滤（保留偶数）==========
        List<Integer> evens = numbers.stream()
                .filter(n -> n % 2 == 0)
                .collect(Collectors.toList());
        System.out.println("偶数：" + evens);   // [8, 8, 2]

        // ========== map 映射（每个数乘以 2）==========
        List<Integer> doubled = numbers.stream()
                .map(n -> n * 2)
                .collect(Collectors.toList());
        System.out.println("翻倍：" + doubled);

        // ========== distinct 去重 + sorted 排序 ==========
        List<Integer> unique = numbers.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        System.out.println("去重排序：" + unique);   // [1, 2, 3, 5, 8, 9]

        // ========== limit 限制条数 ==========
        List<Integer> top3 = numbers.stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .collect(Collectors.toList());
        System.out.println("最大的3个：" + top3);

        // ========== reduce 聚合（求和）==========
        int sum = numbers.stream().reduce(0, Integer::sum);
        System.out.println("总和：" + sum);

        // ========== 统计 ==========
        long count = numbers.stream().filter(n -> n > 5).count();
        System.out.println("大于5的个数：" + count);

        int max = numbers.stream().max(Integer::compareTo).orElse(-1);
        System.out.println("最大值：" + max);

        // ========== 对象操作 ==========
        List<Student> students = new ArrayList<>(Arrays.asList(
                new Student("张三", 22, 85),
                new Student("李四", 20, 92),
                new Student("王五", 21, 68),
                new Student("赵六", 23, 92)
        ));

        // 过滤成绩 >= 85 的学生，按成绩降序排列，只取名字
        List<String> topStudents = students.stream()
                .filter(s -> s.score >= 85)
                .sorted((a, b) -> b.score - a.score)
                .map(s -> s.name)
                .collect(Collectors.toList());
        System.out.println("优秀学生：" + topStudents);

        // 分组统计：按分数段分组（Collectors.groupingBy）
        Map<String, List<Student>> group = students.stream()
                .collect(Collectors.groupingBy(s -> s.score >= 90 ? "优秀" : "良好"));
        System.out.println("分组：" + group.keySet());

        // 求平均分
        double avg = students.stream().mapToInt(s -> s.score).average().orElse(0);
        System.out.println("平均分：" + avg);

        // ========== 并行流 parallelStream（多线程处理）==========
        long start = System.currentTimeMillis();
        long total = Stream.iterate(1L, i -> i + 1)
                .limit(10_000_000)
                .parallel()
                .count();
        long cost = System.currentTimeMillis() - start;
        System.out.println("并行处理 1000 万个数：" + total + " 个，耗时 " + cost + "ms");
    }
}

class Student {
    String name;
    int age;
    int score;

    public Student(String name, int age, int score) {
        this.name = name;
        this.age = age;
        this.score = score;
    }
}
```

## 4.8 Optional 空值处理（JDK8）

Optional 用于**优雅地避免空指针异常**。

```java
import java.util.Optional;

public class OptionalDemo {
    public static void main(String[] args) {
        // 传统写法可能空指针
        String name = null;
        // System.out.println(name.toUpperCase());   // NullPointerException

        // Optional 写法
        Optional<String> opt = Optional.ofNullable(name);

        // orElse：为空则返回默认值
        String result = opt.orElse("默认名字");
        System.out.println("result = " + result);

        // isPresent 判断 / ifPresent 消费
        opt.ifPresent(v -> System.out.println("值存在：" + v));

        // 链式调用
        Optional<String> nonNull = Optional.ofNullable("hello");
        String upper = nonNull.map(String::toUpperCase).orElse("空");
        System.out.println("upper = " + upper);

        // 业务示例：根据 ID 找学生（可能不存在）
        Student s = findById(1);
        // 直接使用 s.name 可能空指针，用 Optional 包装更安全
        String display = Optional.ofNullable(s).map(st -> st.name).orElse("查无此人");
        System.out.println("查询结果：" + display);
    }

    static Student findById(int id) {
        // 模拟：id=1 不存在
        if (id == 1) {
            return null;
        }
        return new Student("张三", 22, 85);
    }
}
```

## 4.9 小结与练习

**本章重点**：
- 异常分类（Error / 受检异常 / 运行时异常）
- try-catch-finally、throws、throw、自定义异常
- 泛型类、泛型方法、通配符
- Lambda 表达式与函数式接口
- Stream 的 filter/map/sorted/distinct/collect/groupingBy
- Optional 空值处理

**课后练习**：
1. 编写程序读取用户输入的数字并相除，处理各种异常。
2. 用 Stream 对一个字符串列表：过滤长度大于 3 的、转大写、按长度排序。
3. 用 Stream 统计一个班级中每个分数段的人数。
4. 自定义一个 `ScoreInvalidException`，成绩不在 0~100 时抛出。

上一章：[03-常用API与集合框架.md](./03-常用API与集合框架.md) | 下一章：[05-多线程与并发.md](./05-多线程与并发.md)
