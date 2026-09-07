# Unity 引擎游戏开发学习专题（C#）

> 本专题是 [csharp-learning](../README.md) 的**游戏方向延伸**，前置基础为已完成 C# 学习指南前 1~7 章（语法 / OOP / 集合与 LINQ / 异步 / .NET 类库）。
>
> 目标读者：会用 C# 写控制台 / Web 程序，但对**游戏引擎与 Unity** 是零基础。专题从安装 Unity Hub 与编辑器开始，以「C# 脚本如何驱动一个游戏世界」为主线，最后交付一个完整可运行的小游戏。
>
> ✅ **已发布：README 索引 + 7 章（主线 4 章 + 进阶 3 章）+ `demo-project/` 纯代码演示工程**。每章都有「动手做 → 概念 → 完整示例 → 实战与验收」，风格与 C# / Rust 学习指南对齐。

## 本专题在体系中的位置

```text
C# 学习指南 1~7 章（语法/OOP/LINQ/异步/类库）
              │
              ▼
Unity 游戏开发专题（本目录）
  主线（第一章~第四章）
  01 引擎与编辑器基础（场景/对象/组件/坐标系）
  02 C# 脚本与生命周期（MonoBehaviour/组件式开发）
  03 物理碰撞、输入与 UI
  04 完整小游戏实战与发布（接宝石）
  进阶（第五章~第七章）
  05 uGUI 与 TextMeshPro 界面开发
  06 动画系统 Animator 与状态机
  07 2D 精灵与 2D 游戏开发
              │
              ▼
demo-project/ —— 可直接打开的 Unity 最小工程（纯代码，无美术资源依赖）
```

## 目录结构

| 章节 | 内容 | 文件 | 状态 |
| --- | --- | --- | --- |
| 第一章 | Unity 与游戏引擎基础：Unity Hub 安装、编辑器五大窗口、场景/游戏对象/组件、坐标系与 Transform、预制体 Prefab、资源管线概念 | [01-Unity引擎与编辑器基础.md](./01-Unity引擎与编辑器基础.md) | ✅ 已发布 |
| 第二章 | C# 脚本与生命周期：脚本即组件、MonoBehaviour 生命周期、事件函数顺序、Time.deltaTime、组件通信、Inspector 序列化字段、协程入门 | [02-Unity脚本生命周期与组件.md](./02-Unity脚本生命周期与组件.md) | ✅ 已发布 |
| 第三章 | 物理碰撞、输入与 UI：Rigidbody/Collider、物理材质、碰撞与触发事件、旧/新输入系统、uGUI 与 IMGUI | [03-物理碰撞输入与UI.md](./03-物理碰撞输入与UI.md) | ✅ 已发布 |
| 第四章 | 完整小游戏实战与发布：需求拆分、代码驱动场景、记分/生命/难度曲线、GameObject 生命周期管理、Build 构建与移动端注意 | [04-小游戏实战与发布.md](./04-小游戏实战与发布.md) | ✅ 已发布 |
| 第五章 | uGUI 与 TextMeshPro 界面开发（进阶）：Canvas 三种渲染模式、CanvasScaler 屏幕适配、EventSystem 事件分发与事件接口、RectTransform 与锚点布局、常用控件、TMP 高质量文字与中文字体、表现与逻辑分离 | [05-uGUI与TextMeshPro界面开发.md](./05-uGUI与TextMeshPro界面开发.md) | ✅ 已发布 |
| 第六章 | 动画系统 Animator 与状态机（进阶）：动画剪辑、Animator Controller、状态/过渡/参数、C# 驱动动画 API、动画事件、Any State、运行时换动作 | [06-动画系统与Animator状态机.md](./06-动画系统与Animator状态机.md) | ✅ 已发布 |
| 第七章 | 2D 精灵与 2D 游戏开发（进阶）：正交相机、Sprite 导入与 SpriteRenderer、排序层、Rigidbody2D/Collider2D、代码生成精灵（零美术自举）、Tilemap 选学 | [07-2D精灵与2D游戏开发.md](./07-2D精灵与2D游戏开发.md) | ✅ 已发布 |
| 示例工程 | 最小可打开的 Unity 工程 + 逐脚本说明（主线演示 1~4 + 2D 演示 5） | [demo-project/](./demo-project/) | ✅ 已发布 |

## 演示工程快速开始

仓库里的 `demo-project/` 是一个**纯代码、零美术资源**的 Unity 最小工程，用于跑通各章代码示例。两种使用方式任选：

1. **方式 A（推荐）**：用 Unity Hub 按本机已安装的 Unity 版本**新建一个空项目**（选 “Universal 3D” 或 “Built-in 3D” 模板均可），然后把本目录 `Assets/Scripts/` 整个文件夹复制进新项目的 `Assets/` 下，点 Play 即可。脚本会自动补全相机与灯光，无需手动搭场景。
2. **方式 B**：直接打开仓库内的 `demo-project/` 文件夹（Unity Hub → Open → 选择该目录）。若提示编辑器版本不匹配，请把 `ProjectSettings/ProjectVersion.txt` 里的版本号改成你本机已安装的 Unity 版本（例如 `6000.0.32f1`），或用方式 A。

打开后默认进入第四章「接宝石」小游戏；运行时按 **数字键 1~5** 切换各章演示（1~4 对应主线各章，5 为第七章 2D 精灵演示），按 **Esc** 清空。具体每个脚本的作用与逐行讲解见 [`demo-project/README.md`](./demo-project/README.md)。

> **为什么只有 1~5 五个演示，而不是七章各一个？** `demo-project` 的定位是“零第三方包、零资产、开箱即跑”。第五章 uGUI 依赖 `com.unity.ugui` 包、第六章 Animator 的 `.controller` 是编辑器资产，二者无法在纯代码自举工程里复现——这两章改走“编辑器搭资源 + 代码驱动”的真实工作流（章节内均有手把手步骤与完整代码）。第七章的 2D 演示可以完全程序化生成精灵，因此成功自举为演示 5。

## 学习路线建议

```text
主线：第一章 → 第二章 → 第三章 → 第四章
  │ （先“玩”编辑器 → 写脚本 → 加物理/输入 → 组装成小游戏）
  ▼
进阶（按兴趣分支，前四章是共同地基）
  界面方向：第五章 uGUI / TextMeshPro（给游戏做 HUD/菜单）
  表现方向：第六章 Animator（让角色“动”起来）
  类型方向：第七章 2D 精灵开发（做 2D 游戏，含可跑演示 5）
```

- 主线各章（一~四）与第七章的 C# 示例都以本专题 `Assets/Scripts/` 中的真实脚本为参照，二者可互相印证；第五、六章因资产驱动，以章节内“编辑器步骤 + 完整代码”为主；
- **Unity 版本说明**：示例基于 Unity 6 LTS（6000.x）编写，C# 代码使用 Unity 的旧输入系统与内置 IMGUI，绝大多数代码在 Unity 2021 LTS 以上均可直接运行。

## 环境要求

- **Unity Hub + Unity 编辑器**（推荐 Unity 6 LTS，6000.x）：https://unity.com/download —— 安装时勾选 “Windows Build Support / 你目标平台的模块” 可选；
- 无需额外美术工具：本专题示例全部用 Unity 内置的 Cube / Sphere / Plane 与纯色材质，零美术资源即可跑通；
- 编写 C# 脚本：Windows 上用 Visual Studio Community，或 VS Code + C# Dev Kit；Unity 会自动为项目生成 `.csproj`；
- **写作与校验说明**：本机未安装 Unity，专题文档与脚本**未在 Unity 中实机编译验证**，代码以 “能在 Unity 6 + 旧输入系统默认工程下运行” 为准则逐行评审过。若个别 API 在新版本有差异（例如 `FindFirstObjectByType` 在更老版本不存在），示例中都给出了备注。

## 学习建议

1. 先跑 `demo-project`，按数字键把 5 个演示都看一遍，再按章节顺序精读代码；
2. Unity 的核心心智模型是**“组件式开发”**：不写大而全的类，而是把一个功能拆成一个个小组件挂到物体上，这与 C# 学习指南里的“组合优于继承”一脉相承；
3. 官方文档首选：https://docs.unity3d.com/Manual/index.html （手册）与 https://docs.unity3d.com/ScriptReference/ （脚本 API）；
4. 每章末尾的“实战与验收”请独立完成——游戏编程 70% 靠动手，看代码不会让你会做游戏。

下一章：[第一章 Unity 引擎与编辑器基础](./01-Unity引擎与编辑器基础.md)
