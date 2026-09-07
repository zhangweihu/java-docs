# demo-project —— Unity 最小演示工程（逐脚本说明）

本工程是 [unity-learning](../README.md) 专题的**纯代码演示工程**：不包含任何美术资源、`.unity` 场景文件或第三方包依赖，打开即可运行五个演示——演示 1~4 对应主线第一章~第四章，演示 5 对应进阶第七章（2D 精灵与 2D 物理）。

> ⚠️ 本机编写时未安装 Unity，脚本按“Unity 6 LTS + 旧输入系统(Input Manager)”的默认工程规范逐行编写，**未经实机编译**。若你的工程启用了新输入系统（Active Input Handling = Input System (New)），请在 Project Settings → Player → Active Input Handling 选择 **Both**，否则脚本里的 `Input.*` 会报“不存在”错误。

## 目录结构

```text
demo-project/
├── ProjectSettings/ProjectVersion.txt   # 记录建议打开的 Unity 版本号（可改为本机已装版本）
├── Packages/manifest.json               # 最小内置模块清单（无任何第三方包）
└── Assets/Scripts/
    ├── AutoBoot.cs                      # 自举入口：Play 后自动创建 DemoManager（第二章 2.1）
    ├── DemoManager.cs                   # 演示总控：保证相机/灯光、按键切换演示（第一/二章组件思维）
    └── Demos/
        ├── LifecycleDemo.cs             # 演示 1：生命周期与组件（第二章）场景搭建
        ├── LifecycleLogger.cs           # 组件：打印生命周期顺序（第二章 2.2）
        ├── Spinner.cs                   # 组件：每帧自转（第二章 2.3）
        ├── HoverMover.cs                # 组件：PingPong 漂浮（第二章 2.3）
        ├── InputDemo.cs                 # 演示 2：输入控制移动（第二/三章）
        ├── PhysicsDemo.cs               # 演示 3：物理投掷（第三章）
        ├── CatchGame.cs                 # 演示 4：完整小游戏「接宝石」（第四章）
        └── Sprite2DDemo.cs              # 演示 5：2D 精灵与 2D 物理（第七章）
```

## 如何打开并运行

**方式 A（推荐，最稳）**：用 Unity Hub 按你本机已安装的版本新建一个空项目（Universal 3D 或 Built-in 3D 模板均可）→ 把本目录的 `Assets/Scripts/` 整个文件夹复制进新项目的 `Assets/` → 打开后直接按 Play。

**方式 B**：Unity Hub → Open → 选择本 `demo-project/` 文件夹。若提示版本不匹配，请把 `ProjectSettings/ProjectVersion.txt` 中版本号改为你已安装的版本，或用方式 A。

运行后：
- 默认进入**演示 4「接宝石」**（第四章完整小游戏）：`←/→` 或 `A/D` 移动挡板接宝石，按 `R` 重开；
- 按数字键 `1`~`5` 切换五个演示（`5` 为第七章 2D 演示），按 `Esc` 清空当前演示回空场景；
- 顶部灰色长条是 `DemoManager.OnGUI` 画的快捷键提示（IMGUI，第三章 3.5）。

> 进阶章节里**只有第七章**在 demo 里有对应演示（演示 5）：它完全由代码生成 2D 精灵，可零资产自举。第五章 uGUI、第六章 Animator 需要编辑器资源/包（`.controller`、`com.unity.ugui`），无法在“零包零资产”工程里复现，请按对应章节在你自己新建的工程里实操。

## 逐脚本说明

### 1. AutoBoot.cs —— 为什么没有场景也能跑

| 章节对应 | 关键 API | 作用 |
| --- | --- | --- |
| 第二章 2.1 | `[RuntimeInitializeOnLoadMethod]` | 静态方法 + 特性：场景加载完成后自动执行，无需挂任何对象 |
| 第二章 2.1 | `Object.FindFirstObjectByType<T>()` | 查找场景里是否已有 DemoManager，避免重复创建 |
| 第一章 1.4 | `new GameObject(...).AddComponent<T>()` | 代码造对象并挂组件（组件式开发） |

**核心思路**：真实项目里场景由编辑器搭好；这里为了“零 .unity 文件可跑”，改用代码在运行第一帧把世界造出来。`AutoBoot` 是入口，真正干活的是 `DemoManager`。

### 2. DemoManager.cs —— 总控与组件化调度

| 章节对应 | 关键 API | 作用 |
| --- | --- | --- |
| 第一章 1.4 | `EnsureCamera()` / `EnsureLight()` | 场景里没有主相机/光源就补一个（空场景也能看到物体） |
| 第一章 1.4 | `AddComponent<Demos.CatchGame>()` | 把“行为”挂到新建空对象上 —— 脚本即组件 |
| 第二章 2.3 | `Input.GetKeyDown(KeyCode.Alpha1)` | 旧输入系统读键 |
| 第三章 3.5 | `OnGUI()` + `GUI.Box` | 顶部快捷键提示条（IMGUI） |
| 第二章 2.2 | `Destroy(_current)` | 切换演示时销毁旧对象（触发其 OnDestroy） |

**你可以这样验证**：先按 1 进入生命周期演示，看 Console 里 `Awake→OnEnable→Start` 顺序；再按 4 进入小游戏，此刻上一个 Demo 对象被销毁，你在 Console 能看到它打印的 `OnDestroy`。

### 3. Demos/LifecycleDemo.cs（+ LifecycleLogger / Spinner / HoverMover）—— 生命周期日志与三种运动句法

演示 1 由四个文件组成（文件目录里可看到组件式代码的组织方式），按数字键 `1` 运行：

| 类（文件） | 讲解章节 | 用途 |
| --- | --- | --- |
| `LifecycleDemo` | 第二章 | 场景搭建：造地面 + 三个方块，并 AddComponent 三种“行为组件” |
| `LifecycleLogger` | 第二章 2.2 | 在每个生命周期函数打日志，观察调用顺序 |
| `Spinner` | 第二章 2.3 | 每帧自转：`方向 × 速度 × Time.deltaTime` |
| `HoverMover` | 第二章 2.3 | `Mathf.PingPong(Time.time, h)` 上下漂浮，另一种周期驱动 |

**观察要点**：按 1 后 Console 会打印 `[LifecycleCube] Awake/OnEnable/Start`；再按别的数字切换演示，能看到它的 `OnDisable/OnDestroy` 日志。

> 为什么拆成 4 个文件？Unity 约定“挂到对象上的脚本文件名要与类名一致”，一个功能一个组件一个文件是最佳实践——这正是组件式开发。

### 4. Demos/InputDemo.cs —— 键盘输入移动

| 章节对应 | 关键 API | 作用 |
| --- | --- | --- |
| 第三章 3.3 | `Input.GetAxisRaw("Horizontal"/"Vertical")` | 读键盘轴得到 -1~1 |
| 第二章 2.3 | `transform.Translate(move * Time.deltaTime)` | 与帧率无关的移动 |
| 第二章 2.3 | `Mathf.Clamp` | 把方块夹在世界边界内 |

按数字键 `2` 运行，用方向键/WASD 移动绿色方块。绿色方块**没有 Rigidbody**——这是刻意设计：纯逻辑移动交给 Transform 即可，避免与物理引擎冲突（第三章 3.1 的“高频错误”）。

### 5. Demos/PhysicsDemo.cs —— 物理投掷

| 章节对应 | 关键 API | 作用 |
| --- | --- | --- |
| 第三章 3.1 | `AddComponent<Rigidbody>()` | 给小球加刚体，物理接管重力 |
| 第三章 3.1 | `GameObject.CreatePrimitive(Sphere)` | 创建自带 SphereCollider+Renderer 的球 |
| 第三章 3.3 | `Input.GetMouseButtonDown(0)` | 鼠标左键 |
| 第三章 3.4 | `Camera.ScreenPointToRay` + `Physics.Raycast` | 屏幕坐标→世界坐标 |

按数字键 `3` 运行，**对着地面点鼠标左键**：每个点击处上方掉下一个彩色小球，受重力下落、落地滚动——物理演算全部由引擎完成。Console 会打印生成日志。可尝试修改 `DemoManager` 的默认演示或调整 `spawnHeight` 观察差异。

### 6. Demos/CatchGame.cs —— 完整小游戏（专题收官）

| 章节对应 | 关键 API | 作用 |
| --- | --- | --- |
| 第四章 4.1~4.4 | 状态机 + 计时器 | `_gameOver` 布尔状态 + `_spawnTimer` 累加 |
| 第四章 4.3 | `LateUpdate` | 先让挡板在 `Update` 移动完，再判定宝石 |
| 第四章 4.3 | 反向遍历 + `Destroy` | 边销毁边遍历安全写法 |
| 第四章 4.3 | `GameObject.CreatePrimitive` + `SetParent` | 把宝石作为子对象统一管理 |
| 第四章 4.3 | `OnGUI()` | IMGUI 计分与结束画面 |

按数字键 `4` 运行（默认）。玩法与验收清单见 [第四章](../04-小游戏实战与发布.md) 的“实战与验收”。这是对你前四章学习成果的最终检验：试着把 `moveSpeed`、`fallSpeed`、`startInterval` 等 `public` 字段在 Inspector 里调一调，感受“不改代码调手感”。

### 7. Demos/Sprite2DDemo.cs —— 2D 精灵 + 2D 物理

| 章节对应 | 关键 API | 作用 |
| --- | --- | --- |
| 第七章 7.5 | `Texture2D.SetPixels` + `Sprite.Create` | 用像素数组“画出”一张纯白精灵，贴图白 × 颜色着色 |
| 第七章 7.2 | `SpriteRenderer.sprite` / `.color` | 显示精灵并上色（零美术资源自举的核心） |
| 第七章 7.1 | `cam.orthographic` + `orthographicSize` | 切到 2D 正交视角 |
| 第七章 7.4 | `Rigidbody2D`（Static/Dynamic）+ `BoxCollider2D`/`CircleCollider2D` | 地面静态碰撞 + 彩球动态受重力下落、互相碰撞 |
| 第七章 7.5.1 | `Camera.ScreenToWorldPoint` | 鼠标点击的屏幕坐标 → 世界坐标 |

按数字键 `5` 运行：**在 Game 视图里点鼠标左键**，点击处会生成一颗随机颜色的 2D 小球，受重力下落、落到地面与其他球碰撞堆积。试着把 `groundY`、`maxGems` 调大调小观察差异。这是第七章“把 3D 物理换成 2D 平行版”的直观验证。

> 更多 2D 玩法改造（2D 版接宝石、序列帧动画、Tilemap 关卡）见 [第七章](../07-2D精灵与2D游戏开发.md) 的实战与验收。

## 常见问题

| 问题 | 解决 |
| --- | --- |
| 报错 `The name 'Input' does not exist...` | 你的工程启用了新输入系统；Project Settings → Player → Active Input Handling 改为 **Both** |
| Play 后画面全黑 | 检查是否已复制整个 `Scripts` 目录且编译通过；`DemoManager.EnsureCamera` 会自动建相机 |
| 打开工程提示版本不匹配 | 修改 `ProjectSettings/ProjectVersion.txt` 里的版本号为你本机版本，或直接用方式 A |
| 按数字键无反应 | 确认焦点在 Game 视图且输入法为英文状态；另外演示只在 Play 运行时生效 |
| 想改默认演示 | 修改 `DemoManager.cs` 里 `StartDemo(4)` 的参数（1~5） |

## 如何把这些代码用于你自己的游戏

1. 新建 Unity 工程后，把需要的演示脚本拷进 `Assets/Scripts/`；
2. 在编辑器里 `GameObject → Create Empty` 建一个空对象，把脚本拖上去（替代代码里的 `AddComponent`）；
3. 把脚本里的场景搭建代码（`CreatePrimitive`…）换成你在编辑器里摆好的对象，引用用 `public GameObject xxx` 在 Inspector 里拖——这正是从“代码自举”迈向“真实项目工作流”的过渡。
