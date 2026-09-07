# 第五章 uGUI 与 TextMeshPro 界面开发

> 前置：第三章 3.5 已带你“会建 uGUI”了（Canvas / Text / Button / onClick 回调）。但**会建 ≠ 能做好界面**。本章把 UI 系统讲扎实：Canvas 三种渲染模式、分辨率适配、锚点布局、事件系统、常用控件，并重点讲 TextMeshPro——游戏里文字（尤其中文）的正确姿势。
>
> 本章与演示工程的关系：`demo-project` 刻意使用 IMGUI 且不引用 `com.unity.ugui` 包，以保持“零第三方依赖可跑”。**uGUI / TextMeshPro 面向正式项目**，请在你自己新建的 Unity 工程里按本章步骤实践。

## 5.1 Canvas：所有 UI 的根，三种渲染模式

**uGUI 的一切 UI 控件都必须放在 Canvas 之下**。Canvas 决定这一块 UI “画在哪里、怎么被渲染”，右键 Hierarchy → **UI → Canvas** 创建时，Unity 会同时补好 `EventSystem`。

| 渲染模式（Render Mode） | 效果 | 典型用途 |
| --- | --- | --- |
| **Screen Space - Overlay** | UI 直接画在屏幕顶层，与 3D 无关，永远可见 | 血条、分数、背包、对话框（最常见） |
| **Screen Space - Camera** | UI 放在指定相机前一段距离，可被后期处理 / 相机特效影响 | 需要 UI 参与画面效果（描边、模糊）时 |
| **World Space** | UI 像 3D 物体一样摆在场景里，有位置有缩放 | 敌人头顶血条、3D 对话框、VR |

在 Inspector 选中 Canvas 的 `Canvas` 组件，改 Render Mode 即可切换。

**每一个 UI 控件（Button / Text / Image）在 Hierarchy 上都是 Canvas 的“子孙”**。深层含义：UI 控件的坐标不是世界坐标，而是相对于画布的**屏幕坐标**（见 5.4 RectTransform）。

## 5.2 CanvasScaler：同一套 UI 适配不同分辨率

新建 Canvas 会自动带上 **Canvas Scaler**。没有它，UI 的尺寸直接用“像素”，在手机横竖屏、窗口缩放时要么太小要么溢出。它的三种缩放模式：

| 模式 | 含义 | 何时用 |
| --- | --- | --- |
| Constant Pixel Size | 1:1 像素，不缩放 | PC 固定窗口、编辑器调试 |
| **Scale With Screen Size** | 以“参考分辨率”为准等比缩放 | 手机/跨平台首选 |
| Constant Physical Size | 按物理尺寸（英寸） | 极少用 |

用 **Scale With Screen Size** 时关键参数：
- **Reference Resolution**（参考分辨率）：假设设计分辨率，例如竖屏手机 `1080×1920`；
- **Screen Match Mode**：参考分辨率与你实际屏幕“不一样”时怎么处理。
  - Match Width Or Height：取宽高缩放比例的平均（0.5 最常用）；
  - Expand / Shrink：以更宽松/更紧的一边为准。

> 心法：**UI 按“设计分辨率”排布，运行时会整体等比缩放**。所以做界面时别关心每台机器的实际分辨率，只管你的参考分辨率下“好不好看”。

## 5.3 EventSystem 与事件是怎么到代码的

### 5.3.1 三个组件各司其职

| 组件 | 作用 |
| --- | --- |
| `EventSystem` | 全局事件管理器，每帧把输入分发到 UI |
| `StandaloneInputModule` | （旧输入系统）把鼠标/键盘/触摸变成 UI 事件；用新输入系统则换成 `InputSystemUIInputModule` |
| `GraphicRaycaster` | 挂在 Canvas 上，把屏幕射线命中到 UI 图形（决定“谁接收点击”） |

新建 Canvas 会自动创建 EventSystem。**删除 EventSystem 后所有按钮都会失灵**，这是新手排错第一站。

### 5.3.2 三种写事件的方式

**方式一：组件面板连线（最常用）**
选中 Button → Inspector → `OnClick()` 列表 → `+` → 把带脚本的对象拖入 → 下拉选方法：

```csharp
public class GameUI : MonoBehaviour
{
    public void Restart() { /* ... */ }
    public void Quit()    { /* ... */ }
}
```

**方式二：代码 AddListener**
```csharp
using UnityEngine;
using UnityEngine.UI;

public class UIBinder : MonoBehaviour
{
    public Button restartBtn;          // Inspector 拖入

    void Start()
    {
        restartBtn.onClick.AddListener(OnRestart);
        // 也支持 lambda：restartBtn.onClick.AddListener(() => Debug.Log("点了"));
    }

    void OnRestart() { Debug.Log("Restart!"); }
}
```

**方式三：组件实现事件接口（适合拖拽、悬停等精细交互）**
让一个普通组件实现 `IPointerClickHandler` 等接口，引擎会把事件派发到**射线命中的那个对象**上：

```csharp
using UnityEngine;
using UnityEngine.EventSystems;

public class DragMe : MonoBehaviour, IBeginDragHandler, IDragHandler, IEndDragHandler
{
    public void OnBeginDrag(PointerEventData e) { /* 拖拽开始 */ }
    public void OnDrag(PointerEventData e)
    {
        transform.position += (Vector3)e.delta;   // delta = 本帧鼠标位移
    }
    public void OnEndDrag(PointerEventData e) { /* 拖拽结束 */ }
}
```

常用事件接口一览：

| 接口 | 触发时机 |
| --- | --- |
| `IPointerClickHandler` | 点击 |
| `IPointerEnterHandler` / `IPointerExitHandler` | 鼠标进入 / 离开 |
| `IPointerDownHandler` / `IPointerUpHandler` | 按下 / 抬起（不等抬起才触发，适合连发） |
| `IBeginDragHandler` / `IDragHandler` / `IEndDragHandler` | 拖拽三阶段 |
| `IScrollHandler` | 滚轮 |
| `ISelectHandler` / `IDeselectHandler` | 选中 / 取消选中（键盘导航） |

## 5.4 RectTransform：UI 的世界观

普通 Transform 用位置/旋转/缩放描述“世界坐标”；UI 的 Transform 是 **RectTransform**，用“矩形”描述在屏幕中的布局。

### 5.4.1 四个核心概念

| 概念 | 含义 |
| --- | --- |
| **Pivot（轴心）** | 自身矩形上“哪个点”作为定位基准。`(0.5,0.5)` 中心、(0,0) 左下 |
| **Anchor（锚点）** | 锚定到**父矩形**的哪个位置（0~1 的小数，或用 preset 预设） |
| **anchoredPosition** | 本对象轴心相对锚点的偏移（像素/单位） |
| **sizeDelta** | 相对锚点定义的区域，宽高的“差量”。锚点重合时它等于实际宽高 |

**锚点预设（Anchor Presets）**：Inspector 的 RectTransform 左上角方形按钮弹出九宫格预设——中心、拉伸填满、左下/右下等。**UI 想“贴在屏幕哪一角/哪一边”，本质是选锚点，而不是改坐标。**

例如“分数永远在屏幕左上角”：
- 锚点选左上角（Anchor Preset 第一行第一列）；
- anchoredPosition 设 `(20, -20)` 表示离左上角往里 20px；
- 这样无论分辨率怎么变，它都贴左上角。

### 5.4.2 在代码里设锚点（全屏拉伸 / 底部居中）

```csharp
RectTransform rt = GetComponent<RectTransform>();

// 让这个控件填满整个父区域（四角锚点都对齐父四角 + 差量为 0）
rt.anchorMin = Vector2.zero;
rt.anchorMax = Vector2.one;
rt.offsetMin = Vector2.zero;
rt.offsetMax = Vector2.zero;

// 底部居中：锚点底部中心，再往上偏移 40px
rt.anchorMin = new Vector2(0.5f, 0f);
rt.anchorMax = new Vector2(0.5f, 0f);
rt.pivot = new Vector2(0.5f, 0f);
rt.anchoredPosition = new Vector2(0f, 40f);
rt.sizeDelta = new Vector2(300f, 60f);
```

### 5.4.3 屏幕坐标 ↔ UI 坐标

鼠标点击得到的 `Input.mousePosition` 是**屏幕像素坐标**，不能直接赋给 UI。转换靠：

```csharp
RectTransformUtility.ScreenPointToLocalPointInRectangle(
    canvasRect,                          // Canvas 的 RectTransform
    Input.mousePosition,                 // 屏幕坐标
    canvas.worldCamera,                  // Overlay 模式传 null
    out Vector2 localPoint);             // 得到的画布局部坐标
```

> 提示：Overlay 模式下 Canvas 的局部坐标 = “设计分辨率坐标”（受 CanvasScaler 影响），所以通常做法是：把 UI 上的点存在 RectTransform 里，需要“点击落点”时转成 localPoint 与 Rect 比较。

### 5.4.4 布局组件（自动排布）

手摆一堆按钮很累，uGUI 提供**布局组件**，可挂到任意对象让它自动排列子物体：

| 组件 | 效果 |
| --- | --- |
| `HorizontalLayoutGroup` | 子物体水平等排 |
| `VerticalLayoutGroup` | 子物体垂直等排 |
| `GridLayoutGroup` | 网格排布（背包格子） |
| `ContentSizeFitter` | 让容器随内容自适应大小（聊天记录） |
| `LayoutElement` | 手动指定某个子元素在布局中的宽高/弹性 |

例：**背包九宫格** = 一个带 `GridLayoutGroup` 的 Image + 9 个 Button，调 `Cell Size` 就自动对齐。

## 5.5 常用控件清单

在 Hierarchy 右键 → UI 下新建，这里列常用的：

| 控件 | 作用 | 常用事件/属性 |
| --- | --- | --- |
| **Text (TMP)** | 显示文字 | `.text` / `.SetText`，见 5.6 |
| **Image** | 显示图片/纯色块（UI 里叫 Image，不是 SpriteRenderer） | `.color`、`.sprite`、`raycastTarget` |
| **Button** | 按钮 | `.onClick`；视觉过渡 `Transition`（Tint/Sprite/Animation） |
| **Toggle** | 开关 | `.onValueChanged(bool)`、`.isOn` |
| **Slider** | 滑条（音量/进度） | `.onValueChanged(float)`、`.value` |
| **InputField (TMP)** | 文本输入 | `.onValueChanged`、`.onEndEdit` |
| **Scroll View** | 可滚动列表 | 内含 Viewport + Content |
| **Canvas Group** | 控制一组 UI 的显隐/透明度/是否可交互 | `.alpha`、`.interactable`、`.blocksRaycasts` |

> 小技巧：`Image` 的 `raycastTarget` 在“不需要接收点击的装饰图片”上记得关掉，否则它挡在按钮上面时按钮点不到——**“UI 点不动”头号排查项**。

## 5.6 TextMeshPro：游戏文字的现代方案（重点）

### 5.6.1 为什么不是旧版 Text

传统 `UnityEngine.UI.Text` 用系统字体位图渲染，缩放会发虚、边缘有锯齿、国际化麻烦、合批差。**TextMeshPro（TMP）** 是 Unity 官方的文字替代方案，默认新工程都用它：
- **SDF（Signed Distance Field）渲染**：把字形转成距离场，放大缩小都清晰锐利；
- 更丰富的排版（字符间距、字重、下划线、富文本标签）；
- 内存与合批表现更好。

> 认识一个类型名：`TextMeshProUGUI` = 用于 uGUI 的 TMP 文字组件；还有 `TextMeshPro`（世界空间 3D 文字）与 `TMP_Text`（二者基类）。

### 5.6.2 第一次使用前的步骤

1. 创建 **Text（TextMeshPro）** 时若弹提示缺少 Essential Resources：菜单 **Window → TextMeshPro → Import TMP Essential Resources**（会往 Assets 导入默认字体等基础资源），导完即可；
2. 场景里会看到带蓝色方块的是 **字体资源缺失** 的占位——先确保上一步完成。

### 5.6.3 中文字体：默认字体没有中文

TMP 默认字体（LiberationSans / 内置 fallback）**不含中文字形**，直接打中文会显示为方块或空白。正确姿势：

1. 准备一个含中文字形的字体文件（.ttf/.otf，如思源黑体、霞鹜文楷，注意版权）；
2. 右键该字体 → **Create → TextMeshPro → Font Asset**，生成 `xxx SDF.asset`；
3. 把生成的 SDF 字体资源拖给 TMP 文字的 `Font Asset` 字段。

**动态字体（推荐新手）**：在 Font Asset Creator 里把 Atlas Population Mode 设为 **Dynamic**，字体按需动态生成字形——一个中文字体文件即可覆盖所有汉字，不用担心“字没进图集”。代价是运行时首次遇到生僻字有一帧卡顿，可通过 `TMP Settings` 里配置 fallback 字体列表缓解。

### 5.6.4 富文本标签（Rich Text）

TMP 支持类似 HTML 的内联标签（Inspector 勾选 `Rich Text`），在字符串里直接写：

```csharp
scoreText.SetText("<color=#FFD700><b>{0}</b></color> 分", score);
// 更多：<size=60>大字</size>、<i>斜体</i>、<sprite=0> 图集表情、<link="..."> 可点击链接
```

> 注意 `<b>` 等标签要求字体资源勾选用了对应特性；大多数情况无需配置，开箱即用。

### 5.6.5 代码里引用 TMP（最常见的坑）

TMP 类型在 **`TMPro` 命名空间**，且 `Text` 会被脚本 API 的 `Text` 混淆，引用时别选错：

```csharp
using TMPro;                 // 关键：TMPro 命名空间

public class ScoreHud : MonoBehaviour
{
    public TextMeshProUGUI scoreText;    // 在 Inspector 拖 TMP 文字进来

    int _score;
    public void AddScore(int v)
    {
        _score += v;
        scoreText.SetText("分数：{0}", _score);   // SetText 比 "…"+_score 拼接更省 GC
    }
}
```

> 补充：如果你在 Unity 6 的新工程里看不到 `Text`（旧版）选项是正常的——新模板默认只提供 TMP。旧版 `UnityEngine.UI.Text` 仍存在于 `com.unity.ugui` 包，仅为历史兼容。

## 5.7 完整示例：给第四章「接宝石」换上一套 uGUI 界面

「接宝石」demo 用的是 IMGUI 计分。真实项目里你会想把它换成 Canvas 界面。下面是“编辑器搭界面 + 代码更新”的完整姿势（这是正式项目最典型的工作流）：

### 第 1 步：搭界面（编辑器操作）

1. 右键 Hierarchy → **UI → Canvas**（自动带 EventSystem）；
2. 给 Canvas 的 `CanvasScaler`：UI Scale Mode = **Scale With Screen Size**，Reference Resolution = `1280×720`；
3. 右键 Canvas → **UI → Text - TextMeshPro**，改名 `ScoreText`：锚点设为左上角，anchoredPosition `(20,-20)`，字号 40，内容“分数：0”；
4. 右键 Canvas → **UI → Button - TextMeshPro**，改名 `RestartBtn`：锚点底部居中，文字“重新开始”。

### 第 2 步：写脚本控制

```csharp
using TMPro;                     // TMP 文字
using UnityEngine;
using UnityEngine.UI;            // Button

public class CatchHud : MonoBehaviour
{
    public TextMeshProUGUI scoreText;      // 拖入 ScoreText
    public Button restartBtn;              // 拖入 RestartBtn
    public GameObject playerPaddle;        // 拖入玩家挡板（需要重置的对象）

    int _score;
    bool _gameOver;

    void Start()
    {
        restartBtn.onClick.AddListener(Restart);   // 方式二：代码绑定事件
    }

    public void AddScore(int v) { _score += v; scoreText.SetText("分数：{0}", _score); }

    public void GameOver() { _gameOver = true; }

    void Restart()
    {
        _score = 0;
        _gameOver = false;
        playerPaddle.transform.position = Vector3.zero;
        // 真实项目里这里还应清空场上宝石等（见第四章的状态机思想）
    }

    void Update()
    {
        // 给 GameOver 加个全屏遮罩？下面演示“用 CanvasGroup 淡入”
    }
}
```

### 第 3 步：让原有游戏脚本通过 HUD 对象汇报事件

把第四章 `CatchGame` 里的 `OnGUI` 计分**替换**为调用 HUD 引用：

```csharp
public CatchHud hud;      // Inspector 拖入挂 CatchHud 的对象

void OnCatchGem() { hud.AddScore(10); }
void OnMissGem()  { if (--lives <= 0) hud.GameOver(); }
```

> 这就是“**表现与逻辑分离**”的雏形：`CatchGame` 只管玩法，UI 长什么样由 `CatchHud` 决定。以后把 IMGUI 换成 TMP、甚至换成别的显示方式，玩法代码都不用动。

## 5.8 实战与验收

### 实战：给「接宝石」做个带生命条的完整 HUD

要求（全部用 uGUI + TextMeshPro，不用 IMGUI）：
1. 左上角：分数（TMP，黄色高亮，用富文本 `<color>` + `<b>`）；
2. 右上角：三条生命用三颗小 Image 表示，漏接一次少一颗；
3. 底部居中：“重新开始”按钮（点击后重置分数与生命，生命图恢复三颗）；
4. 游戏结束时：屏幕中央半透明黑色遮罩（一张全屏 Image，alpha≈0.6）+ “游戏结束”TMP 大字；
5. 用 `CanvasScaler` 让它在 1280×720 与 800×600 下都不跑偏。

### 验收清单

- [ ] 改变 Game 视图分辨率/横竖屏，HUD 仍贴合预期位置（锚点生效）；
- [ ] 按钮点击有视觉反馈（Button Transition 默认 Color Tint 即可）；
- [ ] 中文字体正常显示（不是方块）——说明你正确导入了中文字体资源；
- [ ] 用代码把按钮换成 `onClick.AddListener` 也能跑通；
- [ ] 在代码里通过接口 `IPointerDownHandler` 实现“按住加速下落”的按钮。

### 自检题

1. `Screen Space - Overlay` 与 `Screen Space - Camera` 的区别是什么？什么时候用后者？
2. 为什么“分数放左上角”应该改锚点而不是写死坐标？
3. `EventSystem` 被误删后，现象是什么？
4. `TextMeshProUGUI` 和 `Text` 的命名空间分别是什么？TMP 相比旧 Text 好在哪里？
5. 默认 TMP 打不了中文，至少说出一种解决方案。

## 5.9 本章小结与语言对照

| 概念 | 说明 | 与你已学知识的对照 |
| --- | --- | --- |
| Canvas | 所有 UI 的根，定义渲染空间 | 类似一个“专用场景层级”，UI 对象都在它之下 |
| RectTransform + 锚点 | UI 的布局坐标系统 | 类似 CSS 的 flex / 定位，锚点 = 参照系 |
| CanvasScaler | 分辨率适配 | 类似“响应式布局” |
| EventSystem | 输入分发到 UI | 事件总线/委托分发，与 C# 事件思想同源 |
| `onClick.AddListener` | 事件订阅 | 与 C# 委托 `+=` 高度相似 |
| TextMeshPro | SDF 高质量文字 | 字体资源=资源管线；`SetText` 格式串 = `string.Format` |
| 表现与逻辑分离 | 玩法与 UI 解耦 | 与后端 MVC / 关注点分离同思路 |

**下一章预告**：物体能动、UI 会显示之后，下一步是让角色“看起来在动”——动画系统 Animator 与状态机。
