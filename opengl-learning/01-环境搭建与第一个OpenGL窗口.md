# 第一章：渲染管线总览、环境搭建与第一个 OpenGL 窗口

> 目标：建立“GPU 如何画图”的整体心智模型；搭好一次 C++ + OpenGL 工程模板；跑出第一个窗口并在里面清屏换色。
>
> 前置：[返回 README](./README.md) ｜ 下一章：[02-绘制三角形与着色器.md](./02-绘制三角形与着色器.md)

本章是全程最“苦”但也最值的一步——之后每一章都是在这份工程上**增量替换源码**。请耐心读完概念部分再动手。

## 1.1 图形学第一课：GPU 是怎么画图的

### 1.1.1 光栅化渲染管线

屏幕上的一帧画面，是 CPU 向 GPU 下发“顶点 + 指令”，GPU 内部一条**流水线（渲染管线）**逐级处理的结果：

```text
   你的数据（顶点数组：位置/颜色/纹理坐标…）
        │
        ▼
① 顶点着色器 Vertex Shader    把每个顶点从“模型自己的坐标”变换到屏幕坐标；
        │                     也常在这里做逐顶点光照、变形
        ▼
② 图元装配 Primitive Assembly  把顶点按顺序连成三角形/线段/点
        │
        ▼
③ 光栅化 Rasterization        把三角形“翻译”成屏幕上的一个个像素（片元）
        │
        ▼
④ 片元着色器 Fragment Shader   逐像素计算颜色（贴图采样、光照都在这里）
        │
        ▼
⑤ 测试与混合 Tests & Blending  深度测试决定谁在前；透明度混合
        │
        ▼
   帧缓冲 Framebuffer ────────► 显示到窗口
```

- **顶点着色器**执行次数 = 顶点数；**片元着色器**执行次数 ≈ 屏幕上被三角形盖住的像素数——两者数量级差很多，所以“把复杂计算尽量放在顶点着色器或离线完成”是基本优化意识；
- ①②③⑤ 多数由 GPU 固定单元完成，**可编程的部分是 ① 和 ④**（用 GLSL 语言写），这是现代 GPU 的核心心智；
- “把数据塞给 GPU → 写两个着色器 → 画”就是 OpenGL 的日常循环，第二~五章将逐一打通。

### 1.1.2 OpenGL 是什么：它不是一个“库”

OpenGL 是 **Khronos 组织维护的一套规范（specification）**：规定有哪些函数（如 `glDrawArrays`）、参数是什么、状态机行为如何。真正实现它的是**显卡驱动**。也就是说：

- 你 include 的头文件里只有函数声明，函数实体在驱动里；
- 因此需要 **glad** 这类“加载器”：在运行时向驱动要每个函数的指针（1.3 节细讲）；
- 版本靠**上下文（Context）**区分：本专题统一用 **3.3 Core Profile**（现代可编程管线，也是 WebGL2/绝大多数教程的标准）。

### 1.1.3 状态机思维

OpenGL 是巨型**状态机**：先 `glEnable(...)` 打开某功能，后续所有绘制都受它影响；绑定什么对象（VAO/纹理/着色器）就画什么。调试“为什么没生效”时，第一反应是“现在处于什么状态”——这个思维比记 API 更重要。

## 1.2 C++ 最小实用子集（给非 C++ 读者的快速车道）

本专题用 C++，但**只用到很小一个子集**。你有 Java/Python/C#/Rust 基础的话，对照下表即可，遇到再查：

| 本专题用到的 C++ | 类似 | 一句话 |
| --- | --- | --- |
| `#include <...>` | import | 引入头文件；`"..."` 是本地文件 |
| `int main()` | 各语言入口 | 程序入口函数 |
| `std::vector<float>` | List/array | 自动扩容数组，`push_back` 追加 |
| `std::string` | String | 字符串；`std::to_string(n)` 转字符串 |
| `class / struct` | class | 与 C#/Java 基本一致；默认 struct 成员公开 |
| `new`（少用） | new | 我们用得极少，GL 对象用 `unsigned int` 句柄管理 |
| `glm::vec3/mat4` | 自写数学类 | GLM 数学库，四章起使用 |
| lambda `[capture](...){...}` | lambda | 与各语言一致 |
| `std::cout << x` | print | 控制台输出（配合 `std::endl`） |

几个易错点：
- C++ 用 `//` 行注释与 `/* */` 块注释；
- 函数内变量默认在栈上、离开作用域即销毁，但 GL 对象是 GPU 侧资源，需要手动 `glDelete*` 释放（本专题示例为了短小会在窗口退出时一次性清理，生产代码需认真管理）；
- 若你装的是 MSVC，使用**64 位 + x64** 配置，别用 x86。

## 1.3 三大依赖：GLFW、glad、GLM（与为什么缺一不可）

| 库 | 解决什么问题 | 说明 |
| --- | --- | --- |
| **GLFW** | 创建窗口、创建 OpenGL 上下文、收键盘/鼠标输入 | C 库，官方自带 CMake，跨平台 |
| **glad** | 加载 OpenGL 函数指针 | 因 OpenGL 是“规范”，函数实体在驱动里，需在运行时按名字取指针 |
| **GLM** | 向量/矩阵数学 | Header-only，API 与 GLSL 一致（第四章才真正用到） |

> **为什么必须 glad？** 你在代码里写的 `glClearColor` 其实是个宏或函数指针；Windows 上最古老的 `gl.h` 只到 OpenGL 1.1，3.3 的函数（如 `glGenVertexArrays`）必须通过 `wglGetProcAddress`/`glfwGetProcAddress` 动态取。glad 帮你自动完成：include 它的头 + 启动时调一次 `gladLoadGL`，之后就能像普通函数一样调用全部 GL API。网页在线生成（glad2）或本地生成均可，本专题用 **glad2**（生成头文件为 `glad/gl.h`）。

## 1.4 动手做：搭一份可复现的工程模板

> 本节是**一次性动作**。以下目录结构贯穿全书，请严格照做（文件名/目录名不要改，后续章节按此引用）。

### 步骤 1：准备目录与源码文件

```text
opengl-playground/
├── CMakeLists.txt
├── third_party/
│   ├── glad/
│   │   ├── include/glad/gl.h          ← glad2 在线生成，见步骤 2
│   │   ├── include/KHR/khrplatform.h  ← 同 zip 内自带
│   │   └── src/gl.c                   ← 同 zip 内自带（名字可能是 glad.c，CMake 用通配符）
│   └── glfw/                          ← GLFW 源码（步骤 3）
└── src/
    └── main.cpp                       ← 步骤 4 的代码
```

### 步骤 2：获取 glad（生成源码）

1. 打开在线生成器 **glad2**：<https://glad.dav1d.de/>（若不可用，可本地 `pip install glad` 后 `glad --api gl:core=3.3 --generator c` 生成）；
2. 页面选择：**API/Profile** = `gl` / `3.3` / `Core`；**Options** = 勾选 *Generate a loader*；**Language** = `C/C++`；
3. 点 **Generate** 下载 zip，解压后把 `include/` 与 `src/` 放进 `third_party/glad/`（合并到现有目录）。

### 步骤 3：获取 GLFW

任选其一：
- **方式 A（推荐，离线可控）**：从 <https://github.com/glfw/glfw/releases> 下载 **3.4 Source code** zip，解压到 `third_party/glfw/`（内部含 `CMakeLists.txt`）；
- 方式 B：clone `https://github.com/glfw/glfw` 到该目录。

> GLM 第四、五章才开始用；到时候只需往 `third_party/glm/` 放一个 header-only 目录，或直接用 FetchContent，第四章会给追加命令。

### 步骤 4：写 CMakeLists.txt（一次写好，之后不再改动）

```cmake
cmake_minimum_required(VERSION 3.16)
project(opengl_playground LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Debug)   # 调试构建：着色器报错会带行列号信息
endif()

# ---------- GLFW（自带 CMake）----------
set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/glfw)            # 生成目标 glfw

# ---------- glad（自己编一个小库）----------
file(GLOB GLAD_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/third_party/glad/src/*.c)
add_library(glad STATIC ${GLAD_SOURCES})
target_include_directories(glad PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/third_party/glad/include)

# ---------- 主程序 ----------
add_executable(playground src/main.cpp)

target_link_libraries(playground PRIVATE glad glfw)
if(WIN32)
    target_link_libraries(playground PRIVATE opengl32)   # Windows 必需
endif()
```

- MSVC 用户若报 *gl.h/glu.h 找不到*，是因为 CMake 默认链接了旧版 OpenGL 头，请在 `main.cpp` 顶部加 `#define GLFW_INCLUDE_NONE`（见步骤 5）即可屏蔽；
- macOS 上 GLFW target 已自动带 `-framework Cocoa/OpenGL` 等，无需额外处理。

### 步骤 5：写 main.cpp（第一个窗口）

```cpp
#define GLFW_INCLUDE_NONE          // 不让 GLFW 引入系统自带的旧版 gl.h（与 glad 冲突）
#include <glad/gl.h>               // 1) 先含 glad：提供 3.3 全部函数指针
#include <GLFW/glfw3.h>            // 2) 再含 GLFW：窗口与输入

#include <iostream>

// 窗口尺寸变化时被 GLFW 回调，重设视口
void FramebufferSizeCallback(GLFWwindow*, int width, int height)
{
    glViewport(0, 0, width, height);
}

int main()
{
    // ---------- 1. 初始化 GLFW ----------
    if (!glfwInit())
    {
        std::cerr << "GLFW 初始化失败" << std::endl;
        return -1;
    }

    // 明确要求 OpenGL 3.3 Core Profile（版本号主、次）
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
#ifdef __APPLE__
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);   // macOS 必加
#endif

    // ---------- 2. 创建窗口与上下文 ----------
    GLFWwindow* window = glfwCreateWindow(800, 600, "OpenGL 第一章：清屏换色",
                                          nullptr, nullptr);
    if (window == nullptr)
    {
        std::cerr << "创建窗口失败（驱动不支持 3.3？）" << std::endl;
        glfwTerminate();
        return -1;
    }
    glfwMakeContextCurrent(window);                  // 把窗口的 GL 上下文设为“当前”
    glfwSetFramebufferSizeCallback(window, FramebufferSizeCallback);
    glfwSwapInterval(1);                             // 垂直同步：限制帧率到屏幕刷新率

    // ---------- 3. 用 glad 加载函数指针 ----------
    // gladLoadGL 向驱动索要全部函数地址；失败则后面的 gl* 全是空指针调用
    if (!gladLoadGL(reinterpret_cast<GLADloadfunc>(glfwGetProcAddress)))
    {
        std::cerr << "glad 加载 OpenGL 函数失败" << std::endl;
        return -1;
    }

    // 打印显卡驱动信息，验证版本（应看到 3.3 或更高）
    std::cout << "渲染器: " << glGetString(GL_RENDERER) << std::endl;
    std::cout << "版本:   " << glGetString(GL_VERSION)   << std::endl;

    // 状态机初始设置（每帧需要 + 只设置一次的东西放这里）
    glViewport(0, 0, 800, 600);      // 把画布映射到窗口左下角起 800x600 区域
    glClearColor(0.13f, 0.13f, 0.17f, 1.0f);   // 清屏底色：深蓝灰（RGB 0~1）

    // ---------- 4. 主循环：一帧 = 处理输入 → 绘制 → 交换缓冲 ----------
    while (!glfwWindowShouldClose(window))       // 窗口没被点关闭就继续
    {
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
            glfwSetWindowShouldClose(window, true);   // 按 Esc 请求退出

        glClear(GL_COLOR_BUFFER_BIT);            // 用 glClearColor 的颜色刷一遍画布
        // 本帧真正的“绘制”命令从下一章开始出现在这里

        glfwSwapBuffers(window);                 // 把后缓冲推到屏幕（双缓冲，防闪烁）
        glfwPollEvents();                        // 处理键盘/鼠标/窗口事件
    }

    // ---------- 5. 清理退出 ----------
    glfwTerminate();
    return 0;
}
```

### 步骤 6：编译运行

PowerShell / 终端：

```powershell
cmake -S . -B build
cmake --build build --config Debug
.\build\Debug\playground.exe
```

（Linux/macOS 去掉 `.exe`；macOS 若用 Xcode 生成器请用 `--config Debug` 后到 `build/Debug/` 找产物。）

**预期结果**：出现 800×600 窗口，底色为深蓝灰 `(0.13,0.13,0.17)`，按 **Esc** 或点 X 关闭；控制台打印你的显卡名与 OpenGL 版本。

## 1.5 逐行拆解：这十几行里藏着全书的钥匙

| 代码 | 含义 / 为什么要这么写 |
| --- | --- |
| `#define GLFW_INCLUDE_NONE` | 防止 GLFW 顺带 include 系统旧版 `<GL/gl.h>`（它只有 1.1 函数），避免与 glad 冲突 |
| `#include <glad/gl.h>` 在前 | glad 头定义了全部 3.3 函数指针；必须先于任何 GL 头 |
| `glfwInit()` | 启动 GLFW 内部状态，失败多半是驱动/环境问题 |
| `glfwWindowHint(...)` | 在创建窗口前声明想要的上下文属性；3.3 + Core 是全书的固定配置 |
| `glfwCreateWindow` | 创建窗口与上下文；注意它**不会自动成为当前上下文** |
| `glfwMakeContextCurrent` | 之后所有 `gl*` 调用都作用于这个窗口 |
| `gladLoadGL(glfwGetProcAddress)` | 运行时解析函数指针；`reinterpret_cast` 是因为两个 C 库的函数指针签名不同 |
| `glViewport` | 把 NDC 画布映射到窗口像素区域；窗口 resize 由回调再次调用 |
| `glClearColor + glClear` | 状态机：设置清屏色（一个状态），再执行“用当前状态清屏” |
| 双缓冲 `glfwSwapBuffers` | 绘制写在后缓冲，交换后才显示；避免一帧一帧闪烁 |
| `glfwPollEvents` | 让 GLFW 派发输入/窗口事件（我们监听 Esc 依赖它） |

> 黑屏三连问（现在+以后通用）：
> 1. `gladLoadGL` 成功了吗？（失败 = 所有 gl 调用无效，控制台会打印错误信息）
> 2. `glfwMakeContextCurrent` 之后才调 glad 了吗？（顺序颠倒必黑屏）
> 3. `glViewport` 与窗口尺寸一致吗？（不一致会只画一角）

## 1.6 实战与验收

1. **换色**：把 `glClearColor` 四个参数改成 `(0.0f, 0.5f, 0.5f, 1.0f)`，重编译——应看到青色窗口；能想到哪三个参数是 RGB、第四个是什么吗？（Alpha 透明通道，窗口场景下暂用不到）
2. **改尺寸**：把窗口改成 1280×720，同时检查 `glViewport` 的宽高是否需要同步（Hint：用 `glfwSetFramebufferSizeCallback` 后其实可以只设一次初始值）。
3. **动起来（预告第二章）**：在 `while` 循环里每帧把清屏色做个正弦波动：
   ```cpp
   float t = (float)glfwGetTime();                       // GLFW 自启动以来的秒数
   glClearColor(0.5f + 0.5f * std::sin(t),
                0.3f + 0.3f * std::cos(t), 0.2f, 1.0f);
   ```
   能看到颜色平滑渐变，说明你已理解“每帧清屏 + 每帧变化 = 动画”的最小闭环。
4. **验收标准**：能闭眼说出主循环四行代码的顺序与各自作用；能解释“为什么需要 glad”。

## 本章小结

- GPU 画图 = 顶点着色器 → 光栅化 → 片元着色器 → 测试混合，四大步；前两步在第二章逐个看见；
- OpenGL 是规范不是库，需要 glad 在运行时加载函数、GLFW 造窗口、GLM 算数学；
- 主线循环 = 处理输入 → 清屏/绘制 → `glfwSwapBuffers` → `glfwPollEvents`；
- 你已拥有全书统一的工程模板，后面每章都是增量替换源码。

下一章：[02-绘制三角形与着色器.md](./02-绘制三角形与着色器.md)——把第一个三角形画上屏幕。
