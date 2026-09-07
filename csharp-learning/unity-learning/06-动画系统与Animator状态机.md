# 第六章 动画系统 Animator 与状态机

> 前置：第二、四章。前面你学会了“物体怎么动”（代码改 Transform）。但角色的**走动/奔跑/跳跃/攻击**若全用代码硬算，工作量与 bug 都会爆炸。游戏业界用**动画状态机**解决：把每种动作做成一个状态，用**条件**在状态间切换。本章讲 Unity 的 Animator（Mecanim）系统，并把它与 C# 代码接起来。
>
> 说明：动画资源（`.anim` 片段、`.controller` 状态机）本质是**编辑器创建的资产**，与场景一样需要可视化制作，因此本章主要走“编辑器制作 + C# 驱动”的真实工作流；你会在本章里创建属于自己的动画状态机。

## 6.1 动画的本质：随时间改变属性

“动画”在引擎里就是**在时间轴上给属性做关键帧**。比如：

- 让方块 1 秒内从角度 0 转到 90° → 对 `Rotation` 做两个关键帧（0 秒=0°，1 秒=90°）；
- 让角色从站到蹲 → 对骨骼/位移做关键帧。

Unity 中一段动画存为 **Animation Clip（动画剪辑）**，后缀 `.anim`，是 YAML 资产。它记录：改哪个对象、改哪个属性、每个关键帧的值与插值曲线。

### 6.1.1 认识 Animation 窗口（录制工具）

菜单 **Window → Animation → Animation**（或 Ctrl+6）打开：

1. 在 Hierarchy 选中要动的物体，Animation 窗口点 **Create**，命名如 `Spin`，生成 `Spin.anim`；
2. 点红色的 **录制按钮（●）**，把时间轴拖到 0 秒，在 Inspector 改旋转为 0；
3. 时间轴拖到 1 秒，把旋转改成 360°，Unity 自动在两端写下关键帧；
4. 关录制，点 ▶ 预览——方块转起来了。

这样你就手工“K”了一段动画。**任何能在 Inspector 显示的属性**（位置、旋转、颜色、缩放、自定义脚本的 public 字段）理论上都能录成动画。

> 为什么游戏里角色动画通常来自美术？因为模型是骨骼绑定的，动画本质是“骨骼随时间摆姿势”的关键帧，由 DCC 工具（Blender/Maya/Max）导出 FBX 带入 Unity，而非手 K。但原理一致。

## 6.2 Animator 与 Animator Controller：状态机

**Animator** 是挂在对象上的组件，负责播放动画；它需要一个 **Animator Controller（`.controller`）** 资产，后者是一张**状态机图**。

状态机是软件工程老概念（回顾第四章 4.2）：**同一时刻只能处于一个状态，满足条件就从一个状态跳到另一个状态**。

```
        [speed>0.1]                 [isJumping]
   Idle ──────────► Run ────────────► Jump
    ▲                 │                  │
    └────[speed<=0.1] ┘                  └──[isGrounded]──► Idle
```

### 6.2.1 在 Unity 里做状态机的图形界面

1. 在 Project 窗口右键 → **Create → Animator Controller**，命名 `PlayerCtrl`；
2. 把它拖给物体的 `Animator` 组件的 `Controller` 字段（没有 Animator 组件就先 AddComponent）；
3. 打开 **Window → Animator**（面板）：
   - 左下 **Parameters** 点 `+` 建参数（Float / Int / Bool / Trigger）；
   - 中间是状态图：把 `.anim` 片段从 Project 拖进来自动成为一个**状态**（State）；
   - 右键状态 → **Make Transition**，拖到目标状态，生成**过渡线**；
   - 点过渡线，在 Inspector 设 `Conditions`（满足才过渡）与 `Transition Duration`（过渡时长，0 = 瞬间切换）；
   - 绿色 = 默认状态（入场先播放它）。

### 6.2.2 状态机的核心概念表

| 概念 | 英文 | 说明 |
| --- | --- | --- |
| 状态 | State | 一个动作（内部绑定一段/多段 clip） |
| 默认状态 | Default State | 入场时先进入的状态（绿色） |
| 过渡 | Transition | 从一个状态到另一个的“箭头线” |
| 条件 | Condition | 过渡箭头上要求满足的参数判断 |
| 参数 | Parameter | Float/Int/Bool/Trigger，供条件与混合使用 |
| Any State | Any State | 从任意状态可跳出的“特殊源”，常用于受击/死亡打断 |
| 退出时间 | Exit Time | 播放完成后自动过渡（如“出拳打完自动回待机”） |
| 混合树 | Blend Tree | 一个状态内按参数（如速度）在多个动作间平滑混合 |

**实战经验**：
- **Bool 适合“持续状态”**：`isRunning`、`isGrounded`；
- **Trigger 适合“一次性打断”**：受击、跳跃起跳瞬间、攻击；Trigger 用后会自动复位，但要小心重复触发——判断完记得 `ResetTrigger`；
- **Any State → 受击/死亡**：用 Any State 保证不管正在跑步/跳跃都能立刻被打断。

## 6.3 动手做：给一个方块搭“待机 → 旋转 → 跳跃”三态状态机

### 第 1 步：准备三段动画剪辑

用 6.1 的 Animation 窗口对同一个 Cube 依次 Create 三段：
- `Idle.anim`：0.4 秒不动（录制 0 帧即可）；
- `Spin.anim`：1 秒内绕 Y 轴转 360°（检验动画确实在播放）；
- `Jump.anim`：0.5 秒内从 `Y=0` 升到 `Y=1` 再落回 `Y=0`（平移型动画）。

### 第 2 步：建状态机

1. 创建 `CubeCtrl.controller` 赋给 Cube 的 Animator；
2. 把三个 clip 拖进状态图；右键 Idle → **Set as Layer Default State**；
3. 连线：`Idle ⇄ Spin`，`Idle → Jump`、`Spin → Jump`、`Jump → Idle`；
4. Parameters 加两个：Bool `spinning`、Bool `jumping`；
5. 设置过渡条件：
   - `Idle→Spin`：`spinning == true`；`Spin→Idle`：`spinning == false`；
   - `Idle→Jump`、`Spin→Jump`：`jumping == true`；
   - `Jump→Idle`：`jumping == false`（可顺便取消勾选 Has Exit Time，改由代码控制）。

### 第 3 步：用 C# 驱动参数

```csharp
using UnityEngine;

public class CubeAnimDriver : MonoBehaviour
{
    Animator _anim;

    void Start()
    {
        _anim = GetComponent<Animator>();       // 组件式开发：拿本对象上的 Animator
    }

    void Update()
    {
        // 按下空格时“起跳”，用 Trigger 是一次性的；这里用 Bool 便于演示复位
        if (Input.GetKeyDown(KeyCode.Space))
            _anim.SetBool("jumping", true);
        if (Input.GetKeyUp(KeyCode.Space))
            _anim.SetBool("jumping", false);

        // 按下 B 进入旋转，再按 B 退出
        if (Input.GetKeyDown(KeyCode.B))
            _anim.SetBool("spinning", !_anim.GetBool("spinning"));
    }
}
```

运行后按 **B**：方块从待机平滑过渡到旋转；按**空格**：跳起并回落。整个过程你没写任何“补间/插值”代码——动画是引擎播放的，你只负责告诉它“该进哪个状态”。

## 6.4 Animator 的 C# API 全解

Animator 组件（命名空间 `UnityEngine`）最常用的 API：

| API | 作用 |
| --- | --- |
| `GetComponent<Animator>()` | 拿组件引用 |
| `_anim.SetFloat("speed", v)` / `SetBool` / `SetInteger` / `SetTrigger` | 设参数 |
| `_anim.GetFloat("speed")` / `GetBool` | 读参数（用于条件判断） |
| `_anim.ResetTrigger("hit")` | 手动复位 Trigger（防重复） |
| `_anim.Play("Run", layer, time)` | 直接强制播放某状态（跳过过渡） |
| `_anim.GetCurrentAnimatorStateInfo(0)` | 当前状态信息（`IsName()`、`normalizedTime`） |
| `_anim.HasParameter("speed")` | 判断参数是否存在（省去误写） |
| `Animator.StringToHash("speed")` | 把字符串转成哈希 int，**高频调用时性能更好** |
| `_anim.speed` | 全局播放速度（0 = 暂停，2 = 两倍速） |

**性能写法**：`Update` 每帧调用 `SetBool("jumping", ...)` 其实没多少开销，但若是每个角色每个参数都传字符串，会产生 GC。规范做法是缓存哈希：

```csharp
readonly int JumpHash = Animator.StringToHash("jumping");   // 只算一次
_anim.SetBool(JumpHash, true);
```

### 6.4.1 读“当前在播什么”（判断动画播完没）

```csharp
AnimatorStateInfo info = _anim.GetCurrentAnimatorStateInfo(0);  // 0 = 基础层
if (info.IsName("Jump") && info.normalizedTime >= 1f)
{
    // Jump 已播放完一遍（normalizedTime >= 1），例如此时再施加伤害落地判定
    _anim.SetBool("jumping", false);
}
```

`normalizedTime`：已播放时间除以总时长，1 = 播完一遍。它是实现“攻击动作播完才结算伤害”的常用手段。

## 6.5 动画事件：让动画“通知”代码

有时要“动画播到某个关键帧时做一件事”——例如“抬脚到最高点→播放脚步声”“挥剑到第 20 帧→生成伤害判定”。做法是**动画事件（Animation Event）**：

1. 打开 Animation 窗口选中某段 clip，把时间轴放到想要的时间点；
2. 点 **Events → Add Animation Event**；
3. Inspector 里 `Function` 填**脚本方法名**（如 `OnFootstep`）；
4. 物体上任意脚本定义同名方法，播放到该帧时引擎自动调用：

```csharp
public class Footstep : MonoBehaviour
{
    public void OnFootstep()   // 方法名要与动画事件填写的完全一致
    {
        Debug.Log("这一步踩下去——播放脚步音效/落尘");
    }
}
```

> 方法参数可选：int / float / string / Object，事件里会提供填写框。动画事件是“美术动画”与“程序逻辑”解耦的标准接口：动画师决定“何时触发”，你只写“触发后做什么”。

## 6.6 代码里读模型/换动画（进阶）

### 6.6.1 代码创建 AnimationClip（运行时也可以）

Unity 允许在代码里动态生成一段 clip（比如程序化抖动、程序化武器挥动）：

```csharp
using UnityEngine;

public class RuntimeClip : MonoBehaviour
{
    void Start()
    {
        // 创建一段 1 秒 clip：把本对象绕 Y 轴从 0 转到 360°
        var clip = new AnimationClip();
        clip.legacy = true;                                // 使用旧的 Animation 播放方式
        var curve = AnimationCurve.EaseInOut(0f, 0f, 1f, 360f);
        clip.SetCurve("", typeof(Transform), "localEulerAngles.y", curve);

        var anim = gameObject.AddComponent<Animation>();   // 旧 Animation 组件
        anim.AddClip(clip, "shake");
        anim.Play("shake");
    }
}
```

> ⚠️ 这个示例用旧 `Animation` 组件（而非 Animator）。原因：**Animator 的 controller 资产无法用纯代码创建**（`UnityEditor.Animations` 命名空间只在编辑器可用）。正式项目几乎总是走“编辑器做 .controller + .anim”路线。演示工程由于是“无资产自举”，所以全专题没有用 Animator 做可跑演示——这就是引擎资产管线与纯代码的边界，值得体会。

### 6.6.2 换皮肤/换动作：AnimatorOverrideController

运行时想“同一套状态机，把 Run 换成别的 clip”（角色换装备/换皮肤），不必重做 controller：

```csharp
public class OverrideDemo : MonoBehaviour
{
    public AnimatorOverrideController overrideCtrl;   // 在 Inspector 创建：Assets→Create→Animator Override Controller
    public AnimationClip newRun;

    void Apply()
    {
        overrideCtrl["Run"] = newRun;                 // 用状态名当索引替换 clip
        GetComponent<Animator>().runtimeAnimatorController = overrideCtrl;
    }
}
```

## 6.7 实战与验收

### 实战：给第四章的挡板加“接住反馈动画”

给「接宝石」的玩家挡板做三态：`Idle` →（接住宝石时）`Pulse`（0.15 秒快速放大回弹）→ 回到 Idle，再接 `GameOver`（变灰/下沉）。

要求（必须自己动手搭）：
1. 用 Animation 窗口录 `Pulse.anim` 与 `GameOver.anim`；
2. 建 controller，用 **Trigger 参数**驱动 Pulse（接住时 `SetTrigger`），用 **Bool** 驱动 GameOver；
3. 用 Any State 让 GameOver 能随时打断 Pulse；
4. 尝试在 Pulse 动画的峰值帧加一个动画事件 `OnCatchVfx()`，在脚本里打印日志。

### 验收清单

- [ ] 挡板每次接住宝石都会播放一次“回弹”，不会越叠越快或抖屏；
- [ ] 接住触发用的是 Trigger 且能重复触发（多次接住）——检查是否忘 ResetTrigger；
- [ ] GameOver 后挡板停在“死亡”姿势，不再播 Pulse；
- [ ] 动画事件的日志只在动画峰值帧出现一次；
- [ ] 你能否把 `normalizedTime` 用于“Pulse 播完才允许下一次接住反馈”。

### 自检题

1. 一段动画在文件系统里的后缀是什么？它本质记录了什么？
2. Animator 组件与 Animator Controller 是什么关系？
3. Bool 参数与 Trigger 参数的使用场景差异？
4. 为什么“受击/死亡”这种要随时打断当前动作的过渡常用 Any State？
5. 动画事件的作用是什么？方法名需要与什么一致？

## 6.8 本章小结与语言对照

| 概念 | 说明 | 与你已学知识的对照 |
| --- | --- | --- |
| AnimationClip | 时间轴上的属性关键帧 | 类似“配置化的状态变更序列” |
| Animator Controller | 状态机图资产 | 有限状态机（FSM），第四章状态机思想的工程化 |
| Parameter + Transition | 条件驱动状态跳转 | `if/switch` + 委托回调的结合体 |
| Bool / Trigger | 持续型 / 一次性触发条件 | 布尔开关 vs 一次性事件 |
| 动画事件 | 播到关键帧调用代码方法 | 回调/钩子（hook），类似 C# 事件 |
| `normalizedTime` | 播放进度（0~1+） | 状态进度的比例表达 |

> 状态机不只用于动画：**敌人 AI（巡逻/追击/攻击）、玩法流程（菜单/游戏/结算）都该用状态机**。学会 Animator 后你会发现，第四章的“小游戏状态机”与这里的图形化状态机是同一个思想的两副面孔。

**下一章预告**：离开 3D 的方块世界，看看 2D 游戏怎么做——精灵 Sprite、2D 物理，以及一个能运行的最小 2D 演示。
