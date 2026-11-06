# Unreal Engine 学习指南（UE 5.x 引擎方向）

> 本专题与 [opengl-learning](../opengl-learning/README.md)（图形底层）、[csharp-learning/unity-learning](../csharp-learning/unity-learning/README.md)（Unity 引擎）并列，是仓库的**商业级游戏引擎 / C++ 引擎层方向**学习资料。
>
> 面向**已有任一语言编程经验**（尤其 Java/C#/Python 背景）、但对 UE 零基础的读者。全程穿插 **UE vs Unity / Java / C#** 横向对照，帮你把"引擎黑盒"和已有知识连成一张图。
>
> ✅ **已发布：README 索引 + 3 章**（使用 / 开发 / 技能树）。三章定位不同、可独立阅读：
>
> | 你的目标 | 直接读 |
> | --- | --- |
> | 先把编辑器用起来、能跑能打包 | [01 使用与编辑器指南](./01-UE引擎使用与编辑器指南.md) |
> | 用 C++/蓝图写 Gameplay 逻辑、理解引擎架构 | [02 开发实战指南](./02-UE引擎开发实战指南.md) |
> | 搞清楚要学哪些东西、按什么顺序学、面试考什么 | [03 技能树与知识点](./03-UE开发技能树与知识点.md) |

---

## 为什么单独开一个 UE 专题

- **Unity 是 C# / 组件式，UE 是 C++ / 继承 + 反射**：两者 GameObject-Component 看着像，但对象模型、内存管理（GC vs 手写 UPROPERTY 引用）、构建系统（UHT/UBT）、框架约定（GameMode/GameState/Controller）差别很大，不能简单迁移；
- **UE 是"框架很强"的引擎**：它替你定好了游戏框架（Gameplay Framework），顺着它写很省事，逆着它写处处碰壁——这正是最需要系统学习的地方；
- **UE 5 引入了整套次世代渲染与世界构建**（Nanite / Lumen / World Partition / Virtual Shadow Maps / Chaos），理解"什么时候开、什么时候关"本身就是一个知识点；
- **与 opengl-learning 互补**：那里讲 GPU 管线里发生了什么，这里讲引擎如何把这些能力封装成产品级工具链。

---

## 目录结构

| 章节 | 定位 | 内容 | 文件 | 状态 |
| --- | --- | --- | --- | --- |
| 第一章 | **使用文档** | 版本与许可、硬件与安装、项目创建与模板、编辑器界面全解、视口导航、关卡编辑、Content Browser 资产管理、材质编辑器入门、蓝图可视化脚本、PIE 预览、打包发布、插件与版本控制、常见报错 | [01-UE引擎使用与编辑器指南.md](./01-UE引擎使用与编辑器指南.md) | ✅ 已发布 |
| 第二章 | **开发文档** | 引擎分层与模块（Build.cs/UHT/UBT）、UObject 反射与 GC、核心类继承树、Gameplay Framework 全流程、生命周期与初始化顺序、UE 常用类型与容器、委托与事件、蓝图/C++ 互操作、Enhanced Input、碰撞与射线、定时器与异步、UMG、网络复制与 RPC、GAS 入门、Nanite/Lumen/World Partition、调试与性能、构建打包、完整 C++ 实战（可拾取物 + 交互）、踩坑清单 | [02-UE引擎开发实战指南.md](./02-UE引擎开发实战指南.md) | ✅ 已发布 |
| 第三章 | **技能树** | 岗位分工（Gameplay/引擎/渲染/网络/工具/TA/关卡/美术）、四层技能树、C++ UE 方言子集、数学与图形学知识清单、引擎机制清单、工具链、平台性能、网络同步、协作软技能、四条目标导向学习路线、资源推荐、自检清单 + 面试题 | [03-UE开发技能树与知识点.md](./03-UE开发技能树与知识点.md) | ✅ 已发布 |

---

## 学习路线

```text
                      ┌── 第 03 章（先读，建立地图）──┐
                      │  我要学什么？按什么顺序？      │
                      └──────────┬───────────────────┘
                                 ▼
第 01 章  使用与编辑器          ← 先把引擎装起来、能跑能打包（零代码）
    │      安装 → 界面 → 关卡 → 资产 → 材质 → 蓝图 → PIE → 打包
    ▼
第 02 章  开发实战              ← 再用 C++ / 蓝图写逻辑
    │      UObject 反射 → 核心类 → Gameplay Framework → 输入/碰撞/UI/网络
    │      → 调试性能 → 构建 → 完整实战
    ▼
第 03 章  技能树复盘            ← 对照自检清单，补齐薄弱项、选定专精方向
```

- **只做 Demo / 验证玩法**：第 01 章 + 第 02 章前 6 节即可；
- **要做完整项目 / 找工作**：三章通读，重点第 02 章的构建系统与网络复制、第 03 章的自检清单；
- **图形/渲染方向**：建议先读 [opengl-learning](../opengl-learning/README.md) 再回来看第 02 章渲染小节。

---

## 版本与环境说明

| 组件 | 建议 | 说明 |
| --- | --- | --- |
| 引擎 | **Unreal Engine 5.x**（5.3+ 均可） | 本专题按 UE 5 通用形态编写：Enhanced Input、Nanite、Lumen、World Partition、Chaos、Common UI 等均为 5.x 体系；UE4 与 5 的差异在各章内以「UE4 提示」标注 |
| 获取方式 | Epic Games Launcher（二进制版） / GitHub 源码版 | 新手用 Launcher；需要改引擎源码、调试引擎内部或做定制构建时再编译源码版 |
| 操作系统 | Windows 10/11 64 位（首选）、Linux、macOS | Windows 上工具链最完整 |
| IDE | **Visual Studio 2022**（MSVC，Windows 必备）/ JetBrains Rider / VS Code | 需安装「使用 C++ 的游戏开发」工作负载 |
| 编译工具 | UnrealBuildTool(UBT) + UnrealHeaderTool(UHT) | 引擎自带，第 02 章详解 |
| 版本控制 | **Git + Git LFS** 或 Perforce | UE 项目二进制资产多，第 01 章给出 `.gitignore` 模板 |
| 硬件 | 建议 32GB RAM / 8GB+ 显存 / SSD | 编译与 Shader 编译吃内存和磁盘，见第 01 章硬件表 |

> ⚠️ **本专题只提供学习文档，不附带可编译工程**：UE 工程含大量二进制资产与生成文件，不适合放进纯文本仓库。各章代码为「在你自己工程里新建的类/文件」，按第 01 章创建一次 C++ 项目后逐章添加即可。

---

## 与其他专题的对照

| 概念 | Unity（unity-learning） | Unreal（本专题） | Java / C# 类比 |
| --- | --- | --- | --- |
| 场景对象 | GameObject | **AActor** | 一个可放进容器的对象 |
| 功能挂载 | Component（组合优先） | UActorComponent + 继承（ACharacter 等） | 组合 vs 继承 |
| 脚本 | MonoBehaviour (C#) | **UObject 子类 (C++) + 蓝图** | Bean / 组件 |
| 生命周期 | Awake/Start/Update | **BeginPlay/Tick/EndPlay** | init/回调 |
| 内存 | .NET GC | **UE 反射 GC + UPROPERTY 引用根** | GC 根可达性 |
| UI | uGUI / TMP | **UMG / Slate** | 视图层 |
| 资源 | Assets / Prefab | **Content Browser / Blueprint Class** | 类与实例 |
| 构建 | Unity Build Settings | **UBT + Target.cs + 打包 Cook** | Maven/Gradle |
| 编辑器扩展 | EditorWindow / CustomInspector | **插件 + 编辑器模块 + Details 定制** | IDE 插件 |

---

## 学习建议

1. **先装好、跑通一次 PIE 和一次打包**，再碰 C++——UE 的环境问题（SDK、MSVC、Shader 编译）占了新手 80% 的挫败感；
2. **接受"框架约定"**：GameMode 放规则、GameState 放状态、Controller 放意志、Pawn 放躯体——别按自己习惯乱放，顺着框架写会省大量力气；
3. **每个 UPROPERTY 都要想清楚**：它不只是"字段"，还是"GC 引用根 / 序列化字段 / 蓝图可见性 / 网络复制标记"四件事（第 02 章重点）；
4. **C++ 与蓝图不是二选一**：常见分工是「C++ 定基类与性能敏感逻辑，蓝图派生与调参」，第 02 章给出互操作规范；
5. **性能问题先用工具定位再改**：`stat unit` / Unreal Insights / ProfileGPU，别凭感觉优化（第 02 章调试小节）；
6. 学完本专题后，回看 [opengl-learning](../opengl-learning/README.md) 理解 Nanite/Lumen 背后的 GPU 原理，或对照 [unity-learning](../csharp-learning/unity-learning/README.md) 理解两套引擎的设计取舍。

下一章：[01-UE引擎使用与编辑器指南.md](./01-UE引擎使用与编辑器指南.md)
