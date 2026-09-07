# 第一章 Unity 引擎与编辑器基础

> 前置：已了解 C# 基础语法（见 C# 指南 1~4 章）。本章**不写代码**，先建立游戏引擎的心智模型，学会在编辑器里搭一个最简单的场景，理解“场景 / 游戏对象 / 组件”三者关系——这是之后所有脚本的地基。

## 1.1 什么是游戏引擎，Unity 是什么

**游戏引擎**（Game Engine）是帮你搭建游戏世界的“工具箱 + 运行时”：
- **渲染**：把 3D 模型 / 2D 图片画到屏幕上；
- **物理**：重力、碰撞、刚体运动（由 `PhysX` 等库提供）；
- **输入**：键盘、鼠标、手柄、触摸屏；
- **音频 / 动画 / 粒子 / UI / 网络**……这些若从零自己写，每个都是一门深坑。

Unity 是全球装机量最大的游戏引擎之一，特点是：
- **C# 作为唯一脚本语言**（正好复用你已学的技能）；
- **编辑器可视化**：搭场景像拼乐高，C# 脚本负责给“乐高”写逻辑；
- **一次开发多端发布**：Windows / macOS / Linux / Android / iOS / WebGL / 主机；
- **组件式架构**（Component-based）：万物皆可挂组件，是本专题的核心思维。

对比你已知的“C# 世界”：过去你写的 C# 由 `Main()` 启动、按你的顺序执行；而在 Unity 里**你不再拥有主循环**——引擎每帧自动调用你挂载脚本里约定的方法（如 `Update()`），你只是告诉引擎“每帧做这件事”。这是观念上最大的转变。

## 1.2 动手做：安装并创建第一个工程

### 1.2.1 安装 Unity Hub 与编辑器

1. 去 https://unity.com/download 下载 **Unity Hub**（桌面管理工具）；
2. 在 Hub 里 **Installs → Install Editor**，选一个 **LTS 版本**（推荐 Unity 6 LTS / 6000.x）；
3. 安装时勾选模块：Windows 就勾 “Windows Build Support (IL2CPP)”，之后想发安卓/iOS 再加对应模块；
4. 编辑器第一次启动较慢（要导入一堆内置资源），属正常现象。

> 说明：Unity 个人版（Personal）免费，但编辑器本体体积较大（几个 GB），请预留磁盘空间。

### 1.2.2 创建工程并认识模板

打开 Hub → **New Project**：
- **Universal 3D（URP）**：新版默认渲染管线，画面更好，本专题示例在其中运行没问题；
- **Built-in 3D（内置管线）**：经典模板，兼容性最好。

> 本专题配套工程 `demo-project` 为了“零依赖、处处可跑”，用的是**最朴素的代码生成物体**方式，两种模板都能直接运行。用 Hub 新建项目后，把我们 `demo-project/Assets/Scripts/` 文件夹拷进新工程的 `Assets/` 下即可。

### 1.2.3 编辑器五大窗口（以中文界面为准，英文名也标出）

| 窗口 | 英文 | 作用 |
| --- | --- | --- |
| **Hierarchy（层级）** | Hierarchy | 当前**场景**里有哪些对象（左栏列表） |
| **Scene（场景）** | Scene View | 可视化编辑世界（中间 3D 视图） |
| **Game（游戏）** | Game View | 玩家视角预览（右上角切到 Game） |
| **Inspector（检查器）** | Inspector | 选中对象后查看/修改它的所有组件与属性（右侧） |
| **Project（项目）** | Project | 项目里的所有资源文件（模型/图片/脚本/预制体），底部 |

**第一次先做这几件事**（都做一遍就有感觉了）：
1. 在 Hierarchy 空白处右键 → **3D Object → Cube**，场景里出现一个方块；
2. 选中方块，看右侧 Inspector：有 `Transform`（位置/旋转/缩放）；
3. 用工具栏的手型工具（快捷键 Q）拖动物体；用 W（移动）/E（旋转）/R（缩放）工具；
4. 上方 **Play** 按钮（▶）进入运行态；按一次开始，再按一次停止（运行态里改的东西不会保留！）。

> **游戏开发的经典入门仪式**：把 Cube 放在地面上、旁边放个光，点击 Play 看到它“立”在场景里，然后你才真正开始理解引擎。

## 1.3 核心概念一：场景（Scene）

- **场景**就是一个“关卡 / 世界切片”。新工程的 `SampleScene` 就是一个示例场景；
- 场景文件是 `.unity`（在 `Assets/Scenes/` 下），本质是 YAML 文本，记录了里面所有对象及组件；
- 打包发布时，你要把场景加入 **Build Settings**（File → Build Profiles / Build Settings），否则玩家进入游戏会看到黑屏；
- 你可以有多个场景（主菜单场景、关卡 1、关卡 2），用代码或自动加载切换。

> 配套工程的例子：`demo-project` 里**没有 .unity 场景文件**——我们用 C# 在运行第一帧把整个世界“造”出来（见第四章原理）。这能让你看清“场景 = 对象清单”的本质，但真实项目请务必用编辑器搭场景。

## 1.4 核心概念二：游戏对象与组件（GameObject & Component）

**GameObject（游戏对象）**是场景里的“空壳子”，本身什么都没有；**Component（组件）**才是它的能力。

| 你想让物体… | 挂什么组件 |
| --- | --- |
| 有个位置/可移动 | Transform（每个对象必备） |
| 能被看见 | MeshRenderer + MeshFilter（或 SpriteRenderer） |
| 参与物理下落/碰撞 | Rigidbody（刚体）+ Collider（碰撞体） |
| 播放声音 | AudioSource |
| 有“行为逻辑” | **你自己的 C# 脚本**（MonoBehaviour） |
| 发光照亮场景 | Light |

**经典比喻**：GameObject 是空房间，组件是房间里的家具；C# 脚本是你给房间装的一个“自动化管家”，告诉房间每帧该干什么。

在编辑器里的体现：Hierarchy 选中对象 → Inspector 里的“Add Component”按钮可以加组件；你会看到 Cube 天生带着 `Transform`、`Mesh Filter`、`Box Collider`、`Mesh Renderer`。

## 1.5 核心概念三：坐标系与 Transform

游戏世界里用**三维笛卡尔坐标系**（右手系）：

- **X** 向右，**Y** 向上，**Z** 向屏幕外（朝向你）；
- 在 2D 游戏里，通常忽略 Z 或让 Z=0（用的是 X、Y 平面，但仍然是 3D 世界）。

**Transform 组件**含三类数据（C# 里对应同名属性）：
- `position`：世界位置（Vector3）
- `rotation`：旋转（用四元数 Quaternion 表示，不要直接改 x/y/z 角度）
- `localScale`：缩放（Vector3）

子对象与父对象：把一个 Cube **拖到**另一个 Cube 下面（Hierarchy 里成为子级），子对象会跟着父对象一起动——这就是“父子层级”，做“角色+武器”“汽车+轮子”时非常常用。子对象的 position 若显示为绿色字，表示它是相对父级的**本地坐标**。

**Scene 与 Game 两个坐标的关联**：你在 Scene 视图摆位置，Game 视图显示最终画面（取决于 Camera 的视野）。

> 理论补充：C# 里的 `Vector3` 你已经在 C# 指南学过；Unity 里位置/缩放操作的就是 `Transform`。后面每一行移动代码都是 `transform.position += 方向 * 速度 * Time.deltaTime` 这个套路。

## 1.6 核心概念四：预制体（Prefab）

**Prefab（预制体）**= 一个可复用的对象模板。例如敌人：你先做好一个“敌人”对象（模型+碰撞+脚本），存成 Prefab，之后敌人从“对象”变成“资源”，你可以：
- 在场景里放 100 份副本（Instantiate）；
- 改 Prefab 一次，所有副本同步更新；
- 运行时代码动态生成（本章先了解，第四章会用到）。

创建：把一个对象从 Hierarchy 拖到 Project 窗口 → 变成蓝色图标的 Prefab 资源；之后 Hierarchy 里用它生成的对象名字为蓝色。

## 1.7 核心概念五：资源管线（Asset Pipeline）

Unity 项目里 `Assets/` 文件夹就是你的“素材库”，一切文件（`.cs`、`.png`、`.fbx`、`.mp3`、`.unity`）都是 Asset。重点认知：
- **脚本编译**：所有 `.cs` 放在 Assets 任意子目录都会被 Unity 编译成程序集；改完脚本回到编辑器，它会自动重新编译（右下角转圈）；
- **导入设置**：图片默认按“纹理”导入，音频按“音频剪辑”导入——双击资源可在 Inspector 调导入参数（例如图片的 Sprite 模式、像素压缩）；
- **.meta 文件**：Unity 会给每个资源生成同名 `.meta`（存 GUID）。若直接拷贝整个工程到别处必须连 `.meta` 一起，否则引用会断。这也是为什么仓库的 demo 工程建议“拷 Assets 到新建项目”而不是整个工程拷来拷去。

> 本专题示例为了“零美术资源”，用 `GameObject.CreatePrimitive()` 在代码里造 Cube/Sphere/Plane，材质用纯色——**渲染管线与素材导入完全不需要碰**，把注意力全部留给 C# 逻辑。

## 1.8 第一个“不用写代码”的迷你场景（动手做）

> 目标：不写一行 C#，理解编辑器工作流。

1. 新建场景（File → New Scene），选择 Basic 模板；
2. Hierarchy 右键创建：`3D Object → Plane`（地面）、`3D Object → Cube`（主角）、`Light → Directional Light`（太阳）；
3. 把 Cube 移到 Plane 上方（y≈0.5）；
4. 右键 Cube → **3D Object → Sphere** 让它成为 Cube 子对象（模拟“头顶戴球”）；
5. 选中 Camera（主相机），看它的 Game 预览：把相机拖到合适位置看到你的物体；
6. 点 Play：世界“活”起来了吗？还没——因为你还没写任何逻辑。**感觉一下“运行态 = 代码驱动”**，然后停止；
7. 保存场景（Ctrl+S），命名 `MyFirstScene`。

**验收清单**
- [ ] 能在 Hierarchy 新建并拖动对象，理解父子层级
- [ ] 能用 W/E/R 工具与 Inspector 修改位置/旋转/缩放
- [ ] 能分清 Scene 与 Game 视图
- [ ] 知道 Play 开始/停止，且运行态修改不会保留
- [ ] 能说出“GameObject 是壳，Component 是能力”并举例

## 1.9 本章小结

| 概念 | 一句话 | 后续用到 |
| --- | --- | --- |
| 场景 Scene | 一个世界的切片（关卡） | 第四章发布 |
| 游戏对象 GameObject | 场景里的空壳 | 全专题 |
| 组件 Component | 给壳装能力 | 第二、三章 |
| Transform | 位置/旋转/缩放的载体 | 第二章脚本必用 |
| Prefab | 对象模板，可复制/动态生成 | 第四章节后实战 |
| 资源管线 | Assets 即素材库 | 了解即可 |

**语言对照（C# 控制台世界 → Unity 世界）**

| 控制台 C# | Unity |
| --- | --- |
| `Main()` 按你的顺序执行 | 引擎主循环，每帧回调你脚本的方法 |
| `class Program` | `MonoBehaviour` 子类脚本 |
| `new Car()` | 场景放对象 / `Instantiate(prefab)` |
| `new` 之后自己管理生命周期 | 引擎管理 GameObject 生命周期（激活/销毁） |

下一章：[第二章 C# 脚本与生命周期](./02-Unity脚本生命周期与组件.md)。我们开始给这个“空壳”写灵魂。
