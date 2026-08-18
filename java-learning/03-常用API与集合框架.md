# 第三章 常用 API 与集合框架

> 本章掌握：String 字符串、包装类、集合框架（List/Set/Map）、日期时间 API。
> 集合框架是 Java 面试与开发的高频考点。

## 3.1 String 字符串

String 是**不可变**的（内容一旦创建不可修改），每次拼接都会创建新对象。

### 3.1.1 创建与常用方法

```java
public class StringDemo {
    public static void main(String[] args) {
        // 创建字符串
        String s1 = "hello";                 // 字面量方式（字符串常量池）
        String s2 = new String("hello");     // new 方式（堆内存新对象）

        // 常用方法
        String str = "  Hello Java World  ";
        System.out.println("长度：" + str.length());              // 19
        System.out.println("去空格：" + str.trim());              // Hello Java World
        System.out.println("转大写：" + str.toUpperCase());       // HELLO JAVA WORLD
        System.out.println("转小写：" + str.toLowerCase());
        System.out.println("是否以He开头：" + str.trim().startsWith("He"));   // true
        System.out.println("是否以d结尾：" + str.trim().endsWith("d"));       // true
        System.out.println("包含Java：" + str.contains("Java"));  // true

        // 截取与查找
        System.out.println("第6个字符：" + str.charAt(6));        // J
        System.out.println("Java的索引：" + str.indexOf("Java")); // 9
        System.out.println("截取0-5：" + str.substring(0, 5));    // "  Hel"
        System.out.println("替换：" + str.replace("Java", "Python"));

        // 分割
        String csv = "张三,20,男";
        String[] parts = csv.split(",");
        for (String p : parts) {
            System.out.println("分割结果：" + p);
        }
    }
}
```

### 3.1.2 == 与 equals 的区别（高频面试题）

```java
public class StringEqualsDemo {
    public static void main(String[] args) {
        String a = "hello";
        String b = "hello";
        String c = new String("hello");

        System.out.println(a == b);      // true：都在常量池，同一对象
        System.out.println(a == c);      // false：c 是堆中的新对象
        System.out.println(a.equals(c)); // true：equals 比较内容

        // 结论：比较字符串内容必须用 equals()，不要用 ==
        String d = "hell" + "o";         // 编译期常量折叠，== 也是 true
    }
}
```

### 3.1.3 StringBuilder（可变字符串，频繁拼接用）

```java
public class StringBuilderDemo {
    public static void main(String[] args) {
        // 频繁拼接字符串时，用 StringBuilder 性能远高于 +
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 1000; i++) {
            sb.append(i).append(" ");
        }
        System.out.println("长度：" + sb.length());

        // 其他常用操作
        StringBuilder sb2 = new StringBuilder("Hello");
        sb2.append(" World");            // 追加
        sb2.insert(5, ",");              // 插入
        sb2.replace(6, 11, "Java");      // 替换
        sb2.delete(5, 6);                // 删除
        sb2.reverse();                   // 反转

        // StringBuffer 与 StringBuilder 用法一致，但线程安全（性能稍低）
        StringBuffer buf = new StringBuffer("并发安全");
    }
}
```

## 3.2 包装类（Wrapper Class）

基本类型不能用在泛型/集合中，需要对应的包装类：

| 基本类型 | 包装类 | 基本类型 | 包装类 |
| --- | --- | --- | --- |
| int | Integer | float | Float |
| long | Long | double | Double |
| short | Short | boolean | Boolean |
| byte | Byte | char | Character |

```java
public class WrapperDemo {
    public static void main(String[] args) {
        // 装箱：基本类型 → 包装类
        Integer i1 = Integer.valueOf(100);
        Integer i2 = 100;                    // 自动装箱

        // 拆箱：包装类 → 基本类型
        int n = i1.intValue();
        int m = i2;                          // 自动拆箱

        // 字符串与数字互转
        int num = Integer.parseInt("123");        // 字符串 → int
        double d = Double.parseDouble("3.14");
        String s = String.valueOf(num);           // 数字 → 字符串

        // 注意：null 自动拆箱会抛 NullPointerException
        Integer x = null;
        // int y = x;   // 错误！空指针异常

        // Integer 缓存：-128 ~ 127 之间的值使用缓存
        Integer a = 100, b = 100;
        Integer c = 200, d2 = 200;
        System.out.println(a == b);      // true（缓存范围内）
        System.out.println(c == d2);     // false（超出缓存，两个对象）
    }
}
```

## 3.3 集合框架总览

```
Collection（单列集合）
├── List（有序、可重复）
│   ├── ArrayList   数组实现，查询快、增删慢
│   └── LinkedList  链表实现，增删快、查询慢
├── Set（无序、不可重复）
│   ├── HashSet         哈希实现，无序
│   ├── LinkedHashSet   哈希+链表，按插入顺序
│   └── TreeSet         红黑树，自动排序
└── Queue / Deque（队列）

Map（键值对）
├── HashMap          无序，基于哈希表
├── LinkedHashMap    按插入顺序
└── TreeMap          按键排序
```

## 3.4 List 集合

```java
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ListDemo {
    public static void main(String[] args) {
        // 创建 List（泛型：<元素类型>）
        List<String> list = new ArrayList<>();

        // 增
        list.add("苹果");
        list.add("香蕉");
        list.add("橙子");
        list.add(1, "葡萄");        // 指定下标插入

        // 删
        // list.remove(0);          // 按下标删除
        // list.remove("香蕉");     // 按元素删除

        // 改
        list.set(0, "西瓜");

        // 查
        System.out.println("集合大小：" + list.size());      // 4
        System.out.println("下标1的元素：" + list.get(1));   // 葡萄
        System.out.println("是否包含苹果：" + list.contains("苹果"));
        System.out.println("是否为空：" + list.isEmpty());

        // 遍历方式一：普通 for
        for (int i = 0; i < list.size(); i++) {
            System.out.print(list.get(i) + " ");
        }
        System.out.println();

        // 遍历方式二：增强 for
        for (String item : list) {
            System.out.print(item + " ");
        }
        System.out.println();

        // 遍历方式三：forEach + Lambda（JDK8+）
        list.forEach(item -> System.out.print(item + " "));
        System.out.println();

        // 泛型说明：List 中只能放对象，基本类型要用包装类
        List<Integer> nums = new ArrayList<>();
        nums.add(10);
        nums.add(20);
        int total = 0;
        for (Integer num : nums) {
            total += num;   // 自动拆箱
        }
        System.out.println("总和：" + total);
    }
}
```

### ArrayList 与 LinkedList 对比

```java
import java.util.ArrayList;
import java.util.LinkedList;

public class ListCompareDemo {
    public static void main(String[] args) {
        // ArrayList：底层是数组，按下标访问 O(1)，中间插入删除 O(n)
        // LinkedList：底层是双向链表，头尾插入删除 O(1)，按下标访问 O(n)

        long start, end;

        // 测试尾部追加 10 万次
        start = System.currentTimeMillis();
        ArrayList<Integer> arr = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) arr.add(i);
        end = System.currentTimeMillis();
        System.out.println("ArrayList 尾部追加：" + (end - start) + "ms");

        start = System.currentTimeMillis();
        LinkedList<Integer> link = new LinkedList<>();
        for (int i = 0; i < 100_000; i++) link.add(i);
        end = System.currentTimeMillis();
        System.out.println("LinkedList 尾部追加：" + (end - start) + "ms");
    }
}
```

## 3.5 Set 集合（去重）

```java
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class SetDemo {
    public static void main(String[] args) {
        // HashSet：无序、不重复（去重常用）
        Set<String> set = new HashSet<>();
        set.add("a");
        set.add("b");
        set.add("a");       // 重复元素会被忽略
        set.add("c");
        System.out.println("HashSet：" + set + "，大小=" + set.size());  // [a, b, c]

        // LinkedHashSet：按插入顺序
        Set<String> linked = new LinkedHashSet<>();
        linked.add("c");
        linked.add("a");
        linked.add("b");
        System.out.println("LinkedHashSet：" + linked);   // [c, a, b]

        // TreeSet：自动排序（升序）
        Set<Integer> tree = new TreeSet<>();
        tree.add(5);
        tree.add(1);
        tree.add(3);
        tree.add(2);
        System.out.println("TreeSet：" + tree);   // [1, 2, 3, 5]

        // 遍历
        for (String s : set) {
            System.out.print(s + " ");
        }
        System.out.println();

        // 应用：对 List 去重
        java.util.List<String> list = java.util.Arrays.asList("a", "b", "a", "c", "b");
        Set<String> unique = new HashSet<>(list);
        System.out.println("去重后：" + unique);
    }
}
```

## 3.6 Map 集合（键值对）

```java
import java.util.HashMap;
import java.util.Map;

public class MapDemo {
    public static void main(String[] args) {
        // HashMap：键不能重复，值可以重复
        Map<String, Integer> scores = new HashMap<>();
        scores.put("张三", 85);
        scores.put("李四", 92);
        scores.put("王五", 78);
        scores.put("张三", 95);    // 键重复则覆盖旧值

        // 查询
        System.out.println("张三的成绩：" + scores.get("张三"));   // 95
        System.out.println("键值对数量：" + scores.size());        // 3
        System.out.println("是否包含键王五：" + scores.containsKey("王五"));
        System.out.println("是否包含值78：" + scores.containsValue(78));

        // 删除
        // scores.remove("王五");

        // 遍历方式一：遍历键值对 entrySet（推荐）
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }

        // 遍历方式二：遍历键 keySet
        for (String key : scores.keySet()) {
            System.out.println("key=" + key + ", value=" + scores.get(key));
        }

        // 遍历方式三：forEach + Lambda
        scores.forEach((key, value) -> System.out.println(key + "=" + value));

        // 统计字符出现次数（Map 经典应用）
        String text = "hello world hello java";
        Map<Character, Integer> countMap = new HashMap<>();
        for (char c : text.toCharArray()) {
            countMap.put(c, countMap.getOrDefault(c, 0) + 1);
        }
        System.out.println("字符统计：" + countMap);
    }
}
```

### HashMap 与 Hashtable 区别（面试题）

- `HashMap`：非线程安全（性能高）、允许 null 键/值、JDK8 数组+链表+红黑树。
- `Hashtable`：线程安全（方法加 synchronized）、不允许 null、已过时。
- 并发场景用 `ConcurrentHashMap`。

## 3.7 Collections 工具类

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectionsDemo {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        list.add(3);
        list.add(1);
        list.add(4);
        list.add(1);
        list.add(5);

        Collections.sort(list);                  // 升序排序
        System.out.println("排序：" + list);      // [1, 1, 3, 4, 5]

        Collections.reverse(list);               // 反转
        System.out.println("反转：" + list);

        Collections.shuffle(list);               // 随机打乱
        System.out.println("打乱：" + list);

        System.out.println("最大值：" + Collections.max(list));
        System.out.println("最小值：" + Collections.min(list));

        // 将非线程安全集合包装为线程安全集合
        List<Integer> safe = Collections.synchronizedList(new ArrayList<>());

        // 对象排序：自定义类实现 Comparable 或传入 Comparator
        List<Person> people = new ArrayList<>();
        people.add(new Person("张三", 25));
        people.add(new Person("李四", 20));
        people.add(new Person("王五", 30));
        Collections.sort(people);                // 按 Comparable 定义的规则（年龄）排序
        System.out.println("按年龄排序：" + people);
    }
}

// 实现 Comparable 接口，定义排序规则
class Person implements Comparable<Person> {
    String name;
    int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public int compareTo(Person other) {
        return this.age - other.age;   // 升序
    }

    @Override
    public String toString() {
        return name + "(" + age + ")";
    }
}
```

## 3.8 日期时间 API

### 3.8.1 旧 API（Date / Calendar，了解即可）

```java
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class OldDateDemo {
    public static void main(String[] args) throws Exception {
        Date now = new Date();
        System.out.println("当前时间：" + now);

        // 格式化
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("格式化：" + sdf.format(now));

        // 解析字符串为日期
        Date parsed = sdf.parse("2024-01-01 10:30:00");
        System.out.println("解析结果：" + parsed);

        // Calendar 操作
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.JANUARY, 1);
        cal.add(Calendar.DAY_OF_MONTH, 10);   // 加 10 天
        System.out.println("计算后：" + sdf.format(cal.getTime()));
    }
}
```

### 3.8.2 新 API（java.time，JDK8+ 推荐）

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class NewDateDemo {
    public static void main(String[] args) {
        // 获取当前日期时间
        LocalDate today = LocalDate.now();
        LocalTime time = LocalTime.now();
        LocalDateTime now = LocalDateTime.now();
        System.out.println("日期：" + today);
        System.out.println("时间：" + time);
        System.out.println("日期时间：" + now);

        // 手动指定
        LocalDate birth = LocalDate.of(2000, 5, 20);
        System.out.println("生日：" + birth);

        // 日期运算
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextMonth = today.plusMonths(1);
        LocalDate lastYear = today.minusYears(1);
        System.out.println("明天：" + tomorrow);

        // 比较
        System.out.println("birth 是否在 today 之前：" + birth.isBefore(today));

        // 格式化与解析（线程安全，区别于 SimpleDateFormat）
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
        System.out.println("格式化：" + now.format(fmt));

        // 获取年月日等字段
        System.out.println("年：" + today.getYear() + "，月：" + today.getMonthValue() + "，日：" + today.getDayOfMonth());
    }
}
```

## 3.9 小结与练习

**本章重点**：
- String 的不可变性、`equals()` vs `==`
- StringBuilder 拼接性能
- 集合框架体系与 List/Set/Map 的增删改查
- HashMap 底层原理（数组+链表+红黑树）、HashSet 去重原理
- Comparable / Comparator 排序
- 新日期时间 API

**课后练习**：
1. 统计一段英文文章中每个单词出现的次数（用 Map）。
2. 对一组学生对象按成绩从高到低排序（用 Comparator）。
3. 用 Set 对随机生成的 100 个数去重。
4. 判断一个字符串是否是回文（用 StringBuilder.reverse）。

上一章：[02-面向对象编程.md](./02-面向对象编程.md) | 下一章：[04-异常泛型与Lambda.md](./04-异常泛型与Lambda.md)
