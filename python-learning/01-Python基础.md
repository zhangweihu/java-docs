# 第一章 Python 基础与核心语法

> 本章目标：从零掌握 Python——环境搭建、基础语法、四大内置数据结构、函数、面向对象、异常与文件、模块与常用标准库，最后完成一个命令行实战小项目。所有代码均可复制到 IDE 或脚本文件中直接运行。
>
> 前置知识：本系列假设读者已有 Java 基础（与 java-learning 体系对照学习，事半功倍）。文中会穿插 **Python vs Java** 对比，帮助快速迁移。

## 1.1 为什么学 Python（与 Java 的定位差异）

| 维度 | Python | Java |
| --- | --- | --- |
| 类型系统 | **动态类型**（变量无需声明类型） | 静态类型（编译期强类型） |
| 执行方式 | 解释执行（`.py` 直接跑） | 编译为字节码（`.java` → `.class`） |
| 上手成本 | 极低，代码像伪代码 | 中，样板代码多 |
| 运行性能 | 慢（CPython 解释器） | 快（JIT 如 HotSpot） |
| 适用场景 | 数据分析、AI/ML、脚本自动化、Web（FastAPI/Django）、爬虫 | 企业后端、高并发服务、Android |
| 生态关键字 | pip、venv、PyPI | Maven/Gradle、Maven Central |

**一句话选型**：Python 赢在**开发效率**（写脚本、做分析、快速原型），Java 赢在**运行性能与工程规模**（大型后端）。两者不是替代关系，后端工程师常用 Java 写服务、Python 写工具/分析/自动化。

> 本仓库 java-learning 讲 Java 体系，python-learning 讲 Python 体系，建议两套并行学习、互相印证。

## 1.2 环境搭建

### 1.2.1 安装 Python（3.10+ 均可，本文基于 3.11/3.12）

- 官网下载：https://www.python.org/downloads/ （Windows 安装时**勾选 "Add python.exe to PATH"**）
- 验证安装：

```bash
python --version     # Python 3.12.x
pip --version        # 包管理工具
```

> Windows 下若命令是 `py`（Python Launcher），两者等价，本文统一用 `python`。

### 1.2.2 包管理 pip 与虚拟环境 venv（必会）

```bash
# 安装第三方库
pip install requests
pip install -r requirements.txt      # 批量安装

# 导出依赖清单（团队协作必备）
pip freeze > requirements.txt

# 创建/激活虚拟环境（每个项目隔离依赖，避免全局污染）
python -m venv venv
# Windows 激活：
venv\Scripts\activate
# macOS/Linux 激活：
source venv/bin/activate
# 退出
deactivate
```

### 1.2.3 运行方式与 IDE

```bash
python hello.py        # 运行脚本文件
python                 # 进入交互式 REPL（类似 jshell，边敲边出结果）
```

- **IDLE**：安装自带，入门够用；
- **VS Code + Python 插件**：轻量推荐；
- **PyCharm Community**：Python 全家桶，最像 IDEA（Java 开发者零成本上手）。

## 1.3 第一个程序与 REPL

### 1.3.1 Hello World

```python
# hello.py
# 与 Java 不同：没有类、没有 main 方法声明，文件从上到下执行
print("Hello, Python!")

# print 多个值 + 分隔符
print("a", "b", "c", sep="-")        # a-b-c
print("Hello", end="")                # 不换行
print(" World")                       # Hello World
```

运行结果：

```
Hello, Python!
a-b-c
Hello World
```

**要点（与 Java 对比）**：

- 没有 `public static void main`，脚本顶层代码直接执行；
- `print()` 对应 `System.out.println()`，且**默认自动换行**；
- 不用分号结尾（写了也不报错，但**规范不写**）；
- 缩进**代替花括号**表示代码块——这是 Python 最重要的语法特点！

### 1.3.2 注释

```python
# 单行注释：以 # 开头（Python 没有 //）

"""
多行注释/文档字符串：
用三个双引号或三个单引号
"""

def add(a, b):
    """函数的文档字符串（docstring），help(add) 可查看"""
    return a + b
```

### 1.3.3 缩进规则（Python 的灵魂，面试必问）

```python
age = 20
if age >= 18:
    print("成年")          # 缩进 4 个空格 = 属于 if 的代码块
    print("可以考驾照")
else:
    print("未成年")

# ❌ 错误：缩进不一致
# if age >= 18:
#   print("A")
#     print("B")   # IndentationError

print("这段不属于 if")      # 回到顶层缩进，代码块结束
```

> **铁律**：同一个代码块内缩进必须一致（统一 4 个空格，不要混用 Tab 与空格）。`IndentationError` 是新手第一道坎。

### 1.3.4 输入输出

```python
# input() 永远返回字符串！
name = input("请输入姓名：")
age = input("请输入年龄：")
print(f"你好，{name}，明年你 {int(age) + 1} 岁")

# f-string（格式化字符串，3.6+）——最推荐
price = 9.9
print(f"价格：{price:.2f} 元")          # 价格：9.90 元
print(f"百分比：{0.857:.1%}")            # 百分比：85.7%

# 传统方式：format / %
print("价格：{:.2f}".format(price))
print("价格：%.2f" % price)
```

## 1.4 变量与数据类型

### 1.4.1 动态类型：变量不需要声明

```python
x = 10            # int
x = "hello"       # 同一个变量可以重新赋不同类型！(动态类型)
x = 3.14          # float
x = True          # bool
x = None          # 空值（对应 Java 的 null）
```

```python
# 查看类型
print(type(10))          # <class 'int'>
print(type(3.14))        # <class 'float'>
print(type("abc"))       # <class 'str'>
```

| Java | Python | 说明 |
| --- | --- | --- |
| `int` / `long` | `int` | Python int 无上限（自动大数） |
| `double` / `float` | `float` | 都是双精度浮点 |
| `String` | `str` | 字符串不可变 |
| `boolean` | `bool` | `True` / `False`（注意大写！） |
| `null` | `None` | 空值，判断用 `is None` |
| `BigDecimal` | `Decimal`（标准库 decimal） | 金额精度问题两语言都要注意 |
| `List` / `Map` / `Set` | `list` / `dict` / `set` | 见 1.6 |

### 1.4.2 数字与字符串

```python
# 数字
print(7 // 2)        # 3  整除（Java 的 / 在整数间也是整除）
print(7 % 2)         # 1  取余
print(2 ** 10)       # 1024  幂运算（Java 没有 **）
print(0.1 + 0.2)     # 0.30000000000000004（浮点精度问题，同 Java！金额用 Decimal）

# 字符串
s = "hello"
print(s.upper())             # HELLO
print(s[0])                  # h  索引从 0 开始
print(s[-1])                 # o  负索引：从末尾数（Python 特色！）
print(s[1:4])                # ell  切片 [start:end)，end 不含
print(s[::2])                # hlo  步长切片
print("hello" * 3)           # hellohellohello  字符串重复
print(len(s))                # 5  长度（不是 s.length()）

# 字符串拼接与多行
name = "Tom"
print("Hi, " + name)         # 可以 +，但更推荐 f-string
msg = """第一行
第二行
第三行"""                     # 三引号多行字符串
```

### 1.4.3 类型转换

```python
int("42")          # 42
float("3.14")      # 3.14
str(100)           # "100"
bool("")           # False   空值转布尔为假
bool("a")          # True    非空为真
int("abc")         # ❌ ValueError
```

**布尔真值（Python 特色）**：以下值在条件判断中为"假"——`False`、`0`、`0.0`、`""`（空串）、`[]`、`()`、`{}`、`set()`、`None`；其余为"真"。**所以可以直接 `if not s:` 判断空字符串**，非常简洁。

## 1.5 运算符与流程控制

### 1.5.1 运算符

```python
# 比较与逻辑（与 Java 基本一致）
a, b = 10, 20
print(a == b, a != b, a < b)      # False True True
print(a < b and a > 0)            # True  逻辑与（and，不是 &&）
print(a < b or a > 0)             # True  逻辑或（or）
print(not a > b)                  # True  逻辑非（not）

# 成员与身份运算符（Python 特色）
print("ell" in "hello")           # True  成员判断（in）
print(3 in [1, 2, 3])             # True
x = None
print(x is None)                  # True  身份判断（is），判断 None 用 is
print(x == None)                  # True  但规范用 is None
```

### 1.5.2 if / elif / else（没有 switch）

```python
score = 85
if score >= 90:
    grade = "优秀"
elif score >= 60:                  # else if → elif
    grade = "及格"
else:
    grade = "不及格"
print(grade)                       # 及格

# 三元表达式（Java 的 ? :）
age = 20
status = "成年" if age >= 18 else "未成年"
```

### 1.5.3 for 循环（与 Java 差异最大）

```python
# 1. 遍历可迭代对象（不是下标！）
for fruit in ["apple", "banana", "orange"]:
    print(fruit)

# 2. range 生成数字序列
for i in range(5):                 # 0 1 2 3 4
    print(i)
for i in range(1, 10, 2):          # 1 3 5 7 9  (start, stop, step)
    print(i)
for i in range(10, 0, -1):         # 10 9 ... 1（倒序）

# 3. 带索引遍历（enumerate）
for idx, fruit in enumerate(["apple", "banana"]):
    print(idx, fruit)              # 0 apple / 1 banana

# 4. 遍历 dict（见 1.6.3）
```

### 1.5.4 while 与 break / continue

```python
n = 0
while n < 5:
    n += 1                         # Python 没有 n++！
    if n == 3:
        continue                   # 跳过本次
    if n == 5:
        break                      # 退出循环
    print(n)                       # 1 2 4

# while-else（Python 特色：循环没被 break 打断才执行 else）
count = 0
while count < 3:
    count += 1
else:
    print("循环正常结束")           # 正常走完才打印
```

> **注意**：Python 没有 `do-while`、没有 `switch`；`i++` 不存在，自增写 `i += 1`。

## 1.6 四大内置数据结构（重点）

### 1.6.1 list 列表（可变，对应 Java 的 ArrayList）

```python
# 创建
nums = [1, 2, 3]
mixed = [1, "a", 3.14, True]       # 可以混合类型
empty = []

# 增删改
nums.append(4)                     # [1,2,3,4]
nums.insert(0, 0)                  # [0,1,2,3,4]
nums.extend([5, 6])                # [0,1,2,3,4,5,6]
nums.remove(6)                     # 按值删除第一个
nums.pop()                         # 弹出末尾，返回 5
nums.pop(0)                        # 按索引弹出，返回 0
del nums[0]                        # 删除指定位置
nums[0] = 100                      # 修改

# 查询与切片
nums = [1, 2, 3, 4, 5]
print(nums[1:4])                   # [2, 3, 4]  切片（列表独有的高光操作）
print(nums[::-1])                  # [5, 4, 3, 2, 1]  反转
print(3 in nums)                   # True
print(nums.index(3))               # 2
print(len(nums), max(nums), min(nums), sum(nums))

# 排序
nums.sort()                        # 原地升序
nums.sort(reverse=True)            # 降序
sorted(nums)                       # 返回新列表，原列表不变
```

### 1.6.2 tuple 元组（不可变，对应"只读数组"）

```python
t = (1, 2, 3)
# t[0] = 100  ❌ TypeError（不可修改）
a, b, c = t                        # 解包赋值
print(a, b, c)                     # 1 2 3

# 交换两个变量（Python 一行搞定）
x, y = 10, 20
x, y = y, x                        # 解包交换
print(x, y)                        # 20 10

# 函数返回多值（本质是返回元组）
def get_point():
    return (3, 4)                  # 括号可省：return 3, 4

x, y = get_point()
```

### 1.6.3 dict 字典（对应 Java 的 HashMap）

```python
# 创建
user = {"name": "Tom", "age": 25, "city": "Beijing"}
user = dict(name="Tom", age=25)          # 关键字方式

# 增删改查
print(user["name"])                      # Tom  键不存在会 KeyError
print(user.get("email", "无"))           # 无  安全取值，带默认值
user["email"] = "tom@example.com"        # 新增/修改
user.update({"age": 26, "phone": "138"}) # 批量更新
del user["phone"]
user.pop("age", None)                    # 删除并返回（带默认值防 KeyError）

# 遍历（三种）
for key in user:                         # 遍历键
    print(key)
for value in user.values():              # 遍历值
    print(value)
for k, v in user.items():                # 同时遍历键值（最常用）
    print(k, v)

# 其他
print(len(user))
print("name" in user)                    # True  判断键存在（不要用 keys() 再判断）
```

### 1.6.4 set 集合（去重，对应 Java 的 HashSet）

```python
s = {1, 2, 3, 3, 3}                      # {1, 2, 3}  自动去重
s.add(4)
s.remove(2)                              # 不存在会 KeyError；discard 不会
s.discard(99)                            # 安全删除

# 集合运算
a = {1, 2, 3}
b = {2, 3, 4}
print(a & b)                             # {2, 3}  交集
print(a | b)                             # {1,2,3,4}  并集
print(a - b)                             # {1}  差集

# 去重经典用法
nums = [1, 2, 2, 3, 3, 3]
unique = list(set(nums))                 # [1, 2, 3]
```

### 1.6.5 列表推导式（Python 特色，必须掌握）

```python
# 基础：把循环 + append 压缩成一行
squares = [x ** 2 for x in range(5)]            # [0, 1, 4, 9, 16]

# 带条件
even = [x for x in range(10) if x % 2 == 0]     # [0, 2, 4, 6, 8]

# 双层循环
pairs = [(x, y) for x in range(2) for y in range(2)]  # [(0,0),(0,1),(1,0),(1,1)]

# 字典推导式 / 集合推导式
squared = {x: x ** 2 for x in range(3)}         # {0: 0, 1: 1, 2: 4}
big = {x for x in range(10) if x > 5}           # {6, 7, 8, 9}

# 与 Java Stream 对照：Python 一行搞定过滤+映射
nums = [1, 2, 3, 4, 5]
# Java: nums.stream().filter(n -> n % 2 == 0).map(n -> n * 10).toList()
result = [n * 10 for n in nums if n % 2 == 0]   # [20, 40]
```

## 1.7 函数（重点）

### 1.7.1 定义与调用

```python
def greet(name):                   # def 函数名(参数):
    """打招呼"""
    return f"Hello, {name}!"

print(greet("Tom"))                # Hello, Tom!

# 无返回值 → 返回 None（对应 Java 的 void）
def no_return():
    print("没有 return")

print(no_return())                 # None
```

### 1.7.2 参数类型（Python 参数很灵活）

```python
# 1. 默认参数（Java 没有！）
def order(item, quantity=1, urgent=False):
    return f"{item} x {quantity}" + ("（加急）" if urgent else "")

print(order("咖啡"))                     # 咖啡 x 1
print(order("咖啡", 2, True))            # 咖啡 x 2（加急）

# 2. 关键字参数（调用时按名字传，顺序可乱）
print(order(quantity=3, item="奶茶", urgent=True))

# 3. 可变参数 *args：收集多余的位置参数 → 元组
def total(*nums):
    return sum(nums)

print(total(1, 2, 3, 4))                 # 10

# 4. 关键字参数 **kwargs：收集多余的关键字参数 → 字典
def show(**kwargs):
    for k, v in kwargs.items():
        print(f"{k} = {v}")

show(name="Tom", age=25)

# 5. 强制关键字参数 * 后的参数必须用关键字传
def connect(host, port, *, timeout=5):   # timeout 只能关键字传
    pass

# 6. 参数顺序铁律：位置参数 → 默认参数 → *args → **kwargs
```

### 1.7.3 匿名函数 lambda（对应 Java 的 Lambda）

```python
add = lambda x, y: x + y
print(add(3, 5))                   # 8

# 配合 sorted 的 key 参数（最常用场景）
students = [("Tom", 90), ("Amy", 85), ("Bob", 95)]
students.sort(key=lambda s: s[1], reverse=True)   # 按分数降序
print(students)                    # [('Bob', 95), ('Tom', 90), ('Amy', 85)]

# 对应 Java：students.sort(Comparator.comparing(...))
```

### 1.7.4 装饰器（Python 独特机制，面试重点）

装饰器 = **在不改函数代码的前提下给函数"加功能"**（类似 AOP 的思想，Java 用注解 + 代理实现）。

```python
import time

def timer(func):                        # 装饰器：接收函数，返回新函数
    def wrapper(*args, **kwargs):
        start = time.time()
        result = func(*args, **kwargs)
        print(f"{func.__name__} 耗时 {time.time() - start:.4f}s")
        return result
    return wrapper

@timer                                 # 语法糖：slow_work = timer(slow_work)
def slow_work(n):
    total = 0
    for i in range(n):
        total += i
    return total

print(slow_work(1_000_000))            # 自动打印耗时
```

### 1.7.5 生成器 yield（惰性求值，内存友好）

```python
# 普通函数返回列表：一次性全部生成
def squares(n):
    result = []
    for i in range(n):
        result.append(i ** 2)
    return result

# 生成器：每次 yield 一个，边用边生成（处理大数据不占内存）
def squares_gen(n):
    for i in range(n):
        yield i ** 2

for v in squares_gen(5):               # 0 1 4 9 16
    print(v)

# 生成器表达式（惰性版推导式）
gen = (x ** 2 for x in range(5))       # 注意是圆括号
print(sum(gen))                        # 30  求和不产生大列表
```

## 1.8 面向对象（对比 Java 理解更快）

### 1.8.1 类的基础

```python
class Student:
    # 类属性：所有实例共享（对应 Java 的 static 字段）
    school = "第一中学"

    # 构造方法 __init__（对应 Java 构造器，注意不是 __new__）
    def __init__(self, name, age):
        self.name = name               # 实例属性，self 对应 Java 的 this
        self.age = age
        self.__score = 0               # 双下划线前缀 = "私有"（名字改写，约定私有）

    # 实例方法（第一个参数 self）
    def introduce(self):
        return f"我是 {self.name}，{self.age} 岁"

    # 类方法（cls 指类本身，用 @classmethod）
    @classmethod
    def create_from_str(cls, s):
        name, age = s.split(",")
        return cls(name, int(age))

    # 静态方法（与类无关，@staticmethod）
    @staticmethod
    def is_adult(age):
        return age >= 18

# 使用
s1 = Student("Tom", 15)
s2 = Student.create_from_str("Amy,16")         # 类方法创建
print(s1.introduce())                          # 我是 Tom，15 岁
print(Student.school, s1.school)               # 第一中学
print(Student.is_adult(20))                    # True
```

**与 Java 对照**：

| Java | Python |
| --- | --- |
| `public class Student {}` | `class Student:` |
| `private String name;` | `self.name = ...`（约定 `_` 前缀为私有） |
| 构造器 `Student()` | `__init__(self, ...)` |
| `this` | `self`（必须显式写） |
| `static` 方法/字段 | `@staticmethod` / `@classmethod` / 类属性 |
| `new Student(...)` | `Student(...)`（不需要 new） |
| 接口/抽象类 | `abc.ABC` + `@abstractmethod` |

### 1.8.2 继承与多态

```python
class Animal:
    def __init__(self, name):
        self.name = name

    def sound(self):                   # 基类方法
        return "..."

class Dog(Animal):                     # 继承：class Dog(Animal):
    def sound(self):                   # 方法重写（覆盖）
        return "汪汪"

    def fetch(self):
        return f"{self.name} 捡球"

class Cat(Animal):
    def sound(self):
        return "喵喵"

# 多态：同一接口，不同实现
def let_it_speak(animal):
    print(animal.sound())

let_it_speak(Dog("旺财"))              # 汪汪
let_it_speak(Cat("咪咪"))              # 喵喵

# 调用父类方法：super()
class Puppy(Dog):
    def __init__(self, name):
        super().__init__(name)         # super() 对应 Java 的 super
```

### 1.8.3 魔术方法（双下划线方法，Python 特色）

```python
class Point:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    def __str__(self):                          # str(obj) / print → 字符串
        return f"Point({self.x}, {self.y})"

    def __repr__(self):                         # 解释器显示
        return f"Point({self.x}, {self.y})"

    def __eq__(self, other):                    # == 判断
        return isinstance(other, Point) and self.x == other.x and self.y == other.y

    def __lt__(self, other):                    # 排序比较 <
        return (self.x ** 2 + self.y ** 2) < (other.x ** 2 + other.y ** 2)

    def __add__(self, other):                   # 运算符重载：p1 + p2
        return Point(self.x + other.x, self.y + other.y)

    def __len__(self):                          # len(p)
        return 2

p1, p2 = Point(1, 2), Point(3, 4)
print(p1)                          # Point(1, 2)
print(p1 == Point(1, 2))           # True
print(p1 + p2)                     # Point(4, 6)
print(sorted([p2, p1]))            # 按距离排序
```

> 其他常用魔术方法：`__getitem__`（下标访问 `p[i]`）、`__iter__`（可迭代）、`__call__`（对象当函数调）、`__enter__/__exit__`（配合 with，见 1.9）。

### 1.8.4 property：优雅的 getter/setter

```python
class Account:
    def __init__(self, balance=0):
        self._balance = balance          # 内部用 _ 前缀

    @property                            # 读：account.balance
    def balance(self):
        return self._balance

    @balance.setter                      # 写：account.balance = 100（带校验）
    def balance(self, value):
        if value < 0:
            raise ValueError("余额不能为负")
        self._balance = value

    @balance.deleter                     # del account.balance
    def balance(self):
        del self._balance

acc = Account(100)
print(acc.balance)                       # 100（像字段一样访问，其实是方法）
acc.balance = 200                        # 触发 setter 校验
# acc.balance = -1  ❌ ValueError
```

> 对应 Java 的 getter/setter，但用法像直接访问字段，**先写普通属性，需要校验时再升级为 property**，调用方代码不用改。

## 1.9 异常与文件

### 1.9.1 异常处理（try / except / else / finally）

```python
def divide(a, b):
    try:
        result = a / b
    except ZeroDivisionError:
        return "除数不能为 0"
    except (TypeError, ValueError) as e:     # 捕获多个异常
        return f"类型错误：{e}"
    except Exception as e:                   # 兜底（最后才写 Exception）
        print(f"未知异常：{e}")
        raise                                # 不吞异常，重新抛出
    else:
        return result                        # 没有异常才执行
    finally:
        print("无论是否异常都会执行")          # 资源清理

print(divide(10, 2))      # 5.0
print(divide(10, 0))      # 除数不能为 0
```

**与 Java 对照**：

| Java | Python |
| --- | --- |
| `try { } catch (E e) { }` | `try: ... except E as e: ...` |
| `finally` | `finally`（同名） |
| `throw new E()` | `raise E("msg")`（没有 checked/unchecked 之分） |
| `throws` 声明 | 不需要声明 |

```python
# 自定义异常（继承 Exception）
class InsufficientBalanceError(Exception):
    pass

def withdraw(amount, balance):
    if amount > balance:
        raise InsufficientBalanceError(f"余额不足：需要 {amount}，只有 {balance}")
    return balance - amount

# 断言（开发期检查，可用 python -O 关闭）
age = 10
assert age >= 18, "未成年"          # AssertionError: 未成年
```

### 1.9.2 文件读写（with 语句，Python 的 try-with-resources）

```python
# 写文件
with open("note.txt", "w", encoding="utf-8") as f:   # w 覆盖 / a 追加
    f.write("第一行\n")
    f.write("第二行\n")

# 读文件（with 自动关闭，对应 Java 的 try-with-resources）
with open("note.txt", "r", encoding="utf-8") as f:
    content = f.read()               # 一次性读完
    print(content)

with open("note.txt", "r", encoding="utf-8") as f:
    for line in f:                   # 逐行读（大文件推荐，内存友好）
        print(line.strip())          # strip() 去换行

# 追加
with open("note.txt", "a", encoding="utf-8") as f:
    f.write("追加一行\n")
```

> **必会**：文件读写永远用 `with open(...) as f`，**不要**手动 `f.open()` / `f.close()`（异常时可能漏关）。

### 1.9.3 自定义上下文管理器（理解 with 原理）

```python
class Timer:
    def __enter__(self):
        print("开始计时")
        self.start = time.time()
        return self                  # 返回给 as 后面的变量

    def __exit__(self, exc_type, exc_val, exc_tb):
        print(f"耗时 {time.time() - self.start:.2f}s")
        return False                 # False = 不吞异常

with Timer():
    for i in range(1_000_000):
        pass
# 输出：开始计时 / 耗时 x.xx s
```

## 1.10 模块与常用标准库

### 1.10.1 模块与包

```python
# 一个 .py 文件就是一个模块
# 其他文件导入：import 模块名

# utils.py
def add(a, b):
    return a + b

# main.py
import utils
from utils import add                 # 只导入某个函数
from utils import add as a_func       # 起别名
print(utils.add(1, 2))

# 包：含 __init__.py 的目录（3.3+ 可省略，但建议保留做初始化）

# 导入时立刻执行整个模块，所以用这个守卫（Java main 的 Python 版）
if __name__ == "__main__":
    # 只有"直接运行本文件"时才执行，被导入时不执行
    print("这是主程序入口")
```

> **面试常问**：`if __name__ == "__main__":` 的作用——被 import 时不执行、直接运行时执行。是"脚本 + 模块"两用的关键。

### 1.10.2 常用标准库（内置，无需安装）

```python
# os：路径与系统操作
import os
print(os.getcwd())                    # 当前目录
print(os.listdir("."))                # 列出目录
os.makedirs("a/b/c", exist_ok=True)   # 递归建目录

# pathlib：更现代的路径处理（推荐）
from pathlib import Path
p = Path("data/2026/report.txt")
print(p.name)                         # report.txt
print(p.parent)                       # data/2026
print(p.suffix)                       # .txt
Path("out").mkdir(parents=True, exist_ok=True)

# json：序列化（对应 Java 的 Jackson/Gson）
import json
data = {"name": "Tom", "age": 25, "tags": ["a", "b"]}
text = json.dumps(data, ensure_ascii=False, indent=2)   # Python 转 JSON 字符串
print(text)
back = json.loads(text)               # JSON 字符串转 Python 对象
print(back["name"])

# datetime：日期时间
from datetime import datetime
now = datetime.now()
print(now.strftime("%Y-%m-%d %H:%M:%S"))          # 2026-08-19 14:30:00
print(datetime.strptime("2026-08-19", "%Y-%m-%d"))  # 字符串转时间

# random
import random
print(random.randint(1, 100))         # 1~100 随机整数
print(random.choice(["A", "B", "C"])) # 随机取一个
random.shuffle([1, 2, 3])             # 洗牌

# collections：Counter 计数器（超实用）
from collections import Counter, defaultdict
words = ["a", "b", "a", "c", "a", "b"]
print(Counter(words))                 # Counter({'a': 3, 'b': 2, 'c': 1})
d = defaultdict(list)                 # 键不存在自动给默认值（空 list）
d["x"].append(1)                      # 不会 KeyError！

# re：正则
import re
print(re.findall(r"\d+", "订单号 NO-2026-0819"))   # ['2026', '0819']
print(re.search(r"(\d{4})-(\d{2})", "2026-08-19").groups())  # ('2026', '08')

# functools：常用工具
from functools import reduce
print(reduce(lambda x, y: x * y, [1, 2, 3, 4]))    # 24
```

### 1.10.3 三大高频第三方库（pip 安装）

```python
# 1. requests：HTTP 请求（对应 Java 的 HttpClient/OkHttp）
import requests
resp = requests.get("https://httpbin.org/json", timeout=5)
print(resp.status_code)               # 200
data = resp.json()                    # 自动解析 JSON
resp2 = requests.post("https://httpbin.org/post", json={"name": "Tom"})

# 2. python-dotenv：读取 .env 配置（对应 application.yml 的变量化）
from dotenv import load_dotenv
import os
load_dotenv()
db_url = os.getenv("DB_URL", "mysql://localhost")   # 带默认值

# 3. rich / tqdm：美化输出 / 进度条（脚本自动化很香）
# from rich import print; print("[bold red]错误[/bold red]")
```

## 1.11 综合实战：命令行学生成绩管理系统

把本章知识串起来——数据结构 + 函数 + 异常 + 文件 + 标准库：

```python
# student_manager.py
"""命令行学生成绩管理系统（练习：list + dict + 函数 + 文件 + 异常）"""
import json
from pathlib import Path

DATA_FILE = Path("students.json")

def load_students():
    """读取数据文件，不存在则返回空列表"""
    if DATA_FILE.exists():
        return json.loads(DATA_FILE.read_text(encoding="utf-8"))
    return []

def save_students(students):
    """保存到文件（json 序列化）"""
    DATA_FILE.write_text(json.dumps(students, ensure_ascii=False, indent=2), encoding="utf-8")

def add_student(students, name, score):
    """新增学生（校验分数范围 + 重名检查）"""
    if not 0 <= score <= 100:
        raise ValueError("分数必须在 0~100 之间")
    if any(s["name"] == name for s in students):
        raise ValueError(f"学生 {name} 已存在")
    students.append({"name": name, "score": score})
    save_students(students)

def show_rank(students):
    """按成绩降序展示排名"""
    if not students:
        print("暂无数据")
        return
    for i, s in enumerate(sorted(students, key=lambda x: x["score"], reverse=True), start=1):
        print(f"{i}. {s['name']}: {s['score']} 分")

def menu():
    """主菜单循环（while + input + try/except）"""
    students = load_students()
    while True:
        print("\n===== 学生成绩管理 =====")
        print("1. 添加学生  2. 查看排名  3. 退出")
        choice = input("请选择：")
        try:
            if choice == "1":
                name = input("姓名：").strip()
                score = int(input("分数："))
                add_student(students, name, score)
                print(f"已添加 {name}")
            elif choice == "2":
                show_rank(students)
            elif choice == "3":
                print("再见！")
                break
            else:
                print("无效选择")
        except (ValueError, KeyError) as e:
            print(f"输入有误：{e}")

if __name__ == "__main__":
    menu()
```

运行效果：

```
===== 学生成绩管理 =====
1. 添加学生  2. 查看排名  3. 退出
请选择：1
姓名：Tom
分数：92
已添加 Tom
...
1. Amy: 95 分
2. Tom: 92 分
```

**本项目用到的知识**：dict/list 存储、推导式、lambda 排序、异常校验、with/Path 读写、json 序列化、`__main__` 守卫。

## 1.12 练习与总结

### 练习题

```python
# 1. 用列表推导式找出 1~100 中既能被 3 整除又能被 5 整除的数
# 2. 写函数 word_count(text) 用 Counter 统计词频，返回出现最多的前 3 个词
# 3. 定义一个 BankAccount 类：属性 balance、方法 deposit/withdraw（withdraw 余额不足抛异常）、
#    __str__ 显示余额
# 4. 写一个装饰器 log_call：打印"调用 函数名，参数 xxx"
# 5. 读取 1.9.2 生成的 note.txt，统计每行长度并输出最长一行
# 6. 给学生成绩系统加"修改分数"和"删除学生"功能，并处理文件不存在的情况
# 7. 用 random 实现"猜数字"小游戏：1~100 随机数，输入提示大了/小了，直到猜中并统计次数
```

### 面试高频问题清单

| 问题 | 答案要点 |
| --- | --- |
| Python 为什么快开发慢运行？ | 动态类型 + 解释执行（CPython）；优化用 PyPy/Cython/异步/并发 |
| 可变与不可变类型？ | 可变：list/dict/set；不可变：int/str/tuple/frozenset（哈希前提） |
| `==` 与 `is` 区别？ | `==` 比"值"，`is` 比"身份（内存地址）"；判断 None 用 `is None` |
| 深拷贝与浅拷贝？ | `copy.copy` 浅拷贝（嵌套引用共享）、`copy.deepcopy` 深拷贝 |
| 装饰器原理？ | 函数是对象，装饰器接收函数返回包装函数，`@` 是语法糖 |
| 生成器与普通函数的区别？ | `yield` 惰性求值，保存状态，内存友好 |
| `__init__` 与 `__new__`？ | `__new__` 创建实例（先）、`__init__` 初始化实例（后） |
| GIL 是什么？ | 全局解释器锁，同一时刻仅一个线程执行字节码；IO 密集用多线程，CPU 密集用多进程 |
| `with` 语句原理？ | `__enter__` / `__exit__` 上下文管理器，自动资源释放 |
| `if __name__ == "__main__"`？ | 直接运行时执行，被 import 时不执行 |
| Python 如何做继承/多态？ | `class A(B)` 继承、方法重写、`super()`；鸭子类型动态多态 |
| 列表去重的几种方式？ | `set()`（无序）、`dict.fromkeys`（保序）、推导式 + `seen` 集合 |
| 什么是列表推导式？ | `[expr for x in iterable if cond]`，比 for+append 更简洁高效 |
| 异常处理如何避免吞异常？ | 精准捕获、`raise` 重新抛出、finally 清资源、别裸 `except:` |

### 本章小结

- **定位**（1.1）：Python = 开发效率优先；与 Java 互补而非替代；
- **环境**（1.2）：python + pip + venv 三件套，虚拟环境必开；
- **语法基础**（1.3~1.5）：缩进即代码块、动态类型、f-string、`for x in` 遍历、无 `i++`/`switch`；
- **四大结构**（1.6）：list（切片）、tuple（解包）、dict（`get`/`items`）、set（去重）+ 推导式；
- **函数**（1.7）：默认/关键字参数、`*args/**kwargs`、lambda、装饰器、生成器；
- **面向对象**（1.8）：`__init__`/self/继承/魔术方法/property；
- **异常与文件**（1.9）：try/except/else/finally、`with open`、上下文管理器；
- **模块与标准库**（1.10）：`__main__` 守卫、os/pathlib/json/datetime/Counter/re；
- **实战**（1.11）：学生成绩管理系统串联全部核心知识。

下一章：[02-进阶语法.md](./02-进阶语法.md)（迭代器/生成器深挖、上下文管理器、函数式编程、元类与类型注解）
