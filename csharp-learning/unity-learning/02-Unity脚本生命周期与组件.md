# 第二章 C# 脚本与生命周期

> 前置：第一章 + C# 指南前几章。本章是你从“会写 C#”到“会用 C# 写游戏逻辑”的转折点。配套可运行工程：`demo-project/Assets/Scripts/AutoBoot.cs`、`DemoManager.cs`、`Demos/LifecycleDemo.cs`。

## 2.1 脚本 = 挂在对象上的一个组件

在 Unity 里，**脚本是给 GameObject 挂的一种组件**。你新建一个 `HelloWorld.cs` 并继承 `MonoBehaviour`，拖到某个对象上，引擎就会：
- 在特定时机自动调用它约定的方法（`Start`、`Update`…）；
- 在 Inspector 里展示它的 `public` 字段，方便你调参。

**核心转变（请反复读）**：你不再控制程序的“启动入口”，而是**向引擎注册回调**。引擎每帧问所有活着的脚本：“你要不要动一下？”你只需要实现 `Update()`。

```csharp
using UnityEngine;

public class HelloWorld : MonoBehaviour
{
    void Start()
    {
        Debug.Log("第一次进入 Play 前调用一次");
    }

    void Update()
    {
        Debug.Log("每一帧都调用一次"); // 别每帧刷日志！仅示意
    }
}
```

在 Unity 中的操作：Project 面板右键 → Create → C# Script，命名 `HelloWorld`，双击用 VS 打开，粘贴上面代码，保存后拖到 Hierarchy 的 Cube 上，点 Play——Console 里会不断打印。**脚本文件名必须与类名一致**，否则无法挂载。

> 配套工程里为了省去“手动挂脚本”的操作，`AutoBoot.cs` 用了 `[RuntimeInitializeOnLoadMethod]` 特性让 DemoManager 在 Play 后自动创建——这是“代码驱动场景”的小技巧，第三章会解释，真实项目直接在编辑器里挂脚本即可。

## 2.2 MonoBehaviour 生命周期：引擎何时调用谁

Unity 的脚本有严格的生命周期顺序。最常用的事件函数如下（按调用先后）：

| 事件函数 | 调用时机 | 典型用途 |
| --- | --- | --- |
| `Awake()` | 对象**被实例化/场景加载**时，即使脚本未启用也会调用 | 初始化引用、缓存 `GetComponent` |
| `OnEnable()` | 脚本/对象每次**变为启用**时 | 订阅事件、开启协程 |
| `Start()` | 启用后、**第一次 Update 前** | 初始化只做一次的逻辑 |
| `FixedUpdate()` | 固定频率（默认每秒 50 次，与帧率无关） | **物理相关**移动 |
| `Update()` | **每一帧**一次（帧率越高越快） | 一般游戏逻辑、输入 |
| `LateUpdate()` | 所有 Update 之后，一帧一次 | 相机跟随等“最后处理” |
| `OnDisable()` | 脚本/对象每次被禁用 | 反订阅、清理 |
| `OnDestroy()` | 对象被销毁前 | 释放资源 |

一张经典的调用顺序图（官方时序）：

```text
场景加载/实例化
   └─ Awake()
   └─ OnEnable()
   └─ Start()
   └─ 每帧: FixedUpdate()×(固定次数) → Update() → LateUpdate()
销毁: OnDisable() → OnDestroy()
```

**关键规则**：
1. `Awake` 与 `Start` 都只执行一次，但 `OnEnable/OnDisable` 可能多次（对象反复 SetActive）；
2. `Awake` 在对象生成瞬间执行（哪怕组件未启用），因此**跨脚本引用建议在 Awake 里缓存**，`Start` 用来做“需要对方已就绪”的逻辑；
3. `FixedUpdate` 与 `Update` 的区别是面试/工作高频考点：物理移动写在 FixedUpdate（与帧率无关才稳定），表现性逻辑写 Update。

配套工程里 `Demos/LifecycleDemo.cs` 会生成一个方块并打印各阶段日志，跑起来看 Console 输出即可直观验证顺序。

## 2.3 帧、帧率与 Time.deltaTime（新手的第一个坑）

游戏不是“程序跑完就结束”，而是**每帧刷新**。帧率可能 30、60、144……如果你的移动写成 `position += 1`，60 帧就是每秒 60，144 帧就是每秒 144——**速度随帧率变化**。

正确姿势是乘以 `Time.deltaTime`（上一帧到本帧的秒数）：

```csharp
public class Mover : MonoBehaviour
{
    public float speed = 3f;

    void Update()
    {
        // 每秒向右移动 speed 米，而不是每帧移动 speed 米
        transform.position += Vector3.right * (speed * Time.deltaTime);
    }
}
```

> 写法速记：**“方向 × 速度 × Time.deltaTime”** 是 Unity 移动代码的万能句式。配套工程 `Demos/LifecycleDemo.cs` 的方块自转用的就是 `transform.Rotate(0, 70 * Time.deltaTime, 0)`。

`Time` 类其它常用成员：`time`（游戏运行秒数）、`timeScale`（慢动作：设为 0.5 即半速，为 0 暂停）、`fixedDeltaTime`。

## 2.4 组件通信：GetComponent / 引用

挂在一个对象上的多个脚本、或者不同对象之间要通信。三板斧：

### 2.4.1 同对象上拿组件

```csharp
Rigidbody rb = GetComponent<Rigidbody>();     // 拿自己身上的组件
Renderer renderer = GetComponent<Renderer>(); // 拿渲染器改颜色
```

缓存写法（Awake 里拿一次，避免每帧查找）：

```csharp
public class ColorChanger : MonoBehaviour
{
    Renderer _renderer;

    void Awake()
    {
        _renderer = GetComponent<Renderer>();   // 缓存
    }

    void Update()
    {
        if (Input.GetKeyDown(KeyCode.Space))
        {
            _renderer.material.color = Color.red; // 用缓存
        }
    }
}
```

### 2.4.2 拿场景里别处的对象 / 组件

```csharp
GameObject player = GameObject.Find("Player");   // 按名字找（慢，慎用）
Camera cam = Camera.main;                        // 主相机
Rigidbody rb = player.GetComponent<Rigidbody>();
```

### 2.4.3 Inspector 拖拽赋值（最推荐）

把别的对象 `public` 暴露出来，在 Inspector 里直接把 Hierarchy 对象拖进去，让引用由 Unity 在加载时绑定：

```csharp
public class Follower : MonoBehaviour
{
    public Transform target;   // Inspector 里把“要跟随的物体”拖进来
    public float followSpeed = 5f;

    void Update()
    {
        if (target == null) return;
        transform.position = Vector3.Lerp(transform.position, target.position, followSpeed * Time.deltaTime);
    }
}
```

### 2.4.4 父子 Transform

```csharp
transform.SetParent(otherTransform);       // 设为子级
transform.parent = null;                   // 脱离父级
transform.localPosition = Vector3.zero;    // 本地坐标
```

## 2.5 Inspector 序列化字段与参数调优

`public` 字段（或标 `[SerializeField]` 的 `private` 字段）会出现在 Inspector 上，**无需改代码就能调参**，这是游戏开发的巨大效率点：

```csharp
public class Player : MonoBehaviour
{
    public float moveSpeed = 8f;   // Inspector 可改
    public int lives = 3;
    [SerializeField] private float jumpPower = 5f; // 私有但想暴露
    [Range(0, 100)] public float health = 100f;    // 滑条
    [Header("移动参数")] public float accel = 1f;   // 分组标题
}
```

### 2.5.1 字段命名规范与警告

C# 指南里我们强调 `public` 字段用 PascalCase、私有用 `_camelCase`。Unity 的 Inspector 序列化对字段名的首字母大小写敏感，混用容易产生“Inspector 出现两个同名不同拼写字段”的困惑。团队规范建议：`[SerializeField] private` + 属性访问，或直接 `public` 并在代码里以公开字段为准。本专题统一用 `public` 字段演示（简单直白），真实项目请结合团队规范。

## 2.6 协程（Coroutine）：把逻辑拆成“分步执行”

游戏里经常要“等 2 秒再做 X”“每 0.5 秒闪一下”。协程可以让你**暂停一段逻辑、稍后再继续**，不阻塞主线程：

```csharp
public class EnemySpawner : MonoBehaviour
{
    public GameObject enemyPrefab;   // 拖入敌人预制体

    void Start()
    {
        StartCoroutine(SpawnLoop());
    }

    IEnumerator SpawnLoop()
    {
        while (true)
        {
            Instantiate(enemyPrefab, transform.position, Quaternion.identity);
            yield return new WaitForSeconds(1.5f);   // 停 1.5 秒
        }
    }
}
```

**要点**：
- 协程返回值必须是 `IEnumerator`；
- `yield return` 是“挂起点”，`WaitForSeconds` 表示暂停时长；
- 调用用 `StartCoroutine(...)`；可用 `StopCoroutine` / 禁用对象停止；
- 协程仍然跑在**主线程**上，不是多线程，只是“分段执行”的语法糖——这一点与 C# 指南里 async/await 思想类似，但 `async` 不能直接用于 MonoBehaviour 的普通调用（需要封装的库），初学阶段用协程即可。

> 思考题：把 2.3 的每帧移动与协程的“每 1.5 秒生成一次”对比，理解 Update 与协程是两种不同的“周期驱动”方式。

## 2.7 调试三板斧：Debug.Log / 断点 / 控制台

| 手段 | 用法 | 适合场景 |
| --- | --- | --- |
| `Debug.Log("...")` | 打日志 | 快速确认“代码走到这没有”，配套工程大量使用 |
| `Debug.LogWarning` / `LogError` | 警示/报错 | 标记可疑状态 |
| VS 断点 | 编辑器以 Debug 模式挂接，点断点 | 排查复杂逻辑、看变量 |
| Console 窗口 | 过滤器/Warning/Error 分组、双击跳转 | 日常开发主阵地 |

配套工程里 `LifecycleDemo.cs` 就在关键时机打日志，跑起来 Console 里能清楚看到 Awake→OnEnable→Start→Update 的顺序。

> **实测建议**：Unity 编辑器默认把 Console 放底部；打日志会轻微拖慢性能，正式发布前把不必要的日志删掉或用宏包起来（第四章会提）。

## 2.8 完整示例：让方块自己转 + 跟随（结合 2.2~2.4）

把下列脚本分别挂到两个 Cube 上（A 转，B 跟随 A 的旋转方向前进），Play 观察：

```csharp
// Rotator.cs —— 挂在“转盘”上
using UnityEngine;

public class Rotator : MonoBehaviour
{
    public float speed = 60f;              // 度/秒
    void Update()
    {
        transform.Rotate(0, speed * Time.deltaTime, 0);
    }
}

// Wagon.cs —— 挂在“小车”上，指向转盘方向移动
using UnityEngine;

public class Wagon : MonoBehaviour
{
    public Transform hub;                  // Inspector 拖入转盘
    public float speed = 2f;
    void Update()
    {
        if (hub == null) return;
        transform.position += hub.forward * (speed * Time.deltaTime);
    }
}
```

把 B 设成 A 的子对象再 Play，效果更直观——父子关系 + 每帧移动的组合你会反复用到。

## 2.9 实战与验收

### 实战：场景“开场白”脚本套件

在 demo 工程或你的练习工程中完成：

1. **生命周期观察器**：用配套 `LifecycleDemo.cs` 思想，生成一个方块并分别在 `Awake/OnEnable/Start/Update/OnDisable/OnDestroy` 打印当前帧的 `Time.frameCount`，Play 后按顺序记录日志，并在第 3 秒 `Destroy(gameObject)` 观察销毁日志；
2. **变速小球**：写 `SpeedBall`，用 `public float speed`，在 `Update` 中用 `transform.Translate(Vector3.forward * speed * Time.deltaTime)`，Inspector 改 speed 观察速度变化；再写一个脚本在 2 秒后 `speed *= 1.5f`（提示：协程）；
3. **颜色触发器**：用 `GetComponent<Renderer>()` 缓存，当方块 x > 5 时把颜色改成随机色（`Random.ColorHSV()`），并打印一条 `Debug.LogWarning`；
4. **跟随者**：写一个 `public Transform target`，用 `Vector3.MoveTowards` 或 `Lerp` 让小球每帧靠近目标 3 米内且不超过目标，Play 里手动拖 target 观察跟随。

### 验收清单
- [ ] 能说出 Awake / OnEnable / Start / Update / LateUpdate 的调用顺序并说明区别
- [ ] 能解释为什么移动必须乘 `Time.deltaTime`
- [ ] 会 `GetComponent`、Inspector 拖引用、`[SerializeField]` 与 `[Range]`
- [ ] 能用协程写“每隔 N 秒重复做某事”
- [ ] 完成以上 4 个小脚本并观察到日志与行为

### 自检题（快速回顾）
1. `Awake` 里能不能安全读取另一个还没 `Start` 的对象的数据？为什么？
2. 一个脚本禁用了（Inspector 取消勾选），它的 `Update` 还会执行吗？`Awake` 呢？
3. 协程会阻塞其它脚本吗？`WaitForSeconds` 期间主线程在干嘛？

## 2.10 本章小结与语言对照

| 你熟悉的概念（C#/.NET） | Unity 对应 |
| --- | --- |
| 事件/回调 | MonoBehaviour 生命周期函数（Start/Update…） |
| 反射/查找 | GetComponent / GameObject.Find（尽量少用） |
| 字段注入 | Inspector 序列化字段（public / [SerializeField]） |
| 定时器/Timer | 协程 WaitForSeconds / Time.deltaTime 累加 |
| 属性 | 尽量直接操作 public 字段或封装属性 |
| 类实例化 new | 场景放置 / Instantiate(prefab) |

**一句话总结**：把“程序 = 按顺序执行的语句”忘掉，换成“**世界里有 N 个对象，每个对象每帧做自己的事**”——这就是引擎驱动的 C# 开发。

下一章：[第三章 物理碰撞、输入与 UI](./03-物理碰撞输入与UI.md)。
